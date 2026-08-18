package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.AbstractSellTraderRoleHandler;
import com.seggellion.britannia_mod.npc.NpcRoleHandler;
import com.seggellion.britannia_mod.npc.NpcType;
import com.seggellion.britannia_mod.npc.TraderRoleHandlers;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

public final class ServerCatalogService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private ServerCatalogService() {}

    public static void fetchAndSend(ServerPlayer player, NpcType type, String role, String city, int entityId) {
        if (!validText(role) || !validText(city) || !validInteraction(player, entityId)) {
            LOGGER.warn("Rejected invalid NPC catalog request before Rails fetch");
            return;
        }
        MinecraftServer server = player.server;
        // Vendor/Trader Milestone 7: economic projections (stamped by the
        // assignment reconciler) route to the Rails-authoritative Product
        // catalog. Resolved here, on-thread, before the off-thread fetch.
        //
        // Which catalog depends on the registry's `kind`, NOT on whether the entity happens to
        // carry a type key. Presence of a key says only "Rails owns this NPC"; `kind` says which
        // direction it trades. Routing on presence sent every Trader to the retail endpoint,
        // which holds no listing for any trader key and so answered each one with an empty rows
        // array -- the whole of the "I am not interested in anything you have" bug.
        final String economicTypeKey;
        final java.util.UUID economicCityId;
        final EconomicBuybackCatalogService.Prepared buyback;
        if (player.serverLevel().getEntity(entityId)
                instanceof com.seggellion.britannia_mod.entity.CitizenEntity citizen
                && citizen.getEconomicNpcTypeKey() != null) {
            economicTypeKey = citizen.getEconomicNpcTypeKey();
            economicCityId = citizen.getEconomicCityPublicId();
            buyback = tradesWithPlayers(economicTypeKey, type)
                    ? EconomicBuybackCatalogService.prepare(player, citizen)
                    : null;
        } else {
            economicTypeKey = null;
            economicCityId = null;
            buyback = null;
        }
        try {
            ServerHttpExecutor.submit(server,
                    () -> fetch(player, type, role, city, economicTypeKey, economicCityId, buyback))
                .whenComplete((quote, failure) -> server.execute(() -> {
                    if (failure != null || quote == null) {
                        LOGGER.warn("NPC catalog fetch failed for role={} city={}", role, city);
                        return;
                    }
                    if (server.getPlayerList().getPlayer(player.getUUID()) != player || !validInteraction(player, entityId)) return;
                    ClientboundOpenNpcScreenPayload.sendResolved(
                        player, type, role, city, entityId, quote.products(), quote.notices());
                }));
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("NPC catalog proxy queue is full");
        }
    }

    /**
     * Whether an economic projection buys FROM players rather than selling TO them.
     *
     * <p>The Rails registry is the authority: {@code kind} is constrained to {@code vendor} or
     * {@code trader} at both model and database level. The entity's own {@link NpcType} is the
     * fallback for the window before the registry has synced -- Traders spawn as their concrete
     * {@code AbstractTraderEntity} and Vendors as an {@code AbstractEconomyMerchantEntity}, so
     * it agrees with {@code kind} in every case the reconciler can produce.
     */
    static boolean tradesWithPlayers(String economicTypeKey, NpcType type) {
        var definition = com.seggellion.britannia_mod.service.EconomicNpcRegistryCache.snapshot()
                .economicNpcTypes().get(economicTypeKey);
        if (definition == null) {
            LOGGER.warn("Economic NPC registry has no entry for {}; routing catalog by entity type {}",
                    economicTypeKey, type);
            return type == NpcType.TRADER;
        }
        return "trader".equals(definition.kind());
    }

    private static EconomicBuybackCatalogService.Quote fetch(
            ServerPlayer player, NpcType type, String role, String city,
            String economicTypeKey, java.util.UUID economicCityId,
            EconomicBuybackCatalogService.Prepared buyback) {
        try {
            if (buyback != null) {
                return EconomicBuybackCatalogService.fetch(player, buyback);
            }
            if (economicTypeKey != null && economicCityId != null) {
                return products(fetchEconomicCatalog(player, economicTypeKey, economicCityId));
            }
            if (type == NpcType.MERCHANT && isEconomyMerchant(role)) {
                return products(MerchantCatalogBuilder.build(
                    CityCommodityApi.fetchServer(player.serverLevel(), city), MerchantRecipes.forRole(role)
                ).stream().map(MerchantCatalogEntry::product).limit(ClientboundOpenNpcScreenPayload.MAX_PRODUCTS).toList());
            }
            if (type == NpcType.MERCHANT) return products(fetchMerchantCatalog(player, role, city));
            return products(fetchTraderCatalog(player, type, role, city));
        } catch (Exception error) {
            LOGGER.warn("Server catalog proxy failed: {}", error.toString());
            return products(List.of());
        }
    }

    private static EconomicBuybackCatalogService.Quote products(List<Product> products) {
        return new EconomicBuybackCatalogService.Quote(products, List.of());
    }

    /**
     * Vendor/Trader Milestone 7: the Rails Product catalog is the authority for
     * economic Vendors -- prices, denominations, and availability are computed
     * in Rails; unavailable rows are simply not offered (fail closed, no
     * fallback catalog). Purchases settle through {@link
     * EconomicVendorPurchaseService} (Milestone 14), which re-quotes from this
     * same endpoint server-side at buy time.
     */
    private static List<Product> fetchEconomicCatalog(
            ServerPlayer player, String economicTypeKey, java.util.UUID cityPublicId
    ) throws Exception {
        Map<String, String> query = Map.of(
            "economic_npc_type_key", economicTypeKey,
            "city_public_id", cityPublicId.toString()
        );
        String body = request(player, "GET", Endpoint.ECONOMIC_CATALOG, query, null);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("rows") || !root.get("rows").isJsonArray()) return List.of();

        String catalogRevision = root.has("catalog_revision")
                ? root.get("catalog_revision").getAsString() : "";
        List<Product> products = new java.util.ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rows")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (!row.has("available") || !row.get("available").getAsBoolean()) continue;
            String itemId = row.get("item_id").getAsString();
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.parse(itemId));
            if (item == net.minecraft.world.item.Items.AIR) continue;

            int price = (int) Math.ceil(row.get("unit_price").getAsDouble());
            String denomination = row.has("denomination")
                    ? row.get("denomination").getAsString() : "copper";
            int availableUnits = row.has("available_units") && !row.get("available_units").isJsonNull()
                    ? row.get("available_units").getAsInt() : 0;

            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("max_quantity", availableUnits);
            tag.putString("economic_catalog_revision", catalogRevision);
            // Vendor/Trader Milestone 13: material/quality metadata for
            // recipe-valued goods; Milestone 17 item construction consumes it.
            if (row.has("selected_material") && !row.get("selected_material").isJsonNull()) {
                tag.putString("economic_material", row.get("selected_material").getAsString());
            }
            if (row.has("quality") && !row.get("quality").isJsonNull()) {
                tag.putString("economic_quality", row.get("quality").getAsString());
            }
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));

            String name = row.has("display_name") ? row.get("display_name").getAsString() : itemId;
            products.add(new Product(itemId, name, price, denomination, stack));
            if (products.size() >= ClientboundOpenNpcScreenPayload.MAX_PRODUCTS) break;
        }
        return products;
    }

    private static List<Product> fetchMerchantCatalog(ServerPlayer player, String role, String city) throws Exception {
        Map<String, String> query = Map.of(
            "city", city,
            "role", role,
            "npc_type", role,
            "shard", ServerAuthRegistry.credentials(player.server).orElseThrow().shardName()
        );
        return parseProducts(request(player, "GET", Endpoint.CATALOG, query, null));
    }

    private static List<Product> fetchTraderCatalog(ServerPlayer player, NpcType type, String role, String city) throws Exception {
        NpcRoleHandler handler = TraderRoleHandlers.create(type, role, city);
        if (!(handler instanceof AbstractSellTraderRoleHandler sellHandler)) return List.of();
        JsonObject payload = new JsonObject();
        payload.addProperty("city", city);
        payload.addProperty("role", role);
        payload.addProperty("shard", ServerAuthRegistry.credentials(player.server).orElseThrow().shardName());
        payload.add("inventory", sellHandler.collectServerInventory(player));
        return parseProducts(request(player, "POST", Endpoint.TRADER_CATALOG, Map.of(), payload.toString()));
    }

    private static String request(ServerPlayer player, String method, Endpoint endpoint,
                                  Map<String, String> query, String body) throws Exception {
        var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow().apiUrls()
            .resolveQuery(endpoint, query);
        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(connection);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        byte[] encoded = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (encoded.length > 262_144) throw new IllegalArgumentException("Catalog request too large");
        if (!RailsRequestAuthenticator.apply(connection, player.server, encoded)) {
            throw new IllegalStateException("Server authentication unavailable");
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream output = connection.getOutputStream()) { output.write(encoded); }
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("Catalog endpoint returned HTTP " + status);
        return BoundedHttp.readUtf8(connection.getInputStream(), MAX_RESPONSE_BYTES);
    }

    private static List<Product> parseProducts(String body) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) return List.of();
        JsonArray array = parsed.getAsJsonArray();
        List<Product> products = new ArrayList<>();
        for (JsonElement element : array) {
            if (products.size() >= ClientboundOpenNpcScreenPayload.MAX_PRODUCTS || !element.isJsonObject()) break;
            JsonObject object = element.getAsJsonObject();
            String itemId = boundedString(object, "item_id", 128);
            String name = boundedString(object, "item_name", 256);
            String currency = object.has("currency") ? boundedString(object, "currency", 16) : "copper";
            if (itemId == null || name == null || !currency.matches("(?i)gold|silver|copper")) continue;
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            double rawPrice = object.has("price") ? object.get("price").getAsDouble() : -1;
            if (!Double.isFinite(rawPrice) || rawPrice < 0 || rawPrice > Integer.MAX_VALUE) continue;
            ResourceLocation icon = null;
            if (object.has("icon") && !object.get("icon").isJsonNull()) {
                String rawIcon = boundedString(object, "icon", 256);
                icon = rawIcon == null ? null : ResourceLocation.tryParse(rawIcon);
            }
            products.add(new Product(itemId, name, (int) Math.round(rawPrice), currency,
                new ItemStack(item), icon));
        }
        return List.copyOf(products);
    }

    private static String boundedString(JsonObject object, String key, int maxLength) {
        if (!object.has(key) || object.get(key).isJsonNull()) return null;
        String value = object.get(key).getAsString().trim();
        return value.isEmpty() || value.length() > maxLength ? null : value;
    }

    private static boolean validInteraction(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        return entity != null && entity.isAlive() && player.distanceToSqr(entity) <= 64.0;
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.length() <= 100
            && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean isEconomyMerchant(String role) {
        String lower = role.toLowerCase(Locale.ROOT);
        return lower.contains("baker") || lower.contains("tavernkeeper") || lower.contains("costermonger");
    }

}

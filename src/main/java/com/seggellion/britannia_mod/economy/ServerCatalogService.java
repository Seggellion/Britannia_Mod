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
        try {
            ServerHttpExecutor.submit(server, () -> fetch(player, type, role, city))
                .whenComplete((products, failure) -> server.execute(() -> {
                    if (failure != null || products == null) {
                        LOGGER.warn("NPC catalog fetch failed for role={} city={}", role, city);
                        return;
                    }
                    if (server.getPlayerList().getPlayer(player.getUUID()) != player || !validInteraction(player, entityId)) return;
                    ClientboundOpenNpcScreenPayload.sendResolved(player, type, role, city, entityId, products);
                }));
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("NPC catalog proxy queue is full");
        }
    }

    private static List<Product> fetch(ServerPlayer player, NpcType type, String role, String city) {
        try {
            if (type == NpcType.MERCHANT && isEconomyMerchant(role)) {
                return MerchantCatalogBuilder.build(
                    CityCommodityApi.fetchServer(player.serverLevel(), city), MerchantRecipes.forRole(role)
                ).stream().map(MerchantCatalogEntry::product).limit(ClientboundOpenNpcScreenPayload.MAX_PRODUCTS).toList();
            }
            if (type == NpcType.MERCHANT) return fetchMerchantCatalog(player, role, city);
            return fetchTraderCatalog(player, type, role, city);
        } catch (Exception error) {
            LOGGER.warn("Server catalog proxy failed: {}", error.toString());
            return List.of();
        }
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
        if (!RailsRequestAuthenticator.apply(connection, player.server)) {
            throw new IllegalStateException("Server authentication unavailable");
        }
        if (body != null) {
            byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > 262_144) throw new IllegalArgumentException("Catalog request too large");
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

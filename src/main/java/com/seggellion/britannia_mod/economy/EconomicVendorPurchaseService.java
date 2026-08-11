package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.item.CityProvenanceItemData;
import com.seggellion.britannia_mod.network.payload.BuyMerchantItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 14: retail purchases from an economic Vendor
 * projection. Rails is the price/production authority end to end -- this
 * service quotes from the Rails catalog, settles in EXACTLY the quoted coin
 * denomination (no cross-denomination conversion, no mixed-denomination
 * checkout, per the ratified currency rules), and posts the transaction to
 * {@code economic_purchase}, which re-validates every line through the same
 * {@code ProductAvailability} authority and consumes the city's production
 * inputs atomically. A stale quote is rejected by Rails ({@code
 * quote_changed}) and the player's coins are refunded untouched -- the client
 * never "wins" a price race.
 *
 * <p>The legacy {@link MerchantEconomyService} path (MerchantRecipes catalog,
 * everything-to-copper settlement) remains for non-economic merchants only;
 * stamped {@link CitizenEntity} projections must never fall through to it.
 */
public final class EconomicVendorPurchaseService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_PURCHASE_DISTANCE_SQ = 12.0D * 12.0D;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    private EconomicVendorPurchaseService() {
    }

    /** True when this projection carries the reconciler-stamped economic identity. */
    public static boolean isEconomicVendor(@Nullable CitizenEntity citizen) {
        return citizen != null
                && citizen.getWorldNpcPublicId() != null
                && citizen.getEconomicNpcTypeKey() != null
                && citizen.getEconomicCityPublicId() != null;
    }

    public static void buyRequestedItems(ServerPlayer player, CitizenEntity vendor,
                                         BuyMerchantItemsC2SPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!isEconomicVendor(vendor) || !vendor.isAlive()) {
            fail(player, "Merchant not found.");
            return;
        }
        if (player.distanceToSqr(vendor) > MAX_PURCHASE_DISTANCE_SQ) {
            fail(player, "You are too far away from the merchant.");
            return;
        }
        if (payload.items() == null || payload.items().isEmpty()) {
            fail(player, "Your cart is empty.");
            return;
        }

        UUID worldNpcPublicId = vendor.getWorldNpcPublicId();
        String typeKey = vendor.getEconomicNpcTypeKey();
        UUID cityPublicId = vendor.getEconomicCityPublicId();
        MinecraftServer server = level.getServer();
        ServerHttpExecutor
                .submit(server, () -> {
                    try {
                        return fetchQuotes(player, typeKey, cityPublicId);
                    } catch (Exception e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                })
                .whenComplete((quotes, quoteError) -> server.execute(() -> {
                    if (quoteError != null || quotes == null) {
                        LOGGER.warn("Economic purchase quote failed npc={} type={} error={}",
                                worldNpcPublicId, typeKey, String.valueOf(quoteError));
                        fail(player, "Purchase failed: economy server unavailable.");
                        return;
                    }
                    Order order = buildOrder(quotes, payload.items());
                    if (!order.success()) {
                        fail(player, order.message());
                        return;
                    }
                    if (!reserveExactDenomination(player, order.denomination(), order.total())) {
                        fail(player, "You do not have enough " + order.denomination() + " for this purchase.");
                        return;
                    }

                    String idempotencyKey = "economic:" + player.getUUID() + ":" + worldNpcPublicId + ":" +
                            level.getGameTime() + ":" + UUID.randomUUID();
                    ServerHttpExecutor
                            .submit(server, () -> postPurchase(player, worldNpcPublicId, order, idempotencyKey))
                            .whenComplete((posted, postError) -> server.execute(() -> {
                                if (postError != null || !posted.success()) {
                                    refundDenomination(player, order.denomination(), order.total());
                                    String reason = postError == null
                                            ? posted.message()
                                            : "Purchase failed: economy server unavailable.";
                                    LOGGER.warn("Economic purchase rejected key={} npc={} error={} body={}",
                                            idempotencyKey, worldNpcPublicId, postError,
                                            posted == null ? "" : posted.body());
                                    fail(player, reason);
                                    return;
                                }
                                grantPurchasedItems(player, vendor, order, posted.body());
                                player.sendSystemMessage(Component.literal(
                                        "Purchase complete: " + order.total() + " " + order.denomination() + "."));
                                TransactionSuccessS2CPayload.send(player);
                            }));
                }));
    }

    /**
     * Fetches the current Rails quote rows for this vendor's type and city.
     * These become the {@code expected_unit_price}/{@code expected_denomination}
     * the purchase is posted with -- Rails re-derives its own price at commit
     * time and rejects on any mismatch, so this quote can only ever UNDERPAY
     * into a rejection, never overrule the authority.
     */
    private static Map<String, QuoteRow> fetchQuotes(ServerPlayer player, String typeKey,
                                                     UUID cityPublicId) throws Exception {
        var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow().apiUrls()
                .resolveQuery(Endpoint.ECONOMIC_CATALOG, Map.of(
                        "economic_npc_type_key", typeKey,
                        "city_public_id", cityPublicId.toString()
                ));
        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(connection);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        if (!RailsRequestAuthenticator.apply(connection, player.server, new byte[0])) {
            throw new IllegalStateException("Server authentication unavailable");
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Quote endpoint returned HTTP " + status);
        }
        String body = BoundedHttp.readUtf8(connection.getInputStream(), MAX_RESPONSE_BYTES);

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        Map<String, QuoteRow> rows = new HashMap<>();
        if (!root.has("rows") || !root.get("rows").isJsonArray()) return rows;
        for (JsonElement element : root.getAsJsonArray("rows")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (!row.has("available") || !row.get("available").getAsBoolean()) continue;
            if (!row.has("item_id") || !row.has("unit_price")) continue;
            String itemId = row.get("item_id").getAsString();
            rows.put(normalize(itemId), new QuoteRow(
                    itemId,
                    row.get("unit_price").getAsDouble(),
                    row.has("denomination") ? row.get("denomination").getAsString() : "copper",
                    row.has("available_units") && !row.get("available_units").isJsonNull()
                            ? row.get("available_units").getAsInt() : 0,
                    stringOrNull(row, "selected_material"),
                    stringOrNull(row, "quality"),
                    row.has("display_name") ? row.get("display_name").getAsString() : itemId
            ));
        }
        return rows;
    }

    private static Order buildOrder(Map<String, QuoteRow> quotes,
                                    List<BuyMerchantItemsC2SPayload.ItemRequest> requests) {
        List<OrderLine> lines = new ArrayList<>();
        String denomination = null;
        int total = 0;
        for (BuyMerchantItemsC2SPayload.ItemRequest request : requests) {
            int quantity = Math.max(0, request.quantity());
            if (quantity == 0) continue;
            QuoteRow row = quotes.get(normalize(request.itemId()));
            if (row == null) {
                return Order.failure(request.itemName() + " is no longer available.");
            }
            if (quantity > row.availableUnits()) {
                return Order.failure("Only " + row.availableUnits() + " " + row.displayName() + " are available.");
            }
            if (denomination == null) {
                denomination = row.denomination();
            } else if (!denomination.equals(row.denomination())) {
                // Owner decision: no mixed-denomination checkout, ever.
                return Order.failure("These items must be bought separately: each purchase settles in a single coin denomination.");
            }
            lines.add(new OrderLine(row, quantity));
            total += (int) Math.round(row.unitPrice() * quantity);
        }
        if (lines.isEmpty()) {
            return Order.failure("Your cart is empty.");
        }
        return Order.success(lines, denomination, total);
    }

    private static PostResult postPurchase(ServerPlayer player, UUID worldNpcPublicId,
                                           Order order, String idempotencyKey) {
        try {
            var requestUri = ServerAuthRegistry.credentials(player.server).orElseThrow().apiUrls()
                    .resolve(Endpoint.ECONOMIC_PURCHASE);
            HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Idempotency-Key", idempotencyKey);

            JsonObject payload = new JsonObject();
            payload.addProperty("idempotency_key", idempotencyKey);
            payload.addProperty("world_npc_public_id", worldNpcPublicId.toString());
            payload.addProperty("player_uuid", player.getStringUUID());
            payload.addProperty("player_name", player.getGameProfile().getName());
            JsonArray items = new JsonArray();
            for (OrderLine line : order.lines()) {
                JsonObject item = new JsonObject();
                item.addProperty("item_id", line.row().itemId());
                item.addProperty("quantity", line.quantity());
                item.addProperty("expected_unit_price", line.row().unitPrice());
                item.addProperty("expected_denomination", line.row().denomination());
                items.add(item);
            }
            payload.add("items", items);

            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            if (!RailsRequestAuthenticator.apply(conn, player.server, bodyBytes)) {
                throw new IllegalStateException("Server authentication unavailable");
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            String body = BoundedHttp.readUtf8(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    MAX_RESPONSE_BYTES);
            if (status < 200 || status >= 300) {
                return PostResult.failure(body, rejectionMessage(body));
            }
            LOGGER.info("Economic purchase accepted key={} npc={} total={} {}",
                    idempotencyKey, worldNpcPublicId, order.total(), order.denomination());
            return PostResult.success(body);
        } catch (Exception e) {
            return PostResult.failure(e.toString(), "Purchase failed: economy server unavailable.");
        }
    }

    private static String rejectionMessage(String body) {
        String code = errorCode(body);
        return switch (code) {
            case "quote_changed" -> "The vendor's prices have changed. Please reopen the shop.";
            case "insufficient_stock", "product_unavailable", "product_not_offered",
                 "insufficient_commodity_supply", "missing_required_commodity" ->
                    "The vendor can no longer supply that item.";
            case "mixed_payout_denominations" ->
                    "These items must be bought separately: each purchase settles in a single coin denomination.";
            case "vendor_not_assigned", "vendor_not_found" -> "This vendor is not open for business.";
            default -> "Purchase rejected by economy server.";
        };
    }

    private static String errorCode(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonObject response = JsonParser.parseString(body).getAsJsonObject();
            if (response.has("error") && response.get("error").isJsonObject()) {
                JsonObject error = response.getAsJsonObject("error");
                if (error.has("code")) return error.get("code").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    /**
     * Grants the purchased stacks from the Rails response's construction data
     * ({@code purchase_items}: material, quality, origin city), falling back to
     * the quoted row and the vendor's own reconciler-stamped city when a field
     * is absent. Weight is deliberately NOT invented here: MerchantRecipes'
     * weight tables are legacy-catalog data and Rails {@code
     * Product.requirements} is the only production authority for economic
     * goods, so stacks carry the item's default weight semantics instead.
     */
    private static void grantPurchasedItems(ServerPlayer player, CitizenEntity vendor,
                                            Order order, String responseBody) {
        Map<String, JsonObject> constructionByItemId = parsePurchaseItems(responseBody);
        for (OrderLine line : order.lines()) {
            QuoteRow row = line.row();
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.itemId()));
            if (item == Items.AIR) continue;
            JsonObject construction = constructionByItemId.get(normalize(row.itemId()));

            String material = construction != null
                    ? stringOrNull(construction, "selected_material") : row.selectedMaterial();
            String quality = construction != null
                    ? stringOrNull(construction, "quality") : row.quality();
            UUID originCityId = vendor.getEconomicCityPublicId();
            String originCityName = vendor.getCityName();
            if (construction != null && stringOrNull(construction, "origin_city_public_id") != null
                    && stringOrNull(construction, "origin_city_name") != null) {
                try {
                    originCityId = UUID.fromString(construction.get("origin_city_public_id").getAsString());
                    originCityName = construction.get("origin_city_name").getAsString();
                } catch (IllegalArgumentException ignored) {
                }
            }

            for (int granted = 0; granted < line.quantity(); granted++) {
                ItemStack stack = new ItemStack(item, 1);
                if (material != null || quality != null) {
                    CompoundTag tag = stack.getOrDefault(
                            DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
                    if (material != null) tag.putString("economic_material", material);
                    if (quality != null) tag.putString("economic_quality", quality);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
                CityProvenanceItemData.apply(stack, originCityId, originCityName);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
    }

    private static Map<String, JsonObject> parsePurchaseItems(String body) {
        Map<String, JsonObject> byItemId = new HashMap<>();
        if (body == null || body.isBlank()) return byItemId;
        try {
            JsonObject response = JsonParser.parseString(body).getAsJsonObject();
            if (!response.has("purchase_items") || !response.get("purchase_items").isJsonArray()) {
                return byItemId;
            }
            for (JsonElement element : response.getAsJsonArray("purchase_items")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (item.has("item_id")) byItemId.put(normalize(item.get("item_id").getAsString()), item);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Economic purchase response parse failed body={} error={}", body, e.toString());
        }
        return byItemId;
    }

    @Nullable
    public static Item coinItemFor(String denomination) {
        return switch (denomination == null ? "" : denomination.toLowerCase(Locale.ROOT)) {
            case "gold" -> ItemRegistry.GOLD_COIN.get();
            case "silver" -> ItemRegistry.SILVER_COIN.get();
            case "copper" -> ItemRegistry.COPPER_COIN.get();
            default -> null;
        };
    }

    /**
     * Exact-denomination settlement (public static for GameTest access, the
     * codebase's established pattern): removes exactly {@code amount} coins of
     * {@code denomination} from the player's inventory, or removes NOTHING and
     * returns false. Coins of other denominations are never counted, converted,
     * or touched -- a player rich in gold but short a silver cannot buy a
     * silver-priced item, by the ratified no-automatic-conversion rule.
     */
    public static boolean reserveExactDenomination(ServerPlayer player, String denomination, int amount) {
        Item coin = coinItemFor(denomination);
        if (coin == null || amount < 0) return false;
        if (countDenomination(player, denomination) < amount) return false;

        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != coin) continue;
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            remaining -= take;
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    /** Sums only coins of the given denomination -- no cross-denomination valuation. */
    public static int countDenomination(ServerPlayer player, String denomination) {
        Item coin = coinItemFor(denomination);
        if (coin == null) return 0;
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == coin) total += stack.getCount();
        }
        return total;
    }

    /** Returns coins of exactly the reserved denomination (rejection/outage refunds). */
    public static void refundDenomination(ServerPlayer player, String denomination, int amount) {
        Item coin = coinItemFor(denomination);
        if (coin == null || amount <= 0) return;
        int maxStack = new ItemStack(coin).getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(coin, give);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= give;
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static String stringOrNull(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        TransactionFailedS2CPayload.send(player, message);
    }

    private record QuoteRow(String itemId, double unitPrice, String denomination, int availableUnits,
                            @Nullable String selectedMaterial, @Nullable String quality, String displayName) {
    }

    private record OrderLine(QuoteRow row, int quantity) {
    }

    private record Order(boolean success, List<OrderLine> lines, String denomination, int total, String message) {
        static Order success(List<OrderLine> lines, String denomination, int total) {
            return new Order(true, List.copyOf(lines), denomination, total, "");
        }

        static Order failure(String message) {
            return new Order(false, List.of(), "", 0, message);
        }
    }

    private record PostResult(boolean success, String body, String message) {
        static PostResult success(String body) {
            return new PostResult(true, body, "");
        }

        static PostResult failure(String body, String message) {
            return new PostResult(false, body, message);
        }
    }
}

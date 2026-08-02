package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.entity.AbstractEconomyMerchantEntity;
import com.seggellion.britannia_mod.network.payload.BuyMerchantItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.shop.Product;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MerchantEconomyService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_PURCHASE_DISTANCE_SQ = 12.0D * 12.0D;

    private MerchantEconomyService() {
    }

    public static void buyRequestedItems(ServerPlayer player, BuyMerchantItemsC2SPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof AbstractEconomyMerchantEntity merchant) || !merchant.isAlive()) {
            fail(player, "Merchant not found.");
            return;
        }
        if (player.distanceToSqr(merchant) > MAX_PURCHASE_DISTANCE_SQ) {
            fail(player, "You are too far away from the merchant.");
            return;
        }

        String city = merchant.getCityName() == null || merchant.getCityName().isBlank()
                ? payload.city()
                : merchant.getCityName();
        String role = merchantRole(merchant, payload.role());
        if (city == null || city.isBlank()) {
            fail(player, "This merchant is not associated with a city.");
            return;
        }

        MinecraftServer server = level.getServer();
        ServerHttpExecutor
                .submit(server, () -> preparePurchase(level, city, role, payload.items()))
                .whenComplete((prepared, error) -> server.execute(() -> {
                    if (error != null) {
                        LOGGER.warn("Merchant purchase preparation failed city={} role={} error={}", city, role, error.toString());
                        fail(player, "Purchase failed: economy server unavailable.");
                        return;
                    }
                    if (!prepared.success()) {
                        fail(player, prepared.message());
                        return;
                    }

                    CoinReservation coins = reserveCoins(player, prepared.totalCopper());
                    if (!coins.success()) {
                        fail(player, "You do not have enough coin for this purchase.");
                        return;
                    }

                    String idempotencyKey = "merchant:" + player.getUUID() + ":" + merchant.getUUID() + ":" +
                            level.getGameTime() + ":" + UUID.randomUUID();
                    ServerHttpExecutor
                            .submit(server, () -> postPurchase(level, player, merchant, city, role, prepared, idempotencyKey))
                            .whenComplete((posted, postError) -> server.execute(() -> {
                                if (postError != null || !posted.success()) {
                                    coins.refund(player);
                                    String reason = postError == null ? posted.message() : "Purchase failed: economy server unavailable.";
                                    LOGGER.warn("Merchant purchase rejected key={} city={} role={} error={} body={}",
                                            idempotencyKey, city, role, postError, posted == null ? "" : posted.body());
                                    fail(player, reason);
                                    return;
                                }

                                grantProducts(player, prepared.entries());
                                player.sendSystemMessage(Component.literal("Purchase complete: " + prepared.totalCopper() + " copper."));
                                TransactionSuccessS2CPayload.send(player);
                            }));
                }));
    }

    private static PreparedPurchase preparePurchase(ServerLevel level, String city, String role,
                                                    List<BuyMerchantItemsC2SPayload.ItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return PreparedPurchase.failure("Your cart is empty.");
        }

        List<MerchantCatalogEntry> catalog = MerchantCatalogBuilder.build(
                CityCommodityApi.fetchServer(level, city),
                MerchantRecipes.forRole(role)
        );
        Map<String, MerchantCatalogEntry> byKey = new HashMap<>();
        for (MerchantCatalogEntry entry : catalog) {
            byKey.put(key(entry.product().itemId(), entry.product().name()), entry);
        }

        List<PurchasedEntry> entries = new ArrayList<>();
        int totalCopper = 0;
        for (BuyMerchantItemsC2SPayload.ItemRequest request : requests) {
            int quantity = Math.max(0, request.quantity());
            if (quantity == 0) continue;

            MerchantCatalogEntry entry = byKey.get(key(request.itemId(), request.itemName()));
            if (entry == null) {
                return PreparedPurchase.failure(request.itemName() + " is no longer available.");
            }
            if (quantity > entry.stock()) {
                return PreparedPurchase.failure("Only " + entry.stock() + " " + entry.product().name() + " are available.");
            }

            entries.add(new PurchasedEntry(entry, quantity));
            totalCopper += entry.product().price() * quantity;
        }

        if (entries.isEmpty()) {
            return PreparedPurchase.failure("Your cart is empty.");
        }
        return PreparedPurchase.success(entries, totalCopper);
    }

    private static PurchasePostResult postPurchase(ServerLevel level, ServerPlayer player,
                                                   AbstractEconomyMerchantEntity merchant, String city, String role,
                                                   PreparedPurchase prepared, String idempotencyKey) {
        try {
            var requestUri = ServerAuthRegistry.credentials(level.getServer()).orElseThrow().apiUrls()
                    .resolve(Endpoint.MERCHANT_PURCHASE);
            HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(conn);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Idempotency-Key", idempotencyKey);

            JsonObject payload = new JsonObject();
            payload.addProperty("transaction_type", "purchase");
            payload.addProperty("idempotency_key", idempotencyKey);
            payload.addProperty("player_uuid", player.getStringUUID());
            payload.addProperty("player_name", player.getGameProfile().getName());
            payload.addProperty("city", city);
            payload.addProperty("city_name", city);
            payload.addProperty("role", role);
            payload.addProperty("npc_id", merchant.getUUID().toString());
            payload.addProperty("entity_id", merchant.getId());
            payload.addProperty("shard", ModConfig.SHARD_NAME);
            payload.addProperty("total_price", prepared.totalCopper());
            payload.addProperty("total_copper", prepared.totalCopper());

            JsonArray items = new JsonArray();
            JsonArray inputs = new JsonArray();
            for (PurchasedEntry purchase : prepared.entries()) {
                Product product = purchase.entry().product();
                LOGGER.info("Merchant purchase requested output={} quantity={} city={} role={} price={}",
                        product.itemId(), purchase.quantity(), city, role, product.price());
                JsonObject item = new JsonObject();
                item.addProperty("item_id", product.itemId());
                item.addProperty("item_name", product.name());
                item.addProperty("quantity", purchase.quantity());
                item.addProperty("unit_price", product.price());
                item.addProperty("currency", "copper");
                if (WeightedCommodityItem.hasWeight(product.stack())) {
                    item.addProperty("weight", WeightedCommodityItem.getWeight(product.stack()));
                }
                items.add(item);

                for (MerchantCatalogEntry.ConsumedCommodity input : purchase.entry().inputs()) {
                    double amount = input.amountPerUnit() * purchase.quantity();
                    JsonObject inputObj = new JsonObject();
                    inputObj.addProperty("category", input.category());
                    inputObj.addProperty("subcategory", input.subcategory());
                    inputObj.addProperty("item_name", input.itemName());
                    inputObj.addProperty("commodity_key", input.commodityKey());
                    inputObj.addProperty("amount", amount);
                    inputObj.addProperty("weight", amount);
                    inputObj.addProperty("quantity", 1);
                    inputObj.addProperty("unit_price", input.unitPrice());
                    inputs.add(inputObj);
                    LOGGER.info("Merchant purchase input output={} input={}|{}|{} requiredWeight={} city={}",
                            product.itemId(), input.category(), input.subcategory(), input.itemName(), amount, city);
                }
            }
            payload.add("items", items);
            payload.add("transaction_items", items.deepCopy());
            payload.add("input_commodities", inputs);
            payload.add("consumed_commodities", inputs.deepCopy());
            payload.add("commodity_inputs", inputs.deepCopy());

            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            attachServerAuth(level, conn, bodyBytes);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            String body = BoundedHttp.readUtf8(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(), 1_048_576);
            if (status < 200 || status >= 300) {
                return PurchasePostResult.failure(status, body, "Purchase rejected by economy server.");
            }
            PurchasePostResult parsed = parsePurchaseResponse(status, body, prepared);
            if (!parsed.success()) {
                return parsed;
            }
            LOGGER.info("Merchant purchase accepted key={} city={} role={} body={}", idempotencyKey, city, role, body);
            return PurchasePostResult.success(status, body);
        } catch (Exception e) {
            return PurchasePostResult.failure(0, e.toString(), "Purchase failed: economy server unavailable.");
        }
    }

    private static PurchasePostResult parsePurchaseResponse(int status, String body, PreparedPurchase prepared) {
        if (body == null || body.isBlank()) {
            return PurchasePostResult.success(status, body);
        }

        try {
            JsonObject response = JsonParser.parseString(body).getAsJsonObject();
            if (response.has("success") && !response.get("success").getAsBoolean()) {
                return PurchasePostResult.failure(status, body, "Purchase rejected by economy server.");
            }

            int expectedItems = prepared.entries().stream().mapToInt(PurchasedEntry::quantity).sum();
            int processed = intFrom(response, "items_processed", expectedItems);
            if (processed <= 0 && expectedItems > 0) {
                LOGGER.warn("Merchant purchase processed no Rails items body={}", body);
                return PurchasePostResult.failure(status, body, "Purchase rejected: economy server did not process the merchant item.");
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Merchant purchase response parse failed status={} body={} error={}", status, body, e.toString());
        }

        return PurchasePostResult.success(status, body);
    }

    private static int intFrom(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static void grantProducts(ServerPlayer player, List<PurchasedEntry> entries) {
        for (PurchasedEntry entry : entries) {
            Product product = entry.entry().product();
            ItemStack template = product.stack();
            Item item = template.isEmpty()
                    ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(product.itemId()))
                    : template.getItem();
            if (item == net.minecraft.world.item.Items.AIR) continue;

            int remaining = entry.quantity();
            while (remaining > 0) {
                ItemStack stack = template.isEmpty() ? new ItemStack(item, 1) : template.copy();
                stack.setCount(1);
                if (!WeightedCommodityItem.hasWeight(stack)) {
                    WeightedCommodityItem.setWeight(stack, generatedOutputWeight(player, entry.entry()));
                }
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining--;
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
    }

    private static double generatedOutputWeight(ServerPlayer player, MerchantCatalogEntry entry) {
        String recipeId = merchantRecipeId(entry.product().stack());
        double[] range = outputWeightRange(recipeId);
        if (range != null) {
            if (range[0] == range[1]) return range[0];
            return randomInRange(player, range[0], range[1]);
        }

        double baseWeight = entry.inputs().stream()
                .mapToDouble(MerchantCatalogEntry.ConsumedCommodity::amountPerUnit)
                .sum();
        if (baseWeight <= 0.0D) baseWeight = 0.25D;
        double variance = 0.85D + player.getRandom().nextDouble() * 0.30D;
        return Math.max(0.05D, baseWeight * variance);
    }

    private static String merchantRecipeId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return tag.contains("merchant_recipe") ? tag.getString("merchant_recipe") : "";
    }

    private static double[] outputWeightRange(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) return null;
        if (recipeId.startsWith("fish_steak_")) {
            return new double[]{MerchantRecipes.STANDARD_FISH_STEAK_WEIGHT, MerchantRecipes.STANDARD_FISH_STEAK_WEIGHT};
        }

        return switch (recipeId) {
            case "bread", "oat_bread", "barley_bread", "rye_bread", "rice_bread", "sorghum_bread", "quinoa_bread" ->
                    new double[]{0.30D, 0.70D};
            case "chicken_wing" -> new double[]{0.08D, 0.18D};
            case "chicken_leg" -> new double[]{0.20D, 0.45D};
            case "chicken_breast" -> new double[]{0.25D, 0.55D};
            case "cooked_chicken" -> new double[]{0.40D, 1.20D};
            case "slice_of_bacon" -> new double[]{0.05D, 0.15D};
            case "ham" -> new double[]{0.50D, 1.50D};
            case "cut_of_ribs", "beef_ribs" -> new double[]{0.30D, 0.90D};
            case "leg_of_lamb" -> new double[]{0.60D, 1.80D};
            case "roast_pig" -> new double[]{1.00D, 3.00D};
            case "sausage" -> new double[]{0.15D, 0.35D};
            case "beef_brisket" -> new double[]{0.60D, 1.80D};
            case "apple", "banana", "concord_grapes", "peaches", "pears", "berries" -> new double[]{0.15D, 0.50D};
            case "squash", "carrots", "corn", "cabbage", "lettuce", "onion", "pumpkin", "potato", "tomato" ->
                    new double[]{0.10D, 0.60D};
            default -> null;
        };
    }

    private static double randomInRange(ServerPlayer player, double min, double max) {
        return min + player.getRandom().nextDouble() * Math.max(0.0D, max - min);
    }

    private static CoinReservation reserveCoins(ServerPlayer player, int totalCopper) {
        int available = countCoins(player);
        if (available < totalCopper) return CoinReservation.failure();

        removeAllCoins(player);
        int change = available - totalCopper;
        giveChange(player, change);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
        return CoinReservation.success(totalCopper);
    }

    /**
     * Milestone 11 prerequisite: sums a player's real coin stacks into a total expressed in
     * copper, using {@link CoinConversion}'s centralized ratio rather than inline literals.
     * Made {@code public} (was {@code private}) so a real {@code ServerPlayer} GameTest can
     * prove this refactor is behavior-preserving directly, matching this codebase's existing
     * precedent for exposing a service's internal logic for test access (e.g.
     * {@code BankItemEligibility}'s public static checks).
     */
    public static int countCoins(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() == ItemRegistry.GOLD_COIN.get()) total += stack.getCount() * CoinConversion.COPPER_PER_GOLD;
            else if (stack.getItem() == ItemRegistry.SILVER_COIN.get()) total += stack.getCount() * CoinConversion.COPPER_PER_SILVER;
            else if (stack.getItem() == ItemRegistry.COPPER_COIN.get()) total += stack.getCount();
        }
        return total;
    }

    private static void removeAllCoins(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == ItemRegistry.GOLD_COIN.get()
                    || stack.getItem() == ItemRegistry.SILVER_COIN.get()
                    || stack.getItem() == ItemRegistry.COPPER_COIN.get()) {
                stack.setCount(0);
            }
        }
    }

    /**
     * Milestone 11 prerequisite: delegates the actual gold/silver/copper split to
     * {@link CoinConversion#toCoins}, which implements the exact same greedy
     * largest-denomination-first arithmetic this method's own inline division/remainder chain
     * used before this refactor -- a behavior-preserving call-through, not a reimplementation.
     * Made {@code public} (was {@code private}) for the same test-access reason as
     * {@link #countCoins}.
     */
    public static void giveChange(ServerPlayer player, int copper) {
        CoinConversion.CoinCounts coins = CoinConversion.toCoins(copper);
        giveCoin(player, ItemRegistry.GOLD_COIN.get(), coins.gold());
        giveCoin(player, ItemRegistry.SILVER_COIN.get(), coins.silver());
        giveCoin(player, ItemRegistry.COPPER_COIN.get(), coins.copper());
    }

    private static void giveCoin(ServerPlayer player, Item coin, int amount) {
        int remaining = amount;
        int maxStack = new ItemStack(coin).getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(coin, give);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= give;
        }
    }

    private static String merchantRole(AbstractEconomyMerchantEntity merchant, String fallback) {
        String className = merchant.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (className.contains("baker")) return "Baker";
        if (className.contains("tavern")) return "Tavernkeeper";
        if (className.contains("costermonger")) return "Costermonger";
        return fallback;
    }

    private static String key(String itemId, String name) {
        return normalize(itemId) + "::" + normalize(name);
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static void attachServerAuth(ServerLevel level, HttpURLConnection conn, byte[] body) {
        if (!RailsRequestAuthenticator.apply(conn, level.getServer(), body)) {
            throw new IllegalStateException("Server authentication unavailable");
        }
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        TransactionFailedS2CPayload.send(player, message);
    }

    private record PurchasedEntry(MerchantCatalogEntry entry, int quantity) {}

    private record PreparedPurchase(boolean success, List<PurchasedEntry> entries, int totalCopper, String message) {
        static PreparedPurchase success(List<PurchasedEntry> entries, int totalCopper) {
            List<PurchasedEntry> sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparing(e -> e.entry().product().name()));
            return new PreparedPurchase(true, List.copyOf(sorted), totalCopper, "");
        }

        static PreparedPurchase failure(String message) {
            return new PreparedPurchase(false, List.of(), 0, message);
        }
    }

    private record PurchasePostResult(boolean success, int statusCode, String body, String message) {
        static PurchasePostResult success(int statusCode, String body) {
            return new PurchasePostResult(true, statusCode, body, "");
        }

        static PurchasePostResult failure(int statusCode, String body, String message) {
            return new PurchasePostResult(false, statusCode, body, message);
        }
    }

    private record CoinReservation(boolean success, int spentCopper) {
        static CoinReservation success(int spentCopper) {
            return new CoinReservation(true, spentCopper);
        }

        static CoinReservation failure() {
            return new CoinReservation(false, 0);
        }

        void refund(ServerPlayer player) {
            if (spentCopper > 0) {
                giveChange(player, spentCopper);
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.broadcastFullState();
            }
        }
    }
}

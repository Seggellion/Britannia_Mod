package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.entity.AbstractEconomyMerchantEntity;
import com.seggellion.britannia_mod.network.payload.BuyMerchantItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        CompletableFuture
                .supplyAsync(() -> preparePurchase(level, city, role, payload.items()))
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
                    CompletableFuture
                            .supplyAsync(() -> postPurchase(level, player, merchant, city, role, prepared, idempotencyKey))
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
            URL url = new URL(ModConfig.API_BASE_URL + "merchant_transactions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Idempotency-Key", idempotencyKey);
            attachServerAuth(level, conn);

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
                JsonObject item = new JsonObject();
                item.addProperty("item_id", product.itemId());
                item.addProperty("item_name", product.name());
                item.addProperty("quantity", purchase.quantity());
                item.addProperty("unit_price", product.price());
                item.addProperty("currency", "copper");
                items.add(item);

                for (MerchantCatalogEntry.ConsumedCommodity input : purchase.entry().inputs()) {
                    JsonObject inputObj = new JsonObject();
                    inputObj.addProperty("category", input.category());
                    inputObj.addProperty("commodity_key", input.commodityKey());
                    inputObj.addProperty("amount", input.amountPerUnit() * purchase.quantity());
                    inputObj.addProperty("unit_price", input.unitPrice());
                    inputs.add(inputObj);
                }
            }
            payload.add("items", items);
            payload.add("transaction_items", items.deepCopy());
            payload.add("input_commodities", inputs);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String body = readBody(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (status < 200 || status >= 300) {
                return PurchasePostResult.failure(status, body, "Purchase rejected by economy server.");
            }
            return PurchasePostResult.success(status, body);
        } catch (Exception e) {
            return PurchasePostResult.failure(0, e.toString(), "Purchase failed: economy server unavailable.");
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
            int maxStack = template.isEmpty() ? new ItemStack(item).getMaxStackSize() : template.getMaxStackSize();
            while (remaining > 0) {
                int give = Math.min(remaining, maxStack);
                ItemStack stack = template.isEmpty() ? new ItemStack(item, give) : template.copy();
                stack.setCount(give);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= give;
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
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

    private static int countCoins(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() == ItemRegistry.GOLD_COIN.get()) total += stack.getCount() * 10000;
            else if (stack.getItem() == ItemRegistry.SILVER_COIN.get()) total += stack.getCount() * 100;
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

    private static void giveChange(ServerPlayer player, int copper) {
        giveCoin(player, ItemRegistry.GOLD_COIN.get(), copper / 10000);
        copper %= 10000;
        giveCoin(player, ItemRegistry.SILVER_COIN.get(), copper / 100);
        copper %= 100;
        giveCoin(player, ItemRegistry.COPPER_COIN.get(), copper);
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

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                out.append(buffer, 0, read);
            }
            return out.toString();
        }
    }

    private static void attachServerAuth(ServerLevel level, HttpURLConnection conn) {
        CityAPITokenData data = CityAPITokenData.getOrCreate(level);
        if (data.getApiToken() != null && !data.getApiToken().isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + data.getApiToken());
        }
        if (data.getShardSecret() != null && !data.getShardSecret().isBlank()) {
            conn.setRequestProperty("Shard-Secret", data.getShardSecret());
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

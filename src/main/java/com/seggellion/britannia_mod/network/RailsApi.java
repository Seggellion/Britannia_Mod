package com.seggellion.britannia_mod.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

// Your mod class
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.config.ModConfig;
import com.seggellion.britannia_mod.economy.CityCommodityApi;
import com.seggellion.britannia_mod.economy.MerchantCatalogBuilder;
import com.seggellion.britannia_mod.economy.MerchantRecipes;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.network.payload.GrantCoinsC2SPayload;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.item.WineBottleItem;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;


public class RailsApi {
    private static final String BASE_URL = ModConfig.API_BASE_URL;
    private static final Logger LOGGER = LogUtils.getLogger();

    // ========================================================================
    // 1. BUYING CATALOG (Merchant)
    // ========================================================================
    public static void fetchCatalog(String city, String role, Consumer<List<Product>> callback) {
        if (isEconomyMerchant(role)) {
            fetchEconomyMerchantCatalog(city, role, callback);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "catalog?city=" + city + "&role=" + role);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                attachAuthToken(conn);

                try (InputStream in = conn.getInputStream();
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                    List<Product> products = new ArrayList<>();
                    
                    for (JsonElement e : arr) {
                        JsonObject obj = e.getAsJsonObject();
                        
                        // [CHANGE] Check for currency field, default to copper
                        String currency = obj.has("currency") ? obj.get("currency").getAsString() : "copper";

                        products.add(new Product(
                            obj.get("item_id").getAsString(),
                            obj.get("item_name").getAsString(),
                            obj.get("price").getAsInt(),
                            currency, // Pass currency to Product
                            ResourceLocation.parse(obj.get("icon").getAsString())
                        ));
                    }
                    Minecraft.getInstance().execute(() -> callback.accept(products));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void fetchEconomyMerchantCatalog(String city, String role, Consumer<List<Product>> callback) {
        CompletableFuture.runAsync(() -> {
            List<Product> products = MerchantCatalogBuilder.build(
                            CityCommodityApi.fetchClient(city),
                            MerchantRecipes.forRole(role)
                    ).stream()
                    .map(com.seggellion.britannia_mod.economy.MerchantCatalogEntry::product)
                    .toList();

            Minecraft.getInstance().execute(() -> callback.accept(products));
        });
    }

    private static boolean isEconomyMerchant(String role) {
        if (role == null) return false;
        String lower = role.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("baker") || lower.contains("tavernkeeper") || lower.contains("costermonger");
    }

    // ========================================================================
    // 2. SELLING CATALOG (Trader / Salvage)
    // ========================================================================
    public static void fetchTraderCatalog(String city, String role, JsonArray inventoryData, Consumer<List<Product>> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "trader_catalog");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("city", city);
                payload.addProperty("role", role);
                payload.add("inventory", inventoryData);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                try (InputStream in = conn.getInputStream();
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                     
                    JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                    List<Product> products = new ArrayList<>();

                    for (JsonElement e : arr) {
                        JsonObject obj = e.getAsJsonObject();
                        LOGGER.debug("OBJ: {}", obj);

                        String itemIdStr = obj.get("item_id").getAsString();
                        
                        ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
                        Item item = BuiltInRegistries.ITEM.get(itemId);
                        ItemStack stack = new ItemStack(item);
                        
                        // [CHANGE] Extract currency from API response
                        String currency = obj.has("currency") ? obj.get("currency").getAsString() : "copper";

                        // [CHANGE] Use new Product constructor with currency
                        products.add(new Product(
                            itemIdStr,
                            obj.get("item_name").getAsString(),
                            (int) Math.round(obj.get("price").getAsDouble()),
                            currency, // Pass currency
                            stack 
                        ));
                    }

                    Minecraft.getInstance().execute(() -> {
                        if (products.isEmpty()) {
                            Player player = Minecraft.getInstance().player;
                            if (player != null) {
                                player.displayClientMessage(
                                    Component.literal("§eThe trader says: 'I am not interested in anything you have.'"),
                                    false
                                );
                            }
                            // Don't return, still callback with empty list to clear UI if needed
                        }
                        callback.accept(products);
                    });
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch trader catalog", e);
                e.printStackTrace();
            }
        });
    }


// ========================================================================
// 3. SELL TRANSACTION (Payout logic)
// ========================================================================
public static void sellItems(Player player, String city, String role, int entityId,
                             Map<Product, Integer> cart,
                             int totalPrice,
                             Consumer<Boolean> callback) {
    CompletableFuture.runAsync(() -> {
        try {
            // This list will hold the payload WITH the NBT data
            List<GrantCoinsC2SPayload.SoldItem> pendingSoldItems = new ArrayList<>();
            
            URL url = new URL(BASE_URL + "trader_transactions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            attachAuthToken(conn);

            JsonObject payload = new JsonObject();
            payload.addProperty("city", city);
            payload.addProperty("role", role);
            payload.addProperty("entity_id", entityId);
            payload.addProperty("transaction_type", "sell");
            payload.addProperty("total_price", totalPrice);
            payload.addProperty("player_uuid", getLocalPlayerUUID());
            JsonArray itemsArr = new JsonArray();

            for (Map.Entry<Product, Integer> e : cart.entrySet()) {
                Product product = e.getKey();
                int quantity = e.getValue();
                
                JsonObject obj = new JsonObject();
                obj.addProperty("item_id", product.itemId());
                obj.addProperty("item_name", product.name());
                obj.addProperty("quantity", quantity);

                double weightValue = 0.0;
                boolean dataFound = false;
                boolean correctItemFound = false;
                
                // Variable to capture the NBT tag
                CompoundTag itemTag = null;

                // Scan player inventory to find the actual item source
                for (ItemStack invStack : player.getInventory().items) {
                    if (invStack.isEmpty()) continue;
                    
                    String invId = BuiltInRegistries.ITEM.getKey(invStack.getItem()).toString();
                    LOGGER.debug("inventory check data? {}", product.name());
                    // Simple ID match check
                    if (invId.equals(product.itemId()) || invId.endsWith(":" + product.name())) {
                        
                        // --- CHECK: IS THIS THE EXACT DATA MATCH? ---
                        // If the product name implies strict data (starts with "DATA|"), verify it.
                        if (product.stack().has(DataComponentRegistry.WINE_DATA)) {
                                    // If the inventory item doesn't have data, or the data doesn't match exactly -> Skip
                                    if (!invStack.has(DataComponentRegistry.WINE_DATA) || 
                                        !isMatchingWineData(invStack, product.stack())) {
                                        continue; 
                                    }
                                }

                        // We found the correct item stack!
                        correctItemFound = true;
                        
                        // Capture NBT
                        itemTag = (CompoundTag) invStack.save(player.registryAccess());
                        
                        // 1. Recover Weight (Fish)
                        if (invStack.getItem() instanceof WeightedFishItem fishItem) {
                            weightValue = fishItem.getWeight(invStack);
                        }

                        // 2. Recover Wine Data (Alcohol)
                        if (invStack.has(DataComponentRegistry.WINE_DATA)) {
                            WineData data = WineBottleItem.getWineData(invStack);
                            
                            LOGGER.info("FOUND WINE DATA: Winery={}, Year={}, label_color={}", data.wineryName(), data.year(), data.labelColor());

                            obj.addProperty("winery_name", data.wineryName());
                            obj.addProperty("grape_type", data.grapeType());
                            obj.addProperty("year", data.year());
                            obj.addProperty("region", data.region());
                            obj.addProperty("quality", data.quality());
                            obj.addProperty("label_color", data.labelColor());

                            String bottleColor = "green"; 
                            if (invId.contains("green")) bottleColor = "green";
                            else if (invId.contains("red")) bottleColor = "red";
                            else if (invId.contains("clear")) bottleColor = "clear";
                            else if (invId.contains("brown")) bottleColor = "brown";
                            
                            obj.addProperty("bottle_color", bottleColor);
                            
                            dataFound = true; 
                        }
                        
                        // Break once we found the item, so we use this specific item's NBT
                        break; 
                    }
                }

                if (!correctItemFound && product.itemId().contains("wine")) {
                    LOGGER.warn("WARNING: Selling wine but could not find matching WINE_DATA for ID: {}", product.itemId());
                }

                obj.addProperty("weight", weightValue);
                itemsArr.add(obj);

                // Add to the list we will send to the server later
                pendingSoldItems.add(new GrantCoinsC2SPayload.SoldItem(
                    product.itemId(),
                    product.name(),
                    quantity,
                    weightValue,
                    itemTag 
                ));
            }   

            payload.add("items", itemsArr);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            boolean success = conn.getResponseCode() == 200;
            if (success) {
                try (InputStream in = conn.getInputStream();
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject resp = JsonParser.parseReader(reader).getAsJsonObject();
                    
                    if (resp.has("payout")) {
                        JsonObject payout = resp.getAsJsonObject("payout");
                        int gold   = payout.has("gold")   ? payout.get("gold").getAsInt()   : 0;
                        int silver = payout.has("silver") ? payout.get("silver").getAsInt() : 0;
                        int copper = payout.has("copper") ? payout.get("copper").getAsInt() : 0;
                        String receipt = resp.has("receipt_id") ? resp.get("receipt_id").getAsString() : "";

                        Minecraft.getInstance().execute(() -> {
                            LOGGER.info("Sold List {}", pendingSoldItems);

                            // Send packet to server using the list we built earlier (which has NBT)
                            com.seggellion.britannia_mod.network.NetworkHandler.sendToServer(
                                new GrantCoinsC2SPayload(gold, silver, copper, city, receipt, pendingSoldItems)
                            );
                        });
                    }
                }
            }

            Minecraft.getInstance().execute(() -> callback.accept(success));

        } catch (Exception e) {
            e.printStackTrace();
            Minecraft.getInstance().execute(() -> callback.accept(false));
        }
    });
}

/**
 * Helper method to check if an inventory item matches the specific data signature 
 * expected by the API product.
 */
/**
 * Strict comparison of Wine Data between two ItemStacks.
 */
private static boolean isMatchingWineData(ItemStack invStack, ItemStack productStack) {
    // Safety check
    if (!invStack.has(DataComponentRegistry.WINE_DATA) || 
        !productStack.has(DataComponentRegistry.WINE_DATA)) {
        return false;
    }

    WineData invData = invStack.get(DataComponentRegistry.WINE_DATA);
    WineData prodData = productStack.get(DataComponentRegistry.WINE_DATA);

    // Compare all relevant fields strictly
    return invData.wineryName().equals(prodData.wineryName()) &&
           invData.grapeType().equals(prodData.grapeType()) &&
           invData.year() == prodData.year() &&
           invData.quality() == prodData.quality() &&
           invData.labelColor().equalsIgnoreCase(prodData.labelColor());
}

    // (Helper methods remain unchanged)
    private static void giveCoinsToPlayer(ServerPlayer player, int gold, int silver, int copper) {
        giveCoinStack(player, ItemRegistry.GOLD_COIN.get(), gold);
        giveCoinStack(player, ItemRegistry.SILVER_COIN.get(), silver);
        giveCoinStack(player, ItemRegistry.COPPER_COIN.get(), copper);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
    }

    private static void giveCoinStack(ServerPlayer player, Item coinItem, int amount) {
        if (amount <= 0) return;
        int stackSize = coinItem.getDefaultInstance().getMaxStackSize();
        while (amount > 0) {
            int give = Math.min(amount, stackSize);
            ItemStack stack = new ItemStack(coinItem, give);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            amount -= give;
        }
    }

    public static void buyItems(String city, String role, int entityId,
                                Map<Product, Integer> cart,
                                int totalPrice,
                                Consumer<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(BASE_URL + "merchant_transactions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                attachAuthToken(conn);

                JsonObject payload = new JsonObject();
                payload.addProperty("city", city);
                payload.addProperty("role", role);
                payload.addProperty("entity_id", entityId);
                payload.addProperty("transaction_type", "purchase");
                payload.addProperty("total_price", totalPrice);
                payload.addProperty("player_uuid", getLocalPlayerUUID());

                JsonArray itemsArr = new JsonArray();
                for (Map.Entry<Product, Integer> e : cart.entrySet()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("item_id", e.getKey().itemId());
                    obj.addProperty("item_name", e.getKey().name());
                    obj.addProperty("quantity", e.getValue());
                    obj.addProperty("weight", 0.0);
                    itemsArr.add(obj);
                }
                payload.add("items", itemsArr);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                boolean success = conn.getResponseCode() == 200;
                Minecraft.getInstance().execute(() -> callback.accept(success));

            } catch (Exception e) {
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> callback.accept(false));
            }
        });
    }

    private static void attachAuthToken(HttpURLConnection conn) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            String token = CityAPITokenData.getClientToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            String secret = CityAPITokenData.getClientShardSecret();
            if (secret != null && !secret.isEmpty()) {
                conn.setRequestProperty("Shard-Secret", secret);
            }
        }
    }

    private static String getLocalPlayerUUID() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            return mc.player.getUUID().toString();
        }
        return "unknown";
    }
}

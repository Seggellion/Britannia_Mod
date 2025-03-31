package com.seggellion.britannia_mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.config.ModConfig;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.net.URL;
import java.util.List;
public class SendTransactionToAPI {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void send(ServerLevel serverLevel, String playerUuid, String cityName, List<JsonObject> items, String transactionType, String npcType, String npcId, String npcName, Player player) {
        try {
            JsonObject payload = createPayload(playerUuid, cityName, npcId, npcName, transactionType, items);
            HttpURLConnection conn = initializeConnection(ModConfig.API_BASE_URL + "transactions", serverLevel);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            handleResponse(conn, player, items);
        } catch (Exception e) {
            LOGGER.error("Error sending transaction to API: ", e);
        }
    }

    private static JsonObject createPayload(String playerUuid, String cityName, String npcId, String npcName, String transactionType, List<JsonObject> items) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", playerUuid);
        payload.addProperty("city_name", cityName);
        payload.addProperty("npc_id", npcId);
        payload.addProperty("npc_name", npcName);
        payload.addProperty("shard", "Britannia");
        payload.addProperty("transaction_type", transactionType);
        
   JsonArray itemsArray = new JsonArray();
       JsonArray deductedCommodities = new JsonArray();

    double totalChecksum = 0.0;
    double totalPrice = 0.0;

    // Calculate total price from item details
    for (JsonObject item : items) {
        itemsArray.add(item);


        String itemName = item.get("item_name").getAsString();
        int quantity = 1;


        if (transactionType.equalsIgnoreCase("purchase")) {
            JsonObject recipeData = LocalRecipes.getDeductionsWithChecksum(itemName, quantity);
            JsonArray itemDeductions = recipeData.getAsJsonArray("deductions");
            double itemChecksum = recipeData.get("checksum").getAsDouble();

            for (var deduction : itemDeductions) {
                deductedCommodities.add(deduction);
            }
            totalChecksum += itemChecksum;
        }

        if (item.has("price")) {
            totalPrice += item.get("price").getAsDouble() * item.get("quantity").getAsInt();
        }
    }

        items.forEach(itemsArray::add);
        payload.add("transaction_items", itemsArray);

           payload.add("deducted_commodities", deductedCommodities);
            payload.addProperty("recipe_checksum", totalChecksum);

           if (transactionType.equalsIgnoreCase("purchase")) {
        payload.addProperty("total_price", totalPrice);
    }
        return payload;
    }

    private static HttpURLConnection initializeConnection(String urlString, ServerLevel serverLevel) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
        String apiToken = data.getApiToken();
        if (!apiToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
        }
        conn.setDoOutput(true);
        return conn;
    }

private static void handleResponse(HttpURLConnection conn, Player player, List<JsonObject> items) throws IOException {
    int responseCode = conn.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
            double totalGold = response.get("total_gold").getAsDouble();
            String transactionType = response.has("transaction_type") ? 
                response.get("transaction_type").getAsString().toLowerCase() : "unknown";

            if (player instanceof ServerPlayer serverPlayer) {
                if ("purchase".equals(transactionType)) {
                    // ✅ Remove Gold from Player Inventory and Sync
                    removeGoldCoins(serverPlayer, (int) totalGold);
                } else if ("sell".equals(transactionType)) {
                    // ✅ Give Gold for Sales and Remove Sold Items
                    removeSoldItems(serverPlayer, items);
                    giveGoldCoins(serverPlayer, (int) totalGold);                   
                } else {
                    LOGGER.warn("Unknown transaction type: {}", transactionType);
                }
            } else {
                LOGGER.error("Player is not a ServerPlayer instance.");
            }
        }
    } else {
        LOGGER.warn("Failed to process transaction. Response Code: {}", responseCode);
    }
}

private static void removeGoldCoins(ServerPlayer player, int amount) {
    if (amount <= 0) return;

    // ✅ 1. Remove Gold from Player Inventory
    for (ItemStack stack : player.getInventory().items) {
        if (stack.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
            int toRemove = Math.min(stack.getCount(), amount);
            stack.shrink(toRemove);
            amount -= toRemove;
            if (amount <= 0) break;
        }
    }

    // ✅ 2. Remove Gold from Villager Trade Input Slot
    if (player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu merchantMenu) {
        ItemStack inputSlot = merchantMenu.getSlot(0).getItem(); // Slot 0: Input (Gold Coins)
        if (inputSlot.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
            int toRemove = Math.min(inputSlot.getCount(), amount);
            inputSlot.shrink(toRemove);
            merchantMenu.broadcastChanges();
            LOGGER.info("Removed {} gold from Villager trade slot.", toRemove);
        }
    }

    // ✅ 3. Sync Player Inventory and Trade Slots
    player.containerMenu.broadcastChanges();
    player.inventoryMenu.broadcastFullState();

    if (amount > 0) {
        LOGGER.warn("Player did not have enough gold coins. Missing: {}", amount);
    } else {
        LOGGER.info("Successfully removed gold coins from player inventory and trade slot.");
    }
}

    private static void giveGoldCoins(Player player, int amount) {
        ItemStack coinStack = new ItemStack(ItemRegistry.GOLD_COIN.get());
        int stackSize = coinStack.getMaxStackSize();
        while (amount > 0) {
            int giveAmount = Math.min(amount, stackSize);
            ItemStack stack = new ItemStack(ItemRegistry.GOLD_COIN.get(), giveAmount);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            amount -= giveAmount;
        }
    }

    private static void removeSoldItems(Player player, List<JsonObject> items) {
        items.forEach(item -> {
            String itemId = item.has("item_id") ? item.get("item_id").getAsString() : "";
            String itemName = item.has("item_name") ? item.get("item_name").getAsString() : "";
            int quantity = item.has("quantity") && !item.get("quantity").isJsonNull() 
                ? item.get("quantity").getAsInt() 
                : 1;

            LOGGER.warn("removeSoldItems:{}-- {}, x {}",itemId,itemName, quantity);
            switch (itemId) {
                case "britannia_mod:weighted_wood_item":
                    removeItemsByType(player, itemName, quantity, WeightedWoodItem.class);
                    break;
                case "britannia_mod:weighted_fish_item":
                    removeItemsByType(player, itemName, quantity, WeightedFishItem.class);
                    break;
                case "britannia_mod:purity_ore_item":
                    removeItemsByType(player, itemName, quantity, PurityOreItem.class);
                    break;
                case "britannia_mod:grade_stone_item":
                    removeItemsByType(player, itemName, quantity, GradeStoneItem.class);
                    break;
            }
        });
    }

    private static <T extends ItemStack> void removeItemsByType(Player player, String itemType, int quantity, Class<?> itemClass) {
        if (quantity <= 0) return;
        for (int i = 0; i < player.getInventory().items.size() && quantity > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (itemClass.isInstance(stack.getItem()) && getItemSubtype(stack, itemClass).equalsIgnoreCase(itemType)) {
                int toRemove = Math.min(stack.getCount(), quantity);
                stack.shrink(toRemove);
                quantity -= toRemove;
                if (stack.isEmpty()) {
                    player.getInventory().items.set(i, ItemStack.EMPTY);
                }
            }
        }
            // Sync inventory if player is a ServerPlayer
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.inventoryMenu.broadcastChanges();
            }
    }

    private static String getItemSubtype(ItemStack stack, Class<?> itemClass) {
        if (itemClass == WeightedWoodItem.class) return ((WeightedWoodItem) stack.getItem()).getWoodType(stack);
        if (itemClass == WeightedFishItem.class) return ((WeightedFishItem) stack.getItem()).getFishType(stack);
        if (itemClass == PurityOreItem.class) return ((PurityOreItem) stack.getItem()).getOreType(stack);
        if (itemClass == GradeStoneItem.class) return ((GradeStoneItem) stack.getItem()).getStoneType(stack);
        return "unknown";
    }
}
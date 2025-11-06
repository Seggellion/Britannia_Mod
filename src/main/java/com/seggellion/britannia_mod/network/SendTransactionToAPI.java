package com.seggellion.britannia_mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;

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
            double totalPrice = payload.has("total_price") ? payload.get("total_price").getAsDouble() : 0.0;

            if ("purchase".equalsIgnoreCase(transactionType) && player instanceof ServerPlayer serverPlayer) {
                if (!hasEnoughGold(serverPlayer, (int) totalPrice)) {
                    player.sendSystemMessage(Component.literal("You do not have enough gold for this purchase."));
                    return; // 🚫 Block before API request
                }
            }

            HttpURLConnection conn = initializeConnection(ModConfig.API_BASE_URL + "transactions", serverLevel);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            handleResponse(conn, player, items);
        } catch (Exception e) {
            LOGGER.error("Error sending transaction to API: ", e);
        }
    }

    private static boolean hasEnoughGold(ServerPlayer player, int amount) {
        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                available += stack.getCount();
            }
        }
        if (player.containerMenu instanceof MerchantMenu merchantMenu) {
            ItemStack inputSlot = merchantMenu.getSlot(0).getItem();
            if (inputSlot.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                available += inputSlot.getCount();
            }
        }
        return available >= amount;
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

        for (JsonObject item : items) {
            itemsArray.add(item);
            String itemName = item.get("item_name").getAsString();
            int quantity = item.has("quantity") ? item.get("quantity").getAsInt() : 1;

            if ("purchase".equalsIgnoreCase(transactionType)) {
                JsonObject recipeData = LocalRecipes.getDeductionsWithChecksum(itemName, quantity);
                JsonArray itemDeductions = recipeData.getAsJsonArray("deductions");
                double itemChecksum = recipeData.get("checksum").getAsDouble();

                for (var deduction : itemDeductions) {
                    deductedCommodities.add(deduction);
                }

                totalChecksum += itemChecksum;
            }

            if (item.has("price")) {
                totalPrice += item.get("price").getAsDouble() * quantity;
            }
        }

        payload.add("transaction_items", itemsArray);
        payload.add("deducted_commodities", deductedCommodities);
        payload.addProperty("recipe_checksum", totalChecksum);

        if ("purchase".equalsIgnoreCase(transactionType)) {
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
                        boolean success = removeGoldCoins(serverPlayer, (int) totalGold);
                        if (success) {
                            givePurchasedItems(serverPlayer, items);

                            // ✅ Send success payload (closes screen, plays sound, shows message)
                            TransactionSuccessS2CPayload.send(serverPlayer);
                          //  serverPlayer.sendSystemMessage(Component.literal("Fare thee well, adventurer!"));


                        } else {
                            TransactionFailedS2CPayload.send(serverPlayer, "Insufficient gold for this purchase.");
                        }
                    } else if ("sell".equals(transactionType)) {
                        removeSoldItems(serverPlayer, items);
                        giveGoldCoins(serverPlayer, (int) totalGold);
                    } else {
                        LOGGER.info("Unknown transaction type: {}", transactionType);
                    }
                }
            }
        } else {
        LOGGER.warn("Failed to process transaction. Response Code: {}", responseCode);
    if (player instanceof ServerPlayer serverPlayer) {
        TransactionFailedS2CPayload.send(serverPlayer, "Transaction failed: Server error.");
    }
        }
    }

    private static boolean removeGoldCoins(ServerPlayer player, int amount) {
        if (amount <= 0) return true;

        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                available += stack.getCount();
            }
        }

        if (player.containerMenu instanceof MerchantMenu merchantMenu) {
            ItemStack inputSlot = merchantMenu.getSlot(0).getItem();
            if (inputSlot.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                available += inputSlot.getCount();
            }
        }

        if (available < amount) {
            LOGGER.warn("Player did not have enough gold coins. Has: {}, Needs: {}", available, amount);
            return false;
        }

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                int toRemove = Math.min(stack.getCount(), amount);
                stack.shrink(toRemove);
                amount -= toRemove;
                if (amount <= 0) break;
            }
        }

        if (player.containerMenu instanceof MerchantMenu merchantMenu) {
            ItemStack inputSlot = merchantMenu.getSlot(0).getItem();
            if (inputSlot.getItem().equals(ItemRegistry.GOLD_COIN.get())) {
                int toRemove = Math.min(inputSlot.getCount(), amount);
                inputSlot.shrink(toRemove);
                merchantMenu.broadcastChanges();
                LOGGER.info("Removed {} gold from Villager trade slot.", toRemove);
            }
        }

        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();
        return true;
    }

    private static void givePurchasedItems(ServerPlayer player, List<JsonObject> items) {
        for (JsonObject item : items) {
            if (!item.has("item_id")) continue;

            String itemId = item.get("item_id").getAsString();
            int quantity = item.has("quantity") && !item.get("quantity").isJsonNull()
                    ? item.get("quantity").getAsInt() : 1;

            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) {
                LOGGER.warn("Invalid item_id: {}", itemId);
                continue;
            }

            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), quantity);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        player.inventoryMenu.broadcastChanges();
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
                    ? item.get("quantity").getAsInt() : 1;

            switch (itemId) {
                case "britannia_mod:weighted_wood_item" -> removeItemsByType(player, itemName, quantity, WeightedWoodItem.class);
                case "britannia_mod:weighted_fish_item" -> removeItemsByType(player, itemName, quantity, WeightedFishItem.class);
                case "britannia_mod:purity_ore_item" -> removeItemsByType(player, itemName, quantity, PurityOreItem.class);
                case "britannia_mod:grade_stone_item" -> removeItemsByType(player, itemName, quantity, GradeStoneItem.class);
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

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
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
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
import java.util.List;

public class SendTransactionToAPI {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void send(ServerLevel serverLevel, String playerUuid, String cityName, List<JsonObject> items, String transactionType, String npcType, String npcId, String npcName, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || items == null || items.isEmpty() || items.size() > 64) {
            if (player instanceof ServerPlayer invalidPlayer) {
                TransactionFailedS2CPayload.send(invalidPlayer, "Transaction rejected: invalid item request.");
            }
            return;
        }
        List<JsonObject> boundedItems = items.stream().map(JsonObject::deepCopy).toList();
        var credentials = ServerAuthRegistry.credentials(serverLevel.getServer());
        if (credentials.isEmpty()) {
            TransactionFailedS2CPayload.send(serverPlayer, "Transaction failed: server authentication unavailable.");
            return;
        }
        JsonObject payload = createPayload(playerUuid, cityName, npcId, npcName, transactionType,
            boundedItems, credentials.get().shardName());

        try {
            ServerHttpExecutor.submit(serverLevel.getServer(), () -> post(serverLevel, payload))
                .whenComplete((result, failure) -> serverLevel.getServer().execute(() -> {
                    if (serverLevel.getServer().getPlayerList().getPlayer(serverPlayer.getUUID()) != serverPlayer) return;
                    if (failure != null || result == null || result.statusCode() < 200 || result.statusCode() >= 300) {
                        TransactionFailedS2CPayload.send(serverPlayer, "Transaction failed: economy server unavailable.");
                        return;
                    }
                    applyAuthoritativeResponse(serverPlayer, result.response(), boundedItems);
                }));
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            TransactionFailedS2CPayload.send(serverPlayer, "Transaction failed: economy queue is full.");
        }
    }

    private static JsonObject createPayload(String playerUuid, String cityName, String npcId, String npcName,
                                            String transactionType, List<JsonObject> items, String shardName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player_uuid", playerUuid);
        payload.addProperty("city_name", cityName);
        payload.addProperty("npc_id", npcId);
        payload.addProperty("npc_name", npcName);
        payload.addProperty("shard", shardName);
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

    private static TransactionResult post(ServerLevel serverLevel, JsonObject payload) {
        try {
        var requestUri = ServerAuthRegistry.credentials(serverLevel.getServer()).orElseThrow().apiUrls()
                .resolve(Endpoint.TRANSACTION);
        HttpURLConnection conn = (HttpURLConnection) requestUri.toURL().openConnection();
        BoundedHttp.configure(conn);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (!RailsRequestAuthenticator.apply(conn, serverLevel.getServer())) {
            throw new IOException("Server authentication unavailable");
        }
        conn.setDoOutput(true);
        try (OutputStream output = conn.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        String body = BoundedHttp.readUtf8(
            status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(), 1_048_576);
        JsonObject response = body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        return new TransactionResult(status, response);
        } catch (Exception error) {
            LOGGER.warn("Authoritative transaction request failed: {}", error.toString());
            return new TransactionResult(0, new JsonObject());
        }
    }

    private static void applyAuthoritativeResponse(ServerPlayer player, JsonObject response,
                                                   List<JsonObject> submittedItems) {
        double totalGold = response.has("total_gold") ? response.get("total_gold").getAsDouble() : -1.0D;
        String transactionType = response.has("transaction_type")
            ? response.get("transaction_type").getAsString().toLowerCase() : "unknown";
        if (!Double.isFinite(totalGold) || totalGold < 0.0D) {
            TransactionFailedS2CPayload.send(player, "Transaction failed: invalid economy response.");
            return;
        }

        if ("purchase".equals(transactionType)) {
            List<JsonObject> purchased = authoritativePurchasedItems(response);
            if (purchased.isEmpty()) {
                TransactionFailedS2CPayload.send(player, "Transaction failed: no authoritative purchased items.");
                return;
            }
            if (removeGoldCoins(player, (int) Math.round(totalGold))) {
                givePurchasedItems(player, purchased);
                TransactionSuccessS2CPayload.send(player);
            } else {
                TransactionFailedS2CPayload.send(player, "Insufficient gold for this purchase.");
            }
        } else if ("sell".equals(transactionType)) {
            removeSoldItems(player, submittedItems);
            giveGoldCoins(player, (int) Math.round(totalGold));
            TransactionSuccessS2CPayload.send(player);
        } else {
            TransactionFailedS2CPayload.send(player, "Transaction failed: invalid economy response.");
        }
    }

    private static List<JsonObject> authoritativePurchasedItems(JsonObject response) {
        if (!response.has("purchased_items") || !response.get("purchased_items").isJsonArray()) return List.of();
        JsonArray items = response.getAsJsonArray("purchased_items");
        if (items.isEmpty() || items.size() > 64) return List.of();
        java.util.ArrayList<JsonObject> result = new java.util.ArrayList<>();
        for (var element : items) {
            if (!element.isJsonObject()) return List.of();
            JsonObject item = element.getAsJsonObject();
            String itemId = item.has("item_id") ? item.get("item_id").getAsString() : "";
            int quantity = item.has("quantity") ? item.get("quantity").getAsInt() : 0;
            if (ResourceLocation.tryParse(itemId) == null || itemId.length() > 128 || quantity <= 0 || quantity > 1_024) {
                return List.of();
            }
            result.add(item.deepCopy());
        }
        return List.copyOf(result);
    }

    private record TransactionResult(int statusCode, JsonObject response) {}

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

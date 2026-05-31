package com.seggellion.britannia_mod.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.api.RailsApi;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.SellItemsC2SPayload;
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.seggellion.britannia_mod.item.PurityOreItem;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;



public class TraderRoleHandler implements NpcRoleHandler {
    private final String role;
    private final String city;
    
    private static final Logger LOGGER = LogUtils.getLogger();

    public TraderRoleHandler(String role, String city) {
        this.role = role;
        this.city = city;
    }

    @Override
    public void fetchCatalog(Player player, String city, Consumer<List<Product>> callback) {
        JsonArray inventoryData = collectInventoryForRole(player);
        LOGGER.info("Generic Role Handler loading.");
        RailsApi.fetchTraderCatalog(city, role, inventoryData, callback);
    }

    @Override
    public void performTransaction(Player player, int entityId,
                                   Map<Product, Integer> cart,
                                   int totalPrice,
                                   Runnable onSuccess) {
        NetworkHandler.sendToServer(new SellItemsC2SPayload(city, role, entityId, toRequests(player, cart)));
        onSuccess.run();
    }

    @Override
    public String getActionLabel() {
        return "Sell";
    }

    @Override
    public ResourceLocation getBackground() {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/sell_screen.png");
    }

    // ==========================================================
    // Generic inventory collection logic
    // ==========================================================
    private JsonArray collectInventoryForRole(Player player) {
        String normalizedRole = role.toLowerCase();

        if (normalizedRole.contains("fish")) {
            return collectFishFromInventory(player);
        } else if (normalizedRole.contains("metal") || normalizedRole.contains("miner")) {
            return collectOreFromInventory(player);
        } else if (normalizedRole.contains("hunter") || normalizedRole.contains("butcher")) {
            return collectAnimalDrops(player);
        } else {
            // fallback: collect everything for testing or generic traders
            return collectGenericInventory(player);
        }
    }

    private JsonArray collectFishFromInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof WeightedFishItem fishItem) {
                JsonObject j = new JsonObject();
                j.addProperty("item_id", fishItem.getDescriptionId());
                j.addProperty("weight", fishItem.getWeight(stack));
                j.addProperty("quantity", stack.getCount());
                arr.add(j);
            }
        }
        return arr;
    }

    private JsonArray collectOreFromInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof PurityOreItem oreItem) {
                JsonObject j = new JsonObject();
                j.addProperty("item_id", oreItem.getDescriptionId());
                j.addProperty("purity", oreItem.getPurity(stack));
                j.addProperty("quantity", stack.getCount());
                arr.add(j);
            }
        }
        return arr;
    }

    private JsonArray collectAnimalDrops(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            String id = stack.getDescriptionId().toLowerCase();
            if (id.contains("meat") || id.contains("hide") || id.contains("pelt")) {
                JsonObject j = new JsonObject();
                j.addProperty("item_id", id);
                j.addProperty("quantity", stack.getCount());
                arr.add(j);
            }
        }
        return arr;
    }

    private JsonArray collectGenericInventory(Player player) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : player.getInventory().items) {
            JsonObject j = new JsonObject();
            j.addProperty("item_id", stack.getDescriptionId());
            j.addProperty("quantity", stack.getCount());
            arr.add(j);
        }
        return arr;
    }

    private List<SellItemsC2SPayload.ItemRequest> toRequests(Player player, Map<Product, Integer> cart) {
        List<SellItemsC2SPayload.ItemRequest> requests = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            Product product = entry.getKey();
            CompoundTag tag = null;
            try {
                tag = (CompoundTag) product.stack().save(player.registryAccess());
            } catch (Exception ignored) {
            }
            requests.add(new SellItemsC2SPayload.ItemRequest(
                    product.itemId(),
                    product.name(),
                    entry.getValue(),
                    tag
            ));
        }
        return requests;
    }
}

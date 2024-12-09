package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FishingEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FishingEventHandler.class);

    private static Map<Item, FishData> fishDataMap;

    private static Map<Item, FishData> getFishDataMap() {
        if (fishDataMap == null) {
            fishDataMap = new HashMap<>();
            fishDataMap.put(ItemRegistry.COD.get(), new FishData("cod", 0.7, 1.8));
            fishDataMap.put(ItemRegistry.SALMON.get(), new FishData("salmon", 1.1, 2.1));
            fishDataMap.put(ItemRegistry.TUNA.get(), new FishData("tuna", 7.1, 14.3));
            fishDataMap.put(ItemRegistry.TROUT.get(), new FishData("trout", 0.1, 0.6));
            fishDataMap.put(ItemRegistry.SWORDFISH.get(), new FishData("swordfish", 14.3, 42.9));
        }
        return fishDataMap;
    }

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        LOGGER.info("onItemFished event called");

        Player player = (Player) event.getEntity();
        Level world = player.level();

        LOGGER.info("Event drops size: {}", event.getDrops().size());
        if (event.getDrops().isEmpty()) {
            LOGGER.warn("No drops in ItemFishedEvent");
            return;
        }

        // Clear the drops and cancel the event to prevent default behavior
        event.getDrops().clear();
        event.setCanceled(true);

        // Randomly select a fish type
        Item fishItem = getRandomFish();
        FishData fishData = getFishDataMap().get(fishItem);

        if (fishData == null) {
            LOGGER.error("FishData not found for item {}", fishItem);
            return;
        }

        // Generate weight for the fish
        double weight = generateRandomWeight(fishData.minWeight, fishData.maxWeight);
        LOGGER.info("Generated weight: {}", weight);

        // Create the WeightedFishItem stack
        ItemStack weightedFishStack = new ItemStack(fishItem);
        WeightedFishItem weightedFish = (WeightedFishItem) weightedFishStack.getItem();

        // Set the weight and fish type into the WeightedFishItem
        weightedFish.setWeight(weightedFishStack, weight);
        weightedFish.setFishType(weightedFishStack, fishData.fishType);

        // Give the weighted fish directly to the player
        if (!player.getInventory().add(weightedFishStack)) {
            player.drop(weightedFishStack, false);
        }
        LOGGER.info("Gave WeightedFishItem (type: {}, weight: {}) directly to the player", fishData.fishType, weight);
    }

    private Item getRandomFish() {
        Random random = new Random();
        Object[] fishItems = getFishDataMap().keySet().toArray();
        return (Item) fishItems[random.nextInt(fishItems.length)];
    }

    private double generateRandomWeight(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private static class FishData {
        private final String fishType;
        private final double minWeight;
        private final double maxWeight;

        public FishData(String fishType, double minWeight, double maxWeight) {
            this.fishType = fishType;
            this.minWeight = minWeight;
            this.maxWeight = maxWeight;
        }
    }
}

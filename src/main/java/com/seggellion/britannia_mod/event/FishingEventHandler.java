package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionItemData;
import com.seggellion.britannia_mod.util.WeightedPicker;
import net.minecraft.resources.ResourceLocation;

import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;


public class FishingEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /* Keep your old FishData for variable mass range */
    private static Map<Item, FishData> fishDataMap;

    private static Map<Item, FishData> getFishDataMap() {
        if (fishDataMap == null) {
            fishDataMap = new HashMap<>();
            fishDataMap.put(ItemRegistry.COD.get(),       new FishData("cod",       0.7,  1.8));
            fishDataMap.put(ItemRegistry.SALMON.get(),    new FishData("salmon",    1.1,  2.1));
            fishDataMap.put(ItemRegistry.TUNA.get(),      new FishData("tuna",      7.1, 14.3));
            fishDataMap.put(ItemRegistry.TROUT.get(),     new FishData("trout",     0.1,  0.6));
            fishDataMap.put(ItemRegistry.SWORDFISH.get(), new FishData("swordfish",14.3, 42.9));
        }
        return fishDataMap;
    }

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        Level level   = player.level();

        // Server side only, please
        if (level.isClientSide()) return;

        // Kill vanilla drops
        event.getDrops().clear();
        event.setCanceled(true);

        BlockPos pos = player.blockPosition();
   LOGGER.info("pos: {}", pos);
        // 1) Pull region items of type "fish"
        List<RegionItemData> fishPool = RegionCache.itemsFor(pos, "fish");
        RegionItemData picked = (fishPool.isEmpty()) ? null : WeightedPicker.pick(fishPool);
   LOGGER.info("FishPool: {}", fishPool);


        ItemStack result;
        if (picked != null) {
            // 2) Resolve the namespaced item id -> Item
            ResourceLocation id = ResourceLocation.parse(picked.key); // per your rule
            Item fishItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);

            if (fishItem == null) {
                LOGGER.info("Region fish item not found in registry: {}", picked.key);
                result = fallbackRandomFish();
            } else {
                result = makeWeightedFishStack(fishItem);
            }
        } else {
            // No region or empty list; fallback to your old random logic
            result = fallbackRandomFish();
        }

        // 3) Give to player
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        // 4) Play sound
        level.playSound(null, pos, ModSounds.CATCH_FISH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private ItemStack fallbackRandomFish() {
        Item fishItem = getRandomFish();
        return makeWeightedFishStack(fishItem);
    }

    private ItemStack makeWeightedFishStack(Item fishItem) {
        FishData fishData = getFishDataMap().get(fishItem);
        if (fishData == null) {
            // If it’s not in your FishData map (maybe new content), just give a basic stack
            return new ItemStack(fishItem);
        }

        double weight = generateRandomWeight(fishData.minWeight, fishData.maxWeight);
        ItemStack stack = new ItemStack(fishItem);

        if (stack.getItem() instanceof WeightedFishItem weighted) {
            weighted.setWeight(stack, weight);
            weighted.setFishType(stack, fishData.fishType);
        }

        LOGGER.info("🎣 Caught {} ({} kg)", fishData.fishType, weight);
        return stack;
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

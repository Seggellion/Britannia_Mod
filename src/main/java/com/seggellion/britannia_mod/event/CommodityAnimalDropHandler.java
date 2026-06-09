package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class CommodityAnimalDropHandler {
    private CommodityAnimalDropHandler() {
    }

    @EventBusSubscriber(modid = "britannia_mod")
    public static final class Subscriber {
        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent event) {
            Entity entity = event.getEntity();
            if (entity.level().isClientSide) return;

            // Remove vanilla meats (and cooked variants) from the drop list.
            // Items like Wool or Feathers are ignored and will still drop.
            event.getDrops().removeIf(itemEntity -> {
                Item item = itemEntity.getItem().getItem();
                return item == Items.PORKCHOP || item == Items.COOKED_PORKCHOP ||
                       item == Items.BEEF || item == Items.COOKED_BEEF ||
                       item == Items.CHICKEN || item == Items.COOKED_CHICKEN ||
                       item == Items.MUTTON || item == Items.COOKED_MUTTON ||
                       item == Items.RABBIT || item == Items.COOKED_RABBIT;
                       
                       // Note: If you also want to stop vanilla leather/hides to prioritize 
                       // your RAW_HIDE and RABBIT_PELT, add them to this condition:
                       // || item == Items.LEATHER || item == Items.RABBIT_HIDE
            });

            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
            switch (type) {
                case "pig" -> {
                    weighted(event, ItemRegistry.RAW_PORK.get(), 1, 0.8D, 1.6D);
                    chanceWeighted(event, ItemRegistry.RAW_PORK_BELLY.get(), 0.35D, 0.4D, 0.9D);
                    chanceWeighted(event, ItemRegistry.RAW_PORK_RIBS.get(), 0.25D, 0.4D, 1.0D);
                    chanceWeighted(event, ItemRegistry.RAW_PORK_SHOULDER.get(), 0.20D, 0.5D, 1.1D);
                    chanceWeighted(event, ItemRegistry.ANIMAL_FAT.get(), 0.40D, 0.1D, 0.4D);
                }
                case "cow" -> {
                    weighted(event, ItemRegistry.RAW_BEEF.get(), 1, 1.0D, 2.0D);
                    chanceWeighted(event, ItemRegistry.RAW_BEEF_RIBS.get(), 0.30D, 0.5D, 1.2D);
                    chanceWeighted(event, ItemRegistry.RAW_BEEF_STEAK.get(), 0.35D, 0.6D, 1.2D);
                    chanceWeighted(event, ItemRegistry.RAW_BRISKET.get(), 0.20D, 0.5D, 1.0D);
                    chanceWeighted(event, ItemRegistry.RAW_HIDE.get(), 0.45D, 0.7D, 1.5D);
                }
                case "chicken" -> {
                    weighted(event, ItemRegistry.RAW_CHICKEN.get(), 1, 0.3D, 0.8D);
                    chanceWeighted(event, ItemRegistry.RAW_CHICKEN_LEG.get(), 0.35D, 0.1D, 0.3D);
                    chanceWeighted(event, ItemRegistry.RAW_CHICKEN_BREAST.get(), 0.35D, 0.2D, 0.4D);
                    chanceWeighted(event, ItemRegistry.RAW_CHICKEN_WING.get(), 0.25D, 0.1D, 0.2D);
                    chanceItem(event, ItemRegistry.CHICKEN_EGG.get(), 0.15D);
                }
                case "sheep" -> {
                    weighted(event, ItemRegistry.RAW_LAMB.get(), 1, 0.8D, 1.5D);
                    chanceWeighted(event, ItemRegistry.RAW_LAMB_CHOP.get(), 0.35D, 0.3D, 0.7D);
                    chanceWeighted(event, ItemRegistry.RAW_LEG_OF_LAMB.get(), 0.20D, 0.5D, 1.0D);
                }
                case "rabbit" -> {
                    weighted(event, ItemRegistry.RAW_RABBIT.get(), 1, 0.2D, 0.5D);
                    chanceWeighted(event, ItemRegistry.RAW_RABBIT_LEG.get(), 0.35D, 0.1D, 0.25D);
                    chanceWeighted(event, ItemRegistry.RABBIT_PELT.get(), 0.45D, 0.1D, 0.3D);
                }
                case "turkey" -> {
                    weighted(event, ItemRegistry.RAW_TURKEY.get(), 1, 0.5D, 1.1D);
                    chanceWeighted(event, ItemRegistry.RAW_TURKEY_LEG.get(), 0.35D, 0.2D, 0.4D);
                    chanceWeighted(event, ItemRegistry.RAW_TURKEY_BREAST.get(), 0.35D, 0.3D, 0.6D);
                }
                case "hind", "great_hart" -> {
                    weighted(event, ItemRegistry.RAW_VENISON.get(), 1, 0.8D, 1.8D);
                    chanceWeighted(event, ItemRegistry.RAW_VENISON_HAUNCH.get(), 0.30D, 0.6D, 1.4D);
                    chanceWeighted(event, ItemRegistry.RAW_VENISON_STEAK.get(), 0.30D, 0.5D, 1.0D);
                    chanceWeighted(event, ItemRegistry.DEER_HIDE.get(), 0.45D, 0.5D, 1.1D);
                }
                case "wolf" -> chanceWeighted(event, ItemRegistry.WOLF_PELT.get(), 0.35D, 0.4D, 0.9D);
                case "bear_brown", "bear_black", "bear_grizzly", "bear_polar" ->
                        chanceWeighted(event, ItemRegistry.BEAR_PELT.get(), 0.45D, 1.0D, 2.5D);
                default -> {
                }
            }
        }

        private static void weighted(LivingDropsEvent event, Item item, int count, double minWeight, double maxWeight) {
            ItemStack stack = WeightedCommodityItem.withGeneratedData(item, count, event.getEntity().getRandom(), minWeight, maxWeight);
            drop(event, stack);
        }

        private static void chanceWeighted(LivingDropsEvent event, Item item, double chance, double minWeight, double maxWeight) {
            if (event.getEntity().getRandom().nextDouble() <= chance) {
                weighted(event, item, 1, minWeight, maxWeight);
            }
        }

        private static void chanceItem(LivingDropsEvent event, Item item, double chance) {
            if (event.getEntity().getRandom().nextDouble() <= chance) {
                drop(event, new ItemStack(item));
            }
        }

        private static void drop(LivingDropsEvent event, ItemStack stack) {
            Entity entity = event.getEntity();
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
        }
    }
}
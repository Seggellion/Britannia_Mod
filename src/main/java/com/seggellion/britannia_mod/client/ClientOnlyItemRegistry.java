package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.client.screen.bank.BankChequeTint;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.function.Function;

public class ClientOnlyItemRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void registerSpawnEggColors(RegisterColorHandlersEvent.Item event) {
        LOGGER.info("Registering spawn egg colors for britannia_mod");

        // Register spawn egg colors for Mongbat and Horse Seller
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0x996600; // Background color for Mongbat spawn egg
                } else {
                    return 0xffffff; // Highlight color for Mongbat spawn egg
                }
            }
        }, ItemRegistry.MONGBAT_SPAWN_EGG.get());

        // Register spawn egg colors for Lich and Horse Seller
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0x996600; // Background color for Lich spawn egg
                } else {
                    return 0xffffff; // Highlight color for Lich spawn egg
                }
            }
        }, ItemRegistry.LICH_SPAWN_EGG.get());

        // Register spawn egg colors for Wraith
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0xD3D3D3; // Background color for Lich spawn egg
                } else {
                    return 0xffffff; // Highlight color for Lich spawn egg
                }
            }
        }, ItemRegistry.WRAITH_SPAWN_EGG.get());

        // Register spawn egg colors for Earth Elemental
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0x964B00; // Background color for Lich spawn egg
                } else {
                    return 0xffffff; // Highlight color for Lich spawn egg
                }
            }
        }, ItemRegistry.EARTH_ELEMENTAL_SPAWN_EGG.get());

        // Register spawn egg colors wisp
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0x02D8E9; // Background color for Lich spawn egg
                } else {
                    return 0xffffff; // Highlight color for Lich spawn egg
                }
            }
        }, ItemRegistry.WISP_SPAWN_EGG.get());

        // Register spawn egg colors for Ghoul
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0x8B0000; // Background color for Lich spawn egg
                } else {
                    return 0xffffff; // Highlight color for Lich spawn egg
                }
            }
        }, ItemRegistry.GHOUL_SPAWN_EGG.get());

        // Register spawn egg colors for Mongbat and Horse Seller
        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0xcc0000; // Background color for Daemon Seller spawn egg
                } else {
                    return 0xffffff; // Highlight color for DAemon Seller spawn egg
                }
            }
        }, ItemRegistry.DAEMON_SPAWN_EGG.get());
        LOGGER.info("Registered colors for Mongbat and Horse Seller spawn eggs.");

        // Bank interface rebuild: a cheque is drawn on the deed artwork, tinted by the balance
        // that funded it, so gold, silver and copper cheques are told apart at a glance. The
        // mapping lives in BankChequeTint so it can be tested; this only wires it up.
        event.register((stack, layer) -> {
            if (layer != 0) return 0xFFFFFF;
            return BankChequeTint.forData(stack.get(DataComponentRegistry.BANK_CHEQUE_DATA.get()));
        }, ItemRegistry.BANK_CHEQUE.get());
    }


 public void registerModelData(Item item, Function<ItemStack, Integer> modelDataProvider) {
        // Log registration attempt
        LOGGER.info("registerModelData Loaded for item: {}, modelDataProvider: {}", item, modelDataProvider);

        // Register the custom model data property
        ItemProperties.register(
            item,
            ResourceLocation.fromNamespaceAndPath("minecraft", "custom_model_data"),
            (stack, world, entity, seed) -> {
                LOGGER.info("Custom model data callback triggered for stack: {}", stack);

                // Retrieve the custom model data using the provided function
                int modelDataValue = modelDataProvider.apply(stack);

                LOGGER.info("Registering custom model data for item: {}, CustomModelData: {}", item, modelDataValue);
                return modelDataValue;
            }
        );
    }


}

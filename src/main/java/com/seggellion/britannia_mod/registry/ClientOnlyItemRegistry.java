// ClientOnlyItemRegistry.java
package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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

        event.register(new ItemColor() {
            @Override
            public int getColor(ItemStack stack, int layer) {
                if (layer == 0) {
                    return 0xFFA500; // Background color for Horse Seller spawn egg
                } else {
                    return 0xffffff; // Highlight color for Horse Seller spawn egg
                }
            }
        }, ItemRegistry.HORSE_SELLER_SPAWN_EGG.get());

        LOGGER.info("Registered colors for Mongbat and Horse Seller spawn eggs.");
    }
}

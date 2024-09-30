package com.seggellion.britannia_mod.features;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

public class DiamondToolControl {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        handlePlayerInventory(event.getEntity());
    }

    // Check player inventory and remove diamond tools
    private void handlePlayerInventory(Player player) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            removeDiamondTools(serverPlayer);
        }
    }

    // Remove diamond tools from the player's inventory
    private void removeDiamondTools(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (isDiamondTool(itemStack)) {
                player.getInventory().removeItem(i, itemStack.getCount());  // Remove all diamond tools
                LOGGER.info("Removed {} from player {}", itemStack.getItem().getDescription().getString(), player.getName().getString());
            }
        }
    }

    // Utility method to check if the item is a diamond tool
    private boolean isDiamondTool(ItemStack itemStack) {
        return itemStack.getItem() == Items.DIAMOND_PICKAXE ||
               itemStack.getItem() == Items.DIAMOND_AXE ||
               itemStack.getItem() == Items.DIAMOND_SWORD ||
               itemStack.getItem() == Items.DIAMOND_SHOVEL ||
               itemStack.getItem() == Items.DIAMOND_HOE;
    }
}

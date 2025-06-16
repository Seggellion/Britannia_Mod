package com.seggellion.britannia_mod.event;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class InventoryHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LOGS = 80; // Maximum number of logs allowed
    private static final Set<ItemEntity> cooldownEntities = new HashSet<>(); // Tracks entities on cooldown

@SubscribeEvent
public void onItemPickup(Pre event) {
    ItemEntity itemEntity = event.getItemEntity();
    if (itemEntity == null || itemEntity.getItem().isEmpty()) {
        LOGGER.warn("ItemEntity is null or empty (minecraft:air), skipping log check.");
        return;
    }

    if (event.getPlayer() instanceof ServerPlayer player) {
        int logCount = countLogsInInventory(player);

        ItemStack pickedUpStack = itemEntity.getItem();
        if (pickedUpStack.is(ItemTags.LOGS)) { // Check if the picked-up item is a log
            int totalLogs = logCount + pickedUpStack.getCount();

            if (totalLogs > MAX_LOGS) {
                // Notify the player
                player.sendSystemMessage(Component.literal("You cannot carry more than " + MAX_LOGS + " logs."));
                LOGGER.info("Log pickup prevented: inventory exceeds limit.");

                // Deny the pickup using setCanPickup
                event.setCanPickup(TriState.FALSE);
            }
        }
    }
}




    private int countLogsInInventory(ServerPlayer player) {
        int logCount = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ItemTags.LOGS)) { // Check if the item is a log
                logCount += stack.getCount();
            }
        }
        return logCount;
    }

    private void dropExcessLogs(ServerPlayer player, int excessLogs, ItemStack pickedUpStack) {
        Level level = player.level(); // Updated to use level()
        Vec3 dropPosition = player.position().add(player.getLookAngle().scale(2)); // Drop 2 blocks ahead of the player

        // Split the excess logs from the picked-up stack
        ItemStack excessStack = pickedUpStack.split(excessLogs);

        // Create and drop the item entity for the excess logs
        ItemEntity itemEntity = new ItemEntity(level, dropPosition.x, dropPosition.y, dropPosition.z, excessStack);
        itemEntity.setPickUpDelay(20); // Prevent immediate pickup
        level.addFreshEntity(itemEntity);

        LOGGER.info("Dropped {} excess logs at {}", excessLogs, dropPosition);
    }
}



package com.seggellion.britannia_mod.event;


import com.seggellion.britannia_mod.registry.ItemRegistry;

import com.seggellion.britannia_mod.ModSounds;

import net.minecraft.world.phys.Vec3;
import com.seggellion.britannia_mod.block.entity.WoodSpawnBlockEntity;
import com.seggellion.britannia_mod.block.WoodSpawnBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class PopulationEventHandler {


    private static final Logger LOGGER = LogUtils.getLogger();

@SubscribeEvent
public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled()) return;

    LOGGER.info("Use event");
    // Check if the block is your WoodSpawnBlock
    Level level = event.getLevel();
    BlockPos pos = event.getPos();
    BlockState state = level.getBlockState(pos);
    if (!(state.getBlock() instanceof WoodSpawnBlock)) {
        return;
    }

    // Check if holding a vanilla Name Tag with custom name
    Player player = event.getEntity();
    InteractionHand hand = event.getHand();
    ItemStack heldItem = player.getItemInHand(hand);

    if (heldItem.is(Items.NAME_TAG)) {
        // The default "Name Tag" item can be renamed in an anvil.
        // So we check the display name:
        String nameOnTag = heldItem.getDisplayName().getString().trim();
        if (!nameOnTag.isEmpty() && !nameOnTag.equals("Name Tag")) {


// If the name is bracketed, remove them
if (nameOnTag.startsWith("[") && nameOnTag.endsWith("]")) {
    nameOnTag = nameOnTag.substring(1, nameOnTag.length() - 1);
}
            // The user has presumably renamed it
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WoodSpawnBlockEntity woodSpawnBE) {
                woodSpawnBE.setCityName(nameOnTag);
                player.displayClientMessage(
                    Component.literal("Set city name to: " + nameOnTag),
                    true
                );
                // optionally consume 1 item from the stack:
                // heldItem.shrink(1);

                // Mark the event as handled
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    } else {
        // Possibly show the current city if not holding a valid name tag
        // or do nothing
    }
}

}
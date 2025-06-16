

package com.seggellion.britannia_mod.event;


import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.*;
import com.seggellion.britannia_mod.block.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public class PopulationEventHandler {


    private static final Logger LOGGER = LogUtils.getLogger();

@SubscribeEvent
public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled()) return;

    LOGGER.info("Use event");

    // Check if the block is a spawn block
    Level level = event.getLevel();
    BlockPos pos = event.getPos();
    BlockState state = level.getBlockState(pos);
    Block block = state.getBlock();

    // Check if the block is one of the known spawn blocks
    if (!(block instanceof WoodSpawnBlock || 
          block instanceof FishSpawnBlock || 
        block instanceof BlacksmithSpawnBlock || 
            block instanceof MetalSpawnBlock || 
              block instanceof StoneSpawnBlock || 
          block instanceof HorseSpawnBlock)) {
     //   
     //     block instanceof FoodSpawnBlock
 
        return;
    }

    // Check if holding a vanilla Name Tag with a custom name
    Player player = event.getEntity();
    InteractionHand hand = event.getHand();
    ItemStack heldItem = player.getItemInHand(hand);

    if (heldItem.is(Items.NAME_TAG)) {
        String nameOnTag = heldItem.getDisplayName().getString().trim();
        if (!nameOnTag.isEmpty() && !nameOnTag.equals("Name Tag")) {
            // Remove brackets if present
            if (nameOnTag.startsWith("[") && nameOnTag.endsWith("]")) {
                nameOnTag = nameOnTag.substring(1, nameOnTag.length() - 1);
            }

            // Apply the city name to the block entity
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WoodSpawnBlockEntity woodSpawnBE) {
                woodSpawnBE.setCityName(nameOnTag);
            } else if (be instanceof FishSpawnBlockEntity fishSpawnBE) {
                fishSpawnBE.setCityName(nameOnTag);
            } else if (be instanceof HorseSpawnBlockEntity horseSpawnBE) {
                horseSpawnBE.setCityName(nameOnTag);
            } else if (be instanceof BlacksmithSpawnBlockEntity blacksmithSpawnBE) {
                blacksmithSpawnBE.setCityName(nameOnTag);
            } else if (be instanceof MetalSpawnBlockEntity metalSpawnBE) {
                metalSpawnBE.setCityName(nameOnTag);
            } else if (be instanceof StoneSpawnBlockEntity stoneSpawnBE) {
                stoneSpawnBE.setCityName(nameOnTag);
            } else {
                return; // BlockEntity is not a recognized type
            }

            // Notify the player of the change
            player.displayClientMessage(
                Component.literal("Set city name to: " + nameOnTag),
                true
            );

            // Cancel the event to prevent further processing
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    } 
}


}


package com.seggellion.britannia_mod.event;


import com.seggellion.britannia_mod.registry.ItemRegistry;

import com.seggellion.britannia_mod.ModSounds;

import net.minecraft.world.phys.Vec3;
import com.seggellion.britannia_mod.block.entity.WoodSpawnBlockEntity;
import com.seggellion.britannia_mod.block.WoodSpawnBlock;
import com.seggellion.britannia_mod.block.entity.ArchitectSpawnBlockEntity;
import com.seggellion.britannia_mod.block.ArchitectSpawnBlock;
import com.seggellion.britannia_mod.block.entity.MetalSpawnBlockEntity;
import com.seggellion.britannia_mod.block.MetalSpawnBlock;
import com.seggellion.britannia_mod.block.entity.StoneSpawnBlockEntity;
import com.seggellion.britannia_mod.block.StoneSpawnBlock;
import com.seggellion.britannia_mod.block.entity.FishSpawnBlockEntity;
import com.seggellion.britannia_mod.block.FishSpawnBlock;
import com.seggellion.britannia_mod.block.entity.HorseSpawnBlockEntity;
import com.seggellion.britannia_mod.block.HorseSpawnBlock;
import com.seggellion.britannia_mod.block.entity.BlacksmithSpawnBlockEntity;
import com.seggellion.britannia_mod.block.BlacksmithSpawnBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
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
              block instanceof ArchitectSpawnBlock || 
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
            } else if (be instanceof ArchitectSpawnBlockEntity architectSpawnBE) {
                architectSpawnBE.setCityName(nameOnTag);
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
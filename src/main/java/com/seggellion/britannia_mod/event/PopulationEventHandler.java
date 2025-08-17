

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
import net.minecraft.core.Direction;


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

@SubscribeEvent
public void onRightClickBlock_PlaceFish(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled()) return;

    final Player player = event.getEntity();
    if (player == null) return;

    final ItemStack held = player.getItemInHand(event.getHand());
    if (!(held.getItem() instanceof com.seggellion.britannia_mod.item.WeightedFishItem fishItem)) return;

    // Only intercept when player can’t normally build (Adventure)
    if (player.isCreative() || player.isSpectator() || player.getAbilities().mayBuild) return;

    final Level level = event.getLevel();

    // Client: acknowledge so the animation feels right; server does the placement.
    if (level.isClientSide()) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        return;
    }

    final BlockPos clickedPos = event.getPos();
    final Direction face = event.getFace() != null ? event.getFace() : Direction.UP;

    final BlockState clickedState = level.getBlockState(clickedPos);
    final boolean clickedReplaceable = clickedState.canBeReplaced();
    final BlockPos placePos = clickedReplaceable ? clickedPos : clickedPos.relative(face);

    final BlockState target = level.getBlockState(placePos);
    if (!(target.isAir() || target.canBeReplaced())) return;

    // Support below the target (or above if placing upward from the underside)
    final BlockPos supportPos = (face == Direction.DOWN) ? placePos.above() : placePos.below();

    // Quick sturdy check so it “just works”; swap to your tag once verified.
    if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) return;

    BlockState state = fishItem.getBlock().defaultBlockState();
    if (state.hasProperty(com.seggellion.britannia_mod.block.HorizontalFacingBlock.FACING)) {
        state = state.setValue(
            com.seggellion.britannia_mod.block.HorizontalFacingBlock.FACING,
            player.getDirection().getOpposite()
        );
    }

    if (level.setBlock(placePos, state, 3)) {
        // transfer item data -> block entity (REUSE held & fishItem; do not redeclare)
        var be = level.getBlockEntity(placePos);
        if (be instanceof com.seggellion.britannia_mod.block.entity.FishBlockEntity fbe) {
            fbe.setWeight(fishItem.getWeight(held));
            fbe.setFishType(fishItem.getFishType(held));
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

@SubscribeEvent
public void onLeftClickBlock_PickupFish(
    net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event
) {
    var player = event.getEntity();
    var level = event.getLevel();
    if (player == null) return;
    LOGGER.info("Left clicked block");

    // Only special-handle when normal breaking is blocked
    boolean isAdventure = !player.getAbilities().mayBuild && !player.isCreative() && !player.isSpectator();
    if (!isAdventure) return;

    var pos = event.getPos();
    var state = level.getBlockState(pos);

    // Only our fish blocks
    if (!(state.getBlock() instanceof com.seggellion.britannia_mod.block.WeightedFishBlock)) return;

    if (level.isClientSide()) {
        event.setCanceled(true); // LeftClickBlock has no setCancellationResult
        return;
    }

    // Build the drop with preserved data
    ItemStack drop = new ItemStack(state.getBlock().asItem());
    if (drop.getItem() instanceof com.seggellion.britannia_mod.item.WeightedFishItem fishItem) {
        var be = level.getBlockEntity(pos);
        if (be instanceof com.seggellion.britannia_mod.block.entity.FishBlockEntity fbe) {
            fishItem.setWeight(drop, fbe.getWeight());
            fishItem.setFishType(drop, fbe.getFishType());
        }
    }

    // Remove the block and spawn the item
    level.removeBlock(pos, false);
    net.minecraft.world.level.block.Block.popResource(level, pos, drop);

    // Stop vanilla from trying anything else
    event.setCanceled(true); // no InteractionResult on LeftClickBlock
}

@SubscribeEvent
public void onBreakFish(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
    var level = (net.minecraft.server.level.ServerLevel) event.getLevel();
    var pos = event.getPos();
    var state = level.getBlockState(pos);
        LOGGER.info("Fish block broken");

    if (!(state.getBlock() instanceof com.seggellion.britannia_mod.block.WeightedFishBlock)) return;

    // Build weighted drop
    ItemStack drop = new ItemStack(state.getBlock().asItem());
    if (drop.getItem() instanceof com.seggellion.britannia_mod.item.WeightedFishItem fishItem) {
        var be = level.getBlockEntity(pos);
        if (be instanceof com.seggellion.britannia_mod.block.entity.FishBlockEntity fbe) {
            fishItem.setWeight(drop, fbe.getWeight());
            fishItem.setFishType(drop, fbe.getFishType());
        }
    }

    // Prevent default drops and do our own
    event.setCanceled(true);
    level.removeBlock(pos, false);
    net.minecraft.world.level.block.Block.popResource(level, pos, drop);
}



}
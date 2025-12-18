package com.seggellion.britannia_mod.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;

// [FIX] Import the correct class used in ToolRegistry
import com.seggellion.britannia_mod.item.QualityToolItem; 

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class BreakSpeedHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof TwoHandedAxeItem) {
                BlockPos pos = event.getPos();
                Level level = player.getCommandSenderWorld();
                BlockState state = level.getBlockState(pos);

                if (state.is(BlockTags.LOGS)) {
                    level.playSound(null, pos, ModSounds.CHOP_TREE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        }
    }

    // [IMPORTANT] Ensure onPlayerTick is DELETED from this file entirely.

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockState state = event.getState();
            ItemStack heldItem = player.getMainHandItem();
            Item item = heldItem.getItem();

            if (item instanceof TwoHandedAxeItem) {
                if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                    event.setCanceled(true); 
                } 
            }
            
            // [FIX] Check for QualityToolItem, NOT BritanniaPickaxeItem
            if (item instanceof QualityToolItem) {
                if (!state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
                    // LOGGER.info("Preventing block breaking for non-stone blocks with QualityToolItem.");
                    event.setCanceled(true);
                } else {
                    // LOGGER.info("Breaking stone or ore block with QualityToolItem.");
                    event.setNewSpeed(2.0F); 
                }
            }
        }
    }
}
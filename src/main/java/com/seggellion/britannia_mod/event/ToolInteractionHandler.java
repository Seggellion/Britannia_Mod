package com.seggellion.britannia_mod.event;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;

public class ToolInteractionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Plays a chopping sound when left-clicking logs with a TwoHandedAxe.
     */
    @SubscribeEvent
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof TwoHandedAxeItem) {
                BlockPos pos = event.getPos();
                Level level = player.getCommandSenderWorld();
                BlockState state = level.getBlockState(pos);

                // Check if the block is a log
                if (state.is(BlockTags.LOGS)) {
                    level.playSound(
                        null,
                        pos,
                        ModSounds.CHOP_TREE.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F
                    );
                }
            }
        }
    }

    /**
     * Restricts which blocks can be broken with specific tools.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockState state = event.getState();
            ItemStack heldItem = player.getMainHandItem();
            Item item = heldItem.getItem();

            // TwoHandedAxe → logs only
            if (item instanceof TwoHandedAxeItem) {
                boolean isLog = state.is(BlockTags.LOGS);
                boolean isLeaves = state.is(BlockTags.LEAVES);

                if (!isLog && !isLeaves) {
                    event.setCanceled(true);
                } else {
                    event.setNewSpeed(isLeaves ? 4.0F : 2.0F);
                }
            }
            // IronPickaxe → stone/ore only
            else if (item instanceof BritanniaPickaxeItem) {
                boolean isStone = state.is(BlockTags.BASE_STONE_OVERWORLD);
                boolean isOre = state.is(Blocks.IRON_ORE) ||
                        state.is(Blocks.DEEPSLATE_IRON_ORE) ||
                        state.is(Blocks.GOLD_ORE) ||
                        state.is(BlockRegistry.COPPER_ORE.get()) ||
                        state.is(BlockRegistry.TIN_ORE.get()) ||
                        state.is(BlockRegistry.SILVER_ORE.get()) ||
                        state.is(BlockRegistry.GOLD_ORE.get()) ||
                        state.is(BlockRegistry.SHADOW_IRON_ORE.get()) ||
                        state.is(BlockRegistry.AGAPITE_ORE.get()) ||
                        state.is(BlockRegistry.VERITE_ORE.get()) ||
                        state.is(BlockRegistry.VALORITE_ORE.get()) ||
                        state.is(BlockRegistry.HIGH_PURITY_SILVER_ORE.get());
                if (!isStone && !isOre) {
                    LOGGER.info("Preventing block breaking for non-stone/ore block with IronPickaxe: {}",
                                state.getBlock());
                    event.setCanceled(true);
                } else {
                    // Optionally set a custom break speed for stone/ore
                    event.setNewSpeed(2.0F);
                }
            }
        }
    }
}

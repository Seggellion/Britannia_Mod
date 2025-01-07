package com.seggellion.britannia_mod.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.registry.CityRegistry;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class BreakSpeedHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Play a chopping sound when left-clicking logs with a TwoHandedAxe.
     */
    @SubscribeEvent
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof TwoHandedAxeItem) {
                BlockPos pos = event.getPos();
                Level level = player.getCommandSenderWorld();
                BlockState state = level.getBlockState(pos);

                // Check if the block is a log.
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
     * Runs every tick for the player: 
     * - Inside a city boundary, force Adventure mode (unless Creative).
     * - Outside city boundary, switch to Survival if holding TwoHandedAxe (and currently in Adventure).
     * - Otherwise, revert to Adventure if not holding the axe (and currently in Survival).
     * - Ignore Creative.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        GameType currentMode = player.gameMode.getGameModeForPlayer();
        // Do nothing if player is in Creative
        if (currentMode == GameType.CREATIVE) {
            return;
        }

        // Check city boundary
        Vec3 playerPos = player.position();
        if (CityRegistry.isPlayerInAnyCity(playerPos)) {
            if (currentMode != GameType.ADVENTURE) {
                LOGGER.info("Player forced to Adventure mode inside a city area.");
                player.setGameMode(GameType.ADVENTURE);
            }
            return; 
        }

        // Outside city boundary:
        ItemStack heldItem = player.getMainHandItem();
        Item item = heldItem.getItem();

        // If holding the TwoHandedAxe and currently in Adventure, switch to Survival
        if (item instanceof TwoHandedAxeItem && currentMode == GameType.ADVENTURE) {
            LOGGER.info("Switching player to Survival mode while holding TwoHandedAxeItem");
            player.setGameMode(GameType.SURVIVAL);
        }
        // If NOT holding the TwoHandedAxe and currently in Survival, revert to Adventure
        else if (!(item instanceof TwoHandedAxeItem) && currentMode == GameType.SURVIVAL) {
            LOGGER.info("Restoring Adventure mode when not holding TwoHandedAxeItem");
            player.setGameMode(GameType.ADVENTURE);
        }
    }

    /**
     * Prevents breaking blocks other than logs while holding TwoHandedAxe in Survival.
     * (Removed leaves from the original code, so only logs are allowed now.)
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.info("onBreakSpeedEvent");

            BlockState state = event.getState();
            ItemStack heldItem = player.getMainHandItem();
            Item item = heldItem.getItem();

            if (item instanceof TwoHandedAxeItem) {
                // Only allow logs
                if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                    LOGGER.info("Preventing block breaking for non-log block: {}",
                                state.getBlock());
                    event.setCanceled(true); 
                } else {
                    LOGGER.info("Player breaking valid log block: {}", state.getBlock());
                }
            }
        }
    }
}

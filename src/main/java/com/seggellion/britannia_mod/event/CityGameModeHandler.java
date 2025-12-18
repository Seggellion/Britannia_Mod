package com.seggellion.britannia_mod.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.CityRegistry;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
// [FIX] Import the correct class
import com.seggellion.britannia_mod.item.QualityToolItem; 

public class CityGameModeHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        GameType currentMode = player.gameMode.getGameModeForPlayer();

        if (currentMode == GameType.CREATIVE || currentMode == GameType.SPECTATOR) {
            return;
        }

        // 1. City Check
        if (CityRegistry.isPlayerInAnyCity(player.position())) {
            if (currentMode != GameType.ADVENTURE) {
                LOGGER.info("🏙️ City Boundary detected. Forcing Adventure Mode.");
                player.setGameMode(GameType.ADVENTURE);
            }
            return;
        }

        // 2. Tool Check
        ItemStack heldItem = player.getMainHandItem();
        Item item = heldItem.getItem();

        // [FIX] Use instanceof for both checks. This matches the actual item class.
        boolean isSpecialTool = (item instanceof QualityToolItem) || (item instanceof TwoHandedAxeItem);

        if (isSpecialTool) {
            // If holding tool, ensure SURVIVAL
            if (currentMode == GameType.ADVENTURE) {
                LOGGER.info("⚔️ Tool Detected ({}). Switching to SURVIVAL.", item.getClass().getSimpleName());
                player.setGameMode(GameType.SURVIVAL);
            }
        } else {
            // If NOT holding tool, ensure ADVENTURE
            if (currentMode == GameType.SURVIVAL) {
                // [TRAP] This log will tell us if THIS logic is causing the revert
                LOGGER.info("✋ No Tool. Restoring ADVENTURE. (Item was: {})", item.getClass().getSimpleName());
                player.setGameMode(GameType.ADVENTURE);
            }
        }
    }
}
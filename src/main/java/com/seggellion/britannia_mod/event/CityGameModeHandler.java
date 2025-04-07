package com.seggellion.britannia_mod.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;    // <— Notice: "world.level.GameType" is the newer location
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.CityRegistry;
import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;

public class CityGameModeHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        // The getPlayer() or getEntity() method might differ depending on your NeoForge version
        // If your event object is "event.player" rather than "getPlayer()", adjust accordingly.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        GameType currentMode = player.gameMode.getGameModeForPlayer();

        // Do nothing if player is in Creative
        if (currentMode == GameType.CREATIVE) {
            return;
        }

        // Check if the player is inside a city boundary
        if (CityRegistry.isPlayerInAnyCity(player.position())) {
            // Force Adventure mode inside city boundaries
            if (currentMode != GameType.ADVENTURE) {
                LOGGER.info("Player forced to Adventure mode inside a city area.");
                player.setGameMode(GameType.ADVENTURE);
            }
            return;
        }

        // Outside city boundary
        ItemStack heldItem = player.getMainHandItem();
        Item item = heldItem.getItem();

        // If holding IronPickaxe or TwoHandedAxe and currently in Adventure => switch to Survival
        if ((item == ToolRegistry.PICKAXE.get() || item instanceof TwoHandedAxeItem)
                && currentMode == GameType.ADVENTURE) {
            LOGGER.info("Switching player to Survival mode while holding UO Pickaxe or TwoHandedAxe.");

            player.setGameMode(GameType.SURVIVAL);
        }
        // If not holding either and in Survival => revert to Adventure
        else if (!(item == ToolRegistry.PICKAXE.get())
                && !(item instanceof TwoHandedAxeItem)
                && currentMode == GameType.SURVIVAL) {
            LOGGER.info("Restoring Adventure mode when not holding IronPickaxe or TwoHandedAxe.");
            player.setGameMode(GameType.ADVENTURE);
        }
    }
}

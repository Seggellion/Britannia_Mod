package com.seggellion.britannia_mod.wildresource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Server-authoritative Adventure harvesting plus ordinary-break accounting. */
public final class WildResourceInteractionHandler {
    private static final WildResourceInteractionHandler INSTANCE = new WildResourceInteractionHandler();
    private static boolean registered;

    private WildResourceInteractionHandler() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            return;
        }
        WildResourceNode node = WildResourceSavedData.get(level).nodeAt(event.getPos()).orElse(null);
        WildResourceEntry entry = node == null ? null : WildResources.registry().find(node.resourceId()).orElse(null);
        if (entry != null && entry.harvestStrategy().harvest(
                level, event.getPos(), player, player.getMainHandItem()
        )) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOrdinaryBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            WildResourceHarvestService.recordOrdinaryBreak(level, event.getPos());
        }
    }
}

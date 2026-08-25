package com.seggellion.britannia_mod.wildresource;

import com.seggellion.britannia_mod.resource.extraction.ManagedExtractionPolicy;
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
        WildResourceEntry entry = standingTrackedEntry(level, event.getPos());
        if (entry == null) {
            return;
        }
        // A tracked resource owns the gesture even when authorization or its tool policy refuses.
        // Falling through could hand a denied Adventure click back to vanilla destruction.
        event.setCanceled(true);
        entry.harvestStrategy().harvest(level, event.getPos(), player, player.getMainHandItem());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTrackedBreakGuard(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || standingTrackedEntry(level, event.getPos()) == null) {
            return;
        }

        // A real Creative player performs administrative removal. Vanilla produces no loot and
        // the LOWEST accounting listener clears the node/cooldown without posting an economy event.
        if (ManagedExtractionPolicy.actorOf(player) == ManagedExtractionPolicy.Actor.PLAYER
                && ManagedExtractionPolicy.isCreativeGameMode(player)) {
            return;
        }

        WildResourceHarvestPolicy.Assessment authorization =
                WildResourceHarvestPolicy.evaluate(level, event.getPos(), player);
        if (!authorization.allowed()) {
            event.setCanceled(true);
            authorization.explain(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOrdinaryBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getPlayer() instanceof ServerPlayer player) {
            WildResourceHarvestService.recordOrdinaryBreak(level, event.getPos(), player);
        }
    }

    private static WildResourceEntry standingTrackedEntry(ServerLevel level, net.minecraft.core.BlockPos position) {
        WildResourceNode node = WildResourceSavedData.get(level).nodeAt(position).orElse(null);
        WildResourceEntry entry = node == null
                ? null
                : WildResources.registry().find(node.resourceId()).orElse(null);
        if (entry == null || entry.existingNodeValidator().inspect(level, position)
                == WildResourceEntry.ExistingNodeState.MISSING_OR_REPLACED) {
            return null;
        }
        return entry;
    }
}

package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.vegetation.ManagedVegetationCutTools;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/** Prevents vanilla destruction of managed plants and routes accepted cuts through one transaction. */
public final class ManagedVegetationInteractionHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || ManagedVegetationService.resolveOwnedCutNode(level, event.getPos()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (ManagedVegetationCutTools.canCut(player)) {
            ManagedVegetationService.cutNode(player, level, event.getPos());
        }
    }

    /** Fallback for break paths that do not emit a cancellable left-click interaction first. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || ManagedVegetationService.resolveOwnedCutNode(level, event.getPos()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (ManagedVegetationCutTools.canCut(player)) {
            ManagedVegetationService.cutNode(player, level, event.getPos());
        }
    }

    /** Runs after placement vetoes so only committed construction relinquishes node ownership. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<net.minecraft.core.BlockPos> positions = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots().stream().map(snapshot -> snapshot.getPos().immutable()).toList()
                : List.of(event.getPos().immutable());
        ManagedVegetationService.retireNodesClaimedByPlacement(level, positions);
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().forEach(position ->
                    ManagedVegetationService.scheduleReconciliation(level, position));
        }
    }
}

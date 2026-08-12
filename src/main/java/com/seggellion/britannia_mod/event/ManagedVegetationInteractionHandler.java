package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.farming.GrainHarvestTools;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Prevents vanilla destruction of managed plants and routes accepted cuts through one transaction. */
public final class ManagedVegetationInteractionHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || ManagedVegetationService.resolveNode(level, event.getPos()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (GrainHarvestTools.isGrainHarvestBlade(player.getMainHandItem())) {
            ManagedVegetationService.cutNode(player, level, event.getPos());
        }
    }

    /** Fallback for break paths that do not emit a cancellable left-click interaction first. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || ManagedVegetationService.resolveNode(level, event.getPos()).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (GrainHarvestTools.isGrainHarvestBlade(player.getMainHandItem())) {
            ManagedVegetationService.cutNode(player, level, event.getPos());
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().forEach(position ->
                    ManagedVegetationService.scheduleReconciliation(level, position));
        }
    }
}

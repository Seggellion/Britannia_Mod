package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.block.HouseFarmPlotBlock;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import com.seggellion.britannia_mod.farming.OrangeTreeUtils;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

/** Keeps linked tall/tree presentations from bypassing house-plot ownership or the hoe-only reset. */
public final class HouseFarmPlotInteractionHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        HouseFarmPlotBlockEntity plot = linkedPlot(level, event.getPos()).orElse(null);
        if (plot == null || HouseFarmPlotBlock.mayManagePlot(level, plot.getBlockPos(), player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.displayClientMessage(Component.literal("You may only tend a house farm plot you own."), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || event.getState().getBlock() instanceof HouseFarmPlotBlock) {
            return;
        }
        HouseFarmPlotBlockEntity plot = linkedPlot(level, event.getPos()).orElse(null);
        if (plot == null) {
            return;
        }
        // The assigned presentation is harvested through the existing interaction pipeline.
        // Breaking it would silently unassign or orphan it, so only the plot itself may be broken.
        event.setCanceled(true);
        player.displayClientMessage(Component.literal("Use the proper harvest tool, or clear the plot with a farming hoe."), true);
    }

    static Optional<HouseFarmPlotBlockEntity> linkedPlot(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HouseFarmPlotBlockEntity direct) {
            return Optional.of(direct);
        }
        BlockPos tallAnchor = TallCropSupport.findAnchor(level, pos);
        if (tallAnchor != null && level.getBlockEntity(tallAnchor) instanceof HouseFarmPlotBlockEntity tallPlot) {
            return Optional.of(tallPlot);
        }
        return OrangeTreeUtils.findRoot(level, pos)
                .flatMap(root -> root.getSoilBlockEntity(level))
                .filter(HouseFarmPlotBlockEntity.class::isInstance)
                .map(HouseFarmPlotBlockEntity.class::cast);
    }
}

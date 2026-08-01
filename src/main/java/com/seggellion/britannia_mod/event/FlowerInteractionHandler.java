package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.block.FlowerBlock;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import com.seggellion.britannia_mod.farming.FlowerInteractionService;
import com.seggellion.britannia_mod.farming.FlowerMutationReason;
import com.seggellion.britannia_mod.farming.FlowerPersistentState;
import com.seggellion.britannia_mod.farming.FlowerProtectionService;
import com.seggellion.britannia_mod.farming.GrainHarvestTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Direct and indirect event paths routed through the central flower policy. */
public final class FlowerInteractionHandler {
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        var player = event.getEntity();
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FlowerBlock)
                || player.getAbilities().mayBuild || player.isSpectator()
                || !GrainHarvestTools.isGrainHarvestBlade(player.getMainHandItem())) {
            return;
        }
        event.setCanceled(true);
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            FlowerInteractionService.cutBack(
                    serverPlayer, serverLevel, event.getPos(), serverPlayer.getItemInHand(InteractionHand.MAIN_HAND)
            );
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof FlowerBlock)
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(level.getBlockEntity(event.getPos()) instanceof FlowerBlockEntity flower)) {
            return;
        }
        FlowerPersistentState state = flower.flowerState().orElse(null);
        event.setCanceled(true);
        if (state == null || !FlowerProtectionService.mayMutate(state, player, FlowerMutationReason.NORMAL_BREAK)) {
            level.sendBlockUpdated(event.getPos(), event.getState(), event.getState(), 3);
            return;
        }
        FlowerInteractionService.restoreAfterNormalBreak(player, level, event.getPos());
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos ->
                event.getLevel().getBlockEntity(pos) instanceof FlowerBlockEntity flower
                        && flower.flowerState().map(state ->
                        !FlowerProtectionService.mayMutate(
                                state.protectedFlower(), false, 0, FlowerMutationReason.EXPLOSION
                        )).orElse(false)
        );
    }

    @SubscribeEvent
    public void onFluidPlacement(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof FlowerBlockEntity flower
                && flower.flowerState().map(state ->
                !FlowerProtectionService.mayMutate(
                        state.protectedFlower(), false, 0, FlowerMutationReason.FLUID
                )).orElse(false)) {
            event.setCanceled(true);
        }
    }
}

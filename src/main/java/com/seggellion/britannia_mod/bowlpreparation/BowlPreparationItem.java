package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.util.WaterSourceInteraction;
import java.util.Optional;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Main-hand driver for the two Patch 18 dry preparation steps. */
public final class BowlPreparationItem extends Item {
    public BowlPreparationItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // A deliberately targeted source wins over the dry offhand recipe. The shared service
        // performs permission checks and the exact server-side bowl exchange.
        if (BowlWaterFillingService.supports(held)) {
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() == HitResult.Type.BLOCK) {
                var fluid = level.getFluidState(hit.getBlockPos());
                if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                    WaterSourceInteraction.fillFromSource(
                            level, hit.getBlockPos(), player, hand, held);
                    return InteractionResultHolder.sidedSuccess(
                            player.getItemInHand(hand), level.isClientSide());
                }
            }
        }

        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(held);
        }

        Optional<BowlPreparationPlan> candidate =
                BowlPreparationService.plan(held, player.getOffhandItem());
        if (candidate.isEmpty()) {
            return InteractionResultHolder.pass(held);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(held, false);
        }

        BowlPreparationService.ApplyResult result =
                BowlPreparationService.apply(serverPlayer, candidate.orElseThrow());
        if (result == BowlPreparationService.ApplyResult.APPLIED
                && level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.PLAYERS,
                    0.7F,
                    1.0F);
        }

        // A stale accepted plan still owns this packet, preventing a second-hand reinterpretation.
        return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), false);
    }
}

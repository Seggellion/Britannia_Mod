package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.util.WaterSourceInteraction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        //
        // M11 deferred defect F14: whether the player is aiming at flowing water is DECIDED here
        // and SAID below, if at all. It used to be said here, which was wrong twice. The message is
        // an action-bar line and the dry preparation runs on the same click, so a mix that then
        // SUCCEEDED left "the water here is flowing" standing over a bowl that had just been
        // filled -- a warning about something that did not happen, and one that stays up until the
        // action bar next changes. And because this block sits above the main-hand guard, one
        // right-click with a fillable bowl in each hand said it twice.
        boolean aimedAtFlowing = false;
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
            // M8 item 8: a bowl aimed at water that is not a source filled nothing and said
            // nothing, which reads as a broken bowl rather than as flowing water. The SOURCE_ONLY
            // clip above still decides what fills; nothing here changes that.
            aimedAtFlowing = !level.isClientSide()
                    && speaksForThisHand(player, hand)
                    && aimingAtFlowingWater(level, player);
        }

        if (hand != InteractionHand.MAIN_HAND) {
            if (aimedAtFlowing) sayFlowing(player);
            return InteractionResultHolder.pass(held);
        }

        Optional<BowlPreparationPlan> candidate =
                BowlPreparationService.plan(held, player.getOffhandItem());
        if (candidate.isEmpty()) {
            // M8 item 8: name the failure instead of passing in silence. The return value is
            // unchanged, so what does and does not count as a preparation is untouched.
            //
            // F14: one message per click. A fillable bowl aimed at a stream is the more specific
            // answer to "why did nothing happen" than the mixing diagnosis, which for that gesture
            // could only talk about the other hand.
            if (!level.isClientSide) {
                if (aimedAtFlowing) {
                    sayFlowing(player);
                } else {
                    BowlMixingMessages.send(player, held,
                            BowlPreparationService.diagnose(held, player.getOffhandItem()));
                }
            }
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
        // Nothing is said about flowing water on this path at all: the preparation happened, and a
        // warning about the thing that did not is exactly the F14 defect.
        return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), false);
    }

    /**
     * Whether the crosshair is on water that is not a source block.
     *
     * <p>A second clip, with {@code Fluid.ANY} rather than {@code SOURCE_ONLY}, because the first
     * deliberately cannot see flowing water -- which is what makes the gesture do nothing, and
     * therefore what has to be explained.
     */
    private static boolean aimingAtFlowingWater(Level level, Player player) {
        BlockHitResult anyWater = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (anyWater.getType() != HitResult.Type.BLOCK) return false;
        var fluid = level.getFluidState(anyWater.getBlockPos());
        return fluid.is(FluidTags.WATER) && !fluid.isSource();
    }

    /**
     * Which hand may speak, so one right-click produces at most one warning.
     *
     * <p>Both hands run {@code use} for a single click and both may hold a fillable bowl. The main
     * hand speaks whenever it holds one; the off hand speaks only when the main hand does not, so a
     * bowl carried in the off hand alone is still explained.
     */
    private static boolean speaksForThisHand(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                || !BowlWaterFillingService.supports(player.getMainHandItem());
    }

    private static void sayFlowing(Player player) {
        player.displayClientMessage(
                Component.translatable(QuestScreenText.WATER_FLOWING)
                        .withStyle(ChatFormatting.YELLOW), true);
    }
}

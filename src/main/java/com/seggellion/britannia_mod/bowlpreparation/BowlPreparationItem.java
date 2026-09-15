package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.util.WaterSourceInteraction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Item adapter for the two Patch 18 dry preparation steps. */
public final class BowlPreparationItem extends Item {
    public BowlPreparationItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var recipe = HandRecipeInteraction.use(level, player, hand);
        if (recipe.getResult().consumesAction()) return recipe;
        if (tryFillFromSource(level, player, hand)) return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        if (!level.isClientSide && speaksForThisHand(player, hand)) {
            ItemStack held = player.getItemInHand(hand);
            if (BowlWaterFillingService.supports(held) && aimingAtFlowingWater(level, player)) {
                sayFlowing(player);
            } else if (hand == InteractionHand.MAIN_HAND) {
                BowlMixingMessages.send(player, held,
                        BowlPreparationService.diagnose(held, player.getOffhandItem()));
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    public static boolean tryFillFromSource(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!BowlWaterFillingService.supports(held)) return false;
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        var fluid = level.getFluidState(hit.getBlockPos());
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
        WaterSourceInteraction.fillFromSource(level, hit.getBlockPos(), player, hand, held);
        return true;
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

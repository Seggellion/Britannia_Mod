package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.util.WaterSourceInteraction;
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
}

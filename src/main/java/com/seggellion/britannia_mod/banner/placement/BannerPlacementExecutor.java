package com.seggellion.britannia_mod.banner.placement;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** The held stack changes only after block and block-entity state are complete. */
public final class BannerPlacementExecutor {
    private BannerPlacementExecutor() {
    }

    public static BannerPlacementFailure execute(
            BannerPlacementPlan plan, BannerPlacementMutation mutation, ItemStack heldStack, boolean creative) {
        if (!mutation.placeBanner(plan)) {
            return BannerPlacementFailure.BLOCK_SET_FAILURE;
        }
        BannerPlacementMutation.StateTarget target = mutation.bannerBlockEntity(plan).orElse(null);
        if (target == null) {
            return rollback(mutation, plan, BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        }
        try {
            if (!target.assign(plan.bannerState())
                    || !target.currentState().equals(Optional.of(plan.bannerState()))) {
                return rollback(mutation, plan, BannerPlacementFailure.STATE_TRANSFER_FAILURE);
            }
        } catch (RuntimeException exception) {
            return rollback(mutation, plan, BannerPlacementFailure.STATE_TRANSFER_FAILURE);
        }
        if (!creative) {
            heldStack.shrink(1);
        }
        mutation.afterSuccess(plan);
        return BannerPlacementFailure.NONE;
    }

    private static BannerPlacementFailure rollback(
            BannerPlacementMutation mutation, BannerPlacementPlan plan, BannerPlacementFailure expectedFailure) {
        return mutation.rollback(plan) ? expectedFailure : BannerPlacementFailure.UNEXPECTED_ROLLBACK_FAILURE;
    }
}

package com.seggellion.britannia_mod.banner.placement;

import java.util.Optional;
import com.seggellion.britannia_mod.banner.structure.BannerStructureCell;
import net.minecraft.world.item.ItemStack;

/** The held stack changes only after every cell, anchor state, verification, and sync succeeds. */
public final class BannerPlacementExecutor {
    private BannerPlacementExecutor() {
    }

    public static BannerPlacementFailure execute(
            BannerPlacementPlan plan, BannerPlacementMutation mutation, ItemStack heldStack, boolean creative) {
        BannerPlacementMutation.StateTarget target = null;
        try {
            BannerStructureCell anchorCell = plan.cells().getFirst();
            if (!mutation.placeCell(plan, anchorCell)) {
                return rollback(mutation, plan, BannerPlacementFailure.BLOCK_SET_FAILURE);
            }
            target = mutation.bannerBlockEntity(plan).orElse(null);
            if (target == null) {
                return rollback(mutation, plan, BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
            }
            if (!target.assign(plan.bannerState(), plan.placedStructure())
                    || !target.currentState().equals(Optional.of(plan.bannerState()))
                    || !target.currentStructure().equals(Optional.of(plan.placedStructure()))) {
                return rollback(mutation, plan, BannerPlacementFailure.STATE_TRANSFER_FAILURE);
            }
            for (int index = 1; index < plan.cells().size(); index++) {
                if (!mutation.placeCell(plan, plan.cells().get(index))) {
                    return rollback(mutation, plan, BannerPlacementFailure.BLOCK_SET_FAILURE);
                }
            }
            for (BannerStructureCell cell : plan.cells()) {
                if (!mutation.verifyCell(plan, cell)) {
                    return rollback(mutation, plan, BannerPlacementFailure.FINAL_VERIFICATION_FAILURE);
                }
            }
            if (!target.synchronize()) {
                return rollback(mutation, plan, BannerPlacementFailure.SYNCHRONIZATION_FAILURE);
            }
            mutation.afterSuccess(plan);
        } catch (RuntimeException exception) {
            return rollback(mutation, plan, target == null
                    ? BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE
                    : BannerPlacementFailure.STATE_TRANSFER_FAILURE);
        }
        if (!creative) {
            heldStack.shrink(1);
        }
        return BannerPlacementFailure.NONE;
    }

    private static BannerPlacementFailure rollback(
            BannerPlacementMutation mutation, BannerPlacementPlan plan, BannerPlacementFailure expectedFailure) {
        try {
            return mutation.rollback(plan) ? expectedFailure : BannerPlacementFailure.UNEXPECTED_ROLLBACK_FAILURE;
        } catch (RuntimeException exception) {
            return BannerPlacementFailure.UNEXPECTED_ROLLBACK_FAILURE;
        }
    }
}

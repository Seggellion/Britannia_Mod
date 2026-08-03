package com.seggellion.britannia_mod.structure.placement;

import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Places, initializes, verifies, and synchronizes before consuming the shrine item. */
public final class ShrinePlacementExecutor {
    private ShrinePlacementExecutor() {
    }

    public static ShrinePlacementFailure execute(
            ShrinePlacementPlan plan,
            ShrinePlacementMutation mutation,
            ItemStack heldStack,
            boolean creative) {
        ShrinePlacementMutation.StateTarget target = null;
        try {
            StructureCell anchor = plan.cells().getFirst();
            if (!mutation.placeCell(plan, anchor)) {
                return rollback(mutation, plan, ShrinePlacementFailure.BLOCK_SET_FAILURE);
            }
            target = mutation.anchorBlockEntity(plan).orElse(null);
            if (target == null) {
                return rollback(mutation, plan, ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
            }
            if (!target.assign(plan.placedStructure())
                    || !target.currentState().equals(Optional.of(plan.placedStructure()))) {
                return rollback(mutation, plan, ShrinePlacementFailure.STATE_TRANSFER_FAILURE);
            }
            for (int index = 1; index < plan.cells().size(); index++) {
                if (!mutation.placeCell(plan, plan.cells().get(index))) {
                    return rollback(mutation, plan, ShrinePlacementFailure.BLOCK_SET_FAILURE);
                }
            }
            for (StructureCell cell : plan.cells()) {
                if (!mutation.verifyCell(plan, cell)) {
                    return rollback(mutation, plan, ShrinePlacementFailure.FINAL_VERIFICATION_FAILURE);
                }
            }
            if (!target.synchronize()) {
                return rollback(mutation, plan, ShrinePlacementFailure.SYNCHRONIZATION_FAILURE);
            }
            mutation.afterSuccess(plan);
        } catch (RuntimeException exception) {
            return rollback(mutation, plan, target == null
                    ? ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE
                    : ShrinePlacementFailure.STATE_TRANSFER_FAILURE);
        }
        if (!creative) {
            heldStack.shrink(1);
        }
        return ShrinePlacementFailure.NONE;
    }

    private static ShrinePlacementFailure rollback(
            ShrinePlacementMutation mutation,
            ShrinePlacementPlan plan,
            ShrinePlacementFailure expectedFailure) {
        try {
            return mutation.rollback(plan)
                    ? expectedFailure
                    : ShrinePlacementFailure.UNEXPECTED_ROLLBACK_FAILURE;
        } catch (RuntimeException exception) {
            return ShrinePlacementFailure.UNEXPECTED_ROLLBACK_FAILURE;
        }
    }
}

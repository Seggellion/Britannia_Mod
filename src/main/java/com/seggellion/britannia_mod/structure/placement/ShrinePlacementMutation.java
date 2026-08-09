package com.seggellion.britannia_mod.structure.placement;

import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import java.util.Optional;

/** Narrow mutation boundary shared by the live server adapter and rollback tests. */
public interface ShrinePlacementMutation {
    interface StateTarget {
        boolean assign(PlacedStructureState state);

        Optional<PlacedStructureState> currentState();

        boolean synchronize();
    }

    boolean placeCell(ShrinePlacementPlan plan, StructureCell cell);

    Optional<StateTarget> anchorBlockEntity(ShrinePlacementPlan plan);

    boolean verifyCell(ShrinePlacementPlan plan, StructureCell cell);

    boolean rollback(ShrinePlacementPlan plan);

    void afterSuccess(ShrinePlacementPlan plan);
}

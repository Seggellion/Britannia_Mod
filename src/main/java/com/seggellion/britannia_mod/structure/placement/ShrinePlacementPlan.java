package com.seggellion.britannia_mod.structure.placement;

import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable four-cell transaction for the diagnostic shrine. */
public record ShrinePlacementPlan(
        BlockPos anchorPosition,
        BlockState anchorBlockState,
        PlacedStructureState placedStructure,
        List<StructureCell> cells) {
    public ShrinePlacementPlan {
        anchorPosition = Objects.requireNonNull(anchorPosition, "anchorPosition").immutable();
        Objects.requireNonNull(anchorBlockState, "anchorBlockState");
        Objects.requireNonNull(placedStructure, "placedStructure");
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        if (cells.size() != 4
                || cells.getFirst().role() != StructureCellRole.ANCHOR
                || !cells.getFirst().worldPosition().equals(anchorPosition)
                || !cells.getFirst().placedState().equals(anchorBlockState)) {
            throw new IllegalArgumentException("A Milestone 2 shrine plan requires one anchor and three parts");
        }
        if (cells.stream().filter(cell -> cell.role() == StructureCellRole.ANCHOR).count() != 1) {
            throw new IllegalArgumentException("A shrine plan must contain exactly one anchor");
        }
    }
}

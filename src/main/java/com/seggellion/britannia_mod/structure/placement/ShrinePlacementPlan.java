package com.seggellion.britannia_mod.structure.placement;

import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;

/** Immutable four-cell transaction for the diagnostic shrine. */
public record ShrinePlacementPlan(
        BlockPos anchorPosition,
        BlockState anchorBlockState,
        PlacedStructureState placedStructure,
        List<StructureCell> cells,
        List<ChunkPos> requiredChunks,
        List<BlockPos> authorizedPositions,
        boolean anchorInitializationValidated) {
    public ShrinePlacementPlan {
        anchorPosition = Objects.requireNonNull(anchorPosition, "anchorPosition").immutable();
        Objects.requireNonNull(anchorBlockState, "anchorBlockState");
        Objects.requireNonNull(placedStructure, "placedStructure");
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        requiredChunks = List.copyOf(Objects.requireNonNull(requiredChunks, "requiredChunks"));
        authorizedPositions = Objects.requireNonNull(authorizedPositions, "authorizedPositions").stream()
                .map(BlockPos::immutable)
                .toList();
        if (cells.size() != 4
                || cells.getFirst().role() != StructureCellRole.ANCHOR
                || !cells.getFirst().worldPosition().equals(anchorPosition)
                || !cells.getFirst().placedState().equals(anchorBlockState)) {
            throw new IllegalArgumentException("A Milestone 2 shrine plan requires one anchor and three parts");
        }
        if (cells.stream().filter(cell -> cell.role() == StructureCellRole.ANCHOR).count() != 1) {
            throw new IllegalArgumentException("A shrine plan must contain exactly one anchor");
        }
        if (!cells.stream().map(StructureCell::offset).toList().equals(placedStructure.footprint())
                || new HashSet<>(cells.stream().map(StructureCell::offset).toList()).size() != 4
                || new HashSet<>(cells.stream().map(StructureCell::worldPosition).toList()).size() != 4) {
            throw new IllegalArgumentException("Shrine offsets and world targets must be ordered and unique");
        }
        List<BlockPos> plannedPositions = cells.stream().map(StructureCell::worldPosition).toList();
        if (!authorizedPositions.equals(plannedPositions)) {
            throw new IllegalArgumentException("Every planned target must have placement authorization");
        }
        List<ChunkPos> derivedChunks = cells.stream()
                .map(cell -> new ChunkPos(cell.worldPosition()))
                .distinct()
                .toList();
        if (!requiredChunks.equals(derivedChunks)) {
            throw new IllegalArgumentException("Required chunks must exactly match the ordered target chunks");
        }
        if (!anchorInitializationValidated) {
            throw new IllegalArgumentException("Anchor initialization must be validated before planning succeeds");
        }
    }
}

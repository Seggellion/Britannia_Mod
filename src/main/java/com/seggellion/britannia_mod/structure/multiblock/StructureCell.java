package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One deterministic cell in an immutable placement transaction. */
public record StructureCell(
        LocalOffset offset,
        BlockPos worldPosition,
        StructureCellRole role,
        BlockState originalState,
        BlockState placedState) {
    public StructureCell {
        Objects.requireNonNull(offset, "offset");
        worldPosition = Objects.requireNonNull(worldPosition, "worldPosition").immutable();
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(originalState, "originalState");
        Objects.requireNonNull(placedState, "placedState");
    }
}

package com.seggellion.britannia_mod.banner.structure;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record BannerStructureCell(
        BannerLocalOffset offset,
        BlockPos worldPosition,
        BannerCellRole role,
        BlockState originalState,
        BlockState placedState) {
    public BannerStructureCell {
        Objects.requireNonNull(offset, "offset");
        worldPosition = Objects.requireNonNull(worldPosition, "worldPosition").immutable();
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(originalState, "originalState");
        Objects.requireNonNull(placedState, "placedState");
        if (offset.isAnchor() != (role == BannerCellRole.ANCHOR)) {
            throw new IllegalArgumentException("Cell role must agree with its local offset");
        }
    }
}

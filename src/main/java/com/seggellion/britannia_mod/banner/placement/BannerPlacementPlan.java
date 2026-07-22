package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureCell;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public record BannerPlacementPlan(
        BlockPos anchorPos,
        Direction facing,
        BannerOrientation orientation,
        BlockState anchorBlockState,
        BannerInstanceState bannerState,
        BannerPlacedStructure placedStructure,
        List<BannerStructureCell> cells,
        List<BlockPos> requiredSupportPositions) {
    public BannerPlacementPlan {
        anchorPos = anchorPos.immutable();
        cells = List.copyOf(cells);
        requiredSupportPositions = requiredSupportPositions.stream().map(BlockPos::immutable).toList();
        if (cells.isEmpty() || !cells.getFirst().worldPosition().equals(anchorPos)) {
            throw new IllegalArgumentException("The first deterministic placement cell must be the anchor");
        }
    }
}

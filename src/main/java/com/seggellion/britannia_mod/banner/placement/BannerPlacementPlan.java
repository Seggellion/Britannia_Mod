package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public record BannerPlacementPlan(
        BlockPos supportPos,
        BlockPos targetPos,
        Direction facing,
        BlockState originalTargetState,
        BlockState bannerBlockState,
        BannerInstanceState bannerState) {
}

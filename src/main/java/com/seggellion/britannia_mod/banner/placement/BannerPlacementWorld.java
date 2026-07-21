package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only checks, separated so expected failures are testable without a live level. */
public interface BannerPlacementWorld {
    BlockState blockState(BlockPos pos);
    boolean targetReplaceable(BlockPos pos);
    boolean inWorldBounds(BlockPos pos);
    boolean validWallSupport(BlockPos supportPos, Direction outwardFacing);
    boolean placementAllowed(BlockPos targetPos, Direction outwardFacing, ItemStack stack);
    boolean canCreateBannerBlockEntity(BlockState bannerState);
    boolean canAcceptState(BannerInstanceState state);
}

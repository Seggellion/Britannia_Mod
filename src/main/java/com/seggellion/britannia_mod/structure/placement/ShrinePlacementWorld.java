package com.seggellion.britannia_mod.structure.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only world boundary used to prove placement planning has no mutation side effects. */
public interface ShrinePlacementWorld {
    BlockState blockState(BlockPos pos);

    boolean targetReplaceable(BlockPos pos);

    boolean inWorldBounds(BlockPos pos);

    boolean chunkLoaded(BlockPos pos);

    boolean unrelatedLargeStructureCell(BlockPos pos);

    boolean placementAllowed(BlockPos pos, Direction facing, ItemStack stack);

    boolean canCreateAnchorBlockEntity(BlockState anchorState);

    boolean canEncodePart(BlockState partState);
}

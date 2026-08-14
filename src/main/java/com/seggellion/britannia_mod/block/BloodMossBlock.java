package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Independent managed-vegetation host; it has no farming soil or crop ownership behavior. */
public final class BloodMossBlock extends Block {
    private static final VoxelShape SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 5.0D, 15.0D);

    public BloodMossBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos position, CollisionContext context
    ) {
        return SHAPE;
    }
}

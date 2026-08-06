package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A {@link FloorBlock} that only occupies the top 3 voxels of the block space.
 * Not a vanilla {@code SlabBlock}: it is always a top slab, with no type property and no
 * double-slab form, so the state count stays at {@code facing * variation} and the texture
 * cycling works exactly as it does on the full-height floor.
 */
public class FloorSlabBlock extends FloorBlock {
    public static final int HEIGHT = 3;
    private static final VoxelShape SHAPE = box(0.0D, 16.0D - HEIGHT, 0.0D, 16.0D, 16.0D, 16.0D);

    public FloorSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}

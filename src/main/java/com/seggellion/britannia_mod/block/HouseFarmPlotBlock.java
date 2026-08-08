package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A raised planting bed: a brick kerb around the outside with soil recessed inside it.
 *
 * <p>The block occupies its full cube for collision so a player can walk across the kerb, but the
 * soil surface sits 2 voxels lower than the brick, which is what gives it the sunken look.
 */
public class HouseFarmPlotBlock extends Block {

    /** Matches the model: the brick kerb reaches the top of the block, the soil stops short. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public HouseFarmPlotBlock(BlockBehaviour.Properties properties) {
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

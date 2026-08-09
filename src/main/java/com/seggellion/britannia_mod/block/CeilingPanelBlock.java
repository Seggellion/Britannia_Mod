package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plain ceiling panel occupying the bottom 3 voxels of its block.
 *
 * <p>Deliberately not {@link com.seggellion.britannia_mod.block.structure.TopOnlySlabBlock}, which
 * sits in the TOP 3 voxels: a ceiling has to hang at the bottom of the block so it reads as the
 * underside of the storey above.
 */
public class CeilingPanelBlock extends Block {

    public static final int THICKNESS = 3;
    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, THICKNESS, 16.0D);

    public CeilingPanelBlock(BlockBehaviour.Properties properties) {
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

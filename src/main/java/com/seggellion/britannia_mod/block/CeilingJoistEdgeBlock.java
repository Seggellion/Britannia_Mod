package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ceiling joist edge: a 3-voxel plank panel across the block with an exposed beam hanging along one
 * edge. The underside counterpart of {@link WoodSupportFloorBlock} - a floor deck sits in the top
 * 3 voxels of its block, a ceiling panel in the bottom 3, so it reads correctly from the room below.
 */
public class CeilingJoistEdgeBlock extends HorizontalFacingBlock {

    private static final double PANEL = 3.0D;
    private static final double BEAM_DEPTH = 3.0D;
    private static final double BEAM_DROP = 7.0D;

    private static final VoxelShape PANEL_SHAPE = Block.box(0, 0, 0, 16, PANEL, 16);
    private static final VoxelShape BEAM_NORTH = Block.box(0, 0, 0, 16, BEAM_DROP, BEAM_DEPTH);
    private static final VoxelShape BEAM_SOUTH = Block.box(0, 0, 16 - BEAM_DEPTH, 16, BEAM_DROP, 16);
    private static final VoxelShape BEAM_WEST  = Block.box(0, 0, 0, BEAM_DEPTH, BEAM_DROP, 16);
    private static final VoxelShape BEAM_EAST  = Block.box(16 - BEAM_DEPTH, 0, 0, 16, BEAM_DROP, 16);

    public CeilingJoistEdgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape beam = switch (facing) {
            case SOUTH -> BEAM_SOUTH;
            case EAST  -> BEAM_EAST;
            case WEST  -> BEAM_WEST;
            default    -> BEAM_NORTH;
        };
        return Shapes.or(PANEL_SHAPE, beam);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}

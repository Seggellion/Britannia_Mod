package com.seggellion.britannia_mod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PaintingBlock extends HorizontalFacingBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // A super thin slab on the side facing the player. Units are in voxels (0–16).
    // We'll orient this depending on FACING.
    private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 15.5, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 0.5);
    private static final VoxelShape WEST_SHAPE  = Block.box(15.5, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape EAST_SHAPE  = Block.box(0.0, 0.0, 0.0, 0.5, 16.0, 16.0);

    public PaintingBlock(BlockBehaviour.Properties props) {
        super(props.noOcclusion()); // flat art shouldn't occlude
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext ctx) {
        Direction dir = state.getValue(FACING);
        return switch (dir) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST  -> WEST_SHAPE;
            case EAST  -> EAST_SHAPE;
            default    -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext ctx) {
        // same as collision so selection feels natural
        return getShape(state, level, pos, ctx);
    }
}

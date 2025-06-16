package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;


public class Window2x3Block extends Block {

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    public Window2x3Block(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            placeCollisionBlocks(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            removeCollisionBlocks(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

private void placeCollisionBlocks(Level level, BlockPos origin, Direction facing) {
    Direction right = facing.getClockWise(); // Horizontal span
    Direction forward = facing;              // Depth direction

    for (int x = -1; x <= 1; x++) { // -1, 0, 1 => 3 blocks wide
        for (int y = 0; y < 2; y++) {
            BlockPos offset = origin
                .relative(right, x)  // Left to right from center
                .above(y);

            if (offset.equals(origin)) continue; // Skip main block if inside

                BlockState collisionState = BlockRegistry.WINDOW_COLLISION.get()
                    .defaultBlockState()
                    .setValue(WindowCollisionBlock.FACING, facing);
                level.setBlock(offset, collisionState, Block.UPDATE_CLIENTS);
        }
    }
}

private void removeCollisionBlocks(Level level, BlockPos origin, Direction facing) {
    Direction right = facing.getClockWise();
    Direction forward = facing;

    for (int x = -1; x <= 1; x++) {
        for (int y = 0; y < 2; y++) {
            BlockPos offset = origin
                .relative(right, x)
                .above(y);

            if (offset.equals(origin)) continue;
            BlockState state = level.getBlockState(offset);
            if (state.is(BlockRegistry.WINDOW_COLLISION.get())) {
                level.destroyBlock(offset, false);
            }
        }
    }
}


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}

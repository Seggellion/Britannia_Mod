package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A {@link DoubleWallBlock} with an asymmetric feature that can sit on either side of the wall -
 * a window's frame and mullion, or the timber support on {@code plaster_wall_and_support_blank}.
 *
 * <p>Flipped with the interior decorator tool held in the OFF hand (see
 * {@code InteriorDecoratorToolItem}). The main hand keeps rotating the block, so one tool does both
 * jobs without the two getting in each other's way.
 *
 * <p>Only {@link #MIRRORED} is added on top of the wall state. Neighbor derivation may choose the
 * equivalent perpendicular main edge after a flip to keep a corner joined while preserving its
 * authored branch variant. {@code mirrored=false} is the default, which is
 * what every block placed before this property existed resolves to.
 */
public class MirrorableWallBlock extends DoubleWallBlock {

    /** {@code false} = feature on its authored side, {@code true} = on the opposite side. */
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    public MirrorableWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(SHAPE, WallShape.STRAIGHT)
            .setValue(BRANCH_RIGHT, false)
            .setValue(MIRRORED, false)
            .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MIRRORED);
    }

    /**
     * Corners and junctions swap which edge their perpendicular run hugs when they are mirrored.
     *
     * <p>Reflecting an L across the wall's own axis necessarily turns it the other way, so every
     * {@code _corner} and {@code _t_junction} model in this family draws its branch on the opposite
     * edge from the unmirrored one - {@code ornate_wall_large_window_corner} runs north and west,
     * {@code _corner_window_right} runs north and east. The shape used to be built from
     * {@link #BRANCH_RIGHT} alone, so a flipped corner presented a solid arm on one side while
     * colliding on the other: a player walked through the arm they could see and hit an invisible
     * one across the block. Straight runs are unaffected - mirroring one only moves its pilaster,
     * which sits inside the run's own slab either way.
     */
    @Override
    protected boolean physicalBranchRight(BlockState state) {
        return state.getValue(BRANCH_RIGHT)
            ^ (state.getValue(MIRRORED) && state.getValue(SHAPE) != WallShape.STRAIGHT);
    }

    @Override
    BlockState deriveConnections(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos base = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        WallConnection connection = WallConnection.derive(level, base, state.getValue(FACING),
            physicalBranchRight(state), DoubleWallBlock::runOf);
        boolean branch = connection.branchRight() ^ (state.getValue(MIRRORED) && connection.shape() != WallShape.STRAIGHT);
        Direction facing = connection.facing();
        if (state.getValue(SHAPE) != WallShape.STRAIGHT && connection.shape() != WallShape.STRAIGHT) {
            // Either arm may be primary. Swapping them describes identical physical edges with
            // the opposite handedness, avoiding a one-post/two-post asset change after transforms.
            if (branch != state.getValue(BRANCH_RIGHT)) facing = connection.secondary();
            branch = state.getValue(BRANCH_RIGHT);
        }
        return state.setValue(SHAPE, connection.shape()).setValue(FACING, facing).setValue(BRANCH_RIGHT, branch);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        if (mirror == Mirror.NONE) {
            return mirrored;
        }
        // MIRRORED already reverses the physical branch. Flipping BRANCH_RIGHT too would undo it.
        return mirrored.setValue(MIRRORED, !state.getValue(MIRRORED));
    }
}

package com.seggellion.britannia_mod.block;

import net.minecraft.core.Direction;
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
 * <p>Only {@link #MIRRORED} is added on top of the wall state, so facing, shape, branch side and
 * the plaster style all survive a flip untouched. {@code mirrored=false} is the default, which is
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

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        if (mirror == Mirror.NONE) {
            return mirrored;
        }
        // Reflecting the world reflects the feature with it.
        return mirrored.setValue(MIRRORED, !state.getValue(MIRRORED));
    }
}

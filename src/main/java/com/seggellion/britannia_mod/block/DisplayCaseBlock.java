package com.seggellion.britannia_mod.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Two-block-tall decorative case whose shared horizontal faces disappear beside peer roots.
 *
 * <p>The four connection flags are stored only on the rendering root. This makes the requested
 * independent, end, straight-middle, and corner forms compositional while also giving T and cross
 * layouts a deterministic appearance without additional public block IDs.
 */
public final class DisplayCaseBlock extends DecorativeMultiblockBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Map<Direction, BooleanProperty> SIDES = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST);

    public DisplayCaseBlock(BlockBehaviour.Properties properties) {
        super(properties, 0, 0, 0, 1, 0, 0,
                (x, y, z) -> Block.box(1, 0, 1, 15, 16, 15));
        registerDefaultState(defaultBlockState()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    /** Re-evaluates every root connection from world state; useful to placement code and tests. */
    public BlockState deriveConnections(BlockState root, LevelAccessor level, BlockPos rootPos) {
        if (!root.is(this) || !isRoot(root)) {
            throw new IllegalArgumentException("Display-case connections require a root state");
        }
        BlockState connected = root;
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            connected = connected.setValue(
                    side.getValue(), isPeerRoot(level.getBlockState(rootPos.relative(side.getKey()))));
        }
        return connected;
    }

    public ConnectionForm connectionForm(BlockState state) {
        int count = 0;
        for (BooleanProperty property : SIDES.values()) {
            if (state.getValue(property)) count++;
        }
        if (count == 0) return ConnectionForm.INDEPENDENT;
        if (count == 1) return ConnectionForm.END;
        if (count >= 3) return ConnectionForm.JUNCTION;
        boolean opposite = state.getValue(NORTH) && state.getValue(SOUTH)
                || state.getValue(EAST) && state.getValue(WEST);
        return opposite ? ConnectionForm.MIDDLE : ConnectionForm.CORNER;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level.isClientSide) return;

        BlockState current = level.getBlockState(pos);
        if (!current.is(this) || !hasValidPart(current)) return;
        BlockPos rootPos = anchorPosition(pos, current);
        BlockState root = level.getBlockState(rootPos);
        if (!root.is(this) || !isRoot(root)) return;

        BlockState connected = deriveConnections(root, level, rootPos);
        if (!connected.equals(root)) {
            level.setBlock(rootPos, connected, Block.UPDATE_ALL);
        }
    }

    private boolean isPeerRoot(BlockState state) {
        return state.is(this) && hasValidPart(state) && isRoot(state);
    }

    public enum ConnectionForm {
        INDEPENDENT,
        END,
        MIDDLE,
        CORNER,
        JUNCTION
    }
}

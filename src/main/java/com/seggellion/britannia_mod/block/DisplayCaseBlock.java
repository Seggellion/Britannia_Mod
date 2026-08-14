package com.seggellion.britannia_mod.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block-tall decorative case whose root selects owner-authored neighbor geometry.
 *
 * <p>The root owns the four connection flags and mirrors them to the upper rendering cell.
 * Independent, straight/end, genuine corner, open tee, and open four-way interior models are
 * selected from those flags without additional public block IDs.
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

    private static final VoxelShape LOWER_INDEPENDENT = Block.box(1, 0, 1, 15, 16, 15);
    private static final VoxelShape LOWER_CONNECTED = Shapes.block();
    private static final VoxelShape UPPER_INDEPENDENT = Shapes.or(
            Block.box(1, 0, 2, 2, 1.5, 14),
            Block.box(14, 0, 2, 15, 1.5, 14),
            Block.box(1, 0, 1, 15, 1.5, 2),
            Block.box(1, 0, 14, 15, 1.5, 15),
            Block.box(1, 1.5, 1, 2, 5.5, 2),
            Block.box(1, 1.5, 14, 2, 5.5, 15),
            Block.box(14, 1.5, 1, 15, 5.5, 2),
            Block.box(14, 1.5, 14, 15, 5.5, 15),
            Block.box(2, 5.5, 1, 14, 6, 2),
            Block.box(2, 5.5, 14, 14, 6, 15),
            Block.box(1, 5.5, 1, 2, 6, 15),
            Block.box(14, 5.5, 1, 15, 6, 15));
    private static final VoxelShape UPPER_SIDE_NORTH = Shapes.or(
            Block.box(1, 0, 0, 15, 1.5, 1),
            Block.box(0, 5.5, 0, 16, 6, 1));
    private static final VoxelShape UPPER_POST_NORTH_WEST = Block.box(0, 1.5, 0, 1, 5.5, 1);
    /** Canonical owner corner: connected/open west and south. */
    private static final VoxelShape UPPER_CORNER_SOUTH_WEST = Shapes.or(
            Block.box(15, 0, 0, 16, 1.5, 16),
            Block.box(0, 0, 0, 15, 1.5, 1),
            Block.box(0, 1.5, 0, 1, 5.5, 1),
            Block.box(15, 1.5, 0, 16, 5.5, 1),
            Block.box(15, 1.5, 15, 16, 5.5, 16),
            Block.box(0, 5.5, 0, 16, 6, 1),
            Block.box(15, 5.5, 1, 16, 6, 16));

    public DisplayCaseBlock(BlockBehaviour.Properties properties) {
        super(properties, 0, 0, 0, 1, 0, 0,
                (x, y, z) -> Shapes.empty());
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
    public RenderShape getRenderShape(BlockState state) {
        return hasValidPart(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.is(this) || !hasValidPart(state)) return Shapes.empty();

        BlockState root = rootState(level, pos, state);
        ConnectionForm form = connectionForm(root);
        if (cell(state).y() == 0) {
            return form == ConnectionForm.INDEPENDENT ? LOWER_INDEPENDENT : LOWER_CONNECTED;
        }
        if (form == ConnectionForm.INDEPENDENT) return UPPER_INDEPENDENT;
        if (form == ConnectionForm.CORNER) {
            VoxelShape shape = UPPER_CORNER_SOUTH_WEST;
            if (root.getValue(WEST) && root.getValue(NORTH)) return rotateY(shape, 1);
            if (root.getValue(NORTH) && root.getValue(EAST)) return rotateY(shape, 2);
            if (root.getValue(EAST) && root.getValue(SOUTH)) return rotateY(shape, 3);
            return shape;
        }
        return connectedUpperShape(root);
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
        mirrorConnectionsToUpper(level, rootPos, connected);
    }

    private boolean isPeerRoot(BlockState state) {
        return state.is(this) && hasValidPart(state) && isRoot(state);
    }

    private BlockState rootState(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState root = level.getBlockState(anchorPosition(pos, state));
        return root.is(this) && isRoot(root) ? root : defaultBlockState();
    }

    private void mirrorConnectionsToUpper(Level level, BlockPos rootPos, BlockState root) {
        BlockPos upperPos = rootPos.above();
        BlockState upper = level.getBlockState(upperPos);
        if (!upper.is(this) || !hasValidPart(upper) || cell(upper).y() != 1) return;

        BlockState mirrored = upper;
        for (BooleanProperty property : SIDES.values()) {
            mirrored = mirrored.setValue(property, root.getValue(property));
        }
        if (!mirrored.equals(upper)) {
            // The upper cell owns the cage model so its light is sampled in the cell it occupies.
            level.setBlock(upperPos, mirrored, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static VoxelShape connectedUpperShape(BlockState root) {
        VoxelShape shape = Shapes.empty();
        boolean north = root.getValue(NORTH);
        boolean east = root.getValue(EAST);
        boolean south = root.getValue(SOUTH);
        boolean west = root.getValue(WEST);

        if (!north) shape = Shapes.or(shape, UPPER_SIDE_NORTH);
        if (!east) shape = Shapes.or(shape, rotateY(UPPER_SIDE_NORTH, 1));
        if (!south) shape = Shapes.or(shape, rotateY(UPPER_SIDE_NORTH, 2));
        if (!west) shape = Shapes.or(shape, rotateY(UPPER_SIDE_NORTH, 3));

        if (!north || !west) shape = Shapes.or(shape, UPPER_POST_NORTH_WEST);
        if (!north || !east) shape = Shapes.or(shape, rotateY(UPPER_POST_NORTH_WEST, 1));
        if (!south || !east) shape = Shapes.or(shape, rotateY(UPPER_POST_NORTH_WEST, 2));
        if (!south || !west) shape = Shapes.or(shape, rotateY(UPPER_POST_NORTH_WEST, 3));
        return shape;
    }

    private static VoxelShape rotateY(VoxelShape shape, int quarterTurnsClockwise) {
        VoxelShape rotated = shape;
        for (int turn = 0; turn < quarterTurnsClockwise; turn++) {
            VoxelShape next = Shapes.empty();
            for (var box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(
                        1.0D - box.maxZ, box.minY, box.minX,
                        1.0D - box.minZ, box.maxY, box.maxX));
            }
            rotated = next;
        }
        return rotated;
    }

    public enum ConnectionForm {
        INDEPENDENT,
        END,
        MIDDLE,
        CORNER,
        JUNCTION
    }
}

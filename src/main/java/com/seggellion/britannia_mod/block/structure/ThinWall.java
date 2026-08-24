package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;

/**
 * An edge-hugging wall course - the top, bottom or foundation trim of a villa wall, and a handful
 * of other thin architectural pieces (windows, curtains, arches) that share its state space.
 *
 * <h2>How a junction is encoded</h2>
 * The state carries no shape enum and no branch side; it makes do with {@link #FACING} and one
 * {@link #CORNER} flag, because that is all the art needs. Every family ships exactly four corner
 * models, and each one is the L of two full-length edge panels: the panel on the {@code facing}
 * edge plus the panel on the edge counter-clockwise from it. {@code north_corner} is {north+west},
 * {@code east_corner} is {east+north}, {@code south_corner} is {south+east}, {@code west_corner}
 * is {west+south}. Between them those four models cover every adjacent pair of edges, and because
 * both panels run the full sixteen pixels, the same L also draws a T: one panel continues the
 * through run, the other reaches the branch. So a corner, a T, and (when both far arms hug the
 * same edge) even a cross are all representable - provided the block picks the {@code facing}
 * whose pair matches the edges its neighbours actually hug.
 *
 * <h2>The defect this derivation fixes</h2>
 * This block used to leave {@code FACING} at whatever the player was looking at and only ever
 * toggled {@code CORNER}, so a junction drew the {facing, counter-clockwise} L of its
 * <em>placement</em> facing, not of its neighbourhood. Of the four rotations of the same T, only
 * the one whose run and branch happened to match that baked-in pair connected; the other three
 * stepped sideways by half a block or sprouted their branch on the empty side. That is the same
 * disease {@link DoubleWallBlock} was cured of, so the cure is shared too: connections are derived
 * by {@link WallConnection}, junctions adopt their edges from the straight runs beside them, and
 * straight runs never move because something was built against them.
 *
 * <h2>Pieces that must not adapt</h2>
 * Windows, curtains, arches and the corral pieces are placed <em>into</em> runs but have no
 * junction art, and {@link MultiCellWindowBlock} anchors a whole footprint of collision helpers to
 * its facing - rotating it underneath the art would tear the window apart. Those register with
 * {@link ConnectionRule#FIXED_FACING} and keep the old behaviour exactly: facing stays where the
 * player put it, {@code CORNER} stays the raw both-axes pivot flag it always was.
 */
public class ThinWall extends Block {

    /* ─── block‑state properties ─────────────────────────────── */

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty  CORNER  = BooleanProperty.create("corner");
    public static final BooleanProperty  FILLED  = BooleanProperty.create("filled");

    /** 0 = standard, 1 = alt skin, 2 = alt skin flipped horizontally */
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 2);

    /** How a piece reacts to the wall blocks around it. */
    public enum ConnectionRule {
        /**
         * Wall-run trim: corners and junctions re-derive {@link #FACING} and {@link #CORNER} from
         * the runs beside them, so all four rotations of the same join line up the same way.
         */
        ADAPTIVE_JUNCTIONS,
        /**
         * Straight-only pieces - windows, curtains, arches: no junction art exists, the facing is
         * load-bearing (a window's art and collision footprint hang off it), so it never moves.
         */
        FIXED_FACING
    }

    /* ─── voxel shapes ( 16 px ⇒ 1 block ) ───────────────────── */

    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0,     0,   16, 16, 5.33);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 10.66,   16, 16, 16);
    private static final VoxelShape WEST_SHAPE  = Block.box(0, 0,      0,   5.33,16, 16);
    private static final VoxelShape EAST_SHAPE  = Block.box(10.66,0,   0,   16,  16, 16);
    private static final VoxelShape FULL_SHAPE  = Shapes.block();

private static final VoxelShape FILL_E = Block.box( 0, 0, 0, 16, 8, 16);
private static final VoxelShape FILL_W = Block.box( 0, 0, 0, 16, 8, 16); // same, will be rotated
private static final VoxelShape FILL_N = Block.box( 0, 0, 0, 16, 8, 16);
private static final VoxelShape FILL_S = Block.box( 0, 0, 0, 16, 8, 16);

    private final ConnectionRule connectionRule;

    /* ─── constructor & defaults ─────────────────────────────── */

    public ThinWall(BlockBehaviour.Properties props) {
        this(props, ConnectionRule.ADAPTIVE_JUNCTIONS);
    }

    public ThinWall(BlockBehaviour.Properties props, ConnectionRule connectionRule) {
        super(props);
        this.connectionRule = connectionRule;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CORNER, false)
                .setValue(FILLED, false)
                .setValue(STYLE, 0));
    }

    /* ─── placement ──────────────────────────────────────────── */

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face    = ctx.getHorizontalDirection().getOpposite();
        BlockState placed = defaultBlockState()
                .setValue(FACING, face)
                .setValue(CORNER, false)
                .setValue(STYLE, 0);          // always start in standard style

        if (this.connectionRule == ConnectionRule.ADAPTIVE_JUNCTIONS) {
            // A block that completes a junction has to know it from the moment it lands; only its
            // neighbours get updateShape when it is placed, it does not.
            return deriveConnections(placed, ctx.getLevel(), ctx.getClickedPos());
        }

        BlockState rear = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(face.getOpposite()));
        return placed.setValue(FILLED, fillsGap(rear));
    }

    /* ─── neighbour updates ─────────────────────────────────── */

    @Override
    public BlockState updateShape(BlockState state, Direction fromDir,
                                  BlockState neighbour, LevelAccessor level,
                                  BlockPos pos,   BlockPos neighbourPos) {

        state = connectionUpdate(state, level, pos);

        // Fixed-facing pieces keep the historical filled tracking untouched: the flag only changes
        // when the gap-side neighbour does. Adaptive pieces recompute it inside the derivation,
        // because their gap side can move with the facing.
        if (this.connectionRule == ConnectionRule.FIXED_FACING
                && fromDir == state.getValue(FACING).getOpposite()) {
            state = state.setValue(FILLED, fillsGap(neighbour));
        }

        return state;
    }

    /* ─── connection derivation ─────────────────────────────── */

    /**
     * How this block answers its neighbourhood, under whichever {@link ConnectionRule} it was
     * registered with. This is the single entry point the alignment suite drives, and the same
     * call {@link #updateShape} makes on every neighbour change.
     */
    BlockState connectionUpdate(BlockState state, BlockGetter level, BlockPos pos) {
        if (this.connectionRule == ConnectionRule.ADAPTIVE_JUNCTIONS) {
            return deriveConnections(state, level, pos);
        }
        return state.setValue(CORNER, isPivot(level, pos, state.getValue(FACING)));
    }

    /**
     * Recomputes {@link #FACING}, {@link #CORNER} and {@link #FILLED} from the neighbourhood.
     *
     * <p>The shape and edges come from {@link WallConnection#derive}, the same rule
     * {@link DoubleWallBlock} settled on: the junction adopts its edges from the straight runs
     * beside it, straights never adopt from anything, and a strip already touching the face it
     * hugs needs no branch there at all. The connection is then folded back into this block's
     * two-property encoding by {@link #cornerFacingFor}.
     *
     * <p>Package-visible so the alignment suite can drive it directly instead of standing up a
     * level; it is the same call {@link #updateShape} and {@link #getStateForPlacement} both make.
     */
    BlockState deriveConnections(BlockState state, BlockGetter level, BlockPos pos) {
        WallConnection connection = WallConnection.derive(
            level, pos, state.getValue(FACING), false, ThinWall::runOf);

        boolean corner = connection.shape() != WallShape.STRAIGHT;
        Direction facing = corner ? cornerFacingFor(connection) : connection.facing();

        BlockState rear = level.getBlockState(pos.relative(facing.getOpposite()));
        return state.setValue(FACING, facing)
                    .setValue(CORNER, corner)
                    .setValue(FILLED, fillsGap(rear));
    }

    /**
     * The one {@link #FACING} value whose corner model hugs exactly this connection's two edges.
     *
     * <p>Every {@code *_corner} model is the pair {facing, counter-clockwise}, so a connection
     * whose branch is already counter-clockwise keeps its facing, and one whose branch is
     * clockwise is represented by the branch edge instead - from there, the main run's edge
     * <em>is</em> the counter-clockwise one.
     */
    private static Direction cornerFacingFor(WallConnection connection) {
        return connection.branchRight()
            ? connection.facing().getClockWise()
            : connection.facing();
    }

    /**
     * The run a neighbour describes, or null when it is not part of the thin-wall family.
     *
     * <p>Any {@code ThinWall} counts, fixed-facing ones included: a window sitting in a run is a
     * continuation of that run, and its facing names an edge a junction may line up with. Only
     * straight pieces are ever adopted from, which {@link WallConnection} enforces itself.
     */
    @Nullable
    private static WallConnection runOf(BlockState neighbour) {
        if (!(neighbour.getBlock() instanceof ThinWall)) {
            return null;
        }
        return new WallConnection(
            neighbour.getValue(CORNER) ? WallShape.CORNER : WallShape.STRAIGHT,
            neighbour.getValue(FACING), false);
    }

    private boolean isPivot(BlockGetter level, BlockPos pos, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        boolean parallel = false, perpendicular = false;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (!(n.getBlock() instanceof ThinWall)) continue;

            if (dir.getAxis() == axis) parallel = true;
            else                       perpendicular = true;

            if (parallel && perpendicular) return true;
        }
        return false;
    }

/** rear gap counts as filled only by non-air, non-ThinWall blocks */
private static boolean fillsGap(BlockState rear) {
    if (rear.isAir()) return false;

    if (rear.getBlock() instanceof ThinWall)
        return rear.getValue(ThinWall.FILLED);

    return true;        // any other solid block
}



@Override
public boolean skipRendering(BlockState state,
                             BlockState adjacentState,
                             Direction side) {
    // keep the wall face when the neighbour is any kind of stairs
    if (adjacentState.getBlock() instanceof StairBlock) {
        return false;
    }
    return super.skipRendering(state, adjacentState, side);
}

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return s.getValue(FILLED) ? FULL_SHAPE : switch (s.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST  -> WEST_SHAPE;
            case EAST  -> EAST_SHAPE;
            default    -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {

          if (s.getValue(CORNER))
        return FULL_SHAPE;

        return getShape(s, w, p, c);   // outline equals collision
    }

    /* ─── rotation helpers ─────────────────────────────────── */

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        // FACING is either the hugged edge or the corner pair's representative; both turn with the
        // block, so rotating it keeps the pair coherent ({north,west} -> {east,north} and so on).
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        Direction facing = s.getValue(FACING);
        if (this.connectionRule == ConnectionRule.FIXED_FACING || !s.getValue(CORNER)) {
            return s.setValue(FACING, m.mirror(facing));
        }
        // Reflection swaps a corner's chirality, which this encoding expresses by choosing the
        // other member of the mirrored pair as its representative.
        Direction main   = m.mirror(facing);
        Direction branch = m.mirror(facing.getCounterClockWise());
        return s.setValue(FACING, branch == main.getCounterClockWise() ? main : branch);
    }

    /* ─── state definition ─────────────────────────────────── */

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CORNER, FILLED, STYLE);
    }
}

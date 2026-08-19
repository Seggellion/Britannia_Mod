package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block sandstone wall whose modelled apertures are also open to selection and collision.
 *
 * <h2>Reading the numbers</h2>
 * Every box below is copied out of the matching {@code sandstone_window_*} model file in the
 * model's own coordinates - {@code y} runs 0 to 32 across both halves - and
 * {@link WallModelShape#half} slices out the block being asked. The parallel between a collision
 * definition and the {@code elements} array it came from is the whole point: the previous version
 * had one hand-written pair of shapes standing in for all five variants, and it did not match any
 * of them.
 *
 * <h2>The defect this fixes</h2>
 * That single pair was measured off the {@code _t_junction} art - sill top at {@code y 4}, lintel
 * bottom at {@code y 28} - and was used for the {@code _straight} art as well, which draws a taller
 * sill and a lower lintel ({@code y 8} and {@code y 26}). The straight window therefore collided
 * with a 24 pixel opening while drawing an 18 pixel one, and 24 pixels is exactly a crouching
 * player: standing on the sill and sneaking, a player walked clean through the middle of a wall
 * that plainly shows solid stone above and below the light.
 */
public final class SandstoneWindowBlock extends DoubleWallBlock {

    /** Depth of the wall run: every model in the family draws it over {@code z 0..7}. */
    private static final double DEPTH = 7.0D;

    /**
     * The sill top and lintel bottom of one modelled opening, in model coordinates.
     *
     * @param sillTop      the height the sill stops at, below which the wall is solid
     * @param lintelBottom the height the lintel starts at, above which the wall is solid
     */
    public record Opening(double sillTop, double lintelBottom) {
    }

    /** What every {@code _t_junction} and {@code _corner_branch_right} model in the family draws. */
    public static final Opening JUNCTION_OPENING = new Opening(4.0D, 28.0D);

    /** What the {@code _straight} models draw: a deeper sill and a lower lintel. */
    private static final Opening STRAIGHT_OPENING = new Opening(8.0D, 26.0D);

    /* --- the run hugging the north edge --------------------------------- */

    private static final VoxelShape STRAIGHT_LOWER =
        mainRun(STRAIGHT_OPENING).half(DoubleBlockHalf.LOWER);
    private static final VoxelShape STRAIGHT_UPPER =
        mainRun(STRAIGHT_OPENING).half(DoubleBlockHalf.UPPER);

    /* --- the perpendicular return, on either edge ------------------------ */

    private static final VoxelShape T_JUNCTION_WEST_LOWER = tJunction(false).half(DoubleBlockHalf.LOWER);
    private static final VoxelShape T_JUNCTION_WEST_UPPER = tJunction(false).half(DoubleBlockHalf.UPPER);
    private static final VoxelShape T_JUNCTION_EAST_LOWER = tJunction(true).half(DoubleBlockHalf.LOWER);
    private static final VoxelShape T_JUNCTION_EAST_UPPER = tJunction(true).half(DoubleBlockHalf.UPPER);

    /* --- corners, whose opening differs between the two blocks ----------- */

    private final VoxelShape westCornerLower;
    private final VoxelShape westCornerUpper;
    private final VoxelShape eastCornerLower;
    private final VoxelShape eastCornerUpper;

    /**
     * @param counterClockwiseCornerOpening what this block's {@code _corner} model draws.
     *        {@code sandstone_window_corner} is the one file in the family authored with a six
     *        pixel sill and a lintel at 26, where its own {@code _corner_branch_right} mirror and
     *        the whole ornate family use four and 28. The two registrations pass their own value
     *        rather than the code averaging over an art inconsistency.
     */
    public SandstoneWindowBlock(BlockBehaviour.Properties properties,
                                Opening counterClockwiseCornerOpening) {
        super(properties);
        this.westCornerLower = corner(false, counterClockwiseCornerOpening).half(DoubleBlockHalf.LOWER);
        this.westCornerUpper = corner(false, counterClockwiseCornerOpening).half(DoubleBlockHalf.UPPER);
        this.eastCornerLower = corner(true, JUNCTION_OPENING).half(DoubleBlockHalf.LOWER);
        this.eastCornerUpper = corner(true, JUNCTION_OPENING).half(DoubleBlockHalf.UPPER);
    }

    /* --- model transcriptions -------------------------------------------- */

    /** The full-width run on the north edge, its light set between two jambs three pixels wide. */
    private static WallModelShape mainRun(Opening opening) {
        return WallModelShape.of()
            .box(0.0D, 0.0D, 0.0D, 16.0D, opening.sillTop(), DEPTH)
            .box(0.0D, opening.sillTop(), 0.0D, 3.0D, opening.lintelBottom(), DEPTH)
            .box(13.0D, opening.sillTop(), 0.0D, 16.0D, opening.lintelBottom(), DEPTH)
            .box(0.0D, opening.lintelBottom(), 0.0D, 16.0D, 32.0D, DEPTH);
    }

    /**
     * The perpendicular run, which hugs the east or west edge and carries the same window. Its
     * jambs are two pixels deep at each end of {@code z 7..16}, the length left over once the main
     * run has taken {@code z 0..7}.
     */
    private static WallModelShape returnRun(boolean east, Opening opening) {
        double x0 = east ? 9.0D : 0.0D;
        double x1 = east ? 16.0D : DEPTH;
        return WallModelShape.of()
            .box(x0, 0.0D, DEPTH, x1, opening.sillTop(), 16.0D)
            .box(x0, opening.sillTop(), DEPTH, x1, opening.lintelBottom(), 9.0D)
            .box(x0, opening.sillTop(), 14.0D, x1, opening.lintelBottom(), 16.0D)
            .box(x0, opening.lintelBottom(), DEPTH, x1, 32.0D, 16.0D);
    }

    /** A T: the full-width main run, plus a return on the branch side. */
    private static WallModelShape tJunction(boolean east) {
        return WallModelShape.of()
            .addAll(mainRun(JUNCTION_OPENING))
            .addAll(returnRun(east, JUNCTION_OPENING));
    }

    /**
     * An L: a solid pillar where the two runs meet, a shortened window arm along the north edge
     * beside it, and the return. The arm's jambs are two pixels wide rather than the three of a
     * full-width run, because the pillar has already taken seven pixels of its length.
     */
    private static WallModelShape corner(boolean east, Opening opening) {
        double pillarX0 = east ? 9.0D : 0.0D;
        double pillarX1 = east ? 16.0D : DEPTH;
        double armStart = east ? 0.0D : DEPTH;
        double armEnd = east ? 9.0D : 16.0D;

        return WallModelShape.of()
            .box(pillarX0, 0.0D, 0.0D, pillarX1, 32.0D, DEPTH)
            .box(armStart, 0.0D, 0.0D, armEnd, opening.sillTop(), DEPTH)
            .box(armStart, opening.sillTop(), 0.0D, armStart + 2.0D, opening.lintelBottom(), DEPTH)
            .box(armEnd - 2.0D, opening.sillTop(), 0.0D, armEnd, opening.lintelBottom(), DEPTH)
            .box(armStart, opening.lintelBottom(), 0.0D, armEnd, 32.0D, DEPTH)
            .addAll(returnRun(east, opening));
    }

    /* --- shapes ---------------------------------------------------------- */

    /** The shape as the models are authored: facing north, main run against the north edge. */
    private VoxelShape canonicalShape(BlockState state) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean east = state.getValue(BRANCH_RIGHT);

        return switch (state.getValue(SHAPE)) {
            case STRAIGHT -> lower ? STRAIGHT_LOWER : STRAIGHT_UPPER;
            case CORNER -> lower
                ? (east ? this.eastCornerLower : this.westCornerLower)
                : (east ? this.eastCornerUpper : this.westCornerUpper);
            case T_JUNCTION -> lower
                ? (east ? T_JUNCTION_EAST_LOWER : T_JUNCTION_WEST_LOWER)
                : (east ? T_JUNCTION_EAST_UPPER : T_JUNCTION_WEST_UPPER);
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HorizontalShape.rotateFromNorth(canonicalShape(state), state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}

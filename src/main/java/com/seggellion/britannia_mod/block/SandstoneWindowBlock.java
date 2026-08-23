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
 * definition and the {@code elements} array it came from is the whole point: an earlier version
 * had one hand-written pair of shapes standing in for all five variants, and it did not match any
 * of them - the mismatch was exactly a crouching player walking through solid-looking wall.
 *
 * <h2>Two window styles, one block class</h2>
 * The plain sandstone window draws a stepped arch: two-pixel jambs, a sill at {@code y 9}, and
 * courses corbelling in from {@code x 2..14} at {@code y 18} to {@code x 6..10} under the lintel
 * at {@code y 28}. Its corner models repeat the arch on both runs and leave the meeting of the two
 * walls open rather than solid. The ornate window keeps rectangular lights. Each registration
 * therefore passes its own transcriptions; only the {@code _t_junction} art is still shared.
 */
public final class SandstoneWindowBlock extends DoubleWallBlock {

    /** Depth of the wall run: every model in the family draws it over {@code z 0..7}. */
    private static final double DEPTH = 7.0D;

    /**
     * The sill top and lintel bottom of one rectangular modelled opening, in model coordinates.
     *
     * @param sillTop      the height the sill stops at, below which the wall is solid
     * @param lintelBottom the height the lintel starts at, above which the wall is solid
     */
    public record Opening(double sillTop, double lintelBottom) {
    }

    /** What every {@code _t_junction} model in the family draws. */
    public static final Opening JUNCTION_OPENING = new Opening(4.0D, 28.0D);

    /** What {@code ornate_sandstone_window_straight} draws: a tall sill under a squared light. */
    private static final Opening ORNATE_STRAIGHT_OPENING = new Opening(11.0D, 26.0D);

    /* --- t-junctions, whose art is shared across the family --------------- */

    private static final VoxelShape T_JUNCTION_WEST_LOWER = tJunction(false).half(DoubleBlockHalf.LOWER);
    private static final VoxelShape T_JUNCTION_WEST_UPPER = tJunction(false).half(DoubleBlockHalf.UPPER);
    private static final VoxelShape T_JUNCTION_EAST_LOWER = tJunction(true).half(DoubleBlockHalf.LOWER);
    private static final VoxelShape T_JUNCTION_EAST_UPPER = tJunction(true).half(DoubleBlockHalf.UPPER);

    /* --- runs that differ between the two registrations ------------------- */

    private final VoxelShape straightLower;
    private final VoxelShape straightUpper;
    private final VoxelShape westCornerLower;
    private final VoxelShape westCornerUpper;
    private final VoxelShape eastCornerLower;
    private final VoxelShape eastCornerUpper;

    /** The plain sandstone window, transcribed from its stepped-arch models. */
    public static SandstoneWindowBlock sandstone(BlockBehaviour.Properties properties) {
        return new SandstoneWindowBlock(properties, archedMainRun(), archedWestCorner(), archedEastCorner());
    }

    /** The ornate window: rectangular lights, corners drawn with the junction opening. */
    public static SandstoneWindowBlock ornate(BlockBehaviour.Properties properties) {
        return new SandstoneWindowBlock(properties, mainRun(ORNATE_STRAIGHT_OPENING),
            corner(false, JUNCTION_OPENING), corner(true, JUNCTION_OPENING));
    }

    private SandstoneWindowBlock(BlockBehaviour.Properties properties, WallModelShape straightRun,
                                 WallModelShape westCornerRun, WallModelShape eastCornerRun) {
        super(properties);
        this.straightLower = straightRun.half(DoubleBlockHalf.LOWER);
        this.straightUpper = straightRun.half(DoubleBlockHalf.UPPER);
        this.westCornerLower = westCornerRun.half(DoubleBlockHalf.LOWER);
        this.westCornerUpper = westCornerRun.half(DoubleBlockHalf.UPPER);
        this.eastCornerLower = eastCornerRun.half(DoubleBlockHalf.LOWER);
        this.eastCornerUpper = eastCornerRun.half(DoubleBlockHalf.UPPER);
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

    /** {@code sandstone_window_straight}: the arch, corbelling in one course per step. */
    private static WallModelShape archedMainRun() {
        return WallModelShape.of()
            .box(0.0D, 0.0D, 0.0D, 16.0D, 9.0D, DEPTH)      // sill
            .box(0.0D, 9.0D, 0.0D, 2.0D, 28.0D, DEPTH)      // west jamb
            .box(14.0D, 9.0D, 0.0D, 16.0D, 28.0D, DEPTH)    // east jamb
            .box(2.0D, 18.0D, 0.0D, 3.0D, 28.0D, DEPTH)     // arch, west side
            .box(3.0D, 23.0D, 0.0D, 4.0D, 28.0D, DEPTH)
            .box(4.0D, 25.0D, 0.0D, 5.0D, 28.0D, DEPTH)
            .box(5.0D, 27.0D, 0.0D, 6.0D, 28.0D, DEPTH)
            .box(13.0D, 18.0D, 0.0D, 14.0D, 28.0D, DEPTH)   // arch, east side
            .box(12.0D, 23.0D, 0.0D, 13.0D, 28.0D, DEPTH)
            .box(11.0D, 25.0D, 0.0D, 12.0D, 28.0D, DEPTH)
            .box(10.0D, 27.0D, 0.0D, 11.0D, 28.0D, DEPTH)
            .box(0.0D, 28.0D, 0.0D, 16.0D, 32.0D, DEPTH);   // lintel
    }

    /** {@code sandstone_window_corner}: an arched north run and an arched west return. */
    private static WallModelShape archedWestCorner() {
        return WallModelShape.of()
            // the north run; its west jamb stops at z 5 where the return's arch takes over
            .box(0.0D, 0.0D, 0.0D, 16.0D, 9.0D, DEPTH)      // sill
            .box(0.0D, 9.0D, 0.0D, 2.0D, 28.0D, 5.0D)       // west jamb
            .box(14.0D, 9.0D, 0.0D, 16.0D, 28.0D, DEPTH)    // east jamb
            .box(2.0D, 18.0D, 0.0D, 3.0D, 28.0D, 6.0D)      // arch, west side
            .box(3.0D, 23.0D, 0.0D, 4.0D, 28.0D, 6.0D)
            .box(4.0D, 25.0D, 0.0D, 5.0D, 28.0D, 6.0D)
            .box(5.0D, 27.0D, 0.0D, 6.0D, 28.0D, 6.0D)
            .box(13.0D, 18.0D, 0.0D, 14.0D, 28.0D, DEPTH)   // arch, east side
            .box(12.0D, 23.0D, 0.0D, 13.0D, 28.0D, DEPTH)
            .box(11.0D, 25.0D, 0.0D, 12.0D, 28.0D, DEPTH)
            .box(10.0D, 27.0D, 0.0D, 11.0D, 28.0D, DEPTH)
            .box(0.0D, 28.0D, 0.0D, 16.0D, 32.0D, DEPTH)    // lintel
            // the west return, its arch stepping along z from both ends
            .box(0.0D, 0.0D, DEPTH, 7.0D, 9.0D, 16.0D)      // sill
            .box(0.0D, 18.0D, 15.0D, 3.0D, 28.0D, 16.0D)    // arch, far end
            .box(0.0D, 23.0D, 14.0D, 3.0D, 28.0D, 15.0D)
            .box(0.0D, 25.0D, 13.0D, 3.0D, 28.0D, 14.0D)
            .box(0.0D, 27.0D, 12.0D, 3.0D, 28.0D, 13.0D)
            .box(0.0D, 18.0D, 5.0D, 2.0D, 28.0D, 6.0D)      // arch, corner end
            .box(0.0D, 23.0D, 6.0D, 2.0D, 28.0D, 7.0D)
            .box(0.0D, 25.0D, 7.0D, 2.0D, 28.0D, 8.0D)
            .box(0.0D, 27.0D, 8.0D, 2.0D, 28.0D, 9.0D)
            .box(0.0D, 28.0D, DEPTH, 7.0D, 32.0D, 16.0D);   // lintel
    }

    /** {@code sandstone_window_corner_branch_right}: the same corner mirrored onto the east edge. */
    private static WallModelShape archedEastCorner() {
        return WallModelShape.of()
            // the north run; its east jamb stops at z 5 where the return's arch takes over
            .box(0.0D, 0.0D, 0.0D, 16.0D, 9.0D, DEPTH)      // sill
            .box(0.0D, 9.0D, 0.0D, 2.0D, 28.0D, 6.0D)       // west jamb
            .box(14.0D, 9.0D, 0.0D, 16.0D, 28.0D, 5.0D)     // east jamb
            .box(2.0D, 18.0D, 0.0D, 3.0D, 28.0D, 6.0D)      // arch, west side
            .box(3.0D, 23.0D, 0.0D, 4.0D, 28.0D, 6.0D)
            .box(4.0D, 25.0D, 0.0D, 5.0D, 28.0D, 6.0D)
            .box(5.0D, 27.0D, 0.0D, 6.0D, 28.0D, 6.0D)
            .box(13.0D, 18.0D, 0.0D, 14.0D, 28.0D, 6.0D)    // arch, east side
            .box(12.0D, 23.0D, 0.0D, 13.0D, 28.0D, DEPTH)
            .box(11.0D, 25.0D, 0.0D, 12.0D, 28.0D, DEPTH)
            .box(10.0D, 27.0D, 0.0D, 11.0D, 28.0D, DEPTH)
            .box(0.0D, 28.0D, 0.0D, 16.0D, 32.0D, DEPTH)    // lintel
            // the east return, its arch stepping along z from both ends
            .box(9.0D, 0.0D, DEPTH, 16.0D, 9.0D, 16.0D)     // sill
            .box(13.0D, 18.0D, 15.0D, 16.0D, 28.0D, 16.0D)  // arch, far end
            .box(13.0D, 23.0D, 14.0D, 16.0D, 28.0D, 15.0D)
            .box(13.0D, 25.0D, 13.0D, 16.0D, 28.0D, 14.0D)
            .box(13.0D, 27.0D, 12.0D, 16.0D, 28.0D, 13.0D)
            .box(14.0D, 18.0D, 5.0D, 16.0D, 28.0D, 6.0D)    // arch, corner end
            .box(13.0D, 23.0D, 6.0D, 16.0D, 28.0D, 7.0D)
            .box(13.0D, 25.0D, 7.0D, 16.0D, 28.0D, 8.0D)
            .box(13.0D, 27.0D, 8.0D, 16.0D, 28.0D, 9.0D)
            .box(9.0D, 28.0D, DEPTH, 16.0D, 32.0D, 16.0D);  // lintel
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
            case STRAIGHT -> lower ? this.straightLower : this.straightUpper;
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

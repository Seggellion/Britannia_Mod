package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The thin wall courses - the top, bottom and foundation trim - held to the same junction rule as
 * the full-height walls: where two blocks meet, the art has to touch, and all four rotations of the
 * same join have to settle into rotations of the same geometry.
 *
 * <p>They did neither. {@link ThinWall} used to leave {@code FACING} wherever the player was
 * looking and only ever toggled {@code CORNER}, while every {@code *_corner} model bakes in one
 * fixed pair of edges - the facing edge plus its counter-clockwise neighbour. So of the four
 * rotations of one T-junction, only the one whose run and branch happened to match the placed
 * facing's baked-in pair connected; the other three stepped sideways by half a block or grew their
 * branch on the empty side. The cure is the one {@link DoubleWallBlock} already took:
 * {@link WallConnection} derives the join from the straight runs beside it, and the corner pair is
 * folded back into {@code FACING}+{@code CORNER} because the four corner models between them cover
 * every adjacent pair of edges.
 *
 * <p>The layouts are longer than the rule's reach for the same reason the full-height suite's are:
 * a three-block bar lets every straight touch the junction, which once let a wrong rule go green.
 */
class ThinWallJunctionAlignmentTest {

    /** Model pixels. A course hugging an edge spans its block and is half a block deep. */
    private static final int BLOCK = 16;
    private static final int DEPTH = 8;

    private static ThinWall trim;
    private static ThinWall window;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        trim = new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion());
        window = new ThinWall(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion(),
            ThinWall.ConnectionRule.FIXED_FACING);
    }

    /* ─── the layouts a builder actually makes ───────────────── */

    private static final Map<String, List<BlockPos>> LAYOUTS = new LinkedHashMap<>();

    static {
        for (Direction stem : Direction.Plane.HORIZONTAL) {
            LAYOUTS.put("T with its stem " + stem, tee(stem.getClockWise(), stem));
        }
        for (Direction first : Direction.Plane.HORIZONTAL) {
            LAYOUTS.put("corner " + first + "+" + first.getClockWise(),
                arm(first, 3, arm(first.getClockWise(), 3, origin())));
        }
        LAYOUTS.put("four-way crossing",
            arm(Direction.NORTH, 2, arm(Direction.SOUTH, 2,
                arm(Direction.EAST, 2, arm(Direction.WEST, 2, origin())))));
        LAYOUTS.put("long east-west run", arm(Direction.EAST, 4, arm(Direction.WEST, 4, origin())));
        LAYOUTS.put("long north-south run", arm(Direction.NORTH, 4, arm(Direction.SOUTH, 4, origin())));
        LAYOUTS.put("isolated block", origin());
    }

    /** A five-long bar with a three-long stem off the middle - longer than the rule's reach. */
    private static List<BlockPos> tee(Direction along, Direction stem) {
        return arm(along, 2, arm(along.getOpposite(), 2, arm(stem, 3, origin())));
    }

    private static List<BlockPos> origin() {
        return new ArrayList<>(List.of(BlockPos.ZERO));
    }

    private static List<BlockPos> arm(Direction direction, int length, List<BlockPos> into) {
        for (int step = 1; step <= length; step++) {
            into.add(BlockPos.ZERO.relative(direction, step));
        }
        return into;
    }

    /* ─── the contract ───────────────────────────────────────── */

    @Test
    void neighbouringBlocksHaveArtInContactAcrossTheFaceTheyShare() {
        for (Direction placed : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
            for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
                Map<BlockPos, BlockState> settled = settle(layout.getValue(), placed);

                for (BlockPos pos : layout.getValue()) {
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        BlockState neighbour = settled.get(pos.relative(side));
                        if (neighbour == null) {
                            continue;
                        }
                        int[] mine = spanAtFace(settled.get(pos), side);
                        int[] theirs = spanAtFace(neighbour, side.getOpposite());

                        assertTrue(overlaps(mine, theirs),
                            layout.getKey() + " placed " + placed + ": " + pos + " reaches its "
                                + side + " face over " + describe(mine) + " but the neighbour meets"
                                + " it over " + describe(theirs) + " - the wall is open between them");
                    }
                }
            }
        }
    }

    @Test
    void aStraightRunNeverMovesWhateverIsBuiltAgainstIt() {
        for (Direction facing : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
            for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
                Map<BlockPos, BlockState> settled = settle(layout.getValue(), facing);

                for (BlockPos pos : layout.getValue()) {
                    BlockState state = settled.get(pos);
                    if (state.getValue(ThinWall.CORNER)
                        || !onlyOneAxisHasNeighbours(layout.getValue(), pos)) {
                        continue; // junctions are allowed to move; that is the whole design
                    }
                    Direction settledFacing = state.getValue(ThinWall.FACING);
                    assertTrue(settledFacing == facing
                            || settledFacing.getAxis() != facing.getAxis(),
                        layout.getKey() + " " + pos + ": placed facing " + facing
                            + " but settled on " + settledFacing + " - the run moved");
                }
            }
        }
    }

    @Test
    void theRuleSettlesAndDoesNotOscillate() {
        for (Direction placed : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
            for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
                assertEquals(layout.getValue().size(),
                    settle(layout.getValue(), placed).size(), layout.getKey());
            }
        }
    }

    /**
     * The screenshot defect. One T-junction, built four times at ninety degrees to each other with
     * the same edge choices, has to settle into four rotations of the same states - junction
     * included. Placements here are rotation-covariant (each arm hugs a valid edge for its axis),
     * so this holds block for block; the two base scenarios cover both ways a T can resolve:
     * a junction block, and a run that already touches its branch and stays straight.
     */
    @Test
    void allFourRotationsOfATSettleIntoRotationsOfTheSameJoin() {
        record Scenario(String name, Direction barEdge, Direction stemEdge) {}
        List<Scenario> scenarios = List.of(
            new Scenario("run clear of the stem", Direction.SOUTH, Direction.WEST),
            new Scenario("run already touching the stem", Direction.NORTH, Direction.WEST));

        for (Scenario scenario : scenarios) {
            Map<BlockPos, BlockState> base = settleTee(Direction.NORTH,
                scenario.barEdge(), scenario.stemEdge());

            for (Rotation rotation : Rotation.values()) {
                Direction stem = rotation.rotate(Direction.NORTH);
                Map<BlockPos, BlockState> rotated = settleTee(stem,
                    rotation.rotate(scenario.barEdge()), rotation.rotate(scenario.stemEdge()));

                for (Map.Entry<BlockPos, BlockState> cell : base.entrySet()) {
                    BlockState expected = trim.rotate(cell.getValue(), rotation);
                    BlockState actual = rotated.get(cell.getKey().rotate(rotation));

                    assertEquals(describeState(expected), describeState(actual),
                        scenario.name() + ", stem " + stem + ", cell " + cell.getKey()
                            + ": the rotated build did not settle into the rotated states");
                }
            }
        }
    }

    /**
     * Pins the exact join each stem direction produces, which doubles as the state matrix for the
     * defect report. The bar hugs the edge away from the stem, the stem hugs its
     * counter-clockwise edge, and the junction has to pick, out of the four baked corner pairs,
     * exactly the one whose two panels continue the bar and reach the stem.
     */
    @Test
    void theJunctionAdoptsTheEdgesOfTheRunsBesideIt() {
        record Expectation(Direction stem, Direction barEdge, Direction stemEdge, Direction junctionFacing) {}
        List<Expectation> matrix = List.of(
            new Expectation(Direction.NORTH, Direction.SOUTH, Direction.WEST,  Direction.WEST),
            new Expectation(Direction.EAST,  Direction.WEST,  Direction.NORTH, Direction.NORTH),
            new Expectation(Direction.SOUTH, Direction.NORTH, Direction.EAST,  Direction.EAST),
            new Expectation(Direction.WEST,  Direction.EAST,  Direction.SOUTH, Direction.SOUTH));

        for (Expectation expectation : matrix) {
            Map<BlockPos, BlockState> settled = settleTee(expectation.stem(),
                expectation.barEdge(), expectation.stemEdge());
            BlockState junction = settled.get(BlockPos.ZERO);

            assertTrue(junction.getValue(ThinWall.CORNER),
                "stem " + expectation.stem() + ": the junction did not become a corner");
            assertEquals(expectation.junctionFacing(), junction.getValue(ThinWall.FACING),
                "stem " + expectation.stem() + ": the junction drew the wrong corner pair");
        }
    }

    /**
     * A window is placed into a run but anchors art - and, for the multi-cell ones, a whole
     * footprint of collision helpers - to its facing, so the derivation must never rotate it. It
     * keeps the behaviour it always had: facing pinned, {@code CORNER} the raw pivot flag.
     */
    @Test
    void aFixedFacingPieceKeepsItsFacingWhenARunTeesIntoIt() {
        List<BlockPos> cells = tee(Direction.EAST, Direction.NORTH);
        BlockScene scene = new BlockScene();
        for (BlockPos pos : cells) {
            BlockState placed = (pos.equals(BlockPos.ZERO) ? window : trim).defaultBlockState()
                .setValue(ThinWall.FACING, Direction.NORTH);
            scene.put(pos, placed);
        }
        Map<BlockPos, BlockState> settled = settle(scene, cells);

        BlockState atJunction = settled.get(BlockPos.ZERO);
        assertEquals(Direction.NORTH, atJunction.getValue(ThinWall.FACING),
            "the window moved - a fixed-facing piece must stay where it was placed");
        assertTrue(atJunction.getValue(ThinWall.CORNER),
            "the legacy pivot flag changed behaviour for fixed-facing pieces");
    }

    @Test
    void anIsolatedBlockKeepsItsFacingAndStaysStraight() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockScene scene = new BlockScene();
            BlockState placed = trim.defaultBlockState().setValue(ThinWall.FACING, facing);
            scene.put(BlockPos.ZERO, placed);

            BlockState settled = trim.connectionUpdate(placed, scene, BlockPos.ZERO);
            assertEquals(facing, settled.getValue(ThinWall.FACING));
            assertFalse(settled.getValue(ThinWall.CORNER));
        }
    }

    /**
     * Reflection swaps a corner's chirality. The encoding has no branch flag, so the mirrored
     * state must switch to the corner model whose baked pair is the mirror image of this one's.
     */
    @Test
    void mirroringACornerSwapsItsChirality() {
        BlockState northCorner = trim.defaultBlockState()
            .setValue(ThinWall.FACING, Direction.NORTH).setValue(ThinWall.CORNER, true);

        // {north, west} mirrored east-west is {north, east}, which east_corner draws.
        assertEquals(Direction.EAST,
            trim.mirror(northCorner, Mirror.FRONT_BACK).getValue(ThinWall.FACING));
        // {north, west} mirrored north-south is {south, west}, which west_corner draws.
        assertEquals(Direction.WEST,
            trim.mirror(northCorner, Mirror.LEFT_RIGHT).getValue(ThinWall.FACING));

        BlockState straight = trim.defaultBlockState().setValue(ThinWall.FACING, Direction.NORTH);
        assertEquals(Direction.NORTH,
            trim.mirror(straight, Mirror.FRONT_BACK).getValue(ThinWall.FACING));
        assertEquals(Direction.SOUTH,
            trim.mirror(straight, Mirror.LEFT_RIGHT).getValue(ThinWall.FACING));
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static boolean onlyOneAxisHasNeighbours(List<BlockPos> cells, BlockPos pos) {
        boolean x = cells.contains(pos.east()) || cells.contains(pos.west());
        boolean z = cells.contains(pos.north()) || cells.contains(pos.south());
        return x != z;
    }

    /** The edges a settled state draws panels on: the facing, plus its pair for a corner. */
    private static List<Direction> edgesOf(BlockState state) {
        Direction facing = state.getValue(ThinWall.FACING);
        if (!state.getValue(ThinWall.CORNER)) {
            return List.of(facing);
        }
        return List.of(facing, facing.getCounterClockWise());
    }

    /**
     * The interval of a block's shared face that its art actually reaches, or an empty interval
     * when it does not reach that face at all. A panel hugging an edge spans its block the long
     * way and is {@link #DEPTH} deep, so it touches three of the four faces.
     */
    private static int[] spanAtFace(BlockState state, Direction face) {
        int[] widest = {0, 0};
        for (Direction edge : edgesOf(state)) {
            int[] span = stripAtFace(edge, face);
            if (span[1] - span[0] > widest[1] - widest[0]) {
                widest = span;
            }
        }
        return widest;
    }

    /** Where a panel hugging {@code edge} meets {@code face}, as an interval along that face. */
    private static int[] stripAtFace(Direction edge, Direction face) {
        if (face == edge) {
            return new int[] {0, BLOCK};                     // the face it hugs, over its full span
        }
        if (face == edge.getOpposite()) {
            return new int[] {0, 0};                         // the far face - never reached
        }
        // A face the panel runs between: it meets it only over its own depth.
        return edge == Direction.NORTH || edge == Direction.WEST
            ? new int[] {0, DEPTH} : new int[] {BLOCK - DEPTH, BLOCK};
    }

    private static boolean overlaps(int[] a, int[] b) {
        return Math.min(a[1], b[1]) - Math.max(a[0], b[0]) > 0;
    }

    private static String describe(int[] span) {
        return span[1] - span[0] <= 0 ? "nothing" : span[0] + ".." + span[1];
    }

    private static String describeState(BlockState state) {
        return state.getValue(ThinWall.FACING)
            + (state.getValue(ThinWall.CORNER) ? " corner" : " straight");
    }

    /** A T whose bar and stem are placed hugging the given edges, then settled. */
    private static Map<BlockPos, BlockState> settleTee(Direction stem,
                                                       Direction barEdge, Direction stemEdge) {
        List<BlockPos> cells = tee(stem.getClockWise(), stem);
        BlockScene scene = new BlockScene();
        for (BlockPos pos : cells) {
            boolean onStem = pos.getX() * stem.getStepX() > 0 || pos.getZ() * stem.getStepZ() > 0;
            scene.put(pos, trim.defaultBlockState()
                .setValue(ThinWall.FACING, onStem ? stemEdge : barEdge));
        }
        return settle(scene, cells);
    }

    private static Map<BlockPos, BlockState> settle(List<BlockPos> cells, Direction placedFacing) {
        BlockScene scene = new BlockScene();
        for (BlockPos pos : cells) {
            scene.put(pos, trim.defaultBlockState().setValue(ThinWall.FACING, placedFacing));
        }
        return settle(scene, cells);
    }

    /**
     * Runs the derivation over the whole scene until it stops changing, the way a chain of
     * neighbour updates does in a real level. Failing to settle is itself a defect - it would mean
     * two blocks rewriting each other for ever.
     */
    private static Map<BlockPos, BlockState> settle(BlockScene scene, List<BlockPos> cells) {
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (BlockPos pos : cells) {
                BlockState was = scene.getBlockState(pos);
                BlockState now = ((ThinWall) was.getBlock()).connectionUpdate(was, scene, pos);
                changed |= !was.equals(now);
                scene.put(pos, now);
            }
            if (!changed) {
                Map<BlockPos, BlockState> settled = new LinkedHashMap<>();
                for (BlockPos pos : cells) {
                    settled.put(pos, scene.getBlockState(pos));
                }
                return settled;
            }
        }
        throw new AssertionError("the connection rule never settled - two blocks are fighting");
    }
}

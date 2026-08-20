package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
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
 * Where two wall blocks meet, the art has to touch.
 *
 * <p>It did not. A corner or junction used to force its main run onto the edge away from its
 * branch, while the straight run it was joining stayed where the player put it, so the wall stepped
 * sideways by most of a block at the join. Two of the four T orientations were wrong, and two of
 * the four corners - the ones whose branch reaches north or west.
 *
 * <h2>Why the runs here are long</h2>
 * The first attempt at this had every straight adopt the junction's edge instead, and was checked
 * against a three-block bar - where every straight touches the junction, so every one of them
 * adopted and the suite went green. On a real five-block wall the far blocks never heard about it,
 * the middle flipped to the far edge and the wall tore open at <em>both</em> ends. So the layouts
 * here are deliberately longer than the rule's reach, and the assertion is about geometry rather
 * than about which edge a block chose:
 *
 * <ul>
 *   <li>every pair of neighbours must actually have art in contact across the face they share;</li>
 *   <li>a straight run must never move, whatever is built against it.</li>
 * </ul>
 */
class WallJunctionAlignmentTest {

    /** Model pixels. A run hugging an edge is about five deep and spans its block. */
    private static final int BLOCK = 16;
    private static final int DEPTH = 5;

    private static DoubleWallBlock wall;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        wall = new DoubleWallBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion());
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
        for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
            Map<BlockPos, BlockState> settled = settle(layout.getValue());

            for (BlockPos pos : layout.getValue()) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockState neighbour = settled.get(pos.relative(side));
                    if (neighbour == null) {
                        continue;
                    }
                    int[] mine = spanAtFace(settled.get(pos), side);
                    int[] theirs = spanAtFace(neighbour, side.getOpposite());

                    assertTrue(overlaps(mine, theirs),
                        layout.getKey() + ": " + pos + " reaches its " + side + " face over "
                            + describe(mine) + " but the neighbour meets it over " + describe(theirs)
                            + " - the wall is open between them");
                }
            }
        }
    }

    @Test
    void aStraightRunNeverMovesWhateverIsBuiltAgainstIt() {
        // The first attempt moved them, and a wall already standing jumped to the other side of its
        // own blocks the moment a T was added to it. Junctions adapt; runs stay put.
        for (Direction facing : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
            for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
                Map<BlockPos, BlockState> settled = settle(layout.getValue(), facing);

                for (BlockPos pos : layout.getValue()) {
                    BlockState state = settled.get(pos);
                    if (state.getValue(DoubleWallBlock.SHAPE) != WallShape.STRAIGHT
                        || !onlyOneAxisHasNeighbours(layout.getValue(), pos)) {
                        continue; // junctions are allowed to move; that is the whole design
                    }
                    Direction settledFacing = state.getValue(DoubleWallBlock.FACING);
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
        // settle() throws if it never reaches a fixed point, so this just exercises every layout.
        for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
            assertEquals(layout.getValue().size(), settle(layout.getValue()).size(), layout.getKey());
        }
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static boolean onlyOneAxisHasNeighbours(List<BlockPos> cells, BlockPos pos) {
        boolean x = cells.contains(pos.east()) || cells.contains(pos.west());
        boolean z = cells.contains(pos.north()) || cells.contains(pos.south());
        return x != z;
    }

    /**
     * The interval of a block's shared face that its art actually reaches, or an empty interval
     * when it does not reach that face at all.
     *
     * <p>A strip hugging an edge spans its block the long way and is {@link #DEPTH} deep, so it
     * touches three of the four faces: the two its length runs between, and the one it hugs.
     */
    private static int[] spanAtFace(BlockState state, Direction face) {
        WallConnection run = runOf(state);
        int[] widest = {0, 0};
        for (Direction edge : new Direction[] {run.eastWestEdge(), run.northSouthEdge()}) {
            if (edge == null) {
                continue;
            }
            int[] span = stripAtFace(edge, face);
            if (span[1] - span[0] > widest[1] - widest[0]) {
                widest = span;
            }
        }
        return widest;
    }

    /** Where a strip hugging {@code edge} meets {@code face}, as an interval along that face. */
    private static int[] stripAtFace(Direction edge, Direction face) {
        if (face == edge) {
            return new int[] {0, BLOCK};                     // the face it hugs, over its full span
        }
        if (face == edge.getOpposite()) {
            return new int[] {0, 0};                         // the far face - never reached
        }
        // A face the strip runs between: it meets it only over its own depth.
        return edge == Direction.NORTH || edge == Direction.WEST
            ? new int[] {0, DEPTH} : new int[] {BLOCK - DEPTH, BLOCK};
    }

    private static boolean overlaps(int[] a, int[] b) {
        return Math.min(a[1], b[1]) - Math.max(a[0], b[0]) > 0;
    }

    private static String describe(int[] span) {
        return span[1] - span[0] <= 0 ? "nothing" : span[0] + ".." + span[1];
    }

    private static WallConnection runOf(BlockState state) {
        return new WallConnection(state.getValue(DoubleWallBlock.SHAPE),
                                  state.getValue(DoubleWallBlock.FACING),
                                  state.getValue(DoubleWallBlock.BRANCH_RIGHT));
    }

    private static Map<BlockPos, BlockState> settle(List<BlockPos> cells) {
        return settle(cells, Direction.NORTH);
    }

    /**
     * Runs the derivation over the whole layout until it stops changing, the way a chain of
     * neighbour updates does in a real level. Failing to settle is itself a defect - it would mean
     * two blocks rewriting each other for ever.
     */
    private static Map<BlockPos, BlockState> settle(List<BlockPos> cells, Direction placedFacing) {
        BlockScene scene = new BlockScene();
        for (BlockPos pos : cells) {
            scene.put(pos, wall.defaultBlockState().setValue(DoubleWallBlock.FACING, placedFacing));
        }
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (BlockPos pos : cells) {
                BlockState was = scene.getBlockState(pos);
                BlockState now = wall.deriveConnections(was, scene, pos);
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

package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Where two wall blocks meet, they have to be hugging the same edge.
 *
 * <p>They did not. A corner or junction has its edges forced - the main run is pushed to the edge
 * away from its branch - while a straight run with only one connection had no way to tell and fell
 * back on a fixed default, north for an east-west run and west for a north-south one. Wherever the
 * junction's forced choice was the other one, the wall stepped sideways by most of a block at the
 * junction and came apart.
 *
 * <p>Two of the four T orientations were wrong for exactly this reason, and so were two of the four
 * corners: the ones whose branch reaches north or west. That is why a T built with its stem to the
 * south looked right and the same T mirrored did not.
 *
 * <p>The suite asserts the property rather than the fix: whatever edge a block ends up on, its
 * neighbour along that run must be on the same one.
 */
class WallJunctionAlignmentTest {

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
        LAYOUTS.put("T with its stem north", bar(Direction.EAST, Direction.NORTH));
        LAYOUTS.put("T with its stem south", bar(Direction.EAST, Direction.SOUTH));
        LAYOUTS.put("T with its stem east", bar(Direction.NORTH, Direction.EAST));
        LAYOUTS.put("T with its stem west", bar(Direction.NORTH, Direction.WEST));
        for (Direction first : Direction.Plane.HORIZONTAL) {
            LAYOUTS.put("corner " + first + "+" + first.getClockWise(),
                List.of(BlockPos.ZERO, BlockPos.ZERO.relative(first),
                        BlockPos.ZERO.relative(first.getClockWise())));
        }
        LAYOUTS.put("four-way crossing", List.of(BlockPos.ZERO,
            BlockPos.ZERO.north(), BlockPos.ZERO.south(), BlockPos.ZERO.east(), BlockPos.ZERO.west()));
        LAYOUTS.put("plain east-west run", List.of(
            BlockPos.ZERO.west().west(), BlockPos.ZERO.west(), BlockPos.ZERO,
            BlockPos.ZERO.east(), BlockPos.ZERO.east().east()));
        LAYOUTS.put("plain north-south run", List.of(
            BlockPos.ZERO.north().north(), BlockPos.ZERO.north(), BlockPos.ZERO,
            BlockPos.ZERO.south(), BlockPos.ZERO.south().south()));
    }

    /** Three in a row along {@code along}, plus one stem hanging off the middle. */
    private static List<BlockPos> bar(Direction along, Direction stem) {
        return List.of(BlockPos.ZERO.relative(along), BlockPos.ZERO,
                       BlockPos.ZERO.relative(along.getOpposite()), BlockPos.ZERO.relative(stem));
    }

    /* ─── the contract ───────────────────────────────────────── */

    @Test
    void everyPairOfNeighboursHugsTheSameEdge() {
        for (Map.Entry<String, List<BlockPos>> layout : LAYOUTS.entrySet()) {
            Map<BlockPos, BlockState> settled = settle(layout.getValue());

            for (BlockPos pos : layout.getValue()) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockState neighbour = settled.get(pos.relative(side));
                    if (neighbour == null) {
                        continue;
                    }
                    Direction mine = edgeAlong(settled.get(pos), side.getAxis());
                    Direction theirs = edgeAlong(neighbour, side.getAxis());

                    assertNotNull(mine, layout.getKey() + " " + pos
                        + " has no run reaching its " + side + " neighbour");
                    assertNotNull(theirs, layout.getKey() + " " + pos.relative(side)
                        + " has no run reaching back");
                    assertEquals(mine, theirs, layout.getKey() + ": " + pos + " hugs " + mine
                        + " but its " + side + " neighbour hugs " + theirs
                        + " - the wall steps sideways between them");
                }
            }
        }
    }

    @Test
    void junctionsStillPointTheirBranchAtTheNeighbourThatNeedsIt() {
        // Alignment is not enough on its own: the branch still has to be the arm that reaches the
        // odd neighbour out, or the T would line up beautifully and connect to nothing.
        for (Direction stem : Direction.Plane.HORIZONTAL) {
            Direction along = stem.getClockWise();
            Map<BlockPos, BlockState> settled = settle(bar(along, stem));
            BlockState centre = settled.get(BlockPos.ZERO);

            assertEquals(WallShape.T_JUNCTION, centre.getValue(DoubleWallBlock.SHAPE),
                "stem " + stem + ": three neighbours make a junction");
            assertTrue(reachesNeighbour(centre, stem),
                "stem " + stem + ": the junction has to have an arm reaching it");
            for (Direction side : new Direction[] {along, along.getOpposite()}) {
                assertTrue(reachesNeighbour(centre, side),
                    "stem " + stem + ": the run still has to reach " + side);
            }
        }
    }

    @Test
    void aStraightRunKeepsThePlayersChoiceWhenNothingForcesIt() {
        // The adoption rule must not trample a deliberate placement. A run with no junction on it
        // has nothing authoritative to copy, so the facing the player gave it survives.
        for (Direction facing : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
            BlockScene scene = new BlockScene();
            List<BlockPos> cells = List.of(BlockPos.ZERO.west(), BlockPos.ZERO, BlockPos.ZERO.east());
            for (BlockPos pos : cells) {
                scene.put(pos, wall.defaultBlockState().setValue(DoubleWallBlock.FACING, facing));
            }
            BlockState settled = wall.deriveConnections(scene.getBlockState(BlockPos.ZERO), scene, BlockPos.ZERO);
            assertEquals(facing, settled.getValue(DoubleWallBlock.FACING),
                "a plain run should stay on the edge it was placed against");
        }
    }

    /* ─── helpers ────────────────────────────────────────────── */

    /**
     * Runs the derivation over the whole layout until it stops changing, the way a chain of
     * neighbour updates does in a real level. Failing to settle is itself a defect - it would mean
     * two blocks each rewriting the other for ever.
     */
    private static Map<BlockPos, BlockState> settle(List<BlockPos> cells) {
        BlockScene scene = new BlockScene();
        for (BlockPos pos : cells) {
            scene.put(pos, wall.defaultBlockState());
        }
        for (int pass = 0; pass < 8; pass++) {
            List<BlockState> before = new ArrayList<>();
            List<BlockState> after = new ArrayList<>();
            for (BlockPos pos : cells) {
                BlockState was = scene.getBlockState(pos);
                BlockState now = wall.deriveConnections(was, scene, pos);
                before.add(was);
                after.add(now);
                scene.put(pos, now);
            }
            if (before.equals(after)) {
                Map<BlockPos, BlockState> settled = new LinkedHashMap<>();
                for (BlockPos pos : cells) {
                    settled.put(pos, scene.getBlockState(pos));
                }
                return settled;
            }
        }
        throw new AssertionError("the connection rule never settled - two blocks are fighting");
    }

    /** The edge a state's run hugs on the given axis, or null when it has no run along it. */
    private static Direction edgeAlong(BlockState state, Direction.Axis axis) {
        WallConnection run = new WallConnection(state.getValue(DoubleWallBlock.SHAPE),
                                                state.getValue(DoubleWallBlock.FACING),
                                                state.getValue(DoubleWallBlock.BRANCH_RIGHT));
        return axis == Direction.Axis.X ? run.eastWestEdge() : run.northSouthEdge();
    }

    /** Whether a state has an arm that spans the block far enough to meet the given neighbour. */
    private static boolean reachesNeighbour(BlockState state, Direction side) {
        return edgeAlong(state, side.getAxis()) != null;
    }
}

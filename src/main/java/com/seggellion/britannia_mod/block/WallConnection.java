package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Works out how an edge-aligned architectural run should sit in a block, given which neighbours it
 * has to meet. Shared by {@link DoubleWallBlock} and {@link WoodSupportFloorBlock} so both derive
 * their orientation the same way.
 *
 * <p>A run hugging the north or south edge spans east-west, so it is what reaches an east or west
 * neighbour; a run on the east or west edge spans north-south. {@code facing} names the edge the
 * main run hugs and {@code branchRight} says whether the perpendicular run is on the clockwise
 * ({@code true}) or counter-clockwise ({@code false}) edge from it.
 *
 * @param shape       straight, L-corner or T-junction
 * @param facing      the edge the main run hugs
 * @param branchRight which side of {@code facing} the perpendicular run sits on
 */
public record WallConnection(WallShape shape, Direction facing, boolean branchRight) {

    /**
     * @param currentFacing      the block's present facing, used as a tie-breaker so a deliberate
     *                           player choice survives wherever the geometry allows more than one
     *                           answer (a lone block, or a straight run that could hug either edge)
     * @param currentBranchRight the block's present branch side, used the same way
     * @param isPeer             what counts as a connecting neighbour
     */
    public static WallConnection derive(LevelAccessor level, BlockPos pos,
                                        Direction currentFacing, boolean currentBranchRight,
                                        Predicate<BlockState> isPeer) {

        boolean north = isPeer.test(level.getBlockState(pos.north()));
        boolean south = isPeer.test(level.getBlockState(pos.south()));
        boolean east  = isPeer.test(level.getBlockState(pos.east()));
        boolean west  = isPeer.test(level.getBlockState(pos.west()));

        int connections = (north ? 1 : 0) + (south ? 1 : 0) + (east ? 1 : 0) + (west ? 1 : 0);

        boolean needsEastWestRun   = east || west;
        boolean needsNorthSouthRun = north || south;

        Direction currentSecondary =
            currentBranchRight ? currentFacing.getClockWise() : currentFacing.getCounterClockWise();

        if (!needsEastWestRun && !needsNorthSouthRun) {
            return new WallConnection(WallShape.STRAIGHT, currentFacing, currentBranchRight);
        }

        if (needsEastWestRun && !needsNorthSouthRun) {
            return new WallConnection(WallShape.STRAIGHT,
                eastWestRunEdge(north, south, currentFacing), currentBranchRight);
        }

        if (needsNorthSouthRun && !needsEastWestRun) {
            return new WallConnection(WallShape.STRAIGHT,
                northSouthRunEdge(east, west, currentFacing), currentBranchRight);
        }

        // Both runs are needed: this block turns or branches.
        Direction main = eastWestRunEdge(north, south, currentFacing);
        Direction secondary = northSouthRunEdge(east, west, currentSecondary);

        return new WallConnection(
            connections >= 3 ? WallShape.T_JUNCTION : WallShape.CORNER,
            main,
            secondary == main.getClockWise());
    }

    /**
     * The edge for the east-west run. The run hugs the edge away from the perpendicular branch, so
     * a branch heading south leaves the run on the north edge and vice versa.
     */
    private static Direction eastWestRunEdge(boolean north, boolean south, Direction current) {
        if (south && !north) return Direction.NORTH;
        if (north && !south) return Direction.SOUTH;
        return current.getAxis() == Direction.Axis.Z ? current : Direction.NORTH;
    }

    /**
     * The edge for the north-south run, by the same rule: a run arriving from the east leaves the
     * perpendicular run on the west edge.
     */
    private static Direction northSouthRunEdge(boolean east, boolean west, Direction current) {
        if (east && !west) return Direction.WEST;
        if (west && !east) return Direction.EAST;
        return current.getAxis() == Direction.Axis.X ? current : Direction.WEST;
    }
}

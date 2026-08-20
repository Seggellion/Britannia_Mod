package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Works out how an edge-aligned architectural run should sit in a block, given which neighbours it
 * has to meet. Shared by {@link DoubleWallBlock}, {@link PlasterWallHalfBlock},
 * {@link WoodSupportFloorBlock} and {@link BannisterBlock} so they all derive their orientation the
 * same way.
 *
 * <p>A run hugging the north or south edge spans east-west, so it is what reaches an east or west
 * neighbour; a run on the east or west edge spans north-south. {@code facing} names the edge the
 * main run hugs and {@code branchRight} says whether the perpendicular run is on the clockwise
 * ({@code true}) or counter-clockwise ({@code false}) edge from it.
 *
 * <h2>Why a straight run asks its neighbours</h2>
 * A block with one connection cannot tell which of the two parallel edges to hug, so it used to
 * fall back on a fixed default - north for an east-west run, west for a north-south one. A corner
 * or junction has no such freedom: its main run is pushed to the edge away from its branch. When
 * those two disagreed the wall jogged sideways at the junction, which is what made a T built
 * against a north or west neighbour come apart while the same T built the other way looked right.
 *
 * <p>So a straight run now adopts the edge a corner or junction beside it has already committed to.
 * Junctions never adopt, only straights do, which is what keeps this from oscillating: the
 * authoritative choice flows outwards along the run and nothing flows back.
 *
 * @param shape       straight, L-corner or T-junction
 * @param facing      the edge the main run hugs
 * @param branchRight which side of {@code facing} the perpendicular run sits on
 */
public record WallConnection(WallShape shape, Direction facing, boolean branchRight) {

    /** What counts as a connecting neighbour, and what run that neighbour already describes. */
    @FunctionalInterface
    public interface Peers {
        /** The run {@code neighbour} describes, or {@code null} when it is not a peer at all. */
        @Nullable WallConnection runOf(BlockState neighbour);
    }

    /**
     * @param currentFacing      the block's present facing, used as a tie-breaker so a deliberate
     *                           player choice survives wherever the geometry allows more than one
     *                           answer (a lone block, or a straight run that could hug either edge)
     * @param currentBranchRight the block's present branch side, used the same way
     * @param peers              what counts as a connecting neighbour
     */
    public static WallConnection derive(BlockGetter level, BlockPos pos,
                                        Direction currentFacing, boolean currentBranchRight,
                                        Peers peers) {

        boolean north = peers.runOf(level.getBlockState(pos.north())) != null;
        boolean south = peers.runOf(level.getBlockState(pos.south())) != null;
        boolean east  = peers.runOf(level.getBlockState(pos.east()))  != null;
        boolean west  = peers.runOf(level.getBlockState(pos.west()))  != null;

        int connections = (north ? 1 : 0) + (south ? 1 : 0) + (east ? 1 : 0) + (west ? 1 : 0);

        boolean needsEastWestRun   = east || west;
        boolean needsNorthSouthRun = north || south;

        Direction currentSecondary =
            currentBranchRight ? currentFacing.getClockWise() : currentFacing.getCounterClockWise();

        if (!needsEastWestRun && !needsNorthSouthRun) {
            return new WallConnection(WallShape.STRAIGHT, currentFacing, currentBranchRight);
        }

        if (needsEastWestRun && !needsNorthSouthRun) {
            Direction edge = eastWestRunEdge(north, south, currentFacing);
            return new WallConnection(WallShape.STRAIGHT,
                adopted(level, pos, Direction.Axis.X, edge, peers), currentBranchRight);
        }

        if (needsNorthSouthRun && !needsEastWestRun) {
            Direction edge = northSouthRunEdge(east, west, currentFacing);
            return new WallConnection(WallShape.STRAIGHT,
                adopted(level, pos, Direction.Axis.Z, edge, peers), currentBranchRight);
        }

        // Both runs are needed: this block turns or branches, and its edges are forced.
        Direction main = eastWestRunEdge(north, south, currentFacing);
        Direction secondary = northSouthRunEdge(east, west, currentSecondary);

        return new WallConnection(
            connections >= 3 ? WallShape.T_JUNCTION : WallShape.CORNER,
            main,
            secondary == main.getClockWise());
    }

    /* ─── which edges a run occupies ─────────────────────────── */

    /** The perpendicular edge this run's branch hugs. Meaningless on a straight run. */
    public Direction secondary() {
        return this.branchRight ? this.facing.getClockWise() : this.facing.getCounterClockWise();
    }

    /** The north or south edge this run's east-west arm hugs, or null when it has no such arm. */
    @Nullable
    public Direction eastWestEdge() {
        if (this.facing.getAxis() == Direction.Axis.Z) {
            return this.facing;
        }
        return this.shape == WallShape.STRAIGHT ? null : secondary();
    }

    /** The west or east edge this run's north-south arm hugs, or null when it has no such arm. */
    @Nullable
    public Direction northSouthEdge() {
        if (this.facing.getAxis() == Direction.Axis.X) {
            return this.facing;
        }
        return this.shape == WallShape.STRAIGHT ? null : secondary();
    }

    /**
     * The edge a straight run should hug: whatever a corner or junction along the same run has
     * already committed to, or {@code fallback} when there is none to ask.
     *
     * @param along the axis this run travels, so the axis its neighbours lie on
     */
    private static Direction adopted(BlockGetter level, BlockPos pos, Direction.Axis along,
                                     Direction fallback, Peers peers) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() != along) {
                continue;
            }
            WallConnection neighbour = peers.runOf(level.getBlockState(pos.relative(side)));
            if (neighbour == null || neighbour.shape() == WallShape.STRAIGHT) {
                continue; // only a corner or a junction has a forced edge worth adopting
            }
            Direction edge = along == Direction.Axis.X
                ? neighbour.eastWestEdge() : neighbour.northSouthEdge();
            if (edge != null) {
                return edge;
            }
        }
        return fallback;
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

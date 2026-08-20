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
 * <h2>The junction moves, the run does not</h2>
 * A straight run hugs whichever edge the player gave it and never moves again. That is the whole
 * point: a wall already standing must not jump to the other side of its blocks because something
 * was built against it. So it is the corner or junction that adapts, taking its edges from the
 * straight runs either side of it. Straights never adopt, so nothing can cycle, and nothing ends
 * up anywhere but where it was built.
 *
 * <p>The junction used to do the opposite - it forced its main run onto the edge away from its
 * branch and left behind the run it was joining. That is what tore a T apart when its stem pointed
 * north or west, and a corner whenever its branch did.
 *
 * <h2>Two strips, and when one is enough</h2>
 * A block draws at most two strips: an east-west one hugging {@code north} or {@code south}, and a
 * north-south one hugging {@code west} or {@code east}. A strip spans its block, so as well as
 * reaching along itself it touches the face it hugs - an east-west run on the north edge already
 * meets a neighbour to the north, with no branch at all. Where that is true the block stays
 * {@link WallShape#STRAIGHT} however many neighbours it has, and the corner is formed across the
 * two blocks instead of inside one. A branch there would only reach out of the far side into
 * nothing.
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
            return new WallConnection(WallShape.STRAIGHT,
                keep(currentFacing, Direction.Axis.Z, Direction.NORTH), currentBranchRight);
        }

        if (needsNorthSouthRun && !needsEastWestRun) {
            return new WallConnection(WallShape.STRAIGHT,
                keep(currentFacing, Direction.Axis.X, Direction.WEST), currentBranchRight);
        }

        // Both axes have neighbours. Take each strip's edge from the straight run beside it, so
        // this block lines up with what is already standing instead of dragging it across.
        Direction eastWest = adoptFrom(level, pos, Direction.Axis.X, Direction.Axis.Z, peers);
        if (eastWest == null) {
            eastWest = keep(currentFacing, Direction.Axis.Z, Direction.NORTH);
        }
        Direction northSouth = adoptFrom(level, pos, Direction.Axis.Z, Direction.Axis.X, peers);
        if (northSouth == null) {
            northSouth = keep(currentSecondary, Direction.Axis.X, Direction.WEST);
        }

        // A strip touches the face it hugs, so it may already reach the perpendicular neighbours on
        // its own - and then a branch would only reach out of the far side into nothing.
        boolean eastWestReachesBoth = (!north || eastWest == Direction.NORTH)
                                   && (!south || eastWest == Direction.SOUTH);
        if (eastWestReachesBoth) {
            return new WallConnection(WallShape.STRAIGHT, eastWest, currentBranchRight);
        }
        boolean northSouthReachesBoth = (!east || northSouth == Direction.EAST)
                                     && (!west || northSouth == Direction.WEST);
        if (northSouthReachesBoth) {
            return new WallConnection(WallShape.STRAIGHT, northSouth, currentBranchRight);
        }

        return new WallConnection(
            connections >= 3 ? WallShape.T_JUNCTION : WallShape.CORNER,
            eastWest,
            northSouth == eastWest.getClockWise());
    }

    /** The block's own edge when it lies on the axis wanted, otherwise the family's default. */
    private static Direction keep(Direction current, Direction.Axis wanted, Direction fallback) {
        return current.getAxis() == wanted ? current : fallback;
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
     * The edge a straight run beside this block is already hugging, so a junction can line up with
     * it instead of moving it.
     *
     * <p>Only straights are asked. They are the ones that never adopt, which makes them the stable
     * end of the relationship - two junctions reading each other could disagree for ever.
     *
     * @param probeAxis  which neighbours to ask, so which of this block's strips they share
     * @param wantedAxis the axis the answer has to lie on to be about that strip
     */
    @Nullable
    private static Direction adoptFrom(BlockGetter level, BlockPos pos, Direction.Axis probeAxis,
                                       Direction.Axis wantedAxis, Peers peers) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() != probeAxis) {
                continue;
            }
            WallConnection run = peers.runOf(level.getBlockState(pos.relative(side)));
            if (run == null || run.shape() != WallShape.STRAIGHT) {
                continue;
            }
            if (run.facing().getAxis() == wantedAxis) {
                return run.facing();
            }
        }
        return null;
    }
}

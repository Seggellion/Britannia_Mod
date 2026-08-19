package com.seggellion.britannia_mod.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which edges of its block a {@link DoubleWallBlock} family's art actually fills, per
 * {@link WallShape} and branch side, and the {@link VoxelShape} that follows from it.
 *
 * <p>Collision for these walls is not read from the model JSON - it is rebuilt here from the same
 * edge strips the models are authored against. That works only while every family draws the shape
 * its state names, so when one family's art stops matching the canonical layout the collision has
 * to be told, or the player is blocked by nothing and walks through something.
 *
 * <p>Everything is expressed relative to {@code facing}, so one entry covers all four rotations:
 * the blockstate turns the model with {@code y}, and {@link Direction#getOpposite()} and friends
 * turn the shape with it.
 */
public enum WallArtProfile {

    /**
     * What every approved model in the family draws: the main run hugs {@code facing}, and a corner
     * or junction adds one perpendicular run on the {@code branch_right} edge.
     */
    CANONICAL,

    /**
     * {@code plaster_wall_blank}, whose {@code _t_junction_branch_right} model was re-authored as a
     * single run on the edge <em>opposite</em> {@code facing} with no perpendicular branch at all.
     *
     * <p>Only that one state deviates; the family's straight, corner and left-branch junction
     * models are still canonical, so this profile falls through to {@link #CANONICAL} for them.
     */
    BLANK_PLASTER;

    /**
     * Wall depth used by the collision boxes. The approved straight models occupy roughly
     * {@code z 0..7} once their base beams are counted, so the edge strips match the art.
     */
    private static final double DEPTH = 7.0D;

    private static final VoxelShape EDGE_NORTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, DEPTH);
    private static final VoxelShape EDGE_SOUTH = Block.box(0.0D, 0.0D, 16.0D - DEPTH, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape EDGE_WEST  = Block.box(0.0D, 0.0D, 0.0D, DEPTH, 16.0D, 16.0D);
    private static final VoxelShape EDGE_EAST  = Block.box(16.0D - DEPTH, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    /** [profile][shape][facing2d][branchRight] - built once, because getShape runs per collision test. */
    private static final VoxelShape[][][][] CACHE = buildCache();

    /** The strip of block a run hugging {@code direction} fills. */
    public static VoxelShape edge(Direction direction) {
        return switch (direction) {
            case SOUTH -> EDGE_SOUTH;
            case EAST  -> EDGE_EAST;
            case WEST  -> EDGE_WEST;
            default    -> EDGE_NORTH;
        };
    }

    /** The shape a state's art occupies. */
    public VoxelShape shapeFor(WallShape shape, Direction facing, boolean branchRight) {
        return CACHE[this.ordinal()][shape.ordinal()][facing.get2DDataValue()][branchRight ? 1 : 0];
    }

    private static VoxelShape compute(WallArtProfile profile, WallShape shape,
                                      Direction facing, boolean branchRight) {
        if (profile == BLANK_PLASTER && shape == WallShape.T_JUNCTION && branchRight) {
            // plaster_wall_blank_t_junction_branch_right.json draws one run on the far edge only.
            return edge(facing.getOpposite());
        }
        VoxelShape main = edge(facing);
        if (shape == WallShape.STRAIGHT) {
            return main;
        }
        Direction secondary = branchRight ? facing.getClockWise() : facing.getCounterClockWise();
        return Shapes.or(main, edge(secondary));
    }

    private static VoxelShape[][][][] buildCache() {
        WallArtProfile[] profiles = values();
        WallShape[] shapes = WallShape.values();
        VoxelShape[][][][] cache = new VoxelShape[profiles.length][shapes.length][4][2];
        for (WallArtProfile profile : profiles) {
            for (WallShape shape : shapes) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (int branch = 0; branch < 2; branch++) {
                        cache[profile.ordinal()][shape.ordinal()][facing.get2DDataValue()][branch] =
                            compute(profile, shape, facing, branch == 1);
                    }
                }
            }
        }
        return cache;
    }
}

package com.seggellion.britannia_mod.banner.structure;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Exact orientation-aware local/world transform shared by placement, parts, repair, preview, and removal. */
public final class BannerStructureTransform {
    private BannerStructureTransform() {
    }

    /** FACING points toward the viewer, so the viewer's right is its counter-clockwise horizontal rotation. */
    public static Direction viewerRight(Direction outwardFacing) {
        if (!outwardFacing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Banner facing must be horizontal");
        }
        return outwardFacing.getCounterClockWise();
    }

    public static Direction spanAxis(Direction outwardFacing, BannerOrientation orientation) {
        requireHorizontal(outwardFacing);
        return orientation == BannerOrientation.WALL_PARALLEL
                ? viewerRight(outwardFacing) : outwardFacing;
    }

    public static Direction verticalAxis() {
        return Direction.DOWN;
    }

    public static BlockPos worldPosition(
            BlockPos anchor, Direction outwardFacing, BannerLocalOffset offset) {
        return worldPosition(anchor, outwardFacing, BannerOrientation.WALL_PARALLEL, offset);
    }

    public static BlockPos worldPosition(
            BlockPos anchor, Direction outwardFacing, BannerOrientation orientation, BannerLocalOffset offset) {
        return anchor.relative(spanAxis(outwardFacing, orientation), offset.horizontal())
                .relative(verticalAxis(), offset.vertical()).immutable();
    }

    public static BlockPos anchorPosition(
            BlockPos occupied, Direction outwardFacing, BannerLocalOffset offset) {
        return anchorPosition(occupied, outwardFacing, BannerOrientation.WALL_PARALLEL, offset);
    }

    public static BlockPos anchorPosition(
            BlockPos occupied, Direction outwardFacing, BannerOrientation orientation, BannerLocalOffset offset) {
        return occupied.relative(spanAxis(outwardFacing, orientation).getOpposite(), offset.horizontal())
                .relative(verticalAxis().getOpposite(), offset.vertical()).immutable();
    }

    /** Returns an offset only when the position lies exactly in the selected banner plane and supported bounds. */
    public static Optional<BannerLocalOffset> localOffset(
            BlockPos anchor, Direction outwardFacing, BannerOrientation orientation, BlockPos occupied) {
        Direction span = spanAxis(outwardFacing, orientation);
        int dx = occupied.getX() - anchor.getX();
        int dy = occupied.getY() - anchor.getY();
        int dz = occupied.getZ() - anchor.getZ();
        int horizontal = dx * span.getStepX() + dz * span.getStepZ();
        int vertical = -dy;
        int orthogonal = dx * span.getStepZ() - dz * span.getStepX();
        if (orthogonal != 0 || horizontal < 0 || horizontal > BannerLocalOffset.MAX_HORIZONTAL
                || vertical < 0 || vertical > BannerLocalOffset.MAX_VERTICAL) {
            return Optional.empty();
        }
        return Optional.of(new BannerLocalOffset(horizontal, vertical));
    }

    public static List<BlockPos> requiredSupportPositions(
            BlockPos anchor, Direction outwardFacing, BannerOrientation orientation,
            List<BannerLocalOffset> occupiedOffsets) {
        requireHorizontal(outwardFacing);
        if (orientation == BannerOrientation.WALL_PERPENDICULAR) {
            return List.of(anchor.relative(outwardFacing.getOpposite()).immutable());
        }
        List<BlockPos> result = new ArrayList<>();
        for (BannerLocalOffset offset : occupiedOffsets) {
            if (offset.vertical() == 0) {
                result.add(worldPosition(anchor, outwardFacing, orientation, offset)
                        .relative(outwardFacing.getOpposite()).immutable());
            }
        }
        return List.copyOf(result);
    }

    /** Per-cell diagnostic outline/collision geometry; no final cloth geometry is implied. */
    public static VoxelShape cellShape(BannerOrientation orientation, Direction outwardFacing, boolean anchor) {
        requireHorizontal(outwardFacing);
        double minY = anchor ? 1.0 : 0.0;
        double maxY = anchor ? 15.0 : 16.0;
        if (orientation == BannerOrientation.WALL_PARALLEL) {
            return switch (outwardFacing) {
                case NORTH -> Block.box(1.0, minY, 14.0, 15.0, maxY, 16.0);
                case SOUTH -> Block.box(1.0, minY, 0.0, 15.0, maxY, 2.0);
                case WEST -> Block.box(14.0, minY, 1.0, 16.0, maxY, 15.0);
                case EAST -> Block.box(0.0, minY, 1.0, 2.0, maxY, 15.0);
                default -> throw new IllegalArgumentException("Banner facing must be horizontal");
            };
        }
        return outwardFacing.getAxis() == Direction.Axis.Z
                ? Block.box(7.0, minY, 0.0, 9.0, maxY, 16.0)
                : Block.box(0.0, minY, 7.0, 16.0, maxY, 9.0);
    }

    private static void requireHorizontal(Direction facing) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Banner facing must be horizontal");
        }
    }
}

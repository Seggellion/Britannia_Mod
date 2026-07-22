package com.seggellion.britannia_mod.banner.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Exact wall-parallel local/world transform shared by placement, parts, repair, and removal. */
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

    public static BlockPos worldPosition(
            BlockPos anchor, Direction outwardFacing, BannerLocalOffset offset) {
        return anchor.relative(viewerRight(outwardFacing), offset.horizontal()).below(offset.vertical()).immutable();
    }

    public static BlockPos anchorPosition(
            BlockPos occupied, Direction outwardFacing, BannerLocalOffset offset) {
        return occupied.relative(viewerRight(outwardFacing).getOpposite(), offset.horizontal())
                .above(offset.vertical()).immutable();
    }
}

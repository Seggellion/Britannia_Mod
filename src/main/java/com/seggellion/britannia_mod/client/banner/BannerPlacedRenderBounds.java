package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Finite footprint bounds; calculated only from persisted occupancy and anchor facing. */
public final class BannerPlacedRenderBounds {
    /**
     * Must cover the furthest any rendered geometry reaches outside its occupied cells. After
     * the 2026-08-25 cloth resize the worst case is a perpendicular medium banner: its cloth is
     * pinned at the wall face and runs 1.65 blocks outward from a one-block cell, so it clears
     * the far edge by 0.65, and the pole runs {@code BannerPlacedAssembly.POLE_OVERHANG}
     * (0.125) past that -- 0.775 beyond the cell. The deepest vertical reach is a small
     * banner's 1.25-block cloth hanging out of its single cell, 0.4375 below. Sized above both
     * so the block entity is never culled while any part of it is still on screen.
     *
     * <p>Briefly 0.9375 while Small Curtain hung on a 2.75-block pole; compacting that pole to
     * frame its own fabric returned the furthest reach to a perpendicular medium banner's 0.775,
     * so the margin came back down with it.
     */
    public static final double MOUNT_AND_CLOTH_MARGIN = 0.8125;

    private BannerPlacedRenderBounds() {
    }

    public static AABB from(BannerBlockEntity entity) {
        Direction facing = BannerPlacedRenderStateExtractor.anchorFacing(entity);
        return from(entity.getBlockPos(), facing, entity.placedStructure());
    }

    public static AABB from(
            BlockPos anchor,
            Direction facing,
            Optional<BannerPlacedStructure> structure) {
        if (structure.isEmpty() || !facing.getAxis().isHorizontal()) {
            return new AABB(anchor).inflate(MOUNT_AND_CLOTH_MARGIN);
        }
        BannerPlacedStructure placed = structure.orElseThrow();
        AABB bounds = null;
        for (var offset : placed.occupiedOffsets()) {
            BlockPos occupied = BannerStructureTransform.worldPosition(
                    anchor, facing, placed.orientation(), offset);
            AABB cell = new AABB(occupied);
            bounds = bounds == null ? cell : bounds.minmax(cell);
        }
        return (bounds == null ? new AABB(anchor) : bounds).inflate(MOUNT_AND_CLOTH_MARGIN);
    }
}

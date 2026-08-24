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
     * Must cover the furthest any rendered geometry reaches outside its occupied cells. The
     * worst case is a perpendicular medium banner: its cloth is pinned at the wall face and
     * overhangs its one-block footprint by the full 0.375 at the outer end, and the pole runs
     * {@code BannerPlacedAssembly.POLE_OVERHANG} (0.125) past that -- 0.5 beyond the cell.
     * Sized above that so the block entity is not culled while its pole tip is still on screen.
     */
    public static final double MOUNT_AND_CLOTH_MARGIN = 0.5625;

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

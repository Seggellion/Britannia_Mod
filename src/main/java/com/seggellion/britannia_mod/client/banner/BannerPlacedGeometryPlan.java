package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Anchor-relative cloth and top-mount corners for one immutable placed context. */
public record BannerPlacedGeometryPlan(
        Vec3 topLeft,
        Vec3 bottomLeft,
        Vec3 bottomRight,
        Vec3 topRight,
        Vec3 mountTopLeft,
        Vec3 mountBottomLeft,
        Vec3 mountBottomRight,
        Vec3 mountTopRight,
        Direction frontNormal) {
    private static final double WALL_OFFSET = 0.498;
    private static final double MOUNT_OVERHANG = 0.125;

    public BannerPlacedGeometryPlan {
        Objects.requireNonNull(topLeft, "topLeft");
        Objects.requireNonNull(bottomLeft, "bottomLeft");
        Objects.requireNonNull(bottomRight, "bottomRight");
        Objects.requireNonNull(topRight, "topRight");
        Objects.requireNonNull(mountTopLeft, "mountTopLeft");
        Objects.requireNonNull(mountBottomLeft, "mountBottomLeft");
        Objects.requireNonNull(mountBottomRight, "mountBottomRight");
        Objects.requireNonNull(mountTopRight, "mountTopRight");
        Objects.requireNonNull(frontNormal, "frontNormal");
    }

    public static BannerPlacedGeometryPlan create(
            BannerOrientation orientation,
            Direction facing,
            int width,
            int height,
            BannerPlacedGeometryFamily family,
            boolean fallback) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Placed banner facing must be horizontal");
        }
        Direction span = com.seggellion.britannia_mod.banner.structure.BannerStructureTransform
                .spanAxis(facing, orientation);
        Direction normal = orientation == BannerOrientation.WALL_PARALLEL
                ? facing : facing.getClockWise();
        double horizontalInset = fallback ? 0.0625 : family.horizontalInset();
        double verticalInset = fallback ? 0.0625 : family.verticalInset();

        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        if (orientation == BannerOrientation.WALL_PARALLEL) {
            center = center.add(-facing.getStepX() * WALL_OFFSET, 0,
                    -facing.getStepZ() * WALL_OFFSET);
        }
        Vec3 spanVector = new Vec3(span.getStepX(), 0, span.getStepZ());
        Vec3 clothStart = center.add(spanVector.scale(-0.5 + horizontalInset));
        Vec3 topLeft = clothStart.add(0, 0.5 - verticalInset, 0);
        Vec3 bottomLeft = clothStart.add(0, 0.5 - height + verticalInset, 0);
        Vec3 horizontalLength = spanVector.scale(width - 2.0 * horizontalInset);
        Vec3 topRight = topLeft.add(horizontalLength);
        Vec3 bottomRight = bottomLeft.add(horizontalLength);

        Vec3 mountStart = center.add(spanVector.scale(-0.5 - MOUNT_OVERHANG));
        Vec3 mountTopLeft = mountStart.add(0, 0.56, 0);
        Vec3 mountBottomLeft = mountStart.add(0, 0.35, 0);
        Vec3 mountLength = spanVector.scale(width + 2.0 * MOUNT_OVERHANG);
        return new BannerPlacedGeometryPlan(topLeft, bottomLeft, bottomRight, topRight,
                mountTopLeft, mountBottomLeft, mountBottomLeft.add(mountLength),
                mountTopLeft.add(mountLength), normal);
    }
}

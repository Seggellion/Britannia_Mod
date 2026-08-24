package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
        Direction frontNormal,
        Optional<ResourceLocation> mountGeometry) {
    /**
     * How far a parallel banner's cloth sits from the block centre, i.e. flush with the wall
     * less the pole's standoff. Derived from {@link BannerPlacedAssembly#POLE_STANDOFF} rather
     * than chosen independently: the cloth hangs FROM the pole, so it has to share the pole's
     * plane. It was previously 0.498 -- pinned flat against the wall -- which left no room for
     * a bracket to hold anything and put the pole behind the cloth it was supposed to carry.
     */
    private static final double WALL_OFFSET = 0.5 - BannerPlacedAssembly.POLE_STANDOFF;
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
        mountGeometry = Objects.requireNonNull(mountGeometry, "mountGeometry");
    }

    public static BannerPlacedGeometryPlan create(
            BannerOrientation orientation,
            Direction facing,
            int width,
            int height,
            BannerPlacedGeometryFamily family,
            boolean fallback) {
        return create(orientation, facing, width, height, family, fallback, Optional.empty());
    }

    public static BannerPlacedGeometryPlan create(
            BannerOrientation orientation,
            Direction facing,
            int width,
            int height,
            BannerPlacedGeometryFamily family,
            boolean fallback,
            Optional<ResourceLocation> mountGeometry) {
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
        // A parallel banner's span runs along its wall, so a negative inset (the medium
        // families' cloth is wider than its footprint) overhangs harmlessly at both ends. A
        // perpendicular banner's span starts AT the wall face: the same negative inset would
        // push the cloth's first 3/16 of artwork inside the wall (the assembly already pins its
        // pole to the wall face for exactly this reason). Pin the cloth's wall end at the wall
        // face instead and let the whole overhang extend outward.
        double clothStartAlong = orientation == BannerOrientation.WALL_PERPENDICULAR
                ? -0.5 + Math.max(horizontalInset, 0.0)
                : -0.5 + horizontalInset;
        Vec3 clothStart = center.add(spanVector.scale(clothStartAlong));
        // The cloth quad is always square, because every banner texture is square (128x128)
        // and BannerBlockEntityRenderer maps the WHOLE sprite (getU0..getU1, getV0..getV1)
        // onto this quad -- so the rendered artwork's aspect is its aspect within the texture
        // multiplied by clothWidth/clothHeight, and any non-square quad distorts every banner
        // in the family. verticalInset therefore positions the cloth's TOP edge (keeping it
        // against the mount) and no longer also determines its height; deriving the height
        // from the width is what enforces the invariant structurally rather than leaving it
        // to each family constant to get right independently.
        double clothWidth = width - 2.0 * horizontalInset;
        double clothHeight = clothWidth;
        Vec3 topLeft = clothStart.add(0, 0.5 - verticalInset, 0);
        Vec3 bottomLeft = topLeft.add(0, -clothHeight, 0);
        Vec3 horizontalLength = spanVector.scale(clothWidth);
        Vec3 topRight = topLeft.add(horizontalLength);
        Vec3 bottomRight = bottomLeft.add(horizontalLength);

        boolean orientationSpecific = mountGeometry.isPresent();
        Vec3 mountStart = orientationSpecific && orientation == BannerOrientation.WALL_PERPENDICULAR
                ? center.add(spanVector.scale(-0.5))
                : center.add(spanVector.scale(-0.5 - MOUNT_OVERHANG));
        Vec3 mountTopLeft = mountStart.add(0, 0.56, 0);
        Vec3 mountBottomLeft = mountStart.add(0, 0.35, 0);
        double mountSpan = orientationSpecific && orientation == BannerOrientation.WALL_PERPENDICULAR
                ? width + MOUNT_OVERHANG : width + 2.0 * MOUNT_OVERHANG;
        Vec3 mountLength = spanVector.scale(mountSpan);
        return new BannerPlacedGeometryPlan(topLeft, bottomLeft, bottomRight, topRight,
                mountTopLeft, mountBottomLeft, mountBottomLeft.add(mountLength),
                mountTopLeft.add(mountLength), normal, mountGeometry);
    }
}

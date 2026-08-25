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
        double poleLineY,
        double poleStartAlong,
        double poleEndAlong,
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

    /**
     * Thickness of the placed cloth, in blocks: 0.5 Minecraft model pixels. The renderer draws
     * the cloth as a front and a back face, each offset half of this from the cloth plane, so
     * this is the distance between them. It was effectively zero (0.001 blocks, 1/60th of a
     * pixel) before, which read as a flat decal and let the two faces z-fight at range.
     */
    public static final double CLOTH_THICKNESS = 0.5 / 16.0;

    /**
     * How far along its own pole a perpendicular banner's cloth starts, instead of at the wall
     * face. The wall bracket's collar reaches {@code 4.75} model pixels out from the wall, and
     * the small and medium families paint artwork beginning 5.16 and 4.95 pixels into their
     * cloth -- clearances of 0.41 and 0.20 pixels, which read in-game as the cloth growing out
     * of the hardware. Two pixels of daylight puts every family at least 2.2 pixels clear, in
     * line with the x-small family that already looked right. The pole is measured from the
     * wall regardless (see {@link #poleStartAlong()}), so moving the cloth does not lengthen it.
     */
    public static final double WALL_SIDE_CLEARANCE = 2.0 / 16.0;

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
        // The missing-asset placeholder keeps its own square 1/16 inset; a real family states
        // its cloth outright, because width and height no longer scale together.
        double clothWidth = fallback ? width - 0.125 : family.clothWidth();
        double clothHeight = fallback ? width - 0.125 : family.clothHeight();
        double topInset = fallback ? 0.0625 : family.clothTopInset();
        double clothLift = fallback ? 0.0 : family.clothLift();

        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        if (orientation == BannerOrientation.WALL_PARALLEL) {
            center = center.add(-facing.getStepX() * WALL_OFFSET, 0,
                    -facing.getStepZ() * WALL_OFFSET);
        }
        Vec3 spanVector = new Vec3(span.getStepX(), 0, span.getStepZ());
        // A parallel banner's span runs along its wall, so it centres on its own footprint and
        // any excess width (the medium families' cloth is wider than the block it is anchored
        // in) overhangs evenly at both ends. A perpendicular banner's span starts AT the wall
        // face and runs outward, so centring would bury its first inches of artwork inside the
        // wall; it is pinned to the wall face instead, exactly where the assembly already pins
        // its pole, and the whole cloth extends outward from there.
        boolean perpendicular = orientation == BannerOrientation.WALL_PERPENDICULAR;
        double clothStartAlong = perpendicular
                ? -0.5 + WALL_SIDE_CLEARANCE
                : -0.5 + (width - clothWidth) / 2.0;
        Vec3 clothStart = center.add(spanVector.scale(clothStartAlong));

        // The pole spans the artwork, not the transparent quad around it, and a perpendicular
        // pole always reaches back to its wall bracket no matter where the cloth hangs on it.
        double poleCovered = (fallback ? 1.0 : family.poleArtworkSpan()) * clothWidth;
        double poleStartAlong;
        double poleEndAlong;
        if (perpendicular) {
            poleStartAlong = -0.5;
            poleEndAlong = -0.5 + poleCovered + BannerPlacedAssembly.POLE_OVERHANG;
        } else {
            double clothCentreAlong = clothStartAlong + clothWidth / 2.0;
            poleStartAlong = clothCentreAlong - poleCovered / 2.0 - BannerPlacedAssembly.POLE_OVERHANG;
            poleEndAlong = clothCentreAlong + poleCovered / 2.0 + BannerPlacedAssembly.POLE_OVERHANG;
        }
        // topInset positions the MOUNT LINE -- the height the pole and its brackets sit at,
        // measured down from the anchor block's top face. The cloth hangs from that line, less
        // any family lift (see BannerPlacedGeometryFamily#clothLift), so that a family whose
        // artwork carries transparent margin above its painted cloth can be raised onto its
        // pole without dragging the pole up with it.
        double poleLineY = center.y + 0.5 - topInset;
        Vec3 topLeft = new Vec3(clothStart.x, poleLineY + clothLift, clothStart.z);
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
                mountTopLeft.add(mountLength), normal, poleLineY, poleStartAlong, poleEndAlong,
                mountGeometry);
    }
}

package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where the wooden pole and its metal wall brackets sit for one placed banner, derived from
 * the cloth's own geometry so the three parts can never drift apart.
 *
 * <h2>Canonical model frame</h2>
 * Every assembly model ({@code banner/pole/pole_*} and {@code banner/mount/bracket}) is
 * authored once in a single frame and rotated onto the world from here:
 * <ul>
 *   <li><b>+X</b> runs along the pole, i.e. the banner's span axis</li>
 *   <li><b>+Y</b> is up</li>
 *   <li><b>+Z</b> points away from the wall, toward the viewer</li>
 *   <li>the pole axis lies at model-local {@code (y=0.5, z=POLE_STANDOFF)}, spanning x 0..1</li>
 * </ul>
 *
 * <p>That frame is the banner's own front normal frame, so the rotation is simply
 * {@code -frontNormal.toYRot()} for both orientations -- {@link BannerPlacedGeometryPlan}
 * already computes {@code frontNormal} as {@code facing} when parallel and
 * {@code facing.getClockWise()} when perpendicular, which is exactly the +Z this frame wants.
 * The wall therefore always lies at canonical -Z for a parallel banner. A perpendicular banner
 * runs its pole out from the wall instead, so its single bracket needs the extra quarter turn
 * in {@link #bracketExtraYRotationDegrees()} to face its plate at the wall (canonical -X).
 */
public record BannerPlacedAssembly(
        Vec3 poleCenter,
        double poleLength,
        float yRotationDegrees,
        List<Vec3> bracketAnchors,
        float bracketExtraYRotationDegrees) {

    /**
     * Distance from the wall face to the pole axis, matching the bracket model's own reach.
     * {@link BannerPlacedGeometryPlan#WALL_OFFSET} is derived from this so a parallel banner's
     * cloth hangs in the pole's plane rather than flat against the wall behind it.
     */
    public static final double POLE_STANDOFF = 3.0 / 16.0;

    /** How far the pole runs past the cloth at each end, leaving room for the brackets. */
    public static final double POLE_OVERHANG = 0.125;

    /** How far a bracket's collar sits in from the pole's tip, so the finial stays visible. */
    public static final double BRACKET_INSET = 0.125;

    /**
     * Half the width of a bracket's wall plate along the pole, mirroring
     * {@code banner/mount/bracket}'s 4-pixel plate. Kept here so a family can work out where its
     * hardware actually finishes when it needs to sit close to its own cloth.
     */
    public static final double BRACKET_PLATE_HALF_WIDTH = 2.0 / 16.0;

    /** Model-local point that {@link #poleCenter()} and each bracket anchor are placed at. */
    public static final Vec3 MODEL_POLE_AXIS = new Vec3(0.5, 0.5, POLE_STANDOFF);

    public BannerPlacedAssembly {
        Objects.requireNonNull(poleCenter, "poleCenter");
        bracketAnchors = List.copyOf(Objects.requireNonNull(bracketAnchors, "bracketAnchors"));
        if (poleLength <= 0.0) {
            throw new IllegalArgumentException("poleLength must be positive");
        }
    }

    public static BannerPlacedAssembly from(BannerPlacedGeometryPlan geometry, BannerOrientation orientation) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(orientation, "orientation");

        Vec3 span = geometry.topRight().subtract(geometry.topLeft());
        Vec3 spanUnit = span.normalize();
        // The assembly hangs on the MOUNT LINE, not on the cloth's top edge. They are the same
        // height for every family except one that lifts its cloth to meet artwork margin (see
        // BannerPlacedGeometryFamily#clothLift) -- reading the cloth there would carry the pole
        // and brackets up with it and close no gap at all.
        Vec3 clothTopCentre = geometry.topLeft().add(span.scale(0.5));
        Vec3 mountCentre = new Vec3(clothTopCentre.x, geometry.poleLineY(), clothTopCentre.z);

        // Work in a 1-D coordinate along the span axis, measured from the anchor block's centre.
        // Dotting with spanUnit drops the normal component, so this is unaffected by the cloth
        // being offset toward the wall.
        Vec3 axisOrigin = new Vec3(0.5, geometry.poleLineY(), 0.5);
        double clothStart = geometry.topLeft().subtract(axisOrigin).dot(spanUnit);
        double clothEnd = geometry.topRight().subtract(axisOrigin).dot(spanUnit);
        double clothMiddle = (clothStart + clothEnd) / 2.0;

        boolean parallel = orientation == BannerOrientation.WALL_PARALLEL;
        // Where the pole runs is decided by the plan, which knows both how wide the cloth is and
        // how much of that width the family's artwork actually paints. Deriving it from the cloth
        // corners here instead would tie the pole to the transparent quad -- so a family whose art
        // fills a third of its texture got a pole three times longer than the banner it carries,
        // and shifting a cloth clear of its wall bracket would drag the pole out with it.
        double poleStartAlong = geometry.poleStartAlong();
        double poleEndAlong = geometry.poleEndAlong();
        double poleLength = poleEndAlong - poleStartAlong;

        // The bracket's collar sits on the pole axis and its plate reaches POLE_STANDOFF back
        // from there, so anchoring the perpendicular bracket that far out from the wall face
        // lands its plate exactly on the wall.
        List<Vec3> brackets = parallel
                ? List.of(pointAt(mountCentre, spanUnit, clothMiddle, poleStartAlong + BRACKET_INSET),
                          pointAt(mountCentre, spanUnit, clothMiddle, poleEndAlong - BRACKET_INSET))
                : List.of(pointAt(mountCentre, spanUnit, clothMiddle, -0.5 + POLE_STANDOFF));

        return new BannerPlacedAssembly(
                pointAt(mountCentre, spanUnit, clothMiddle, (poleStartAlong + poleEndAlong) / 2.0),
                poleLength,
                -geometry.frontNormal().toYRot(),
                brackets,
                parallel ? 0.0F : 90.0F);
    }

    private static Vec3 pointAt(Vec3 clothTopCentre, Vec3 spanUnit, double clothMiddle, double along) {
        return clothTopCentre.add(spanUnit.scale(along - clothMiddle));
    }
}

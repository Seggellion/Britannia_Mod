package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.client.banner.BannerPlacedAssembly;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * The owner's 2026-08-25 polish pass, driven by a three-banner comparison shot and a Small
 * Curtain close-up: hardware out of proportion with the cloth it carries, cloth growing out of
 * the wall bracket, and a curtain still too narrow.
 *
 * <p>Sizes are model pixels. The wall-side measurements are taken along the POLE, which for a
 * perpendicular banner runs out of the wall, not across it -- that axis is the one the collision
 * was on.
 */
class BannerPolishPassTest {
    private static final double EPS = 1.0e-6;
    /** {@code banner/mount/bracket}'s collar reaches this far out of the wall. */
    private static final double BRACKET_REACH_PX = 4.75;

    private static double px(double blocks) {
        return blocks * 16.0;
    }

    private static BannerPlacedGeometryPlan plan(
            BannerPlacedGeometryFamily family, BannerOrientation orientation) {
        return BannerPlacedGeometryPlan.create(
                orientation, Direction.NORTH, family.width(), family.height(), family, false);
    }

    private static double poleLengthPx(BannerPlacedGeometryFamily family, BannerOrientation orientation) {
        return px(BannerPlacedAssembly.from(plan(family, orientation), orientation).poleLength());
    }

    /** How far a perpendicular family's painted artwork starts out from the wall face. */
    private static double artworkStartPx(BannerPlacedGeometryFamily family, String banner) throws Exception {
        double leftFraction = BannerXSmallArtwork.leftEdgeFraction(banner);
        return px(BannerPlacedGeometryPlan.WALL_SIDE_CLEARANCE + leftFraction * family.clothWidth());
    }

    @Test
    void everyPerpendicularFamilysArtworkClearsItsWallBracket() throws Exception {
        // The defect: small's artwork began 5.16px out and medium's 4.95px, against a collar
        // reaching 4.75px -- clearances of 0.41px and 0.20px, which read as collision in-game.
        record Case(BannerPlacedGeometryFamily family, String banner) {
        }
        for (Case row : new Case[] {
                new Case(BannerPlacedGeometryFamily.SMALL, "iron_ward"),
                new Case(BannerPlacedGeometryFamily.MEDIUM, "argent_shield"),
                new Case(BannerPlacedGeometryFamily.ROAD_GUARD, "road_guard")}) {
            double clearance = artworkStartPx(row.family(), row.banner()) - BRACKET_REACH_PX;
            assertTrue(clearance >= 2.0,
                    row.family() + " artwork clears its bracket by only " + clearance + "px");
        }
    }

    @Test
    void movingTheClothClearOfTheBracketDoesNotLengthenAnyPole() {
        // The pole is measured from the wall, so the clearance shift slides the cloth along it
        // rather than pushing its far end out. Medium's pole length in particular must not move.
        assertEquals(28.4, poleLengthPx(
                BannerPlacedGeometryFamily.MEDIUM, BannerOrientation.WALL_PERPENDICULAR), EPS,
                "medium pole length must be unchanged");
        assertEquals(22.0, poleLengthPx(
                BannerPlacedGeometryFamily.SMALL, BannerOrientation.WALL_PERPENDICULAR), EPS,
                "small pole length must be unchanged");
    }

    @Test
    void theXSmallPoleIsShorterThanItWasAndStillCarriesItsArtwork() throws Exception {
        double pole = poleLengthPx(BannerPlacedGeometryFamily.ROAD_GUARD, BannerOrientation.WALL_PERPENDICULAR);
        assertTrue(pole < 22.0 - 2.0,
                "the x-small pole must be visibly shorter than the 22px that overwhelmed it, was " + pole);
        // ...but it must still reach past the far edge of the painted pennant it holds up.
        double artworkEnd = px(BannerPlacedGeometryPlan.WALL_SIDE_CLEARANCE
                + BannerXSmallArtwork.rightEdgeFraction("road_guard")
                        * BannerPlacedGeometryFamily.ROAD_GUARD.clothWidth());
        assertTrue(pole >= artworkEnd,
                "the pole stops at " + pole + "px, short of artwork ending at " + artworkEnd + "px");
        assertEquals(BannerPlacedGeometryFamily.Baseline.PENNANT_POLE,
                BannerPlacedGeometryFamily.ROAD_GUARD.poleArtworkSpan(), EPS);
    }

    @Test
    void xSmallCarriesThirtyPercentMoreClothWhileStayingNarrowerThanSmall() throws Exception {
        var small = BannerPlacedGeometryFamily.SMALL;
        var xSmall = BannerPlacedGeometryFamily.ROAD_GUARD;
        // 30% more cloth, taken as area so it does not overshoot "only slightly taller".
        double areaBefore = 20.0 * 20.0;
        double areaAfter = px(xSmall.clothWidth()) * px(xSmall.clothHeight());
        assertEquals(1.3, areaAfter / areaBefore, 1.0e-9, "x-small must carry 30% more cloth");

        double smallWidth = BannerXSmallArtwork.visibleWidthPx("iron_ward", px(small.clothWidth()));
        double xSmallWidth = BannerXSmallArtwork.visibleWidthPx("road_guard", px(xSmall.clothWidth()));
        assertTrue(xSmallWidth < smallWidth * 0.85,
                "x-small must stay clearly the narrower family: " + xSmallWidth + " vs " + smallWidth);

        double smallHeight = BannerXSmallArtwork.visibleHeightPx("iron_ward", px(small.clothHeight()));
        double xSmallHeight = BannerXSmallArtwork.visibleHeightPx("road_guard", px(xSmall.clothHeight()));
        assertTrue(xSmallHeight > smallHeight, "x-small must now be taller than small");
        assertTrue(xSmallHeight < smallHeight * 1.2,
                "only SLIGHTLY taller: " + xSmallHeight + " vs " + smallHeight);
    }

    @Test
    void smallCurtainIsExactlyTwiceTheWidthItWasAndStaysWallHung() throws Exception {
        var curtain = BannerPlacedGeometryFamily.SMALL_CURTAIN;
        assertEquals(40.0, px(curtain.clothWidth()), EPS, "double the 20px it was");
        assertEquals(2.0 * BannerXSmallArtwork.visibleWidthPx("small_curtain", 20.0),
                BannerXSmallArtwork.visibleWidthPx("small_curtain", px(curtain.clothWidth())), EPS,
                "the drawn curtain must be exactly twice as wide as before");
        // It grows in height with its family, and keeps a pole that spans what it carries.
        assertEquals(22.8035085, px(curtain.clothHeight()), 1.0e-6);
        assertEquals(BannerPlacedGeometryFamily.Baseline.FULL_POLE, curtain.poleArtworkSpan(), EPS);
        // A wall-parallel banner keeps two brackets and no quarter turn.
        var assembly = BannerPlacedAssembly.from(
                plan(curtain, BannerOrientation.WALL_PARALLEL), BannerOrientation.WALL_PARALLEL);
        assertEquals(2, assembly.bracketAnchors().size());
        assertEquals(0.0F, assembly.bracketExtraYRotationDegrees());
    }

    @Test
    void nothingTheOwnerAlreadyApprovedMoved() {
        assertEquals(20.0, px(BannerPlacedGeometryFamily.SMALL.clothWidth()), EPS);
        assertEquals(20.0, px(BannerPlacedGeometryFamily.SMALL.clothHeight()), EPS);
        assertEquals(26.4, px(BannerPlacedGeometryFamily.MEDIUM.clothWidth()), EPS);
        assertEquals(28.6, px(BannerPlacedGeometryFamily.MEDIUM.clothHeight()), EPS);
        assertEquals(37.2, px(BannerPlacedGeometryFamily.LARGE.clothWidth()), EPS);
        assertEquals(40.3, px(BannerPlacedGeometryFamily.LARGE.clothHeight()), EPS);
        assertEquals(3.0, px(BannerPlacedGeometryFamily.LARGE.clothLift()), EPS);
        assertEquals(0.5, px(BannerPlacedGeometryPlan.CLOTH_THICKNESS), EPS);
        // The x-small mount stays flush with the block top, as fixed in the previous pass.
        for (BannerPlacedGeometryFamily family : new BannerPlacedGeometryFamily[] {
                BannerPlacedGeometryFamily.ROAD_GUARD, BannerPlacedGeometryFamily.SMALL_CURTAIN,
                BannerPlacedGeometryFamily.SMALL}) {
            assertEquals(16.0, px(1.0 - family.clothTopInset()) + 3.0, EPS,
                    family + " bracket must stay flush with the block top");
        }
    }

    @Test
    void everyFacingKeepsTheCorrectedProportions() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            for (BannerOrientation orientation : BannerOrientation.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                            orientation, facing, family.width(), family.height(), family, false);
                    String label = family + "/" + orientation + "/" + facing;
                    assertEquals(px(family.clothWidth()),
                            px(plan.topRight().subtract(plan.topLeft()).length()), EPS, label + " width");
                    assertEquals(px(family.clothHeight()),
                            px(plan.topLeft().y - plan.bottomLeft().y), EPS, label + " height");
                    assertEquals(px(family.clothWidth() * family.poleArtworkSpan()) + 2.0
                                    * (orientation == BannerOrientation.WALL_PARALLEL ? 2.0 : 1.0),
                            px(BannerPlacedAssembly.from(plan, orientation).poleLength()), EPS,
                            label + " pole length");
                }
            }
        }
    }
}

package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderBounds;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * The owner's 2026-08-25 follow-up: an x-small banner hung like a tiny pennant from hardware
 * sunk into its block, next to a small banner that read correctly. Two separate defects, pinned
 * separately here so neither can come back on its own.
 *
 * <p>All coordinates are model pixels with the anchor block's floor at Y=0 and its top face at
 * Y=16, which is the frame the owner measures in.
 */
class BannerXSmallFamilyProportionTest {
    private static final double EPS = 1.0e-9;
    private static final double BLOCK_TOP = 16.0;
    /** {@code banner/mount/bracket}'s plate is 6px tall, centred on the pole axis. */
    private static final double PLATE_HALF = 3.0;
    /** {@code banner/pole/pole_*} is 2.5px across, centred on the pole axis. */
    private static final double POLE_RADIUS = 1.25;

    /** Every family that renders an x-small catalogue banner, plus its placeholder. */
    private static final Set<BannerPlacedGeometryFamily> X_SMALL_FAMILIES = EnumSet.of(
            BannerPlacedGeometryFamily.X_SMALL,
            BannerPlacedGeometryFamily.ROAD_GUARD,
            BannerPlacedGeometryFamily.SMALL_CURTAIN);

    private static double px(double blocks) {
        return blocks * 16.0;
    }

    private static BannerPlacedGeometryPlan plan(
            BannerPlacedGeometryFamily family, BannerOrientation orientation, Direction facing) {
        return BannerPlacedGeometryPlan.create(
                orientation, facing, family.width(), family.height(), family, false);
    }

    private static double clothTopPx(BannerPlacedGeometryFamily family) {
        return px(plan(family, BannerOrientation.WALL_PARALLEL, Direction.NORTH).topLeft().y);
    }

    private static double clothBottomPx(BannerPlacedGeometryFamily family) {
        return px(plan(family, BannerOrientation.WALL_PARALLEL, Direction.NORTH).bottomLeft().y);
    }

    private static double mountLinePx(BannerPlacedGeometryFamily family) {
        return px(plan(family, BannerOrientation.WALL_PARALLEL, Direction.NORTH).poleLineY());
    }

    @Test
    void xSmallHangsToExactlyTheSameDepthAsSmall() {
        // The defect: 12px of cloth cleared the block by one pixel, against small's seven.
        assertEquals(-7.0, clothBottomPx(BannerPlacedGeometryFamily.SMALL), EPS, "small cloth bottom");
        for (BannerPlacedGeometryFamily family : X_SMALL_FAMILIES) {
            // Since the 2026-08-25 polish pass x-small carries 30% more cloth than small, so it
            // hangs a little past small's -7 rather than level with it.
            assertEquals(-9.8035085, clothBottomPx(family), 1.0e-6, family + " cloth bottom");
            assertEquals(22.8035085, px(family.clothHeight()), 1.0e-6, family + " cloth height");
            assertTrue(px(family.clothHeight()) > 12.0 + 6.0,
                    family + " must be substantially taller than the 12px that was too short");
        }
    }

    @Test
    void xSmallGainsItsHeightDownwardAndNotByRidingUpTheWall() {
        // Height must come from a longer drop, not from lifting the cloth off its mount: the
        // cloth top stays on the mount line, exactly where the pole is.
        for (BannerPlacedGeometryFamily family : X_SMALL_FAMILIES) {
            assertEquals(0.0, px(family.clothLift()), EPS, family + " must not lift its cloth");
            assertEquals(mountLinePx(family), clothTopPx(family), EPS,
                    family + " cloth must start at its mount line");
            assertEquals(13.0, clothTopPx(family), EPS, family + " cloth top");
        }
        // Large keeps the lift it was given in the previous pass.
        assertEquals(3.0, px(BannerPlacedGeometryFamily.LARGE.clothLift()), EPS);
    }

    @Test
    void everyFamilyThatMountsFlushFinishesItsBracketOnTheBlockTop() {
        // The second defect: x-small's hardware sat 2px inside the block. The mount line is the
        // plate's own half-height below the top face, so the plate finishes exactly on it.
        for (BannerPlacedGeometryFamily family : X_SMALL_FAMILIES) {
            double bracketTop = mountLinePx(family) + PLATE_HALF;
            assertEquals(BLOCK_TOP, bracketTop, EPS, family + " bracket top must be flush with the block top");
            assertEquals(BLOCK_TOP, mountLinePx(BannerPlacedGeometryFamily.SMALL) + PLATE_HALF, EPS,
                    "small is the reference and must stay flush");
            assertEquals(mountLinePx(BannerPlacedGeometryFamily.SMALL), mountLinePx(family), EPS,
                    family + " must share small's mount line");
            // The pole rides inside that plate rather than above the block.
            assertTrue(mountLinePx(family) + POLE_RADIUS < BLOCK_TOP,
                    family + " pole must stay under the block top");
        }
    }

    @Test
    void xSmallStaysTheNarrowFamilyThroughItsArtwork() throws Exception {
        // The quad is a window onto a square texture, so equal windows still draw different
        // banners: a road-guard pennant fills ~31% of its texture width, the small family ~48%.
        double small = BannerXSmallArtwork.visibleWidthPx("iron_ward",
                px(BannerPlacedGeometryFamily.SMALL.clothWidth()));
        double roadGuard = BannerXSmallArtwork.visibleWidthPx("road_guard",
                px(BannerPlacedGeometryFamily.ROAD_GUARD.clothWidth()));
        assertTrue(roadGuard < small * 0.75,
                "an x-small pennant must still read as clearly narrower than a small banner: "
                        + roadGuard + "px against " + small + "px");

        double smallHeight = BannerXSmallArtwork.visibleHeightPx("iron_ward",
                px(BannerPlacedGeometryFamily.SMALL.clothHeight()));
        double roadGuardHeight = BannerXSmallArtwork.visibleHeightPx("road_guard",
                px(BannerPlacedGeometryFamily.ROAD_GUARD.clothHeight()));
        // The 2026-08-25 polish pass moved this relationship: x-small is now deliberately the
        // slightly TALLER family, having been given 30% more cloth than small.
        assertTrue(roadGuardHeight > smallHeight,
                "x-small must now hang past small: " + roadGuardHeight + "px against " + smallHeight + "px");
        assertTrue(roadGuardHeight < smallHeight * 1.2,
                "only slightly taller: " + roadGuardHeight + "px against " + smallHeight + "px");
    }

    @Test
    void theOtherFamiliesKeepTheSizesTheOwnerAlreadyApproved() {
        assertEquals(20.0, px(BannerPlacedGeometryFamily.SMALL.clothWidth()), EPS);
        assertEquals(20.0, px(BannerPlacedGeometryFamily.SMALL.clothHeight()), EPS);
        assertEquals(26.4, px(BannerPlacedGeometryFamily.MEDIUM.clothWidth()), 1.0e-6);
        assertEquals(28.6, px(BannerPlacedGeometryFamily.MEDIUM.clothHeight()), 1.0e-6);
        assertEquals(37.2, px(BannerPlacedGeometryFamily.LARGE.clothWidth()), 1.0e-6);
        assertEquals(40.3, px(BannerPlacedGeometryFamily.LARGE.clothHeight()), 1.0e-6);
        assertEquals(0.5, px(BannerPlacedGeometryPlan.CLOTH_THICKNESS), EPS);
        // Medium and large keep their own historic mount lines; only x-small was realigned.
        assertEquals(0.03125, BannerPlacedGeometryFamily.MEDIUM.clothTopInset(), EPS);
        assertEquals(0.0625, BannerPlacedGeometryFamily.LARGE.clothTopInset(), EPS);
    }

    @Test
    void theCorrectedXSmallSizeSurvivesEveryFacingAndBothOrientations() {
        for (BannerPlacedGeometryFamily family : X_SMALL_FAMILIES) {
            for (BannerOrientation orientation : BannerOrientation.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BannerPlacedGeometryPlan plan = plan(family, orientation, facing);
                    String label = family + "/" + orientation + "/" + facing;
                    assertEquals(px(family.clothHeight()),
                            px(plan.topLeft().y - plan.bottomLeft().y), EPS, label + " height");
                    assertEquals(px(family.clothWidth()),
                            px(plan.topRight().subtract(plan.topLeft()).length()), EPS, label + " width");
                    assertEquals(13.0, px(plan.poleLineY()), EPS, label + " mount line");
                    assertEquals(13.0, px(plan.topLeft().y), EPS, label + " cloth top");
                }
            }
        }
    }

    @Test
    void theTallerXSmallStillFitsTheExistingRenderBounds() {
        // Its reach now equals the small family's, which the current margin already covered, so
        // no global bounds change is needed.
        double margin = BannerPlacedRenderBounds.MOUNT_AND_CLOTH_MARGIN;
        assertEquals(0.9375, margin, EPS, "render bounds cover Small Curtain's doubled width");
        for (BannerPlacedGeometryFamily family : X_SMALL_FAMILIES) {
            double downward = family.clothHeight() + family.clothTopInset()
                    - family.clothLift() - family.height();
            assertTrue(downward <= margin + EPS, family + " hangs " + downward + " past the margin");
            // Small Curtain hangs wall-parallel, so its span reach is measured across its
            // footprint rather than out from the wall like its perpendicular cousins.
            double along = family == BannerPlacedGeometryFamily.SMALL_CURTAIN
                    ? (family.clothWidth() - family.width()) / 2.0 + 0.125
                    : family.clothWidth() + BannerPlacedGeometryPlan.WALL_SIDE_CLEARANCE
                            - family.width();
            assertTrue(along <= margin + EPS, family + " reaches " + along + " past the margin");
        }
    }

    @Test
    void theXSmallCatalogueKeepsItsOrientationsAndItsOneWallHungCurtain() throws Exception {
        var snapshot = DyeResolverFixtures.productionSnapshot();
        int perpendicular = 0;
        for (var definition : snapshot.banners().activeDefinitions()) {
            String geometry = definition.assets().geometry().getPath();
            if (geometry.equals("banner/small_curtain/geometry")) {
                assertEquals(List.of(BannerOrientation.WALL_PARALLEL),
                        definition.supportedOrientations(), "small_curtain must stay wall-hung");
            } else if (geometry.equals("banner/road_guard/geometry")) {
                assertEquals(List.of(BannerOrientation.WALL_PERPENDICULAR),
                        definition.supportedOrientations(), definition.id() + " must stay perpendicular");
                perpendicular++;
            }
        }
        assertEquals(8, perpendicular, "the eight road-guard-style x-small banners");
    }
}

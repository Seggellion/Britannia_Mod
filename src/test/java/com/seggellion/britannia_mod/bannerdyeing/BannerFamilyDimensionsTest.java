package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.client.banner.BannerPlacedAssembly;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import com.seggellion.britannia_mod.client.banner.BannerPlacedRenderBounds;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Exact placed-cloth dimensions for every family, in the owner's own unit: Minecraft model
 * pixels, 16 to the block. These are pinned as absolute sizes rather than ratios so a future
 * change to a shared constant cannot quietly rescale a family and still pass.
 */
class BannerFamilyDimensionsTest {
    private static final double EPS = 1.0e-9;

    private static double px(double blocks) {
        return blocks * 16.0;
    }

    private static BannerPlacedGeometryPlan plan(
            BannerPlacedGeometryFamily family, BannerOrientation orientation, Direction facing) {
        return BannerPlacedGeometryPlan.create(
                orientation, facing, family.width(), family.height(), family, false);
    }

    private static double clothWidthPx(BannerPlacedGeometryPlan plan, Direction span) {
        Vec3 delta = plan.topRight().subtract(plan.topLeft());
        return px(delta.x * span.getStepX() + delta.z * span.getStepZ());
    }

    private static double clothHeightPx(BannerPlacedGeometryPlan plan) {
        return px(plan.topLeft().y - plan.bottomLeft().y);
    }

    @Test
    void everyFamilyDrawsItsApprovedClothSizeInModelPixels() {
        // family, width px, height px  -- baseline x the owner's 2026-08-25 factors.
        record Expected(BannerPlacedGeometryFamily family, double widthPx, double heightPx) {
        }
        List<Expected> expected = List.of(
                // x-small: 6 x 6 doubled.
                new Expected(BannerPlacedGeometryFamily.X_SMALL, 12.0, 12.0),
                new Expected(BannerPlacedGeometryFamily.ROAD_GUARD, 12.0, 12.0),
                new Expected(BannerPlacedGeometryFamily.SMALL_CURTAIN, 12.0, 12.0),
                // small: 10 x 10 doubled.
                new Expected(BannerPlacedGeometryFamily.SMALL, 20.0, 20.0),
                // medium: 22 x 22, +20% wide and +30% tall.
                new Expected(BannerPlacedGeometryFamily.MEDIUM, 26.4, 28.6),
                // medium-wall placeholder: 28 x 28 on the same factors.
                new Expected(BannerPlacedGeometryFamily.MEDIUM_WALL, 33.6, 36.4),
                // large: 31 x 31, +20% wide and +30% tall.
                new Expected(BannerPlacedGeometryFamily.LARGE, 37.2, 40.3));

        assertEquals(BannerPlacedGeometryFamily.values().length, expected.size(),
                "every family must have a pinned cloth size");
        for (Expected row : expected) {
            assertEquals(row.widthPx(), px(row.family().clothWidth()), EPS, row.family() + " width");
            assertEquals(row.heightPx(), px(row.family().clothHeight()), EPS, row.family() + " height");
        }
    }

    @Test
    void scaleFactorsAreExactlyWhatWasRequestedFromTheOldSizes() {
        var baselineDouble = BannerPlacedGeometryFamily.Baseline.DOUBLE;
        assertEquals(BannerPlacedGeometryFamily.Baseline.X_SMALL * baselineDouble,
                BannerPlacedGeometryFamily.X_SMALL.clothWidth(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.X_SMALL * baselineDouble,
                BannerPlacedGeometryFamily.X_SMALL.clothHeight(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.SMALL * baselineDouble,
                BannerPlacedGeometryFamily.SMALL.clothWidth(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.SMALL * baselineDouble,
                BannerPlacedGeometryFamily.SMALL.clothHeight(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.MEDIUM * 1.2,
                BannerPlacedGeometryFamily.MEDIUM.clothWidth(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.MEDIUM * 1.3,
                BannerPlacedGeometryFamily.MEDIUM.clothHeight(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.LARGE * 1.2,
                BannerPlacedGeometryFamily.LARGE.clothWidth(), EPS);
        assertEquals(BannerPlacedGeometryFamily.Baseline.LARGE * 1.3,
                BannerPlacedGeometryFamily.LARGE.clothHeight(), EPS);
    }

    @Test
    void onlyLargeLiftsItsClothAndItLiftsExactlyThreePixels() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            double expected = family == BannerPlacedGeometryFamily.LARGE ? 3.0 : 0.0;
            assertEquals(expected, px(family.clothLift()), EPS, family + " cloth lift");
        }
    }

    @Test
    void theLiftRaisesTheClothAndLeavesThePoleAndBracketsWhereTheyWere() {
        BannerPlacedGeometryPlan large = plan(
                BannerPlacedGeometryFamily.LARGE, BannerOrientation.WALL_PARALLEL, Direction.NORTH);
        // The mount line is untouched by the lift; the cloth top now sits 3px above it.
        assertEquals(1.0 - BannerPlacedGeometryFamily.LARGE.clothTopInset(), large.poleLineY(), EPS);
        assertEquals(3.0, px(large.topLeft().y - large.poleLineY()), EPS);

        BannerPlacedAssembly assembly = BannerPlacedAssembly.from(large, BannerOrientation.WALL_PARALLEL);
        assertEquals(large.poleLineY(), assembly.poleCenter().y, EPS, "pole must stay on the mount line");
        for (Vec3 bracket : assembly.bracketAnchors()) {
            assertEquals(large.poleLineY(), bracket.y, EPS, "brackets must stay on the mount line");
        }
        // And the cloth must still overlap the pole it hangs from rather than float above it:
        // the pole is 2.5px across, so its top is 1.25px above the mount line.
        assertTrue(px(large.topLeft().y - large.poleLineY()) > 1.25,
                "a lifted cloth must still reach the pole");
    }

    @Test
    void clothIsExactlyHalfAPixelThickInEveryOrientation() {
        for (BannerOrientation orientation : BannerOrientation.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BannerPlacedGeometryPlan plan = plan(
                        BannerPlacedGeometryFamily.MEDIUM, orientation, facing);
                // The renderer offsets the front and back faces by the pass depth along the
                // cloth normal, so the pass depth is half the thickness.
                assertEquals(0.5, px(BannerPlacedGeometryPlan.CLOTH_THICKNESS), EPS);
                assertEquals(0.25, px(BannerPlacedGeometryPlan.CLOTH_THICKNESS / 2.0), EPS);
                assertTrue(plan.frontNormal().getAxis().isHorizontal(),
                        "thickness is applied along a horizontal normal in both orientations");
            }
        }
    }

    @Test
    void orientationAndFacingNeverChangeAFamilysClothDimensions() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            for (BannerOrientation orientation : BannerOrientation.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BannerPlacedGeometryPlan plan = plan(family, orientation, facing);
                    Direction span = com.seggellion.britannia_mod.banner.structure
                            .BannerStructureTransform.spanAxis(facing, orientation);
                    String label = family + "/" + orientation + "/" + facing;
                    assertEquals(px(family.clothWidth()), clothWidthPx(plan, span), EPS, label + " width");
                    assertEquals(px(family.clothHeight()), clothHeightPx(plan), EPS, label + " height");
                }
            }
        }
    }

    @Test
    void renderBoundsCoverTheFurthestClothAndPoleReachOfEveryFamily() {
        double margin = BannerPlacedRenderBounds.MOUNT_AND_CLOTH_MARGIN;
        // Anchor-local: the anchor cell spans y 0..1 and the footprint runs down to y = 1 - height.
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            double clothTop = 1.0 - family.clothTopInset() + family.clothLift();
            double clothBottom = clothTop - family.clothHeight();
            double reach = Math.max(Math.max(
                            // Parallel: cloth centres on the footprint, pole overhangs each end.
                            (family.clothWidth() - family.width()) / 2.0 + BannerPlacedAssembly.POLE_OVERHANG,
                            // Perpendicular: cloth starts at the wall face of a single cell.
                            family.clothWidth() - family.width() + BannerPlacedAssembly.POLE_OVERHANG),
                    Math.max(
                            // Below the footprint, and above the anchor cell's top face.
                            (1.0 - family.height()) - clothBottom,
                            clothTop - 1.0));
            assertTrue(reach <= margin + EPS,
                    family + " reaches " + reach + " blocks outside its cells, past the "
                            + margin + " render-bounds margin");
        }
    }

    @Test
    void everyFamilyStillCentresParallelAndPinsPerpendicularToTheWall() {
        for (BannerPlacedGeometryFamily family : BannerPlacedGeometryFamily.values()) {
            // Facing NORTH: the supporting wall is the anchor cell's +Z face, at z = 1, and a
            // parallel banner's span runs west, so its footprint spans x from 1 back to 1 - width.
            BannerPlacedGeometryPlan perpendicular = plan(
                    family, BannerOrientation.WALL_PERPENDICULAR, Direction.NORTH);
            assertEquals(1.0, perpendicular.topLeft().z, EPS,
                    family + " perpendicular cloth must start on the wall face");

            BannerPlacedGeometryPlan parallel = plan(
                    family, BannerOrientation.WALL_PARALLEL, Direction.NORTH);
            double clothMidpoint = (parallel.topLeft().x + parallel.topRight().x) / 2.0;
            assertEquals(1.0 - family.width() / 2.0, clothMidpoint, EPS,
                    family + " parallel cloth must stay centred on its footprint");
        }
    }
}

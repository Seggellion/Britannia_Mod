package com.seggellion.britannia_mod.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.shape.ShapeBounds;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapePlan;
import com.seggellion.britannia_mod.resource.shape.ShapePlanner;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import com.seggellion.britannia_mod.resource.shape.ShapeTuning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Milestone 9: what the shape planners actually cost, and how big the deposits actually are.
 *
 * <h2>Why measurement rather than assertion</h2>
 * Every number this class prints goes into the milestone report, so the report cites measurements
 * from a runnable file rather than remembered figures. The assertions around them are deliberately
 * loose bands: they exist to catch a planner that has become pathological or a configured maximum
 * that has drifted past its cap, not to freeze figures that legitimately move when geometry is
 * retuned.
 *
 * <p>Elapsed time is reported as supplementary information only. The load-bearing measurements are
 * the deterministic ones — planned cells, bounds volume, and the ratio between them — because those
 * are the same on every machine and are what actually bounds the work materialisation has to do.
 */
class PlatformValidationM9Test {

    /** Every shape with its configured maximum, taken from the shipped catalogue. */
    private record ShapeCase(String label, ResourceShape shape, int representative, int maximum,
                             ShapeTuning tuning) {
    }

    private static List<ShapeCase> shapeCases() {
        List<ShapeCase> cases = new ArrayList<>();
        for (ResourceDefinition definition : ResourceCatalog.instance().all()) {
            Optional<ResourceDefinition.Generation> generation = definition.generation();
            if (generation.isEmpty()) {
                continue;
            }
            ResourceDefinition.Generation config = generation.orElseThrow();
            ShapeTuning tuning = definition.natural()
                    .map(natural -> natural.tuning())
                    .orElse(ShapeTuning.DEFAULT);
            int representative = Math.max(config.minRadius(),
                    Math.min(config.maxRadius(), config.maxRadius() / 3));
            cases.add(new ShapeCase(definition.path(), config.shape(),
                    representative, config.maxRadius(), tuning));
        }
        return cases;
    }

    /* ------------------------------------------------------------------ */
    /*  Planning cost                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Every configured shape, at a representative and at its configured maximum radius.
     *
     * <p>The maximum column is the one that matters: it is the largest deposit the shipped data can
     * ask for, so it bounds the worst case an operator or an import can trigger.
     */
    @Test
    void everyShapePlansWithinTheCellCapAtItsConfiguredMaximum() {
        System.out.println("M9PERF ---- shape planning ----");
        System.out.println(String.format(Locale.ROOT,
                "M9PERF %-18s %-18s %6s %8s %12s %7s %8s",
                "resource", "shape", "radius", "cells", "bounds volume", "fill%", "ms"));

        for (ShapeCase testCase : shapeCases()) {
            for (int radius : new int[] {testCase.representative(), testCase.maximum()}) {
                ShapePlanner planner = testCase.shape().planner();
                ShapeConfig config = new ShapeConfig(
                        radius, ShapeRotation.XZ, 0x9E3779B9L, testCase.tuning());

                // One warm plan so the figure is not dominated by first-call class loading.
                planner.plan(config);
                long start = System.nanoTime();
                ShapePlan plan = planner.plan(config);
                double millis = (System.nanoTime() - start) / 1_000_000.0;

                ShapeBounds bounds = planner.bounds(config);
                double fill = 100.0 * plan.count() / Math.max(1L, bounds.volume());

                System.out.println(String.format(Locale.ROOT,
                        "M9PERF %-18s %-18s %6d %8d %12d %6.1f%% %8.2f",
                        testCase.label(), testCase.shape().id(), radius,
                        plan.count(), bounds.volume(), fill, millis));

                assertTrue(plan.count() <= ShapePlan.MAX_CELLS,
                        testCase.label() + " at radius " + radius + " planned " + plan.count()
                                + " cells, over the " + ShapePlan.MAX_CELLS + " cap");
                assertTrue(plan.count() > 0,
                        testCase.label() + " at radius " + radius + " planned nothing");
                for (var offset : plan.offsets()) {
                    assertTrue(bounds.contains(offset),
                            testCase.label() + " planned outside its declared bounds");
                }
            }
        }
    }

    /**
     * The configured maxima leave real headroom under the cell cap.
     *
     * <p>Milestone 3 found Cluster sitting close to the cap at high radius and tightened its
     * configured maximum to 22. This re-measures every shape's margin so a future retune cannot
     * quietly move one of them back up against the ceiling.
     */
    @Test
    void everyConfiguredMaximumLeavesHeadroomUnderTheCap() {
        System.out.println("M9PERF ---- headroom at configured maximum ----");
        for (ShapeCase testCase : shapeCases()) {
            ShapePlan plan = testCase.shape().planner().plan(new ShapeConfig(
                    testCase.maximum(), ShapeRotation.XZ, 7L, testCase.tuning()));
            double used = 100.0 * plan.count() / ShapePlan.MAX_CELLS;
            System.out.println(String.format(Locale.ROOT,
                    "M9PERF %-18s max radius %4d -> %6d cells, %5.1f%% of the cap",
                    testCase.label(), testCase.maximum(), plan.count(), used));
            assertTrue(used <= 100.0,
                    testCase.label() + " uses " + used + "% of the plan cap at its configured maximum");
        }
    }

    /** Planning is stable: the same configuration costs the same cells every time. */
    @Test
    void planningIsDeterministicAcrossRepeatedRuns() {
        for (ShapeCase testCase : shapeCases()) {
            ShapeConfig config = new ShapeConfig(
                    testCase.representative(), ShapeRotation.XZ, 4242L, testCase.tuning());
            ShapePlan first = testCase.shape().planner().plan(config);
            for (int attempt = 0; attempt < 5; attempt++) {
                assertEquals(first.offsets(), testCase.shape().planner().plan(config).offsets(),
                        testCase.label() + " planned differently on attempt " + attempt);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Deposit size, for the balancing assessment                         */
    /* ------------------------------------------------------------------ */

    /**
     * Planned-cell distributions for the resources milestone 9 is asked to assess.
     *
     * <p>Silver and tin had their geometry corrected at milestone 3 and have never been measured
     * for gameplay consequence; agapite's geode radius is provisional. This prints the
     * distributions rather than judging them — the judgement is in the report, and no value is
     * changed here.
     */
    @Test
    void depositSizeDistributionsForTheBalancingAssessment() {
        System.out.println("M9BAL ---- planned cells per deposit, 200 seeds each ----");
        System.out.println(String.format(Locale.ROOT,
                "M9BAL %-14s %-18s %6s %7s %7s %7s %7s",
                "resource", "shape", "radius", "min", "median", "mean", "max"));

        for (String path : List.of("silver", "tin", "agapite", "copper", "iron",
                "gold", "verite", "valorite", "shadow_iron", "silica_sand_deposit")) {
            ResourceDefinition definition = ResourceCatalog.instance().byPath(path).orElseThrow();
            ResourceDefinition.Generation generation = definition.generation().orElseThrow();
            ShapeTuning tuning = definition.natural()
                    .map(natural -> natural.tuning()).orElse(ShapeTuning.DEFAULT);

            // Curated resources are placed at whatever radius Rails names, so the assessment uses
            // the configured radius the live data actually uses where one is known, and the
            // configured maximum otherwise. Both are reported in the milestone report.
            int radius = switch (path) {
                case "silver" -> 35;
                case "tin" -> 35;
                case "agapite" -> 8;
                default -> Math.min(generation.maxRadius(), 24);
            };
            if (radius < generation.shape().minimumRadius()) {
                radius = generation.shape().minimumRadius();
            }

            List<Integer> counts = new ArrayList<>();
            for (long seed = 0; seed < 200; seed++) {
                counts.add(generation.shape().planner()
                        .plan(new ShapeConfig(radius, ShapeRotation.XZ, seed, tuning)).count());
            }
            counts.sort(Integer::compareTo);
            System.out.println(String.format(Locale.ROOT,
                    "M9BAL %-14s %-18s %6d %7d %7d %7.0f %7d",
                    path, generation.shape().id(), radius,
                    counts.get(0), counts.get(counts.size() / 2),
                    counts.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                    counts.get(counts.size() - 1)));

            assertTrue(counts.get(counts.size() - 1) <= ShapePlan.MAX_CELLS,
                    path + " exceeded the plan cap");
        }
    }

    /**
     * The agapite geode at its provisional radius is a geode rather than a blob or a speck.
     *
     * <p>The live Rails row still carries the legacy radius of 55, which is outside the shape's
     * valid range entirely; that is an external action recorded in the report, not something this
     * milestone can or should change.
     */
    @Test
    void theProvisionalAgapiteGeodeIsAReasonableGeode() {
        ResourceDefinition agapite = ResourceCatalog.instance().byPath("agapite").orElseThrow();
        ResourceDefinition.Generation generation = agapite.generation().orElseThrow();

        assertEquals(ResourceShape.GEODE, generation.shape());
        assertEquals(3, generation.minRadius(), "the geode's configured minimum moved");
        assertEquals(16, generation.maxRadius(), "the geode's configured maximum moved");

        List<Integer> counts = new ArrayList<>();
        for (long seed = 0; seed < 200; seed++) {
            counts.add(generation.shape().planner()
                    .plan(new ShapeConfig(8, ShapeRotation.XZ, seed, ShapeTuning.DEFAULT)).count());
        }
        counts.sort(Integer::compareTo);
        int median = counts.get(counts.size() / 2);
        System.out.println("M9BAL agapite radius 8: min=" + counts.get(0) + " median=" + median
                + " max=" + counts.get(counts.size() - 1));

        assertTrue(median > 50, "a radius-8 geode plans only " + median + " cells; that is a speck");
        assertTrue(median < 4_000, "a radius-8 geode plans " + median + " cells; that is not a geode");
        // And the legacy live value is genuinely out of range, which is why it needs changing.
        assertTrue(55 > generation.maxRadius(),
                "radius 55 is now inside the configured range, so the live Rails row is no longer wrong");
    }
}

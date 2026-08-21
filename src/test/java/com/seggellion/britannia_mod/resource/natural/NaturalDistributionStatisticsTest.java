package com.seggellion.britannia_mod.resource.natural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.ResourceShape;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapePlan;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import com.seggellion.britannia_mod.resource.shape.ShapeTuning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the shipped silica distribution actually produces, measured rather than asserted by feel.
 *
 * <h2>Why this is a test and not a one-off script</h2>
 * The selector is pure, so its whole distribution can be computed over millions of owner cells with
 * no server, no world and no chunk. That makes the balancing evidence reproducible: the numbers in
 * the milestone report come from this file, and if somebody changes {@code cell_chunks} or
 * {@code chance} the assertions here move with it and say so.
 *
 * <p>Rarity is bounded from both sides on purpose. A lower bound catches a configuration that has
 * quietly stopped generating anything; an upper bound catches silica becoming ubiquitous, which is
 * the failure the milestone brief is most concerned about.
 */
class NaturalDistributionStatisticsTest {

    /** Several seeds, so the configuration is not accidentally tuned to one world. */
    private static final long[] SEEDS = {1L, 987654321L, -4242L, 0x5EEDL};

    /** A square of owner cells big enough for the rates to settle. */
    private static final int CELL_SPAN = 220;

    private static ResourceDefinition silica() {
        return ResourceCatalog.instance().byId("britannia_mod:silica_sand_deposit").orElseThrow();
    }

    private static NaturalGeneration natural() {
        return silica().natural().orElseThrow();
    }

    /* ------------------------------------------------------------------ */
    /*  Occurrence rate                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * The occurrence chance is honoured, and the grid is not biased.
     *
     * <p>Measured over four seeds so a single unlucky world cannot pass it, and bounded tightly
     * enough that a change to {@code chance} shows up here rather than in a live world.
     */
    @Test
    void theConfiguredOccurrenceChanceIsWhatTheGridProduces() {
        NaturalGeneration natural = natural();
        for (long seed : SEEDS) {
            int cells = 0;
            int produced = 0;
            for (int cellX = 0; cellX < CELL_SPAN; cellX++) {
                for (int cellZ = 0; cellZ < CELL_SPAN; cellZ++) {
                    cells++;
                    if (NaturalDepositSelector.candidateFor(seed, silica(), natural, cellX, cellZ)
                            .isPresent()) {
                        produced++;
                    }
                }
            }
            double rate = produced / (double) cells;
            assertTrue(Math.abs(rate - natural.chance()) < 0.02,
                    "seed " + seed + " produced a candidate in " + percent(rate)
                            + " of owner cells against a configured " + percent(natural.chance()));
        }
    }

    /**
     * Silica is regionally uncommon, expressed the way a player would notice it.
     *
     * <p>One deposit per N chunks is the useful normalised measure here: a cell is
     * {@code cell_chunks²} chunks, and only some cells produce anything.
     */
    @Test
    void silicaIsRegionallyUncommonRatherThanUbiquitous() {
        NaturalGeneration natural = natural();
        double chunksPerCell = (double) natural.cellChunks() * natural.cellChunks();
        double chunksPerDeposit = chunksPerCell / natural.chance();

        // These bound the *candidate* rate, which is all a unit test can see. The biome gate then
        // multiplies it: measured against real Overworld noise over three seeds, only 4.4% of
        // candidates land in a biome that allows silica, so the rate a player actually meets is
        // roughly twenty times sparser than the number bounded here.
        assertTrue(chunksPerDeposit > 50,
                "one silica candidate every " + Math.round(chunksPerDeposit)
                        + " chunks is not regionally uncommon even before the biome gate");
        assertTrue(chunksPerDeposit < 500,
                "one silica candidate every " + Math.round(chunksPerDeposit)
                        + " chunks, times a twenty-fold biome rejection, is rare enough that a"
                        + " player would never meet one");
    }

    /* ------------------------------------------------------------------ */
    /*  Spacing                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * No two deposits can overlap, and the grid keeps real ground between them.
     *
     * <p>The margin in the selector is what guarantees this: an origin sits at least {@code margin}
     * inside its own cell, so two neighbouring origins are at least {@code cell - 2*margin} apart,
     * and a deposit reaches at most {@code maxRadius}.
     */
    @Test
    void neighbouringDepositsAreAlwaysClearOfOneAnother() {
        NaturalGeneration natural = natural();
        for (long seed : SEEDS) {
            List<int[]> origins = new ArrayList<>();
            for (int cellX = 0; cellX < 40; cellX++) {
                for (int cellZ = 0; cellZ < 40; cellZ++) {
                    NaturalDepositSelector.candidateFor(seed, silica(), natural, cellX, cellZ)
                            .ifPresent(c -> origins.add(new int[] {c.originX(), c.originZ()}));
                }
            }
            assertTrue(origins.size() > 100, "seed " + seed + " produced too few deposits to measure");

            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < origins.size(); i++) {
                for (int j = i + 1; j < origins.size(); j++) {
                    double dx = origins.get(i)[0] - origins.get(j)[0];
                    double dz = origins.get(i)[1] - origins.get(j)[1];
                    nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz));
                }
            }
            assertTrue(nearest >= natural.cellBlocks() / 2.0,
                    "seed " + seed + " put two deposits " + Math.round(nearest)
                            + " blocks apart, closer than the half-cell (" + (natural.cellBlocks() / 2)
                            + ") the grid guarantees");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Deposit size                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * The planned deposits are meaningful without being enormous.
     *
     * <p>The brief is explicit that thousands of cells per deposit needs economic evidence, and
     * there is none, so this bounds the size from above as well as below and the report says the
     * figure is provisional.
     */
    @Test
    void plannedDepositsAreASensibleSize() {
        NaturalGeneration natural = natural();
        ResourceShape shape = silica().generation().orElseThrow().shape();
        List<Integer> counts = new ArrayList<>();

        for (long seed : SEEDS) {
            for (int cellX = 0; cellX < 30; cellX++) {
                for (int cellZ = 0; cellZ < 30; cellZ++) {
                    NaturalDepositSelector.candidateFor(seed, silica(), natural, cellX, cellZ)
                            .ifPresent(candidate -> {
                                ShapePlan plan = shape.planner().plan(new ShapeConfig(
                                        candidate.radius(), ShapeRotation.XZ,
                                        candidate.plannerSeed(), natural.tuning()));
                                counts.add(plan.count());
                            });
                }
            }
        }
        assertTrue(counts.size() > 200, "not enough deposits to characterise: " + counts.size());
        counts.sort(Integer::compareTo);

        int min = counts.get(0);
        int max = counts.get(counts.size() - 1);
        int median = counts.get(counts.size() / 2);
        double mean = counts.stream().mapToInt(Integer::intValue).average().orElseThrow();

        assertTrue(min >= 200,
                "the smallest planned lens is " + min + " cells, which is barely a deposit");
        assertTrue(max <= 3_000,
                "the largest planned lens is " + max + " cells; that is a scale the economy has not"
                        + " been shown to support");
        assertTrue(median > 400 && median < 2_000,
                "median planned lens is " + median + " cells");
        assertTrue(mean > 400 && mean < 2_000, "mean planned lens is " + mean + " cells");
    }

    /** Radius and thickness stay inside the configured range, every time. */
    @Test
    void everyCandidateStaysInsideItsConfiguredRanges() {
        NaturalGeneration natural = natural();
        for (long seed : SEEDS) {
            for (int cellX = 0; cellX < 60; cellX++) {
                for (int cellZ = 0; cellZ < 60; cellZ++) {
                    NaturalDepositSelector.candidateFor(seed, silica(), natural, cellX, cellZ)
                            .ifPresent(candidate -> {
                                assertTrue(candidate.radius() >= natural.minRadius()
                                                && candidate.radius() <= natural.maxRadius(),
                                        "radius " + candidate.radius() + " outside "
                                                + natural.minRadius() + ".." + natural.maxRadius());
                                assertTrue(candidate.depth() >= natural.depth() - natural.depthJitter()
                                                && candidate.depth() <= natural.depth()
                                                        + natural.depthJitter(),
                                        "depth " + candidate.depth() + " outside its jitter band");
                            });
                }
            }
        }
    }

    /** Every radius in the configured range is actually reachable, so the range means something. */
    @Test
    void theWholeConfiguredRadiusRangeIsUsed() {
        NaturalGeneration natural = natural();
        boolean[] seen = new boolean[natural.maxRadius() + 1];
        for (int cellX = 0; cellX < 80; cellX++) {
            for (int cellZ = 0; cellZ < 80; cellZ++) {
                NaturalDepositSelector.candidateFor(1L, silica(), natural, cellX, cellZ)
                        .ifPresent(candidate -> seen[candidate.radius()] = true);
            }
        }
        for (int radius = natural.minRadius(); radius <= natural.maxRadius(); radius++) {
            assertTrue(seen[radius], "radius " + radius + " never occurs, so the range is a fiction");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Cost                                                               */
    /* ------------------------------------------------------------------ */

    /** A generating chunk considers a handful of owner cells, not a neighbourhood of them. */
    @Test
    void oneChunkConsidersOnlyAFewOwnerCells() {
        int considered = NaturalDepositSelector.cellsConsideredPerChunk(natural());
        assertTrue(considered <= 9,
                "a chunk considers " + considered + " owner cells, which is more work per chunk than"
                        + " this distribution should ever need");
        assertEquals(4, considered,
                "the per-chunk cell window changed; the performance figures in the report are stale");
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }
}

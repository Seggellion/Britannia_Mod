package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.natural.NaturalDepositSelector;
import com.seggellion.britannia_mod.resource.natural.NaturalGeneration;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Locale;

/**
 * Milestone 10A: what natural generation costs a chunk, per resource.
 *
 * <h2>Why this exists</h2>
 * The first world generated with the three metals enabled hung the server. That turned out to be a
 * configuration mistake rather than a platform defect, but the reason it was possible to make is
 * worth pinning: {@code horizontalReach()} is the resource's configured maximum radius, and every
 * chunk within that distance of a deposit's origin plans and registers the whole deposit — even the
 * chunks the deposit does not actually reach.
 *
 * <p>For a compact shape that is fine. For a shape whose radius means something other than
 * horizontal extent — {@code Vertical}, whose radius sets the height of a column barely a dozen
 * blocks wide — a large radius quietly multiplies the work by the square of a distance the deposit
 * never travels. A radius-96 iron column is planned by 169 chunks and lands in about four of them.
 *
 * <p>So the cost is measured per resource and bounded, rather than assumed to be small.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NaturalGenerationCostGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** No chunk may ask more than this many owner cells of any one resource. */
    private static final int MAX_CELLS_PER_CHUNK = 16;

    /**
     * No deposit may be considered by more than this many chunks.
     *
     * <p>{@code (2 * reach / 16 + 1)^2}. A deposit that reaches 96 blocks is considered by 169
     * chunks; one that reaches 16 is considered by 9. The limit is set where the work stops being
     * proportional to the deposit and starts being proportional to the search window.
     */
    private static final int MAX_CHUNKS_PER_DEPOSIT = 49;

    private NaturalGenerationCostGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static int chunksConsidering(NaturalGeneration natural) {
        int perAxis = 2 * natural.horizontalReach() / 16 + 1;
        return perAxis * perAxis;
    }

    /**
     * Every naturally generating resource stays inside its per-chunk work budget.
     *
     * <p>Both numbers are structural — they come from the configuration, not from a timing run — so
     * they mean the same thing on every machine and cannot flake.
     */
    @GameTest(template = TEMPLATE)
    public static void naturalGenerationWorkPerChunkIsBounded(GameTestHelper helper) {
        System.out.println("M10ACOST ---- per-chunk natural generation work ----");
        int totalCells = 0;
        for (ResourceDefinition resource : ResourceCatalog.instance().all()) {
            if (resource.natural().isEmpty()) {
                continue;
            }
            NaturalGeneration natural = resource.natural().orElseThrow();
            int cells = NaturalDepositSelector.cellsConsideredPerChunk(natural);
            int chunks = chunksConsidering(natural);
            totalCells += cells;

            System.out.println(String.format(Locale.ROOT,
                    "M10ACOST %-24s reach=%3d  cell=%3d chunks  owner cells asked per chunk=%2d"
                            + "  chunks considering one deposit=%3d",
                    resource.path(), natural.horizontalReach(), natural.cellChunks(), cells, chunks));

            check(cells <= MAX_CELLS_PER_CHUNK,
                    resource.path() + " asks " + cells + " owner cells of every generated chunk,"
                            + " over the budget of " + MAX_CELLS_PER_CHUNK);
            check(chunks <= MAX_CHUNKS_PER_DEPOSIT,
                    resource.path() + " has a horizontal reach of " + natural.horizontalReach()
                            + ", so " + chunks + " chunks each plan and register the same deposit"
                            + " -- over the budget of " + MAX_CHUNKS_PER_DEPOSIT
                            + ". Reduce the natural maximum radius, or use a shape whose radius"
                            + " means horizontal extent.");
        }
        System.out.println("M10ACOST total owner cells asked of one chunk across all resources: "
                + totalCells);
        check(totalCells <= 4 * MAX_CELLS_PER_CHUNK,
                "a generated chunk asks " + totalCells + " owner cells in total");
        helper.succeed();
    }

    /**
     * The search window is never smaller than the deposits it has to find.
     *
     * <p>The correctness direction. A reach shorter than a deposit's true horizontal extent would
     * leave the outer chunks of a deposit never asking about it, so those cells would simply never
     * be placed — a silently truncated deposit, which is much worse than a generous search.
     *
     * <p>The overshoot is reported rather than asserted, because for {@code Vertical} the radius is
     * a column height and the declared bounds are correspondingly generous. That is the shape's
     * contract and milestone 10A does not change M3 geometry; what it does instead is keep the
     * configured natural radius small enough that the resulting window stays inside the work budget
     * asserted above.
     */
    @GameTest(template = TEMPLATE)
    public static void theSearchWindowIsNeverSmallerThanTheDeposits(GameTestHelper helper) {
        for (ResourceDefinition resource : ResourceCatalog.instance().all()) {
            if (resource.natural().isEmpty()) {
                continue;
            }
            NaturalGeneration natural = resource.natural().orElseThrow();
            var planner = resource.generation().orElseThrow().shape().planner();

            int widest = 0;
            for (long seed = 0; seed < 40; seed++) {
                var plan = planner.plan(new com.seggellion.britannia_mod.resource.shape.ShapeConfig(
                        natural.maxRadius(),
                        com.seggellion.britannia_mod.resource.shape.ShapeRotation.XZ,
                        seed, natural.tuning()));
                for (var offset : plan.offsets()) {
                    widest = Math.max(widest, Math.max(Math.abs(offset.x()), Math.abs(offset.z())));
                }
            }

            System.out.println(String.format(Locale.ROOT,
                    "M10ACOST %-24s reach=%3d  furthest planned cell=%3d  overshoot=%3d blocks",
                    resource.path(), natural.horizontalReach(), widest,
                    natural.horizontalReach() - widest));

            check(natural.horizontalReach() >= widest,
                    resource.path() + " searches only " + natural.horizontalReach()
                            + " blocks out but plans cells " + widest + " blocks from its origin;"
                            + " the outer part of every deposit would never be placed");
        }
        helper.succeed();
    }

    /** Naturally generating resources are exactly the ones intended, and each has its own salt. */
    @GameTest(template = TEMPLATE)
    public static void everyNaturalResourceHasItsOwnSalt(GameTestHelper helper) {
        java.util.Map<Integer, String> bySalt = new java.util.LinkedHashMap<>();
        for (ResourceDefinition resource : ResourceCatalog.instance().all()) {
            if (resource.natural().isEmpty()) {
                continue;
            }
            int salt = resource.natural().orElseThrow().salt();
            String previous = bySalt.put(salt, resource.id());
            check(previous == null,
                    "salt " + salt + " is used by both " + previous + " and " + resource.id()
                            + "; retuning one would reroll the other");
        }
        check(bySalt.size() >= 4, "only " + bySalt.size() + " resources generate naturally");
        System.out.println("M10ACOST salts: " + bySalt);
        helper.succeed();
    }
}

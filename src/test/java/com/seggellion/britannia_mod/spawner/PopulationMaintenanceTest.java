package com.seggellion.britannia_mod.spawner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The bound on one population top-up cycle.
 *
 * <p>These exist because the loop this replaces did not terminate. Both spawn block entities ran
 * {@code while (townNpcIds.size() < townPersonAmount) spawnTownsperson(...);}, and
 * {@code spawnTownsperson} returns without adding anyone whenever no valid position is found, entity
 * creation returns null, or {@code addFreshEntity} refuses. On a spawner that could never place a
 * townsperson, that loop ran forever on the server thread: no exception, no crash, just a world that
 * stopped ticking, with the position search logging as it spun.
 *
 * <p>Every test here asserts a <em>counted</em> number of attempts rather than relying on a test
 * timeout, so the always-fails case proves termination by arithmetic instead of by not hanging the
 * suite. Against the old code the first test would never have returned at all.
 */
final class PopulationMaintenanceTest {

    /** Matches MAX_TOWNSPERSON_SPAWN_ATTEMPTS_PER_CYCLE in both spawn block entities. */
    private static final int BUDGET = 8;

    @Test
    void everyAttemptFailingStillTerminatesWithinBudget() {
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.Outcome outcome =
                PopulationMaintenance.fill(0, 5, BUDGET, () -> {
                    attempts.incrementAndGet();
                    return false;
                });

        assertAll(
                () -> assertEquals(5, attempts.get(),
                        "a shortfall of 5 must cost exactly 5 attempts, not an unbounded number"),
                () -> assertEquals(5, outcome.attempts()),
                () -> assertEquals(0, outcome.successes()),
                () -> assertTrue(outcome.incomplete(0, 5)));
    }

    @Test
    void aShortfallLargerThanTheBudgetIsCappedByTheBudget() {
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.Outcome outcome =
                PopulationMaintenance.fill(0, 500, BUDGET, () -> {
                    attempts.incrementAndGet();
                    return false;
                });

        assertAll(
                () -> assertEquals(BUDGET, attempts.get(),
                        "the per-cycle ceiling, not the shortfall, bounds a failing cycle"),
                () -> assertEquals(BUDGET, outcome.attempts()));
    }

    @Test
    void failedAttemptsSpendBudgetSoTheCeilingHoldsRegardlessOfOutcome() {
        // The property that makes the bound deterministic: the cost of a cycle cannot depend on
        // how hospitable the world happens to be.
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.fill(0, 500, BUDGET, () -> {
            attempts.incrementAndGet();
            return attempts.get() % 2 == 0;
        });

        assertEquals(BUDGET, attempts.get());
    }

    @Test
    void progressIsRetainedWhenEarlyAttemptsFailAndALaterOneSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.Outcome outcome =
                PopulationMaintenance.fill(0, 4, BUDGET, () -> attempts.incrementAndGet() >= 3);

        assertAll(
                () -> assertEquals(4, outcome.attempts()),
                () -> assertEquals(2, outcome.successes(),
                        "attempts 3 and 4 succeeded and must both count"),
                () -> assertTrue(outcome.incomplete(0, 4)));
    }

    @Test
    void aSmallShortfallWithValidPositionsRecoversFullyInOneCycle() {
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.Outcome outcome =
                PopulationMaintenance.fill(1, 4, BUDGET, () -> {
                    attempts.incrementAndGet();
                    return true;
                });

        assertAll(
                () -> assertEquals(3, attempts.get(), "only the shortfall is attempted"),
                () -> assertEquals(3, outcome.successes()),
                () -> assertTrue(!outcome.incomplete(1, 4), "the target is met, so nothing defers"));
    }

    @Test
    void aTypicalTownComplementStillFillsInASingleCycle() {
        // The sibling food-gated spawners use a complement of 4, and the budget is deliberately
        // above that so ordinary cold starts are not slowed by this fix.
        PopulationMaintenance.Outcome outcome = PopulationMaintenance.fill(0, 4, BUDGET, () -> true);

        assertAll(
                () -> assertEquals(4, outcome.successes()),
                () -> assertTrue(!outcome.incomplete(0, 4)));
    }

    @Test
    void anAlreadySatisfiedPopulationAttemptsNothing() {
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.Outcome outcome =
                PopulationMaintenance.fill(4, 4, BUDGET, () -> {
                    attempts.incrementAndGet();
                    return true;
                });

        assertAll(
                () -> assertEquals(0, attempts.get()),
                () -> assertEquals(0, outcome.attempts()),
                () -> assertTrue(!outcome.incomplete(4, 4)));
    }

    @Test
    void anOverfullPopulationAttemptsNothingRatherThanUnderflowing() {
        AtomicInteger attempts = new AtomicInteger();

        PopulationMaintenance.Outcome outcome =
                PopulationMaintenance.fill(9, 4, BUDGET, () -> {
                    attempts.incrementAndGet();
                    return true;
                });

        assertAll(
                () -> assertEquals(0, attempts.get()),
                () -> assertEquals(0, outcome.attempts()));
    }

    @Test
    void consecutiveCyclesKeepMakingProgressOnAShortfallTooLargeForOne() {
        // A shortfall beyond one cycle's budget must not stall: each cycle carries it forward.
        int target = 20;
        int population = 0;
        int cycles = 0;

        while (population < target && cycles < 100) {
            PopulationMaintenance.Outcome outcome =
                    PopulationMaintenance.fill(population, target, BUDGET, () -> true);
            population += outcome.successes();
            cycles++;
        }

        assertEquals(3, cycles, "20 with a budget of 8 should take 8 + 8 + 4");
        assertEquals(target, population, "the shortfall must be fully absorbed across cycles");
    }

    @Test
    void aCycleThatCanNeverSucceedNeverAdvancesButAlwaysReturns() {
        int population = 0;
        for (int cycle = 0; cycle < 50; cycle++) {
            PopulationMaintenance.Outcome outcome =
                    PopulationMaintenance.fill(population, 5, BUDGET, () -> false);
            population += outcome.successes();
            assertEquals(5, outcome.attempts(), "each cycle stays bounded, cycle " + cycle);
        }
        assertEquals(0, population, "nothing was ever placed, and nothing hung either");
    }

    @Test
    void aNonPositiveBudgetIsRejectedRatherThanSilentlyDoingNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> PopulationMaintenance.fill(0, 5, 0, () -> true));
    }
}

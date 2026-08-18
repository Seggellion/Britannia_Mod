package com.seggellion.britannia_mod.spawner;

import java.util.function.BooleanSupplier;

/**
 * Bounds one population top-up cycle to a fixed number of attempts.
 *
 * <h2>Why this exists</h2>
 * Both spawn block entities filled their townsperson shortfall with
 * {@code while (townNpcIds.size() < townPersonAmount) spawnTownsperson(...);}. That loop only
 * terminates if every call makes progress, and {@code spawnTownsperson} has three paths that
 * return without adding anyone: no valid spawn position, entity creation returning null, and
 * {@code addFreshEntity} refusing. Any of those, persisting, spins the server thread forever —
 * no exception, no crash, just a world that stops ticking — and the position search logs as it
 * goes, so the freeze also floods the console.
 *
 * <h2>The rule</h2>
 * A cycle attempts at most {@code min(shortfall, maxAttempts)} spawns. <em>Attempts</em> are
 * budgeted, not successes: a failed attempt spends budget exactly like a successful one, which is
 * what makes the upper bound deterministic no matter what the world looks like. Whatever shortfall
 * is left is simply left, for the next maintenance cycle to pick up. Nothing reschedules itself
 * and nothing retries immediately.
 *
 * <p>Deliberately not one-spawn-per-cycle: with a budget above the usual town size, a cold start
 * still fills in a single cycle, so ordinary population recovery is unchanged. Only the pathological
 * case — a shortfall that cannot be satisfied — is different, and there the change is from "never
 * returns" to "returns having tried a few times".
 */
public final class PopulationMaintenance {
    private PopulationMaintenance() {}

    /**
     * What one cycle did.
     *
     * @param attempts  spawns attempted, never more than the budget
     * @param successes attempts that actually added someone
     */
    public record Outcome(int attempts, int successes) {
        /** True when the cycle ran out of budget or attempts failed, leaving the target unmet. */
        public boolean incomplete(int startingPopulation, int target) {
            return startingPopulation + successes < target;
        }
    }

    /**
     * Runs at most {@code min(target - current, maxAttempts)} attempts.
     *
     * @param current     population right now
     * @param target      population wanted
     * @param maxAttempts hard per-cycle ceiling; must be positive
     * @param attempt     one spawn attempt, returning whether it added anyone
     */
    public static Outcome fill(int current, int target, int maxAttempts, BooleanSupplier attempt) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");

        int shortfall = target - current;
        if (shortfall <= 0) return new Outcome(0, 0);

        int budget = Math.min(shortfall, maxAttempts);
        int successes = 0;
        for (int attempts = 1; attempts <= budget; attempts++) {
            if (attempt.getAsBoolean()) successes++;
        }
        return new Outcome(budget, successes);
    }
}

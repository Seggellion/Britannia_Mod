package com.seggellion.britannia_mod.mining;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Deterministic expected-value calculator for Mining progression (milestone 4).
 *
 * <p>Calls the production {@link MiningSkill#gainChance} directly rather than restating the
 * formula, so the published calibration can never drift from shipped behaviour. Each 0.1 step is
 * an independent geometric trial, so the expected number of activations to cross it is
 * {@code 1 / chance} and the expectation of the whole journey is their sum — exact arithmetic,
 * not a sampled simulation, so there is no flaky Monte Carlo in the balance proof.
 */
final class MiningProgressionCalculator {

    /** Progression bands the design document asks to be reported (§9.3). */
    static final List<double[]> BANDS = List.of(
            new double[] {0.0, 25.0},
            new double[] {25.0, 50.0},
            new double[] {50.0, 65.0},
            new double[] {65.0, 80.0},
            new double[] {80.0, 90.0},
            new double[] {90.0, 99.0},
            new double[] {99.0, 100.0});

    private MiningProgressionCalculator() {
    }

    /**
     * Expected qualifying activations to move Mining from {@code from} to {@code to} while always
     * working the material chosen by {@code challengeAt}.
     */
    static double expectedActivations(double from, double to, DoubleUnaryOperator challengeAt) {
        double total = 0.0;
        int firstTenth = (int) Math.round(from * 10.0);
        int lastTenth = (int) Math.round(to * 10.0);
        for (int tenth = firstTenth; tenth < lastTenth; tenth++) {
            float current = (float) (tenth / 10.0);
            float chance = MiningSkill.gainChance(current, (float) challengeAt.applyAsDouble(current));
            if (chance <= 0.0f) {
                return Double.POSITIVE_INFINITY;
            }
            total += 1.0 / chance;
        }
        return total;
    }

    /**
     * The recommended route: at every skill value, work the hardest resource the gate actually
     * lets you break. Derived from the shipped catalogue, so adding a tier re-derives the route.
     */
    static DoubleUnaryOperator bestAvailableChallenge(MineableCatalog catalog) {
        return current -> catalog.active().stream()
                .map(MineableDefinition::challenge)
                .filter(challenge -> challenge <= current)
                .max(Float::compare)
                .orElse(0.0f);
    }

    /** The floor route: nothing but regular Stone, forever. */
    static DoubleUnaryOperator stoneOnlyChallenge() {
        return current -> 0.0;
    }
}

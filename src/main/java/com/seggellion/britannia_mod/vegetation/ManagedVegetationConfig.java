package com.seggellion.britannia_mod.vegetation;

import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration for weights and randomized managed-vegetation delays.
 *
 * <h2>Why the pace lives in compile-time scales rather than in the stored defaults</h2>
 * This is a {@code ModConfig.Type.SERVER} spec, so the stored values live in
 * {@code <world>/serverconfig/britannia-managed-vegetation.toml} and are written <em>once</em>, when
 * a world first loads. Raising a {@code DEFAULT_*} constant afterwards therefore changes nothing for
 * any world that already exists: the file on disk still holds the old number, and the spec only
 * corrects entries that are missing or out of range. A pace fix expressed purely as a changed
 * default ships, passes its unit tests, and does nothing at all on the live server.
 *
 * <p>So the pace is expressed as a fraction applied to <em>whatever the configuration says</em>:
 * {@link #NATURAL_GROWTH_INTERVAL_SCALE_NUMERATOR}/{@link #NATURAL_GROWTH_INTERVAL_SCALE_DENOMINATOR}
 * for discovery and
 * {@link #GRASS_GROWTH_INTERVAL_SCALE_NUMERATOR}/{@link #GRASS_GROWTH_INTERVAL_SCALE_DENOMINATOR}
 * for the short-to-tall stage. A legacy world holding the Patch 18C numbers, a freshly created world
 * holding today's defaults, and an operator's customised values all pass through the same multiplier
 * and all land on the same effective pace. The stored bases are deliberately left alone for the same
 * reason: doubling the default <em>and</em> the multiplier would quadruple the delay on new worlds
 * while only doubling it on existing ones, which is the exact inconsistency this arrangement exists
 * to prevent.
 *
 * <p>Effective defaults, for both a legacy and a fresh world: discovery {@code 1/24576} per
 * grass-block random tick, and {@code 4800..9600} ticks from short grass to tall grass.
 */
public final class ManagedVegetationConfig {
    public static final int DEFAULT_GRASS_WEIGHT = 75;
    public static final int DEFAULT_FERN_WEIGHT = 20;
    public static final int DEFAULT_FLOWER_WEIGHT = 5;
    public static final boolean DEFAULT_NATURAL_GROWTH_ENABLED = true;
    public static final int DEFAULT_NATURAL_GROWTH_CHANCE_DENOMINATOR = 4_096;
    public static final int DEFAULT_NATURAL_GROWTH_SPEED_DIVISOR = 2;
    public static final int NATURAL_GROWTH_INTERVAL_SCALE_NUMERATOR = 3;
    public static final int NATURAL_GROWTH_INTERVAL_SCALE_DENOMINATOR = 1;
    public static final int GRASS_GROWTH_INTERVAL_SCALE_NUMERATOR = 2;
    public static final int GRASS_GROWTH_INTERVAL_SCALE_DENOMINATOR = 1;
    public static final int DEFAULT_CUT_REGROW_MIN_TICKS = 1_200;
    public static final int DEFAULT_CUT_REGROW_MAX_TICKS = 2_400;
    public static final int DEFAULT_GRASS_GROWTH_MIN_TICKS = 2_400;
    public static final int DEFAULT_GRASS_GROWTH_MAX_TICKS = 4_800;
    public static final int DEFAULT_RETRY_TICKS = 200;
    public static final int DEFAULT_FLOWER_STAGE_TICK_MULTIPLIER = 1_200;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue GRASS_WEIGHT;
    private static final ModConfigSpec.IntValue FERN_WEIGHT;
    private static final ModConfigSpec.IntValue FLOWER_WEIGHT;
    private static final ModConfigSpec.BooleanValue NATURAL_GROWTH_ENABLED;
    private static final ModConfigSpec.IntValue NATURAL_GROWTH_CHANCE_DENOMINATOR;
    private static final ModConfigSpec.IntValue NATURAL_GROWTH_SPEED_DIVISOR;
    private static final ModConfigSpec.IntValue CUT_REGROW_MIN_TICKS;
    private static final ModConfigSpec.IntValue CUT_REGROW_MAX_TICKS;
    private static final ModConfigSpec.IntValue GRASS_GROWTH_MIN_TICKS;
    private static final ModConfigSpec.IntValue GRASS_GROWTH_MAX_TICKS;
    private static final ModConfigSpec.IntValue RETRY_TICKS;
    private static final ModConfigSpec.IntValue FLOWER_STAGE_TICK_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("managedVegetation");
        GRASS_WEIGHT = builder.comment("Relative weight for the short-grass family.")
                .defineInRange("grassWeight", DEFAULT_GRASS_WEIGHT, 1, 1_000_000);
        FERN_WEIGHT = builder.comment("Relative weight for Britannia fern (britannia_mod:fern).")
                .defineInRange("fernWeight", DEFAULT_FERN_WEIGHT, 1, 1_000_000);
        FLOWER_WEIGHT = builder.comment("Relative weight for a random existing UltimaCraft flower (default 5%).")
                .defineInRange("flowerWeight", DEFAULT_FLOWER_WEIGHT, 1, 1_000_000);
        NATURAL_GROWTH_ENABLED = builder.comment(
                "Allow vanilla grass-block random ticks to create managed vegetation in clear air above."
        ).define("naturalGrowthEnabled", DEFAULT_NATURAL_GROWTH_ENABLED);
        NATURAL_GROWTH_CHANCE_DENOMINATOR = builder.comment(
                "Base natural-growth roll denominator retained for existing world compatibility."
        ).defineInRange(
                "naturalGrowthChanceDenominator",
                DEFAULT_NATURAL_GROWTH_CHANCE_DENOMINATOR,
                1,
                1_000_000
        );
        NATURAL_GROWTH_SPEED_DIVISOR = builder.comment(
                "Additional natural-growth slowdown retained for compatibility; 2 gives an effective default 1/24576."
        ).defineInRange(
                "naturalGrowthSpeedDivisor",
                DEFAULT_NATURAL_GROWTH_SPEED_DIVISOR,
                1,
                1_000
        );
        CUT_REGROW_MIN_TICKS = builder.defineInRange(
                "cutRegrowMinTicks", DEFAULT_CUT_REGROW_MIN_TICKS, 1, Integer.MAX_VALUE
        );
        CUT_REGROW_MAX_TICKS = builder.defineInRange(
                "cutRegrowMaxTicks", DEFAULT_CUT_REGROW_MAX_TICKS, 1, Integer.MAX_VALUE
        );
        GRASS_GROWTH_MIN_TICKS = builder.comment(
                "Base short-to-tall grass minimum; the compile-time stage scale doubles it to 4800 effective ticks."
        ).defineInRange(
                "grassGrowthMinTicks", DEFAULT_GRASS_GROWTH_MIN_TICKS, 1, Integer.MAX_VALUE
        );
        GRASS_GROWTH_MAX_TICKS = builder.comment(
                "Base short-to-tall grass maximum; the compile-time stage scale doubles it to 9600 effective ticks."
        ).defineInRange(
                "grassGrowthMaxTicks", DEFAULT_GRASS_GROWTH_MAX_TICKS, 1, Integer.MAX_VALUE
        );
        RETRY_TICKS = builder.defineInRange("retryTicks", DEFAULT_RETRY_TICKS, 1, Integer.MAX_VALUE);
        FLOWER_STAGE_TICK_MULTIPLIER = builder.comment(
                "Game ticks per existing flower growth-profile tick for managed wild flowers."
        ).defineInRange(
                "flowerStageTickMultiplier", DEFAULT_FLOWER_STAGE_TICK_MULTIPLIER, 1, Integer.MAX_VALUE
        );
        builder.pop();
        SPEC = builder.build();
    }

    private ManagedVegetationConfig() {
    }

    public static int grassWeight() {
        return GRASS_WEIGHT.get();
    }

    public static int fernWeight() {
        return FERN_WEIGHT.get();
    }

    public static int flowerWeight() {
        return FLOWER_WEIGHT.get();
    }

    public static boolean naturalGrowthEnabled() {
        return NATURAL_GROWTH_ENABLED.get();
    }

    public static int naturalGrowthChanceDenominator() {
        return NATURAL_GROWTH_CHANCE_DENOMINATOR.get();
    }

    public static int naturalGrowthSpeedDivisor() {
        return NATURAL_GROWTH_SPEED_DIVISOR.get();
    }

    public static long effectiveNaturalGrowthChanceDenominator() {
        return scaledNaturalGrowthChanceDenominator(
                naturalGrowthChanceDenominator(), naturalGrowthSpeedDivisor()
        );
    }

    public static boolean shouldNaturallyGrow(RandomSource random) {
        return naturalGrowthEnabled()
                && oneIn(random, boundedRollDenominator(effectiveNaturalGrowthChanceDenominator()));
    }

    /**
     * The effective denominator as a value {@link RandomSource#nextInt(int)} will actually accept.
     *
     * <p>This used to be {@code Math.toIntExact}, which was safe only because the old scale halved
     * the interval: the widest accepted configuration, {@code 1_000_000 * 1_000}, came to
     * 1.5&nbsp;billion and still fitted an {@code int}. Tripling it instead takes the same
     * configuration to 3&nbsp;billion, so {@code toIntExact} would throw — and it would throw inside
     * a vanilla grass-block random tick, on the server thread, for every grass block in every loaded
     * chunk. A configuration value the spec itself accepts must never be able to do that.
     *
     * <p>Clamping rather than wrapping or throwing is what keeps the failure mode harmless. It only
     * engages above {@link Integer#MAX_VALUE}, where one grass block would need roughly four
     * thousand years to pass the roll either way, so the clamp cannot change any outcome a player or
     * operator could observe; below that bound the configured probability is exact.
     */
    static int boundedRollDenominator(long effectiveDenominator) {
        if (effectiveDenominator <= 0L) {
            throw new IllegalArgumentException(
                    "Natural growth denominator must be positive: " + effectiveDenominator);
        }
        return (int) Math.min(effectiveDenominator, Integer.MAX_VALUE);
    }

    static long scaledNaturalGrowthChanceDenominator(int baseDenominator, int speedDivisor) {
        if (baseDenominator <= 0 || speedDivisor <= 0) {
            throw new IllegalArgumentException("Natural growth denominators must be positive");
        }
        long previousInterval = Math.multiplyExact((long) baseDenominator, speedDivisor);
        long scaledNumerator = Math.multiplyExact(previousInterval, NATURAL_GROWTH_INTERVAL_SCALE_NUMERATOR);
        return (scaledNumerator + NATURAL_GROWTH_INTERVAL_SCALE_DENOMINATOR - 1L)
                / NATURAL_GROWTH_INTERVAL_SCALE_DENOMINATOR;
    }

    static boolean oneIn(RandomSource random, int denominator) {
        if (random == null || denominator <= 0) {
            throw new IllegalArgumentException("Natural growth requires a random source and positive denominator");
        }
        return random.nextInt(denominator) == 0;
    }

    public static int cutRegrowDelay(RandomSource random) {
        int minimum = CUT_REGROW_MIN_TICKS.get();
        return randomBetween(random, minimum, Math.max(minimum, CUT_REGROW_MAX_TICKS.get()));
    }

    /**
     * How long a managed node stays visible as short grass before it becomes tall grass.
     *
     * <p>Read through {@link #scaledGrassGrowthTicks} rather than straight off the spec, so an
     * existing world still holding the Patch 18C base of {@code 2400..4800} and a freshly created
     * world holding the same base both arrive at {@code 4800..9600}.
     */
    public static int grassGrowthDelay(RandomSource random) {
        int minimum = boundedTickCount(effectiveGrassGrowthMinTicks());
        int maximum = boundedTickCount(effectiveGrassGrowthMaxTicks());
        return randomBetween(random, minimum, Math.max(minimum, maximum));
    }

    public static long effectiveGrassGrowthMinTicks() {
        return scaledGrassGrowthTicks(GRASS_GROWTH_MIN_TICKS.get());
    }

    public static long effectiveGrassGrowthMaxTicks() {
        int minimum = GRASS_GROWTH_MIN_TICKS.get();
        return scaledGrassGrowthTicks(Math.max(minimum, GRASS_GROWTH_MAX_TICKS.get()));
    }

    /** The configured stage length with the compile-time stage scale applied. */
    static long scaledGrassGrowthTicks(int configuredTicks) {
        if (configuredTicks <= 0) {
            throw new IllegalArgumentException("Grass growth ticks must be positive: " + configuredTicks);
        }
        long scaledNumerator = Math.multiplyExact(
                (long) configuredTicks, GRASS_GROWTH_INTERVAL_SCALE_NUMERATOR);
        return (scaledNumerator + GRASS_GROWTH_INTERVAL_SCALE_DENOMINATOR - 1L)
                / GRASS_GROWTH_INTERVAL_SCALE_DENOMINATOR;
    }

    /**
     * A scaled stage length as a tick count the delay arithmetic can hold.
     *
     * <p>{@code grassGrowthMaxTicks} accepts {@link Integer#MAX_VALUE}, and doubling that overflows
     * the {@code int} arithmetic in {@link #randomBetween}. Clamping keeps the widest accepted
     * configuration meaning "effectively never" instead of wrapping to a negative delay, which would
     * have made a node permanently overdue and re-dispatched on every tick.
     */
    static int boundedTickCount(long scaledTicks) {
        if (scaledTicks <= 0L) {
            throw new IllegalArgumentException("Scaled tick count must be positive: " + scaledTicks);
        }
        return (int) Math.min(scaledTicks, Integer.MAX_VALUE);
    }

    public static int retryTicks() {
        return RETRY_TICKS.get();
    }

    public static int flowerStageTickMultiplier() {
        return FLOWER_STAGE_TICK_MULTIPLIER.get();
    }

    public static int randomBetween(RandomSource random, int minimum, int maximum) {
        if (minimum <= 0 || maximum < minimum) {
            throw new IllegalArgumentException("Delay range must be positive and ordered");
        }
        if (minimum == maximum) {
            return minimum;
        }
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}

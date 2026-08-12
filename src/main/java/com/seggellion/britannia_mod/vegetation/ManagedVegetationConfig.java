package com.seggellion.britannia_mod.vegetation;

import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration for weights and randomized managed-vegetation delays. */
public final class ManagedVegetationConfig {
    public static final int DEFAULT_GRASS_WEIGHT = 75;
    public static final int DEFAULT_FERN_WEIGHT = 20;
    public static final int DEFAULT_FLOWER_WEIGHT = 5;
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
        FERN_WEIGHT = builder.comment("Relative weight for vanilla fern.")
                .defineInRange("fernWeight", DEFAULT_FERN_WEIGHT, 1, 1_000_000);
        FLOWER_WEIGHT = builder.comment("Relative weight for a random existing UltimaCraft flower (default 5%).")
                .defineInRange("flowerWeight", DEFAULT_FLOWER_WEIGHT, 1, 1_000_000);
        CUT_REGROW_MIN_TICKS = builder.defineInRange(
                "cutRegrowMinTicks", DEFAULT_CUT_REGROW_MIN_TICKS, 1, Integer.MAX_VALUE
        );
        CUT_REGROW_MAX_TICKS = builder.defineInRange(
                "cutRegrowMaxTicks", DEFAULT_CUT_REGROW_MAX_TICKS, 1, Integer.MAX_VALUE
        );
        GRASS_GROWTH_MIN_TICKS = builder.defineInRange(
                "grassGrowthMinTicks", DEFAULT_GRASS_GROWTH_MIN_TICKS, 1, Integer.MAX_VALUE
        );
        GRASS_GROWTH_MAX_TICKS = builder.defineInRange(
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

    public static int cutRegrowDelay(RandomSource random) {
        return randomBetween(random, CUT_REGROW_MIN_TICKS.get(), CUT_REGROW_MAX_TICKS.get());
    }

    public static int grassGrowthDelay(RandomSource random) {
        return randomBetween(random, GRASS_GROWTH_MIN_TICKS.get(), GRASS_GROWTH_MAX_TICKS.get());
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

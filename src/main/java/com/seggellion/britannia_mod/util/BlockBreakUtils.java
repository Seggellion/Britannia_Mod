package com.seggellion.britannia_mod.util;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import java.util.HashMap;
import java.util.Map;

public final class BlockBreakUtils {
    // Example min–max purity values for custom ore types
    private static final Map<String, Double[]> ORE_TYPES = new HashMap<>();
    static {
        ORE_TYPES.put("shadow_iron", new Double[]{1.0, 3.0});
        ORE_TYPES.put("verite", new Double[]{4.0, 6.0});
        ORE_TYPES.put("valorite", new Double[]{6.0, 8.0});
        ORE_TYPES.put("dull_copper", new Double[]{0.5, 2.0});
        ORE_TYPES.put("copper", new Double[]{1.0, 3.0});
        ORE_TYPES.put("tin", new Double[]{2.0, 4.0});
        ORE_TYPES.put("gold", new Double[]{5.0, 7.0});
    }

    private BlockBreakUtils() {}

    /**
     * Deduce the "type" of ore from its BlockState.
     */
    public static String deduceOreType(BlockState state) {
        if (state.is(Blocks.IRON_ORE)) return "Iron ore";
        if (state.is(Blocks.DEEPSLATE_IRON_ORE)) return "Shadow Iron ore";
        if (state.is(Blocks.GOLD_ORE)) return "Gold ore";
        // Add more logic or custom blocks as needed
        return "unknown";
    }

    /**
     * Deduce the "type" of stone from its BlockState.
     */
    public static String deduceStoneType(BlockState state) {
        if (state.is(Blocks.STONE))       return "Cobblestone";
        if (state.is(Blocks.GRAVEL))      return "Gravel";
        if (state.is(Blocks.SANDSTONE))   return "Sandstone";
        if (state.is(Blocks.DIORITE))     return "Diorite";
        if (state.is(Blocks.ANDESITE))    return "Andesite";
        if (state.is(Blocks.CALCITE))     return "Limestone";
        if (state.is(Blocks.GRANITE))     return "Granite";
        if (state.is(Blocks.BLACKSTONE))  return "Blackrock";
        return "Unknown";
    }

    public static int generateStoneGrade() {
        return 1 + RandomSource.create().nextInt(5); // random 1..5
    }

    public static int generateRandomPurity() {
        return 1 + RandomSource.create().nextInt(5); // random 1..5
    }

    public static double generateRandomWeight(double min, double max) {
        return min + RandomSource.create().nextDouble() * (max - min);
    }
}

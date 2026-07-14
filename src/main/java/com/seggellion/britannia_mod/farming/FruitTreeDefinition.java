package com.seggellion.britannia_mod.farming;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Set;
import java.util.function.Supplier;

public record FruitTreeDefinition(
        String id,
        String displayName,
        Supplier<? extends Item> fruitItem,
        Supplier<? extends Item> seedOrSaplingItem,
        Supplier<? extends Block> rootBlock,
        Supplier<? extends Block> trunkBlock,
        Supplier<? extends Block> branchBlock,
        Supplier<? extends Block> leafBlock,
        Supplier<? extends Block> fruitBlock,
        int trunkHeight,
        int maxGrowthStep,
        int branchRadius,
        int canopyRadiusX,
        int canopyRadiusY,
        int canopyRadiusZ,
        float baseFruitDensity,
        float maxFruitDensity,
        int minFruitPerBlock,
        int maxFruitPerBlock,
        float inhospitableTolerance,
        float qualitySensitivity,
        Set<FarmingClimate> idealClimates,
        Set<FarmingClimate> allowedClimates,
        Set<FarmingClimate> forbiddenClimates,
        int minAltitude,
        int maxAltitude
) {
    public int randomFruitYield(net.minecraft.util.RandomSource random) {
        int min = Math.max(1, minFruitPerBlock);
        int max = Math.max(min, maxFruitPerBlock);
        return min + random.nextInt(max - min + 1);
    }
}

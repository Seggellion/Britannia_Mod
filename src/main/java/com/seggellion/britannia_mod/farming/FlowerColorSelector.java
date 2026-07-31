package com.seggellion.britannia_mod.farming;

import net.minecraft.util.RandomSource;

public interface FlowerColorSelector {
    FlowerColor select(
            FlowerDefinition species,
            FlowerPlantingContext context,
            RandomSource random,
            FlowerColorSelectionReason reason
    );
}

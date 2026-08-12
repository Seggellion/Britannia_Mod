package com.seggellion.britannia_mod.vegetation;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One weighted vegetation family and its lifecycle strategy. */
public record ManagedVegetationEntry(
        ResourceLocation id,
        int weight,
        ManagedVegetationGrowthStrategy growthStrategy
) {
    public ManagedVegetationEntry {
        Objects.requireNonNull(id, "Managed vegetation entry ID is required");
        if (weight <= 0) {
            throw new IllegalArgumentException("Managed vegetation entry weight must be positive");
        }
        Objects.requireNonNull(growthStrategy, "Managed vegetation growth strategy is required");
    }
}

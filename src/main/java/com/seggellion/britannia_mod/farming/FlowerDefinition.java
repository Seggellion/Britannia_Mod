package com.seggellion.britannia_mod.farming;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record FlowerDefinition(
        ResourceLocation id,
        ResourceLocation seedItemId,
        ResourceLocation harvestedItemId,
        int naturalMaximumStage,
        int absoluteMaximumStage,
        FlowerGrowthProfile growthProfile,
        List<FlowerPaletteEntry> palette,
        ResourceLocation fallbackColorId,
        FlowerColorLifecycle colorLifecycle
) {
    public FlowerDefinition {
        Objects.requireNonNull(id, "Flower species ID is required");
        Objects.requireNonNull(seedItemId, "Flower seed item ID is required");
        Objects.requireNonNull(harvestedItemId, "Harvested flower item ID is required");
        Objects.requireNonNull(growthProfile, "Flower growth profile is required");
        palette = List.copyOf(Objects.requireNonNull(palette, "Flower palette is required"));
        Objects.requireNonNull(fallbackColorId, "Flower fallback color ID is required");
        Objects.requireNonNull(colorLifecycle, "Flower color lifecycle is required");
        FlowerDefinitionValidator.validateDefinitionValues(
                id, seedItemId, harvestedItemId, naturalMaximumStage, absoluteMaximumStage,
                palette, fallbackColorId, colorLifecycle
        );
    }

    public boolean isPoppy() {
        return id.getNamespace().equals("britannia_mod") && id.getPath().equals("poppy");
    }
}

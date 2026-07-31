package com.seggellion.britannia_mod.farming;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record FlowerPaletteEntry(ResourceLocation colorId, int weight, String rarityLabel) {
    public FlowerPaletteEntry {
        Objects.requireNonNull(colorId, "Palette color ID is required");
        if (weight <= 0) {
            throw new IllegalArgumentException("Palette weight must be positive for " + colorId + ": " + weight);
        }
        rarityLabel = rarityLabel == null ? "" : rarityLabel.trim();
    }
}

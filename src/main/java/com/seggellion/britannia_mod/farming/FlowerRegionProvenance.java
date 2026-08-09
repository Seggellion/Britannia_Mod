package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** Stable planting provenance; current growth climate is resolved from the world separately. */
public record FlowerRegionProvenance(String plantingRegionName, FarmingClimate plantingClimate) {
    public static final String UNKNOWN_REGION = "Unknown";

    public FlowerRegionProvenance {
        plantingRegionName = plantingRegionName == null || plantingRegionName.isBlank()
                ? UNKNOWN_REGION
                : plantingRegionName.trim();
        Objects.requireNonNull(plantingClimate, "Planting climate is required");
        if (!FlowerDefinitionValidator.canonicalClimates().contains(plantingClimate)) {
            throw new IllegalArgumentException("Planting provenance must use a canonical FarmingClimate: " + plantingClimate);
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlantingRegionName", plantingRegionName);
        tag.putString("PlantingClimate", plantingClimate.name());
        return tag;
    }

    public static FlowerRegionProvenance fromTag(CompoundTag tag) {
        try {
            return new FlowerRegionProvenance(
                    tag.getString("PlantingRegionName"),
                    FarmingClimate.valueOf(tag.getString("PlantingClimate"))
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid flower planting-region provenance", exception);
        }
    }
}

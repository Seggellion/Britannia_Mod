package com.seggellion.britannia_mod.farming;

import java.util.Objects;

/** Inputs captured by a future successful planting transaction before block replacement. */
public record FlowerPlantingContext(
        FlowerSoilSnapshot soil,
        FlowerRegionProvenance regionProvenance,
        FarmingClimate currentClimate,
        int altitude,
        FlowerPlantingOrigin origin,
        boolean logicalServer
) {
    public FlowerPlantingContext {
        Objects.requireNonNull(soil, "Flower planting soil is required");
        Objects.requireNonNull(regionProvenance, "Flower planting region provenance is required");
        Objects.requireNonNull(currentClimate, "Flower planting climate is required");
        Objects.requireNonNull(origin, "Flower planting origin is required");
        if (!FlowerDefinitionValidator.canonicalClimates().contains(currentClimate)) {
            throw new IllegalArgumentException("Planting context must use a canonical FarmingClimate: " + currentClimate);
        }
        if (altitude < -64 || altitude > 320) {
            throw new IllegalArgumentException("Planting altitude must be within Minecraft build bounds: " + altitude);
        }
    }
}

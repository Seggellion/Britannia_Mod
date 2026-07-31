package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** NBT-ready identity/state contract for the later FlowerBlockEntity milestone. */
public record FlowerPersistentState(
        int dataVersion,
        ResourceLocation speciesId,
        FlowerColor color,
        int growthStage,
        FlowerPlantingOrigin plantingOrigin,
        boolean protectedFlower,
        Optional<UUID> planterUuid,
        FlowerRegionProvenance regionProvenance,
        FlowerQuality quality,
        FlowerSoilSnapshot soil,
        FlowerGrowthState growthState
) {
    public static final int CURRENT_DATA_VERSION = 1;

    public FlowerPersistentState {
        if (dataVersion < 1 || dataVersion > CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported flower data version: " + dataVersion);
        }
        Objects.requireNonNull(speciesId, "Flower species ID is required");
        Objects.requireNonNull(color, "Flower color is required");
        if (growthStage < 1 || growthStage > 7) {
            throw new IllegalArgumentException("Flower growth stage must be in 1..7: " + growthStage);
        }
        Objects.requireNonNull(plantingOrigin, "Flower planting origin is required");
        planterUuid = Objects.requireNonNull(planterUuid, "Flower planter UUID optional is required");
        Objects.requireNonNull(regionProvenance, "Flower region provenance is required");
        Objects.requireNonNull(quality, "Flower quality is required");
        Objects.requireNonNull(soil, "Flower soil state is required");
        Objects.requireNonNull(growthState, "Flower growth state is required");
        if (plantingOrigin == FlowerPlantingOrigin.ADMIN && !protectedFlower) {
            throw new IllegalArgumentException("Administrator-planted flowers must be protected");
        }
        if (plantingOrigin == FlowerPlantingOrigin.PLAYER && protectedFlower) {
            throw new IllegalArgumentException("Ordinary player-planted flowers cannot be marked administrator-protected");
        }
    }

    public static FlowerPersistentState newlyPlanted(
            FlowerDefinition definition,
            FlowerColor color,
            FlowerPlantingContext context,
            Optional<UUID> planterUuid,
            FlowerRegistry registry
    ) {
        if (!registry.isAllowedColor(definition, color)) {
            throw new IllegalArgumentException("Selected tint " + color.hex() + " is outside the species palette for " + definition.id());
        }
        boolean protectedFlower = context.origin() == FlowerPlantingOrigin.ADMIN;
        return new FlowerPersistentState(
                CURRENT_DATA_VERSION,
                definition.id(),
                color,
                1,
                context.origin(),
                protectedFlower,
                planterUuid,
                context.regionProvenance(),
                FlowerQuality.DEFAULT,
                context.soil(),
                FlowerGrowthState.newlyPlanted()
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", dataVersion);
        tag.putString("SpeciesId", speciesId.toString());
        tag.putInt("ColorTint", color.tintValue());
        tag.putInt("GrowthStage", growthStage);
        tag.putString("PlantingOrigin", plantingOrigin.name());
        tag.putBoolean("Protected", protectedFlower);
        planterUuid.ifPresent(uuid -> tag.putUUID("PlanterUuid", uuid));
        tag.put("RegionProvenance", regionProvenance.toTag());
        tag.putInt("Quality", quality.value());
        tag.put("Soil", soil.toTag());
        tag.put("GrowthState", growthState.toTag());
        return tag;
    }

    /** Deserialization has no selector dependency and therefore cannot reroll color. */
    public static FlowerPersistentState fromTag(CompoundTag tag, FlowerRegistry registry) {
        requireKey(tag, "DataVersion");
        requireKey(tag, "SpeciesId");
        requireKey(tag, "ColorTint");
        requireKey(tag, "GrowthStage");
        requireKey(tag, "PlantingOrigin");
        requireKey(tag, "RegionProvenance");
        requireKey(tag, "Quality");
        requireKey(tag, "Soil");
        requireKey(tag, "GrowthState");

        ResourceLocation speciesId = FlowerDefinitionValidator.parseNamespacedId("Saved flower SpeciesId", tag.getString("SpeciesId"));
        FlowerDefinition definition = registry.byId(speciesId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown saved flower species: " + speciesId));
        int stage = tag.getInt("GrowthStage");
        if (stage > definition.absoluteMaximumStage()) {
            throw new IllegalArgumentException("Saved flower stage " + stage + " exceeds absolute maximum "
                    + definition.absoluteMaximumStage() + " for " + speciesId);
        }
        FlowerPlantingOrigin origin;
        try {
            origin = FlowerPlantingOrigin.valueOf(tag.getString("PlantingOrigin"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown saved flower planting origin: " + tag.getString("PlantingOrigin"), exception);
        }
        return new FlowerPersistentState(
                tag.getInt("DataVersion"),
                speciesId,
                new FlowerColor(tag.getInt("ColorTint")),
                stage,
                origin,
                tag.getBoolean("Protected"),
                tag.hasUUID("PlanterUuid") ? Optional.of(tag.getUUID("PlanterUuid")) : Optional.empty(),
                FlowerRegionProvenance.fromTag(tag.getCompound("RegionProvenance")),
                new FlowerQuality(tag.getInt("Quality")),
                FlowerSoilSnapshot.fromTag(tag.getCompound("Soil")),
                FlowerGrowthState.fromTag(tag.getCompound("GrowthState"))
        );
    }

    private static void requireKey(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            throw new IllegalArgumentException("Saved flower data is missing required field " + key);
        }
    }
}

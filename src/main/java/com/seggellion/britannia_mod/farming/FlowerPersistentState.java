package com.seggellion.britannia_mod.farming;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED_LOAD_WARNINGS = ConcurrentHashMap.newKeySet();

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
        return toTag(true);
    }

    public CompoundTag toClientTag() {
        return toTag(false);
    }

    private CompoundTag toTag(boolean includePlanterUuid) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", dataVersion);
        tag.putString("SpeciesId", speciesId.toString());
        tag.putInt("ColorTint", color.tintValue());
        tag.putInt("GrowthStage", growthStage);
        tag.putString("PlantingOrigin", plantingOrigin.name());
        tag.putBoolean("Protected", protectedFlower);
        if (includePlanterUuid) {
            planterUuid.ifPresent(uuid -> tag.putUUID("PlanterUuid", uuid));
        }
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
        requireKey(tag, "RegionProvenance");
        requireKey(tag, "Quality");
        requireKey(tag, "Soil");
        requireKey(tag, "GrowthState");

        ResourceLocation speciesId = FlowerDefinitionValidator.parseNamespacedId("Saved flower SpeciesId", tag.getString("SpeciesId"));
        FlowerDefinition definition = registry.byId(speciesId).orElse(null);
        if (definition == null) {
            warnOnce("unknown-species:" + speciesId,
                    "[flower persistence] Unknown saved flower species {}; preserving the saved identity in a safe missing-species state",
                    speciesId);
        }
        int savedStage = tag.getInt("GrowthStage");
        int maximumStage = definition == null ? 7 : definition.absoluteMaximumStage();
        int stage = Math.max(1, Math.min(maximumStage, savedStage));
        if (stage != savedStage) {
            warnOnce("stage:" + speciesId + ":" + savedStage,
                    "[flower persistence] Clamped saved stage {} to {} for {}",
                    savedStage, stage, speciesId);
        }
        boolean legacyOriginOrProtection = !tag.contains("PlantingOrigin") || !tag.contains("Protected");
        FlowerPlantingOrigin origin = FlowerPlantingOrigin.PLAYER;
        boolean protectedFlower = false;
        if (!legacyOriginOrProtection) {
            try {
                origin = FlowerPlantingOrigin.valueOf(tag.getString("PlantingOrigin"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown saved flower planting origin: " + tag.getString("PlantingOrigin"), exception);
            }
            protectedFlower = tag.getBoolean("Protected");
        }
        FlowerColor color = FlowerColor.fromSavedTint(tag.getInt("ColorTint"));
        if (!color.isValidTint()) {
            warnOnce("invalid-color:" + speciesId + ":" + color.tintValue(),
                    "[flower persistence] Saved tint {} is not a 24-bit RGB value for {}; preserving it for deterministic fallback rendering",
                    color.tintValue(), speciesId);
        } else if (definition != null && !registry.isAllowedColor(definition, color)) {
            warnOnce("unknown-color:" + speciesId + ":" + color.tintValue(),
                    "[flower persistence] Saved tint {} is outside the current palette for {}; preserving it for deterministic fallback rendering",
                    color.hex(), speciesId);
        }
        return new FlowerPersistentState(
                tag.getInt("DataVersion"),
                speciesId,
                color,
                stage,
                origin,
                protectedFlower,
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

    public FlowerColor visualColor(FlowerRegistry registry) {
        return registry.byId(speciesId)
                .filter(definition -> registry.isAllowedColor(definition, color))
                .map(definition -> color)
                .orElseGet(() -> registry.byId(speciesId)
                        .map(registry::fallbackColor)
                        .orElse(new FlowerColor(0xFFFFFF)));
    }

    /** Returns the same planted identity with only soil simulation state replaced. */
    public FlowerPersistentState withSoil(FlowerSoilSnapshot updatedSoil) {
        return new FlowerPersistentState(
                dataVersion, speciesId, color, growthStage, plantingOrigin, protectedFlower,
                planterUuid, regionProvenance, quality, Objects.requireNonNull(updatedSoil), growthState
        );
    }

    /** Returns the same planted identity with only natural growth state replaced. */
    public FlowerPersistentState withGrowth(int updatedStage, FlowerGrowthState updatedGrowthState) {
        return new FlowerPersistentState(
                dataVersion, speciesId, color, updatedStage, plantingOrigin, protectedFlower,
                planterUuid, regionProvenance, quality, soil, Objects.requireNonNull(updatedGrowthState)
        );
    }

    /** Returns the same planted identity with only authoritative quality replaced. */
    public FlowerPersistentState withQuality(FlowerQuality updatedQuality) {
        return new FlowerPersistentState(
                dataVersion, speciesId, color, growthStage, plantingOrigin, protectedFlower,
                planterUuid, regionProvenance, Objects.requireNonNull(updatedQuality), soil, growthState
        );
    }

    private static void warnOnce(String key, String message, Object... arguments) {
        if (REPORTED_LOAD_WARNINGS.add(key)) {
            LOGGER.warn(message, arguments);
        }
    }
}

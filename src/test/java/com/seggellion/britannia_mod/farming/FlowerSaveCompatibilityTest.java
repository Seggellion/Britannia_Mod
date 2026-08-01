package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerSaveCompatibilityTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final UUID PLANTER = UUID.fromString("0bcaf51e-4ec2-4c2a-b384-19d5f6aa81e5");
    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void everySpeciesStagePaletteAndSoilOriginRoundTripsAtCurrentVersion() {
        for (FlowerDefinition definition : registry.definitions().values()) {
            List<FlowerColor> colors = definition.palette().stream()
                    .map(entry -> registry.color(entry.colorId()).orElseThrow().color())
                    .toList();
            for (int stage = 1; stage <= definition.absoluteMaximumStage(); stage++) {
                for (FlowerColor color : colors) {
                    boolean admin = stage % 2 == 0;
                    boolean community = stage % 3 == 0;
                    FlowerPersistentState original = state(definition, color, stage, admin, community,
                            stage % 4 == 0 ? Optional.empty() : Optional.of(PLANTER));
                    FlowerPersistentState loaded = FlowerPersistentState.fromTag(original.toTag(), registry);
                    assertEquals(original, loaded, definition.id() + " stage " + stage + " " + color.hex());
                    assertEquals(FlowerPersistentState.CURRENT_DATA_VERSION, loaded.dataVersion());
                    assertEquals(color.tintValue(), loaded.color().tintValue());
                }
            }
        }
    }

    @Test
    void blockedMaturePoppyMasteryAndAbsentUuidStatesRemainExact() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        for (FlowerPersistentState original : List.of(
                state(poppy, registry.fallbackColor(poppy), 6, false, false, Optional.of(PLANTER))
                        .withGrowth(6, new FlowerGrowthState(1.0F, 0, false)),
                state(poppy, registry.fallbackColor(poppy), 7, true, true, Optional.empty())
                        .withGrowth(7, new FlowerGrowthState(1.0F, 0, false)),
                state(poppy, registry.fallbackColor(poppy), 3, false, true, Optional.empty())
                        .withGrowth(3, new FlowerGrowthState(0.42F, 17, true))
        )) {
            FlowerPersistentState loaded = FlowerPersistentState.fromTag(original.toTag(), registry);
            assertEquals(original, loaded);
            assertEquals(original.planterUuid(), loaded.planterUuid());
            assertEquals(original.growthState().blocked(), loaded.growthState().blocked());
        }
    }

    @Test
    void legacyMissingOriginProtectionAndUuidUseApprovedDefaults() {
        FlowerDefinition lily = registry.byId(FlowerRegistry.LILY).orElseThrow();
        CompoundTag base = state(lily, registry.fallbackColor(lily), 4, true, false,
                Optional.of(PLANTER)).toTag();

        CompoundTag missingOrigin = base.copy();
        missingOrigin.remove("PlantingOrigin");
        FlowerPersistentState originLoaded = FlowerPersistentState.fromTag(missingOrigin, registry);
        assertEquals(FlowerPlantingOrigin.PLAYER, originLoaded.plantingOrigin());
        assertFalse(originLoaded.protectedFlower());

        CompoundTag missingProtection = base.copy();
        missingProtection.remove("Protected");
        FlowerPersistentState protectionLoaded = FlowerPersistentState.fromTag(missingProtection, registry);
        assertEquals(FlowerPlantingOrigin.PLAYER, protectionLoaded.plantingOrigin());
        assertFalse(protectionLoaded.protectedFlower());

        CompoundTag missingUuid = base.copy();
        missingUuid.remove("PlanterUuid");
        assertTrue(FlowerPersistentState.fromTag(missingUuid, registry).planterUuid().isEmpty());
    }

    @Test
    void unknownSpeciesUnknownPaletteTintAndInvalidRawTintArePreservedWithoutReroll() {
        FlowerDefinition snowdrop = registry.byId(FlowerRegistry.SNOWDROP).orElseThrow();
        CompoundTag base = state(snowdrop, registry.fallbackColor(snowdrop), 5,
                false, false, Optional.of(PLANTER)).toTag();

        CompoundTag unknownSpecies = base.copy();
        ResourceLocation missing = ResourceLocation.parse("britannia_mod:removed_flower_fixture");
        unknownSpecies.putString("SpeciesId", missing.toString());
        FlowerPersistentState speciesLoaded = FlowerPersistentState.fromTag(unknownSpecies, registry);
        assertEquals(missing, speciesLoaded.speciesId());
        assertEquals(0xFFFFFF, speciesLoaded.visualColor(registry).tintValue());
        assertEquals(missing.toString(), speciesLoaded.toTag().getString("SpeciesId"));

        CompoundTag unknownTint = base.copy();
        unknownTint.putInt("ColorTint", 0x010203);
        FlowerPersistentState tintLoaded = FlowerPersistentState.fromTag(unknownTint, registry);
        assertEquals(0x010203, tintLoaded.color().tintValue());
        assertEquals(0x010203, tintLoaded.toTag().getInt("ColorTint"));
        assertEquals(registry.fallbackColor(snowdrop), tintLoaded.visualColor(registry));

        CompoundTag invalidTint = base.copy();
        invalidTint.putInt("ColorTint", -7);
        FlowerPersistentState invalidLoaded = FlowerPersistentState.fromTag(invalidTint, registry);
        assertEquals(-7, invalidLoaded.color().tintValue());
        assertEquals(-7, invalidLoaded.toTag().getInt("ColorTint"));
        assertEquals(registry.fallbackColor(snowdrop), invalidLoaded.visualColor(registry));
    }

    @Test
    void invalidStagesClampDeterministicallyAndUnsupportedVersionsFail() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        CompoundTag base = state(poppy, registry.fallbackColor(poppy), 4,
                false, false, Optional.of(PLANTER)).toTag();

        CompoundTag low = base.copy();
        low.putInt("GrowthStage", -50);
        assertEquals(1, FlowerPersistentState.fromTag(low, registry).growthStage());

        CompoundTag high = base.copy();
        high.putInt("GrowthStage", 99);
        assertEquals(7, FlowerPersistentState.fromTag(high, registry).growthStage());

        CompoundTag versionZero = base.copy();
        versionZero.putInt("DataVersion", 0);
        assertThrows(IllegalArgumentException.class,
                () -> FlowerPersistentState.fromTag(versionZero, registry));

        CompoundTag future = base.copy();
        future.putInt("DataVersion", FlowerPersistentState.CURRENT_DATA_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> FlowerPersistentState.fromTag(future, registry));
    }

    @Test
    void missingRequiredFieldsAndInvalidCommunityRestorationFailClosed() {
        FlowerDefinition campion = registry.byId(FlowerRegistry.CAMPION).orElseThrow();
        CompoundTag base = state(campion, registry.fallbackColor(campion), 2,
                false, true, Optional.of(PLANTER)).toTag();
        for (String field : List.of("DataVersion", "SpeciesId", "ColorTint", "GrowthStage",
                "RegionProvenance", "Quality", "Soil", "GrowthState")) {
            CompoundTag missing = base.copy();
            missing.remove(field);
            assertThrows(IllegalArgumentException.class,
                    () -> FlowerPersistentState.fromTag(missing, registry), field);
        }

        CompoundTag invalidCommunity = base.copy();
        CompoundTag soil = invalidCommunity.getCompound("Soil");
        soil.remove("CommunityRestoration");
        assertThrows(IllegalArgumentException.class,
                () -> FlowerPersistentState.fromTag(invalidCommunity, registry));
    }

    @Test
    void persistenceAndRenderingSourcesContainNoSelectorOrIdentityRewritePath() throws IOException {
        String persistence = source("farming/FlowerPersistentState.java");
        String renderer = source("client/renderer/FlowerBlockEntityRenderer.java");
        assertFalse(persistence.contains("WeightedFlowerColorSelector"));
        assertFalse(renderer.contains("WeightedFlowerColorSelector"));
        assertFalse(renderer.contains("setFlowerState"));
        assertFalse(renderer.contains("saveAdditional"));
        assertTrue(persistence.contains("REPORTED_LOAD_WARNINGS.add(key)"));
        assertTrue(persistence.contains("preserving it for deterministic fallback rendering"));
    }

    private FlowerPersistentState state(
            FlowerDefinition definition,
            FlowerColor color,
            int stage,
            boolean admin,
            boolean community,
            Optional<UUID> planter
    ) {
        FlowerSoilSnapshot soil = community
                ? FlowerSoilSnapshot.communitySoil(4, 2, 0.2F, 0.4F, 0.6F, 0.8F, 42_000L)
                : FlowerSoilSnapshot.privateSoil(3, 1, 0.3F, 0.5F, 0.7F, 0.9F);
        float progress = stage >= definition.naturalMaximumStage() ? 1.0F : stage / 8.0F;
        return new FlowerPersistentState(
                FlowerPersistentState.CURRENT_DATA_VERSION,
                definition.id(), color, stage,
                admin ? FlowerPlantingOrigin.ADMIN : FlowerPlantingOrigin.PLAYER,
                admin, planter,
                new FlowerRegionProvenance("Milestone 9 fixture", definition.growthProfile().idealClimates().iterator().next()),
                new FlowerQuality(40 + stage), soil,
                new FlowerGrowthState(progress, stage * 3, stage == 3)
        );
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8);
    }
}

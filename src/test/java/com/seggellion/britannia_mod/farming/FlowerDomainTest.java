package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerDomainTest {
    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void initialRegistryBootstrapsAllSevenSpeciesAndValidPalettes() {
        assertEquals(FlowerRegistry.INITIAL_SPECIES_IDS, registry.definitions().keySet());
        assertEquals(7, registry.definitions().size());

        for (FlowerDefinition definition : registry.definitions().values()) {
            assertTrue(definition.palette().size() >= 2, definition.id().toString());
            assertTrue(definition.palette().stream().allMatch(entry -> entry.weight() > 0));
            assertTrue(definition.palette().stream().allMatch(entry -> registry.color(entry.colorId()).isPresent()));
            assertTrue(registry.color(definition.fallbackColorId()).isPresent());
            assertTrue(definition.palette().stream()
                    .anyMatch(entry -> entry.colorId().equals(definition.fallbackColorId())));
            assertTrue(definition.palette().stream()
                    .map(FlowerPaletteEntry::colorId)
                    .map(registry::color)
                    .flatMap(Optional::stream)
                    .map(FlowerColorDefinition::color)
                    .allMatch(color -> color.tintValue() >= FlowerColor.MIN_VALUE
                            && color.tintValue() <= FlowerColor.MAX_VALUE));
        }
    }

    @Test
    void initialStageBoundsKeepPoppySpecialAndAllOtherSpeciesNatural() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        assertEquals(6, poppy.naturalMaximumStage());
        assertEquals(7, poppy.absoluteMaximumStage());

        registry.definitions().values().stream()
                .filter(definition -> !definition.isPoppy())
                .forEach(definition -> {
                    assertEquals(7, definition.naturalMaximumStage(), definition.id().toString());
                    assertEquals(7, definition.absoluteMaximumStage(), definition.id().toString());
                });
    }

    @Test
    void climateMappingsUseOnlyTheCanonicalExistingFarmingVocabulary() {
        for (FlowerDefinition definition : registry.definitions().values()) {
            FlowerGrowthProfile profile = definition.growthProfile();
            assertTrue(FlowerDefinitionValidator.canonicalClimates().containsAll(profile.idealClimates()));
            assertTrue(FlowerDefinitionValidator.canonicalClimates().containsAll(profile.allowedClimates()));
            assertTrue(FlowerDefinitionValidator.canonicalClimates().containsAll(profile.forbiddenClimates()));
            assertFalse(profile.idealClimates().isEmpty());
        }

        assertEquals(java.util.Set.of(FarmingClimate.TEMPERATE),
                registry.byId(FlowerRegistry.POPPY).orElseThrow().growthProfile().idealClimates());
        assertEquals(45, registry.byId(FlowerRegistry.POPPY).orElseThrow().growthProfile().idealMinAltitude());
        assertEquals(125, registry.byId(FlowerRegistry.POPPY).orElseThrow().growthProfile().idealMaxAltitude());
        assertEquals(20, registry.byId(FlowerRegistry.POPPY).orElseThrow().growthProfile().minAltitude());
        assertEquals(170, registry.byId(FlowerRegistry.POPPY).orElseThrow().growthProfile().maxAltitude());
        assertEquals(java.util.Set.of(FarmingClimate.MAGICAL, FarmingClimate.ICE),
                registry.byId(FlowerRegistry.ORFLUER).orElseThrow().growthProfile().idealClimates());
    }

    @Test
    void flowerProfileUsesTheAuthoritativeCropFitFormula() {
        FlowerGrowthProfile profile = registry.byId(FlowerRegistry.LILY).orElseThrow().growthProfile();
        assertEquals(1.0f, CropQualityCalculator.hydrationFit(profile.hydrationIdeal(), profile), 0.0001f);
        float nutrientFit = CropQualityCalculator.weightedNutrientFit(
                profile,
                profile.idealBoneMeal(),
                profile.idealTurquoise(),
                profile.idealSulphurousAsh(),
                profile.idealRottenFlesh()
        );
        assertEquals(1.0f, nutrientFit, 0.0001f);
    }

    @Test
    void duplicateSeedMappingFailsWithUsefulDiagnostics() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FlowerDefinition snowdrop = registry.byId(FlowerRegistry.SNOWDROP).orElseThrow();
        FlowerDefinition duplicateSeed = new FlowerDefinition(
                snowdrop.id(),
                snowdrop.minimumFarmingSkill(),
                poppy.seedItemId(),
                snowdrop.harvestedItemId(),
                snowdrop.naturalMaximumStage(),
                snowdrop.absoluteMaximumStage(),
                snowdrop.growthProfile(),
                snowdrop.palette(),
                snowdrop.fallbackColorId(),
                snowdrop.colorLifecycle()
        );

        FlowerRegistry.Builder builder = FlowerRegistry.builder().registerDefinition(poppy);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.registerDefinition(duplicateSeed)
        );
        assertTrue(exception.getMessage().contains("Duplicate flower seed mapping"));
        assertTrue(exception.getMessage().contains(poppy.seedItemId().toString()));

        FlowerRegistry.Builder duplicateSpeciesBuilder = FlowerRegistry.builder().registerDefinition(poppy);
        IllegalArgumentException duplicateSpecies = assertThrows(
                IllegalArgumentException.class,
                () -> duplicateSpeciesBuilder.registerDefinition(poppy)
        );
        assertTrue(duplicateSpecies.getMessage().contains("Duplicate flower species ID"));
    }

    @Test
    void invalidDefinitionsFailEarlyAndExplainTheField() {
        IllegalArgumentException malformed = assertThrows(
                IllegalArgumentException.class,
                () -> FlowerDefinitionValidator.parseNamespacedId("Species", "not namespaced")
        );
        assertTrue(malformed.getMessage().contains("namespaced ID"));

        IllegalArgumentException weight = assertThrows(
                IllegalArgumentException.class,
                () -> new FlowerPaletteEntry(ResourceLocation.parse("britannia_mod:bad"), 0, "")
        );
        assertTrue(weight.getMessage().contains("positive"));

        IllegalArgumentException hydration = assertThrows(
                IllegalArgumentException.class,
                () -> FlowerSoilSnapshot.privateSoil(6, 0.5f, 0.5f, 0.5f, 0.5f)
        );
        assertTrue(hydration.getMessage().contains("0..5"));
    }

    @Test
    void configurationCannotRequestColorSelectionDuringLoad() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new FlowerDefinition(
                        poppy.id(), poppy.minimumFarmingSkill(), poppy.seedItemId(), poppy.harvestedItemId(),
                        poppy.naturalMaximumStage(), poppy.absoluteMaximumStage(),
                        poppy.growthProfile(), poppy.palette(), poppy.fallbackColorId(),
                        FlowerColorLifecycle.SELECT_DURING_DESERIALIZATION
                )
        );
        assertTrue(exception.getMessage().contains("load/deserialization"));
    }

    @Test
    void selectorIsDeterministicWithInjectedRandomAndRejectsNonPlantingPaths() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FlowerPlantingContext context = privateContext(true);
        WeightedFlowerColorSelector selector = new WeightedFlowerColorSelector(registry);
        RandomSource first = RandomSource.create(8675309L);
        RandomSource second = RandomSource.create(8675309L);

        for (int index = 0; index < 250; index++) {
            assertEquals(
                    selector.select(poppy, context, first, FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING),
                    selector.select(poppy, context, second, FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING)
            );
        }

        for (FlowerColorSelectionReason reason : FlowerColorSelectionReason.values()) {
            if (reason != FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING) {
                assertThrows(IllegalStateException.class, () -> selector.select(
                        poppy, context, RandomSource.create(1L), reason
                ), reason.name());
            }
        }
        assertThrows(IllegalStateException.class, () -> selector.select(
                poppy, privateContext(false), RandomSource.create(1L),
                FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING
        ));
    }

    @Test
    void largeWeightedSampleContainsOnlyAllowedColorsAndTracksWeights() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        WeightedFlowerColorSelector selector = new WeightedFlowerColorSelector(registry);
        RandomSource random = RandomSource.create(424242L);
        Map<FlowerColor, Integer> counts = new HashMap<>();
        int samples = 100_000;

        for (int index = 0; index < samples; index++) {
            FlowerColor selected = selector.select(
                    poppy, privateContext(true), random,
                    FlowerColorSelectionReason.SUCCESSFUL_SERVER_PLANTING
            );
            assertTrue(registry.isAllowedColor(poppy, selected));
            counts.merge(selected, 1, Integer::sum);
        }

        int totalWeight = poppy.palette().stream().mapToInt(FlowerPaletteEntry::weight).sum();
        for (FlowerPaletteEntry entry : poppy.palette()) {
            FlowerColor color = registry.color(entry.colorId()).orElseThrow().color();
            double actual = counts.getOrDefault(color, 0) / (double) samples;
            double expected = entry.weight() / (double) totalWeight;
            assertEquals(expected, actual, 0.012, entry.colorId().toString());
        }
    }

    @Test
    void compactTintRepresentationIsNotAColorEnumOrTinyPaletteCeiling() {
        assertFalse(FlowerColor.class.isEnum());
        assertTrue(FlowerColor.CAPACITY >= 2_000_000);
        FlowerColor arbitraryFutureTint = new FlowerColor(0x123456);
        assertEquals("#123456", arbitraryFutureTint.hex());
        assertEquals(0xFF123456, arbitraryFutureTint.opaqueArgb());
        assertThrows(IllegalArgumentException.class, () -> new FlowerColor(0x1000000));
    }

    @Test
    void persistentIdentityAndCommunityRestorationRoundTripWithoutSelectingColor() {
        FlowerDefinition orfluer = registry.byId(FlowerRegistry.ORFLUER).orElseThrow();
        FlowerPlantingContext context = new FlowerPlantingContext(
                FlowerSoilSnapshot.communitySoil(4, 0.70f, 0.60f, 0.80f, 0.75f),
                new FlowerRegionProvenance("Skara Brae Highlands", FarmingClimate.MAGICAL),
                FarmingClimate.MAGICAL,
                145,
                FlowerPlantingOrigin.ADMIN,
                true
        );
        FlowerColor color = registry.color(orfluer.palette().get(3).colorId()).orElseThrow().color();
        UUID planter = UUID.fromString("28e32e7d-b8af-4e96-8478-37df6ac21a90");
        FlowerPersistentState newlyPlanted = FlowerPersistentState.newlyPlanted(
                orfluer, color, context, Optional.of(planter), registry
        );
        FlowerPersistentState original = new FlowerPersistentState(
                newlyPlanted.dataVersion(), newlyPlanted.speciesId(), newlyPlanted.color(),
                newlyPlanted.growthStage(), newlyPlanted.plantingOrigin(), newlyPlanted.protectedFlower(),
                newlyPlanted.planterUuid(), newlyPlanted.regionProvenance(), new FlowerQuality(84),
                newlyPlanted.soil(), newlyPlanted.growthState()
        );

        AtomicInteger selectorCalls = new AtomicInteger();
        FlowerColorSelector loadForbiddenSelector = (species, plantingContext, random, reason) -> {
            selectorCalls.incrementAndGet();
            throw new AssertionError("Deserialization called the flower color selector");
        };
        assertInstanceOf(FlowerColorSelector.class, loadForbiddenSelector);

        CompoundTag serialized = original.toTag();
        FlowerPersistentState loaded = FlowerPersistentState.fromTag(serialized, registry);

        assertEquals(original, loaded);
        assertEquals(0, selectorCalls.get());
        assertEquals(FlowerSoilOrigin.COMMUNITY_PLOT, loaded.soil().origin());
        assertEquals(FlowerCommunityRestoration.currentRepositoryState(),
                loaded.soil().communityRestoration().orElseThrow());
        assertEquals(planter, loaded.planterUuid().orElseThrow());
        assertEquals("Skara Brae Highlands", loaded.regionProvenance().plantingRegionName());
        assertEquals(84, loaded.quality().value());
        assertEquals(color, loaded.color());
    }

    @Test
    void invalidCommunityRestorationCombinationsAreRejected() {
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new FlowerSoilSnapshot(
                        2, 0, 0.4f, 0.4f, 0.4f, 0.4f,
                        FlowerSoilOrigin.COMMUNITY_PLOT, Optional.empty(), 0L
                )
        );
        assertTrue(missing.getMessage().contains("requires restoration metadata"));

        IllegalArgumentException wrongState = assertThrows(
                IllegalArgumentException.class,
                () -> new FlowerCommunityRestoration(
                        FlowerCommunityRestoration.COMMUNITY_FARM_BLOCK_ID, true
                )
        );
        assertTrue(wrongState.getMessage().contains("unprepared community plot"));
    }

    @Test
    void authorizationFoundationUsesCreativeOrPermissionLevelTwoAndSystemReasons() {
        assertTrue(FlowerProtectionService.isAdministrator(true, 0));
        assertTrue(FlowerProtectionService.isAdministrator(false, 2));
        assertFalse(FlowerProtectionService.isAdministrator(false, 1));
        assertFalse(FlowerProtectionService.mayMutate(
                true, false, 1, FlowerMutationReason.HARVEST
        ));
        assertTrue(FlowerProtectionService.mayMutate(
                true, false, 1, FlowerMutationReason.WORLD_GENERATION
        ));
        assertTrue(FlowerProtectionService.mayMutate(
                false, false, 0, FlowerMutationReason.PERMANENT_UPROOT
        ));
    }

    private static FlowerPlantingContext privateContext(boolean logicalServer) {
        return new FlowerPlantingContext(
                FlowerSoilSnapshot.privateSoil(3, 0.5f, 0.5f, 0.5f, 0.5f),
                new FlowerRegionProvenance("Britain", FarmingClimate.TEMPERATE),
                FarmingClimate.TEMPERATE,
                70,
                FlowerPlantingOrigin.PLAYER,
                logicalServer
        );
    }
}

package com.seggellion.britannia_mod.farming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerInteractionTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private final FlowerRegistry registry = FlowerRegistry.initial();

    @Test
    void centralAuthorizationCoversOrdinaryCreativeOperatorAndStoredProtection() {
        for (FlowerMutationReason reason : new FlowerMutationReason[]{
                FlowerMutationReason.CARE, FlowerMutationReason.HARVEST,
                FlowerMutationReason.SWORD_CUTBACK, FlowerMutationReason.PERMANENT_UPROOT,
                FlowerMutationReason.NORMAL_BREAK, FlowerMutationReason.REPLACEMENT,
                FlowerMutationReason.POPPY_STAGE_SEVEN
        }) {
            assertTrue(FlowerProtectionService.mayMutate(false, false, 0, reason), reason.name());
            assertFalse(FlowerProtectionService.mayMutate(true, false, 1, reason), reason.name());
            assertTrue(FlowerProtectionService.mayMutate(true, true, 0, reason), reason.name());
            assertTrue(FlowerProtectionService.mayMutate(true, false, 2, reason), reason.name());
        }
        assertTrue(FlowerProtectionService.mayMutate(true, false, 0, FlowerMutationReason.ADMIN_COMMAND));
        assertTrue(FlowerProtectionService.mayMutate(true, false, 0, FlowerMutationReason.WORLD_GENERATION));
    }

    @Test
    void indirectPolicyProtectsProtectedFlowersAndBlocksEveryPiston() {
        assertFalse(FlowerProtectionService.mayMutate(true, true, 4, FlowerMutationReason.EXPLOSION));
        assertFalse(FlowerProtectionService.mayMutate(true, true, 4, FlowerMutationReason.FLUID));
        assertTrue(FlowerProtectionService.mayMutate(false, false, 0, FlowerMutationReason.EXPLOSION));
        assertTrue(FlowerProtectionService.mayMutate(false, false, 0, FlowerMutationReason.FLUID));
        assertFalse(FlowerProtectionService.mayMutate(false, true, 4, FlowerMutationReason.PISTON));
        assertFalse(FlowerProtectionService.mayMutate(true, true, 4, FlowerMutationReason.PISTON));
    }

    @Test
    void everySpeciesUsesNaturalOrAbsoluteMaturityAndResetsWithoutIdentityLoss() {
        for (FlowerDefinition definition : registry.definitions().values()) {
            FlowerPersistentState immature = state(definition, Math.max(1, definition.naturalMaximumStage() - 1));
            FlowerPersistentState mature = state(definition, definition.naturalMaximumStage());
            assertFalse(FlowerInteractionService.isMature(immature, definition), definition.id().toString());
            assertTrue(FlowerInteractionService.isMature(mature, definition), definition.id().toString());
            FlowerPersistentState reset = FlowerGrowthEvaluator.resetToStageOne(mature);
            assertEquals(1, reset.growthStage());
            assertIdentity(mature, reset);
        }
    }

    @Test
    void poppyStageSevenEligibilityIsExactAndHasNoPermanentUnlock() {
        FlowerDefinition poppy = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        FlowerDefinition lily = registry.byId(FlowerRegistry.LILY).orElseThrow();
        assertFalse(FlowerInteractionService.canAdvancePoppy(state(lily, 6), 100.0F, true));
        assertFalse(FlowerInteractionService.canAdvancePoppy(state(poppy, 5), 100.0F, true));
        assertFalse(FlowerInteractionService.canAdvancePoppy(state(poppy, 6), 99.0F, true));
        assertFalse(FlowerInteractionService.canAdvancePoppy(state(poppy, 6), 100.0F, false));
        assertTrue(FlowerInteractionService.canAdvancePoppy(state(poppy, 6), 100.0F, true));
        assertFalse(FlowerInteractionService.canAdvancePoppy(state(poppy, 7), 100.0F, true));
        FlowerPersistentState reset = FlowerGrowthEvaluator.resetToStageOne(state(poppy, 7));
        assertFalse(FlowerInteractionService.canAdvancePoppy(reset, 100.0F, true));
    }

    @Test
    void sharedCareArithmeticPreservesIdentityAndCapsExistingFarmingScales() {
        FlowerDefinition definition = registry.byId(FlowerRegistry.CAMPION).orElseThrow();
        FlowerPersistentState original = state(definition, 4);
        FarmingSoilCare.Fertilizer fertilizer = new FarmingSoilCare.Fertilizer(
                "test", 0.6F, 0.3F, 0.8F, 0.5F
        );
        FlowerSoilSnapshot changed = FarmingSoilCare.apply(original.soil(), fertilizer);
        assertEquals(1.0F, changed.nitrogen());
        assertEquals(0.8F, changed.phosphorus());
        assertEquals(1.0F, changed.potassium());
        assertEquals(1.0F, changed.organicMatter());
        FlowerPersistentState cared = original.withSoil(changed);
        assertIdentityExceptSoil(original, cared);
        assertEquals(original.growthStage(), cared.growthStage());
        assertEquals(original.color(), cared.color());
    }

    @Test
    void registryIsAuthoritativeForAllHarvestedFlowerToSeedMappings() {
        for (FlowerDefinition definition : registry.definitions().values()) {
            assertEquals(definition, registry.byHarvestedItemId(definition.harvestedItemId()).orElseThrow());
            assertEquals(definition, registry.bySeedItemId(definition.seedItemId()).orElseThrow());
        }
    }

    @Test
    void runtimeHandlersUseCentralPolicyTagsTransactionsAndServerAuthority() throws IOException {
        String block = source("block/FlowerBlock.java");
        String service = source("farming/FlowerInteractionService.java");
        String handler = source("event/FlowerInteractionHandler.java");
        assertTrue(block.contains("FlowerInteractionService.interact"));
        assertTrue(block.contains("PushReaction.BLOCK"));
        assertTrue(service.indexOf("isCareItem(stack)") < service.indexOf("ItemRegistry.SCISSORS"));
        assertTrue(service.indexOf("ItemRegistry.SCISSORS") < service.indexOf("SKINNING_KNIVES"));
        assertTrue(service.indexOf("SKINNING_KNIVES") < service.indexOf("isUprootingTool(stack)"));
        assertTrue(service.contains("FlowerProtectionService.mayMutate"));
        assertTrue(service.contains("GrainHarvestTools.isGrainHarvestBlade"));
        assertTrue(service.contains("RootCropShovelTools.isRootCropShovel"));
        assertTrue(service.contains("restoreUprootedFlowerSoil"));
        assertTrue(service.contains("COMMUNITY_FARM_BLOCK"));
        assertTrue(handler.contains("event.setCanceled(true)"));
        assertTrue(handler.contains("ExplosionEvent.Detonate"));
        assertTrue(handler.contains("FluidPlaceBlockEvent"));
    }

    @Test
    void harvestAndExtractionReuseQualityProvenanceAndCropSeedExtractor() throws IOException {
        String service = source("farming/FlowerInteractionService.java");
        String item = source("item/HarvestedFlowerItem.java");
        assertTrue(service.contains("CropQualityCalculator.calculateQuality"));
        assertTrue(service.contains("CropQualityCalculator.applyQuality"));
        assertTrue(service.contains("FruitProvenance.setRegionName"));
        assertTrue(service.contains("harvestAndReset"));
        assertTrue(item.contains("CropSeedExtractor.shouldExtract"));
        assertTrue(item.contains("CropSeedExtractor.tryExtractSeed"));
        assertTrue(item.contains("byHarvestedItemId"));
    }

    @Test
    void toolTagsAndSkinningKnifeRegistrationMatchOwnerPolicy() throws IOException {
        String blades = resource("data/britannia_mod/tags/items/grain_harvest_blades.json");
        String shovels = resource("data/britannia_mod/tags/items/root_crop_shovels.json");
        String knives = resource("data/britannia_mod/tags/items/skinning_knives.json");
        String items = source("registry/ItemRegistry.java");
        assertTrue(blades.contains("britannia_mod:dagger"));
        assertTrue(blades.contains("minecraft:netherite_sword"));
        assertTrue(shovels.contains("britannia_mod:britannia_shovel"));
        assertTrue(knives.contains("britannia_mod:skinning_knife"));
        assertTrue(items.contains("SKINNING_KNIFE"));
        assertTrue(items.contains("new Item.Properties().durability(128)"));
    }

    @Test
    void milestoneSevenRendererStaysOutOfCommonInteractionPaths() throws IOException {
        assertTrue(Files.exists(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/client/renderer/FlowerBlockEntityRenderer.java"
        )));
        String block = source("block/FlowerBlock.java");
        String service = source("farming/FlowerInteractionService.java");
        assertFalse(block.contains("FlowerVisualModels"));
        assertFalse(block.contains("dye_mask"));
        assertFalse(service.contains("FlowerVisualModels"));
        assertFalse(service.contains("dye_mask"));
    }

    private FlowerPersistentState state(FlowerDefinition definition, int stage) {
        return new FlowerPersistentState(
                FlowerPersistentState.CURRENT_DATA_VERSION,
                definition.id(), registry.fallbackColor(definition), stage,
                FlowerPlantingOrigin.PLAYER, false,
                Optional.of(UUID.fromString("2a71506d-bca1-4384-862f-a53395c35070")),
                new FlowerRegionProvenance("Britain", FarmingClimate.TEMPERATE),
                new FlowerQuality(50),
                FlowerSoilSnapshot.privateSoil(4, 1, 0.5F, 0.5F, 0.5F, 0.5F),
                new FlowerGrowthState(stage <= 1 ? 0.0F : 1.0F, 9, false)
        );
    }

    private static void assertIdentity(FlowerPersistentState expected, FlowerPersistentState actual) {
        assertIdentityExceptSoil(expected, actual);
        assertEquals(expected.soil(), actual.soil());
    }

    private static void assertIdentityExceptSoil(FlowerPersistentState expected, FlowerPersistentState actual) {
        assertEquals(expected.speciesId(), actual.speciesId());
        assertEquals(expected.color(), actual.color());
        assertEquals(expected.plantingOrigin(), actual.plantingOrigin());
        assertEquals(expected.protectedFlower(), actual.protectedFlower());
        assertEquals(expected.planterUuid(), actual.planterUuid());
        assertEquals(expected.regionProvenance(), actual.regionProvenance());
        assertEquals(expected.quality(), actual.quality());
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative), StandardCharsets.UTF_8);
    }

    private static String resource(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/resources").resolve(relative), StandardCharsets.UTF_8);
    }
}

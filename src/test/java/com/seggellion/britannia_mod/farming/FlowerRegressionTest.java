package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerRegressionTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void representativeAnnualPerennialTallTrellisBerryTreeRootGrainAndGrapeContractsRemainDistinct() {
        Map<String, ExpectedCrop> expected = Map.of(
                "carrot", new ExpectedCrop(CropLifecycle.ANNUAL, CropGrowthHabit.GROUND,
                        CropSupportRequirement.NONE, CropHarvestTool.HAND, 0, false, false, false),
                "tomato", new ExpectedCrop(CropLifecycle.TRELLIS, CropGrowthHabit.TRELLIS_CROP,
                        CropSupportRequirement.TRELLIS, CropHarvestTool.SCISSORS, 4, true, false, false),
                "corn", new ExpectedCrop(CropLifecycle.ANNUAL, CropGrowthHabit.TALL_CROP,
                        CropSupportRequirement.NONE, CropHarvestTool.SCISSORS, 0, false, true, false),
                "blueberry", new ExpectedCrop(CropLifecycle.ANNUAL, CropGrowthHabit.GROUND,
                        CropSupportRequirement.NONE, CropHarvestTool.BARE_HAND, 0, false, false, false),
                "apple", new ExpectedCrop(CropLifecycle.TREE, CropGrowthHabit.FRUIT_TREE,
                        CropSupportRequirement.TREE_STRUCTURE, CropHarvestTool.HAND, 0, true, false, true),
                "mandrake", new ExpectedCrop(CropLifecycle.ANNUAL, CropGrowthHabit.GROUND,
                        CropSupportRequirement.NONE, CropHarvestTool.ROOT_SHOVEL, 0, false, false, false),
                "wheat", new ExpectedCrop(CropLifecycle.ANNUAL, CropGrowthHabit.GROUND,
                        CropSupportRequirement.NONE, CropHarvestTool.GRAIN_BLADE, 0, false, false, false),
                "grapes", new ExpectedCrop(CropLifecycle.PERENNIAL, CropGrowthHabit.GRAPE_VINE,
                        CropSupportRequirement.NONE, CropHarvestTool.BARE_HAND, 4, true, false, false)
        );

        expected.forEach((id, contract) -> {
            CropDefinition crop = CropRegistry.byId(id).orElseThrow();
            assertEquals(contract.lifecycle(), crop.lifecycle(), id);
            assertEquals(contract.habit(), crop.growthHabit(), id);
            assertEquals(contract.support(), crop.supportRequirement(), id);
            assertEquals(contract.tool(), crop.harvestTool(), id);
            assertEquals(contract.regrowthAge(), crop.clampedPostHarvestRegrowthAge(), id);
            assertEquals(contract.perennial(), crop.persistsAfterHarvest(), id);
            assertEquals(contract.tall(), crop.tallCrop(), id);
            assertEquals(contract.tree(), crop.treeCrop(), id);
            assertEquals(crop.lifecycle() == CropLifecycle.ANNUAL, crop.clearsOnHarvest(), id);
        });

        assertEquals(4, CropRegistry.byId("hops").orElseThrow().clampedPostHarvestRegrowthAge());
        assertEquals(5, CropRegistry.byId("banana").orElseThrow().clampedPostHarvestRegrowthAge());
        assertFalse(CropRegistry.byId("tomato").orElseThrow().clampedPostHarvestRegrowthAge() == 1);
        assertFalse(CropRegistry.byId("grapes").orElseThrow().clampedPostHarvestRegrowthAge() == 1);
    }

    @Test
    void cropGrowthQualityHydrationAndSeedReturnInputsRemainOnTheirEstablishedScale() {
        CropDefinition wheat = CropRegistry.byId("wheat").orElseThrow();
        CropDefinition mandrake = CropRegistry.byId("mandrake").orElseThrow();

        assertEquals(1.0F, CropQualityCalculator.hydrationFit(wheat.hydrationIdeal(), wheat), 0.0001F);
        assertEquals(1.0F, CropQualityCalculator.weightedNutrientFit(
                wheat, wheat.idealBoneMeal(), wheat.idealTurquoise(),
                wheat.idealSulphurousAsh(), wheat.idealRottenFlesh()), 0.0001F);
        assertEquals(1.0F, CropQualityCalculator.weightedNutrientFit(
                mandrake, 1.0F, 1.0F, 1.0F, 1.0F), 0.0001F);
        assertEquals(0.35F, wheat.seedReturnChance(), 0.0001F);
        assertEquals(6, wheat.minYield());
        assertEquals(6, wheat.maxYield());
        assertTrue(wheat.isMatureAge(wheat.maxGrowthAge()));
        assertFalse(wheat.isMatureAge(wheat.maxGrowthAge() - 1));
    }

    @Test
    void soilWeatherCommunityAndCareConstantsRemainTheExistingFarmingAuthority() {
        assertEquals(5, FarmingBlockEntity.MAX_HYDRATION);
        assertEquals(1_200L, FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS);
        assertEquals(3_600L, CommunityFarmBlockEntity.PREPARED_EXPIRY_TICKS);
        assertEquals(0.10F, FarmingBlock.HYDRATION_DECAY_CHANCE, 0.0001F);
        assertEquals(0, FarmingBlock.hydrationAfterRain(0, false));
        assertEquals(2, FarmingBlock.hydrationAfterRain(0, true));
        assertEquals(4, FarmingBlock.hydrationAfterRain(4, true));
        assertTrue(FarmingBlock.hydrationDecays(3, 0.05F));
        assertFalse(FarmingBlock.hydrationDecays(3, 0.50F));
        assertFalse(FarmingBlock.hydrationDecays(0, 0.00F));
        assertEquals(1.0F, FarmingSoilCare.addNormalized(0.8F, 0.6F), 0.0001F);
    }

    @Test
    void flowerBranchDoesNotEnterOrdinaryPlantingHarvestOrExtractionPaths() throws IOException {
        String farmingBlock = source("block/FarmingBlock.java");
        int care = farmingBlock.indexOf("applyCareItem(");
        int flowers = farmingBlock.indexOf("FlowerPlantingService.tryPlant(");
        int grapes = farmingBlock.indexOf("stack.getItem() instanceof GrapeSeedsItem", flowers);
        int crops = farmingBlock.indexOf("CropRegistry.bySeed", flowers);
        assertTrue(care >= 0 && care < flowers && flowers < grapes && grapes < crops);

        String ordinaryPlanting = between(farmingBlock,
                "public static ItemInteractionResult tryPlantSeed(",
                "public static ItemInteractionResult tryHarvestCrop(");
        String ordinaryHarvest = between(farmingBlock,
                "public static ItemInteractionResult tryHarvestCrop(",
                "public static ItemInteractionResult harvestTallCropFromSegment(");
        assertFalse(ordinaryPlanting.contains("Flower"));
        assertFalse(ordinaryHarvest.contains("Flower"));
        assertTrue(ordinaryHarvest.contains("seedReturnChance"));
        assertTrue(ordinaryHarvest.contains("hurtAndBreak(1"));
        assertTrue(ordinaryHarvest.contains("FruitProvenance.setRegionName"));
        assertTrue(ordinaryHarvest.contains("CropQualityCalculator.calculateQuality"));

        String extractor = source("item/CropSeedExtractor.java");
        String healing = source("item/SeedExtractableHealingCropItem.java");
        String grapesItem = source("item/GrapesItem.java");
        assertFalse(extractor.contains("FlowerRegistry"));
        assertTrue(healing.contains("CropSeedExtractor.tryExtractSeed"));
        assertTrue(grapesItem.contains("CropSeedExtractor.tryExtractSeed"));
        assertTrue(grapesItem.contains("GrapeSeedsItem.setVariety"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from, start);
        return source.substring(from, to);
    }

    private record ExpectedCrop(
            CropLifecycle lifecycle,
            CropGrowthHabit habit,
            CropSupportRequirement support,
            CropHarvestTool tool,
            int regrowthAge,
            boolean perennial,
            boolean tall,
            boolean tree
    ) {
    }
}

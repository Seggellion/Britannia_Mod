package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.renderer.HouseFarmPlotBlockEntityRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseFarmPlotIntegrationTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @Test
    void housePlotUsesExistingCropFlowerAndHoePipelines() throws IOException {
        String block = source("block/HouseFarmPlotBlock.java");
        String entity = source("block/entity/HouseFarmPlotBlockEntity.java");
        String flowerEntity = source("block/entity/FlowerBlockEntity.java");
        String planting = source("farming/FlowerPlantingService.java");
        String harvesting = source("block/FarmingBlock.java");
        String hoe = source("item/FarmingHoeItem.java");
        String handler = source("event/HouseFarmPlotInteractionHandler.java");
        String mod = source("BritanniaMod.java");

        assertTrue(block.contains("class HouseFarmPlotBlock extends FarmingBlock"));
        assertTrue(entity.contains("class HouseFarmPlotBlockEntity extends FlowerBlockEntity"));
        assertTrue(flowerEntity.contains("class FlowerBlockEntity extends FarmingBlockEntity"));
        assertTrue(planting.contains("originalState.getBlock() instanceof HouseFarmPlotBlock"));
        assertTrue(harvesting.contains("persistentHouseAssignment"));
        assertTrue(hoe.contains("plot.clearPersistentAssignment(serverLevel)"));
        assertTrue(handler.contains("TallCropSupport.findAnchor"));
        assertTrue(handler.contains("OrangeTreeUtils.findRoot"));
        assertTrue(mod.contains("new HouseFarmPlotInteractionHandler()"));
        assertTrue(entity.contains("FlowerRegistry.POPPY"));
        assertTrue(entity.contains("HouseFarmPlotAssignment.uninitialized()"));
        assertTrue(entity.contains("HouseFarmPlotAssignment.cleared()"));
    }

    @Test
    void rendererAnchorMatchesMeasuredSoilWithoutDuplicatingPlantModels() throws IOException {
        Path model = PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/block/structure/house_farm_plot_soil.json"
        );
        JsonObject json = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
        JsonArray top = json.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to");

        assertEquals(14.0D, top.get(1).getAsDouble());
        assertEquals(14.0D / 16.0D, HouseFarmPlotBlockEntityRenderer.SOIL_SURFACE_Y);
        assertEquals(15.0D / 16.0D, HouseFarmPlotBlockEntityRenderer.NON_TALL_CROP_RENDER_Y);

        Path models = PROJECT.resolve("src/main/resources/assets/britannia_mod/models/block");
        try (var paths = Files.walk(models)) {
            assertFalse(paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.contains("house_plot_stage") || name.contains("house_crop")));
        }
    }

    @Test
    void rendererLightsPlantsFromTheExposedSurfaceInsteadOfInsideTheOpaquePlot() throws IOException {
        String renderer = source("client/renderer/HouseFarmPlotBlockEntityRenderer.java");

        assertTrue(renderer.contains("sampleSurfaceLight(entity, packedLight)"));
        assertTrue(renderer.contains("entity.getBlockPos().above()"));
        assertTrue(renderer.contains("LevelRenderer.getLightColor(level, surfacePos)"));
        assertTrue(renderer.contains("LightTexture.pack("));
        assertTrue(renderer.contains("surfaceLight, packedOverlay, SOIL_SURFACE_Y"));
        assertTrue(renderer.contains("surfaceLight, packedOverlay, NON_TALL_CROP_RENDER_Y"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative
        ));
    }
}

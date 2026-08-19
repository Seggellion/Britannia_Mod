package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the properties that make the grape arbor one plant rather than three.
 *
 * <p>The arbor is a single model drawn by the plot's block entity renderer while the three blocks
 * above the plot are occupancy-only segments. The failure this protects against is subtle and
 * entirely silent: give those segments a drawable kind and the arbor renders once per block, which
 * looks like a rendering glitch rather than a data error and would never fail a model-path check.
 */
class GrapeArborArchitectureTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MAIN = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrapeVarietyManager.loadFromBootstrap(varietyPerColor());
    }

    @Test
    void theArborReservesItsPlotAndTheThreeBlocksAboveIt() {
        CropDefinition grapes = grapeCrop();
        assertTrue(grapes.tallCrop(), "the arbor must reserve the blocks it visually fills");
        assertEquals(4, grapes.maxHeight());
        assertEquals(3, TallCropSupport.maxUpperSegmentCount(grapes));
        assertTrue(TallCropSupport.isGrapeArbor(grapes));
    }

    @Test
    void occupancyAppearsOnlyOnceTheArborIsFruiting() {
        CropDefinition grapes = grapeCrop();
        for (int stage = 0; stage <= 5; stage++) {
            assertEquals(0, TallCropSupport.upperSegmentCountForStage(grapes, stage),
                "a young vine must not reserve blocks it does not yet fill, at stage " + stage);
            assertEquals(1, TallCropSupport.heightForStage(grapes, stage));
        }
        for (int stage = 6; stage <= grapes.maxGrowthAge(); stage++) {
            assertEquals(3, TallCropSupport.upperSegmentCountForStage(grapes, stage),
                "the fruiting arbor must reserve all three blocks above its plot, at stage " + stage);
            assertEquals(4, TallCropSupport.heightForStage(grapes, stage));
        }
    }

    /**
     * The arbor's occupancy is its own block rather than a corn stalk variant, because corn is
     * registered walk-through and an arbor must not be. This also keeps the segments from drawing.
     */
    @Test
    void arborOccupancyIsItsOwnSolidInvisibleBlock() throws Exception {
        String arbor = Files.readString(MAIN.resolve("block/GrapeArborBlock.java"), StandardCharsets.UTF_8);
        assertTrue(arbor.contains("RenderShape.INVISIBLE"),
            "the occupancy block must draw nothing, or the arbor renders once per block");
        assertTrue(arbor.contains("getCollisionShape"), "the occupancy block must define collision");

        String registry = Files.readString(
            PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"),
            StandardCharsets.UTF_8);
        int arborAt = registry.indexOf("GRAPE_ARBOR_BLOCK");
        assertTrue(arborAt > 0, "the arbor occupancy block must be registered");
        // The defect this replaces: corn's noCollission() left the arbor with nothing to walk into.
        assertFalse(registry.substring(arborAt, arborAt + 400).contains("noCollission"),
            "the arbor occupancy block must not be registered without collision");

        String blockstate = Files.readString(
            PROJECT.resolve("src/main/resources/assets/britannia_mod/blockstates/grape_arbor_block.json"),
            StandardCharsets.UTF_8);
        assertFalse(blockstate.contains("model"), "the arbor occupancy blockstate must reference no model");
    }

    @Test
    void onlyThePlotRendersTheArbor() throws Exception {
        String renderer = Files.readString(
            MAIN.resolve("client/renderer/FarmingBlockEntityRenderer.java"), StandardCharsets.UTF_8);
        String segment = Files.readString(MAIN.resolve("block/GrapeArborBlock.java"), StandardCharsets.UTF_8);
        assertFalse(segment.contains("GrapeVisualResolver"),
            "the occupancy segment must never resolve an arbor model of its own");
        assertTrue(renderer.contains("CropVisualModels.modelAnchorYOffset"),
            "the plot renderer is the single place the arbor is drawn and anchored");
    }

    /**
     * Checks the arbor's real geometry against where the renderer puts it, because "sunk into the
     * soil" and "hovering" are the two ways this can look wrong and neither shows up as an error.
     * Measured from the model files rather than restated, so re-authoring the arbor is caught here.
     */
    @Test
    void theArborRestsOnTheSoilAndStaysInsideTheBlocksItReserves() throws IOException {
        CropDefinition grapes = grapeCrop();
        assertEquals(2.0D, CropVisualModels.modelAnchorYOffset(grapes), 1.0e-9);
        assertEquals(0.0D, CropVisualModels.modelAnchorYOffset(CropRegistry.byId("wheat").orElseThrow()), 1.0e-9);

        double anchor = CropVisualModels.modelAnchorYOffset(grapes);
        double reservedTop = 1.0D + TallCropSupport.maxUpperSegmentCount(grapes);

        for (ResourceLocation model : GrapeVisualResolver.allModelLocations()) {
            JsonObject json = JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/resources/assets/britannia_mod/models/" + model.getPath() + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();

            double lowest = Double.MAX_VALUE;
            double highest = -Double.MAX_VALUE;
            for (JsonElement element : json.getAsJsonArray("elements")) {
                JsonObject cuboid = element.getAsJsonObject();
                lowest = Math.min(lowest, cuboid.getAsJsonArray("from").get(1).getAsDouble() / 16.0D);
                highest = Math.max(highest, cuboid.getAsJsonArray("to").get(1).getAsDouble() / 16.0D);
            }

            double bottomInWorld = anchor + lowest;
            double topInWorld = anchor + highest;
            assertTrue(bottomInWorld >= 1.0D - 1.0e-6,
                model + " sinks into the soil: its base lands at " + bottomInWorld);
            assertTrue(topInWorld <= reservedTop + 1.0e-6,
                model + " grows past the blocks the arbor reserves: top at " + topInWorld);
        }
    }

    @Test
    void everyVarietyKeepsItsColourAcrossTheWholeGrowthRange() {
        CropDefinition grapes = grapeCrop();
        Map<GrapeColor, String> varieties = varietyIdsByColor();
        assertEquals(8, varieties.size());

        for (Map.Entry<GrapeColor, String> entry : varieties.entrySet()) {
            String ripe = GrapeVisualResolver.modelLocation(grapes, 7, entry.getValue()).toString();
            assertTrue(ripe.endsWith("_" + entry.getKey().getSerializedName()),
                "ripe arbor must use its own colour: " + ripe);
            // Regrowth after harvest drops to a fruitless stage; the variety must survive that.
            assertEquals(
                GrapeVisualResolver.modelLocation(grapes, 4, entry.getValue()).toString(),
                GrapeVisualResolver.modelLocation(grapes, grapes.clampedPostHarvestRegrowthAge(), entry.getValue())
                    .toString());
        }

        assertNotEquals(
            GrapeVisualResolver.modelLocation(grapes, 7, varieties.get(GrapeColor.RED)).toString(),
            GrapeVisualResolver.modelLocation(grapes, 7, varieties.get(GrapeColor.BLUE)).toString());
    }

    private static CropDefinition grapeCrop() {
        return CropRegistry.byId("grapes").orElseThrow(() -> new AssertionError("grapes crop is not registered"));
    }

    private static Map<GrapeColor, String> varietyIdsByColor() {
        Map<GrapeColor, String> byColor = new EnumMap<>(GrapeColor.class);
        for (GrapeColor color : GrapeColor.values()) {
            byColor.put(color, "test_" + color.getSerializedName());
        }
        return byColor;
    }

    private static List<GrapeVariety> varietyPerColor() {
        List<GrapeVariety> varieties = new ArrayList<>();
        varietyIdsByColor().forEach((color, id) -> varieties.add(new GrapeVariety(
            id, "Test " + color.getSerializedName(), 3,
            0.5f, 0.5f, 0.5f, 0.5f,
            "Temperate", 60, 100,
            0x000000, 1, color)));
        return varieties;
    }
}

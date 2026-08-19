package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The grape vine visuals are Fabric-authored models selected from the crop's stored variety, so the
 * failure mode they invite is a silent one: a colour whose model or texture was never carried across
 * still resolves to a path, and the client shows the missing-texture checkerboard rather than
 * throwing. These assertions walk the same paths {@link GrapeVisualResolver} produces at runtime and
 * require the file behind each of them to exist.
 */
class GrapeVineAssetContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    /** Minecraft rejects block model elements outside this range, which the Fabric models sit near. */
    private static final double MIN_ELEMENT_COORDINATE = -16.0D;
    private static final double MAX_ELEMENT_COORDINATE = 32.0D;

    /** Required before {@code CropRegistry} can bind its deferred item holders. */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrapeVarietyManager.loadFromBootstrap(varietyPerColor());
    }

    @Test
    void everyColourAndAgeResolvesToAModelThatExists() {
        CropDefinition grapes = grapeCrop();
        for (Map.Entry<GrapeColor, String> entry : varietyIdsByColor().entrySet()) {
            for (int age = 0; age <= grapes.maxGrowthAge(); age++) {
                ResourceLocation model = GrapeVisualResolver.modelLocation(grapes, age, entry.getValue());
                assertTrue(
                        Files.isRegularFile(modelFile(model)),
                        "missing grape model for colour " + entry.getKey() + " at age " + age + ": " + model);
            }
        }
    }

    @Test
    void ageMapsToTheFabricVisualStageAndOnlyRipeStagesCarryColour() {
        CropDefinition grapes = grapeCrop();
        // Ages 0 and 1 deliberately share the seedling stage, so the vine's first visible growth
        // step lands on age 2 rather than on planting.
        List<String> expectedByAge = List.of(
                "grape_vine_stage_1",
                "grape_vine_stage_1",
                "grape_vine_stage_2",
                "grape_vine_stage_3",
                "grape_vine_stage_4",
                "grape_vine_stage_5",
                "grape_vine_stage_6_red",
                "grape_vine_stage_7_red");
        assertEquals(grapes.maxGrowthAge() + 1, expectedByAge.size());

        String redVariety = varietyIdsByColor().get(GrapeColor.RED);
        for (int age = 0; age < expectedByAge.size(); age++) {
            assertEquals(
                    "britannia_mod:block/crops/grapes/" + expectedByAge.get(age),
                    GrapeVisualResolver.modelLocation(grapes, age, redVariety).toString(),
                    "unexpected visual stage at age " + age);
        }
    }

    @Test
    void unripeStagesLookIdenticalRegardlessOfColourAndRipeStagesNeverDo() {
        CropDefinition grapes = grapeCrop();
        Map<GrapeColor, String> varieties = varietyIdsByColor();

        for (int age = 0; age <= 5; age++) {
            Set<String> distinct = new LinkedHashSet<>();
            for (String varietyId : varieties.values()) {
                distinct.add(GrapeVisualResolver.modelLocation(grapes, age, varietyId).toString());
            }
            assertEquals(1, distinct.size(), "fruitless age " + age + " must not vary by colour: " + distinct);
        }

        for (int age = 6; age <= grapes.maxGrowthAge(); age++) {
            Set<String> distinct = new LinkedHashSet<>();
            for (String varietyId : varieties.values()) {
                distinct.add(GrapeVisualResolver.modelLocation(grapes, age, varietyId).toString());
            }
            assertEquals(
                    GrapeColor.values().length,
                    distinct.size(),
                    "ripe age " + age + " must give each of the eight colours its own model");
        }
    }

    @Test
    void modelSelectionIsAFunctionOfStoredVarietyAlone() {
        CropDefinition grapes = grapeCrop();
        String varietyId = varietyIdsByColor().get(GrapeColor.BLUE);
        ResourceLocation first = GrapeVisualResolver.modelLocation(grapes, 7, varietyId);
        for (int repeat = 0; repeat < 32; repeat++) {
            assertEquals(first, GrapeVisualResolver.modelLocation(grapes, 7, varietyId));
        }
    }

    @Test
    void registeredStandaloneModelsCoverEveryResolvableModel() {
        CropDefinition grapes = grapeCrop();
        Set<String> registered = new LinkedHashSet<>();
        for (ResourceLocation model : GrapeVisualResolver.allModelLocations()) {
            registered.add(model.toString());
        }

        for (String varietyId : varietyIdsByColor().values()) {
            for (int age = 0; age <= grapes.maxGrowthAge(); age++) {
                String model = GrapeVisualResolver.modelLocation(grapes, age, varietyId).toString();
                assertTrue(registered.contains(model), "resolver can produce an unregistered model: " + model);
            }
        }

        // Five fruitless stages plus eight colours across the two ripe stages.
        assertEquals(5 + 2 * GrapeColor.values().length, registered.size());
    }

    @Test
    void everyModelResolvesItsTexturesAndStaysInsideMinecraftsElementRange() throws IOException {
        List<ResourceLocation> models = GrapeVisualResolver.allModelLocations();
        assertFalse(models.isEmpty());

        for (ResourceLocation model : models) {
            Path file = modelFile(model);
            assertTrue(Files.isRegularFile(file), "missing model file: " + model);
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

            JsonObject textures = json.getAsJsonObject("textures");
            assertNotNull(textures, "model declares no textures: " + model);
            Set<String> declaredKeys = new LinkedHashSet<>(textures.keySet());
            for (String key : declaredKeys) {
                String reference = textures.get(key).getAsString();
                assertTrue(
                        Files.isRegularFile(textureFile(reference)),
                        "model " + model + " references a texture that does not exist: " + reference);
            }

            for (JsonElement element : json.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                assertCoordinatesInRange(model, object.getAsJsonArray("from"));
                assertCoordinatesInRange(model, object.getAsJsonArray("to"));

                JsonObject faces = object.getAsJsonObject("faces");
                for (String faceName : faces.keySet()) {
                    String texture = faces.getAsJsonObject(faceName).get("texture").getAsString();
                    assertTrue(
                            declaredKeys.contains(texture.substring(1)),
                            "model " + model + " face " + faceName + " uses undeclared texture " + texture);
                }
            }
        }
    }

    @Test
    void supersededGrapeModelsAreGoneAndTheirReplacementsAreNot() {
        Path crops = ASSETS.resolve("models/block/crops");
        for (int age = 0; age <= 7; age++) {
            Path placeholder = crops.resolve("grapes/grapes_age_" + age + ".json");
            assertFalse(Files.exists(placeholder), "superseded placeholder still present: " + placeholder);
        }
        assertTrue(Files.isRegularFile(crops.resolve("grapes/grape_vine_stage_7_purple.json")));
    }

    private static void assertCoordinatesInRange(ResourceLocation model, JsonArray coordinates) {
        for (JsonElement coordinate : coordinates) {
            double value = coordinate.getAsDouble();
            assertTrue(
                    value >= MIN_ELEMENT_COORDINATE && value <= MAX_ELEMENT_COORDINATE,
                    "model " + model + " has an element coordinate Minecraft will reject: " + value);
        }
    }

    private static Path modelFile(ResourceLocation model) {
        return ASSETS.resolve("models/" + model.getPath() + ".json");
    }

    private static Path textureFile(String reference) {
        return ASSETS.resolve("textures/" + reference.substring(reference.indexOf(':') + 1) + ".png");
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
                id,
                "Test " + color.getSerializedName(),
                3,
                0.5f, 0.5f, 0.5f, 0.5f,
                "Temperate", 60, 100,
                0x000000, 1, color)));
        return varieties;
    }
}

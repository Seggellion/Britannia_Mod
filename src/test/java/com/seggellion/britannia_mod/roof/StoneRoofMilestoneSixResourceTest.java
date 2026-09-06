package com.seggellion.britannia_mod.roof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoneRoofMilestoneSixResourceTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve(
            "src/main/resources/assets/britannia_mod");
    private static final Path DATA = PROJECT.resolve("src/main/resources/data");
    private static final Path ROOF_MODELS = ASSETS.resolve("models/block/structure/roof");

    private static final List<Material> MATERIALS = List.of(
            new Material(
                    "slate_roof_flat",
                    "slate_roof",
                    "Slate Roof",
                    List.of(
                            "slate_roof_flat",
                            "slate_roof_1_flat",
                            "slate_roof_2_flat",
                            "slate_roof_flat",
                            "slate_roof_1_flat",
                            "slate_roof_2_flat")),
            new Material(
                    "sandstone_roof",
                    "sandstone_roof",
                    "Sandstone Roof",
                    numberedTextures("sandstone_roof")),
            new Material(
                    "limestone_roof",
                    "limestone_roof",
                    "Limestone Roof",
                    numberedTextures("limestone_roof")));

    @Test
    void sharedParentIsOneTopHalfGeometryWithSixTexturedFaces() throws Exception {
        JsonObject parent = json(ROOF_MODELS.resolve("top_only_slab.json"));
        assertEquals("minecraft:block/block", parent.get("parent").getAsString());
        assertEquals("#roof", parent.getAsJsonObject("textures")
                .get("particle").getAsString());

        JsonArray elements = parent.getAsJsonArray("elements");
        assertEquals(1, elements.size());
        JsonObject element = elements.get(0).getAsJsonObject();
        assertEquals(List.of(0, 8, 0), ints(element.getAsJsonArray("from")));
        assertEquals(List.of(16, 16, 16), ints(element.getAsJsonArray("to")));
        JsonObject faces = element.getAsJsonObject("faces");
        assertEquals(6, faces.size());
        for (String face : List.of("down", "up", "north", "south", "west", "east")) {
            assertEquals("#roof", faces.getAsJsonObject(face).get("texture").getAsString());
            assertEquals(face, faces.getAsJsonObject(face).get("cullface").getAsString());
        }
    }

    @Test
    void everyMaterialMapsAllInheritedTypesAndSixVariationsDeterministically()
            throws Exception {
        for (Material material : MATERIALS) {
            JsonObject variants = json(ASSETS.resolve(
                    "blockstates/" + material.id() + ".json"))
                    .getAsJsonObject("variants");
            assertEquals(18, variants.size(), material.id());

            for (int variation = 0; variation < 6; variation++) {
                String expectedModel = "britannia_mod:block/structure/roof/"
                        + material.modelStem() + "_" + (variation + 1);
                for (String type : List.of("bottom", "top", "double")) {
                    JsonElement selector = variants.get(
                            "type=" + type + ",variation=" + variation);
                    assertTrue(selector != null && selector.isJsonObject(),
                            material.id() + " missing deterministic " + type
                                    + " variation " + variation);
                    assertEquals(expectedModel,
                            selector.getAsJsonObject().get("model").getAsString());
                    assertFalse(expectedModel.endsWith("_double"));
                }
            }
        }
    }

    @Test
    void everyReferencedModelTextureAndCanonicalItemModelExists() throws Exception {
        for (Material material : MATERIALS) {
            for (int variation = 0; variation < 6; variation++) {
                Path modelPath = ROOF_MODELS.resolve(
                        material.modelStem() + "_" + (variation + 1) + ".json");
                JsonObject model = json(modelPath);
                assertEquals("britannia_mod:block/structure/roof/top_only_slab",
                        model.get("parent").getAsString());
                String expectedTexture = "britannia_mod:block/roof/"
                        + material.textures().get(variation);
                assertEquals(expectedTexture,
                        model.getAsJsonObject("textures").get("roof").getAsString());
                assertTrue(Files.isRegularFile(ASSETS.resolve(
                        "textures/block/roof/" + material.textures().get(variation) + ".png")),
                        expectedTexture);
            }

            JsonObject item = json(ASSETS.resolve(
                    "models/item/" + material.id() + ".json"));
            assertEquals("britannia_mod:block/structure/roof/"
                            + material.modelStem() + "_1",
                    item.get("parent").getAsString());
        }
    }

    @Test
    void canonicalLootMiningLocalizationAndNoRecipeContractsAreComplete()
            throws Exception {
        JsonArray pickaxe = json(DATA.resolve(
                "minecraft/tags/block/mineable/pickaxe.json")).getAsJsonArray("values");
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));

        for (Material material : MATERIALS) {
            String id = "britannia_mod:" + material.id();
            JsonObject loot = json(DATA.resolve(
                    "britannia_mod/loot_table/blocks/" + material.id() + ".json"));
            assertEquals("minecraft:block", loot.get("type").getAsString());
            assertEquals("britannia_mod:blocks/" + material.id(),
                    loot.get("random_sequence").getAsString());
            JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            assertEquals(id, pool.getAsJsonArray("entries").get(0).getAsJsonObject()
                    .get("name").getAsString());
            assertEquals("minecraft:survives_explosion",
                    pool.getAsJsonArray("conditions").get(0).getAsJsonObject()
                            .get("condition").getAsString());

            assertEquals(1, occurrences(pickaxe, id),
                    id + " must occur exactly once in mineable/pickaxe");
            assertEquals(material.displayName(),
                    language.get("block.britannia_mod." + material.id()).getAsString());
            assertFalse(Files.exists(DATA.resolve(
                    "britannia_mod/recipe/" + material.id() + ".json")),
                    material.id() + " gained an unspecced recipe");
        }
    }

    private static List<String> numberedTextures(String stem) {
        return List.of(
                stem + "_1", stem + "_2", stem + "_3",
                stem + "_4", stem + "_5", stem + "_6");
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource: " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<Integer> ints(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsInt).toList();
    }

    private static int occurrences(JsonArray values, String expected) {
        return (int) values.asList().stream()
                .map(JsonElement::getAsString)
                .filter(expected::equals)
                .count();
    }

    private record Material(
            String id, String modelStem, String displayName, List<String> textures) {
    }
}

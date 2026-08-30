package com.seggellion.britannia_mod.roof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoneRoofMilestoneSevenCompatibilityTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve(
            "src/main/resources/assets/britannia_mod");

    @Test
    void historicalStairKeepsItsCompleteStateAndModelContract() throws Exception {
        JsonObject variants = json(ASSETS.resolve("blockstates/slate_roof.json"))
                .getAsJsonObject("variants");
        Map<String, String> modelsByShape = Map.of(
                "straight", "slate_roof",
                "outer_left", "slate_roof_outer",
                "outer_right", "slate_roof_outer",
                "inner_left", "slate_roof_inner",
                "inner_right", "slate_roof_inner");

        assertEquals(40, variants.size());
        for (String facing : List.of("north", "east", "south", "west")) {
            for (String half : List.of("bottom", "top")) {
                for (Map.Entry<String, String> shape : modelsByShape.entrySet()) {
                    String selector = "facing=" + facing + ",half=" + half
                            + ",shape=" + shape.getKey();
                    JsonElement definition = variants.get(selector);
                    assertTrue(definition != null && definition.isJsonObject(), selector);
                    assertEquals("britannia_mod:block/" + shape.getValue(),
                            definition.getAsJsonObject().get("model").getAsString(), selector);
                }
            }
        }

        for (String model : List.of("slate_roof", "slate_roof_outer", "slate_roof_inner")) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/" + model + ".json")),
                    model);
        }
        assertEquals("britannia_mod:block/slate_roof",
                json(ASSETS.resolve("models/item/slate_roof.json"))
                        .get("parent").getAsString());
    }

    @Test
    void numberedCompatibilityFlatsResolveEveryInheritedSlabType() throws Exception {
        for (String id : List.of("slate_roof_1_flat", "slate_roof_2_flat")) {
            JsonObject variants = json(ASSETS.resolve("blockstates/" + id + ".json"))
                    .getAsJsonObject("variants");
            String expectedModel = "britannia_mod:block/structure/" + id;
            assertEquals(3, variants.size(), id);
            for (String type : List.of("bottom", "top", "double")) {
                JsonElement definition = variants.get("type=" + type);
                assertTrue(definition != null && definition.isJsonObject(), id + " " + type);
                assertEquals(expectedModel,
                        definition.getAsJsonObject().get("model").getAsString());
            }

            assertTrue(Files.isRegularFile(ASSETS.resolve(
                    "models/block/structure/" + id + ".json")), id);
            assertEquals(expectedModel,
                    json(ASSETS.resolve("models/item/" + id + ".json"))
                            .get("parent").getAsString());
        }
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing compatibility resource: " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}

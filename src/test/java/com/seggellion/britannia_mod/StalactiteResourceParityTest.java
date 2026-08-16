package com.seggellion.britannia_mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StalactiteResourceParityTest {
    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/britannia_mod");

    @Test
    void everyStalagmiteModelHasAnExactCeilingReflectedCounterpart() throws IOException {
        for (int index = 1; index <= 7; index++) {
            JsonObject floor = read("models/block/decorations/cave/stalagmite_" + index + ".json");
            JsonObject ceiling = read("models/block/decorations/cave/stalactite_" + index + ".json");
            assertEquals(floor.get("textures"), ceiling.get("textures"),
                    "stalactite_" + index + " should reuse its stalagmite textures");

            JsonArray floorElements = floor.getAsJsonArray("elements");
            JsonArray ceilingElements = ceiling.getAsJsonArray("elements");
            assertEquals(floorElements.size(), ceilingElements.size(),
                    "stalactite_" + index + " element parity");
            for (int elementIndex = 0; elementIndex < floorElements.size(); elementIndex++) {
                assertElementReflection(index, elementIndex,
                        floorElements.get(elementIndex).getAsJsonObject(),
                        ceilingElements.get(elementIndex).getAsJsonObject());
            }

            JsonObject blockstate = read("blockstates/stalactite_" + index + ".json");
            JsonObject variants = blockstate.getAsJsonObject("variants");
            assertEquals(4, variants.size(), "stalactite_" + index + " facing variants");
            for (String facing : new String[]{"north", "south", "west", "east"}) {
                assertEquals("britannia_mod:block/decorations/cave/stalactite_" + index,
                        variants.getAsJsonObject("facing=" + facing).get("model").getAsString());
            }
            assertEquals("britannia_mod:block/decorations/cave/stalactite_" + index,
                    read("models/item/stalactite_" + index + ".json").get("parent").getAsString());
        }
    }

    private static void assertElementReflection(
            int modelIndex, int elementIndex, JsonObject floor, JsonObject ceiling) {
        String label = "stalactite_" + modelIndex + " element " + elementIndex;
        JsonArray floorFrom = floor.getAsJsonArray("from");
        JsonArray floorTo = floor.getAsJsonArray("to");
        JsonArray ceilingFrom = ceiling.getAsJsonArray("from");
        JsonArray ceilingTo = ceiling.getAsJsonArray("to");
        assertEquals(number(floorFrom, 0), number(ceilingFrom, 0), label + " min X");
        assertEquals(number(floorTo, 0), number(ceilingTo, 0), label + " max X");
        assertEquals(number(floorFrom, 2), number(ceilingFrom, 2), label + " min Z");
        assertEquals(number(floorTo, 2), number(ceilingTo, 2), label + " max Z");
        assertEquals(16.0D - number(floorTo, 1), number(ceilingFrom, 1), 1.0E-9D,
                label + " min Y");
        assertEquals(16.0D - number(floorFrom, 1), number(ceilingTo, 1), 1.0E-9D,
                label + " max Y");

        if (floor.has("rotation")) {
            JsonObject floorRotation = floor.getAsJsonObject("rotation");
            JsonObject ceilingRotation = ceiling.getAsJsonObject("rotation");
            assertEquals(16.0D - number(floorRotation.getAsJsonArray("origin"), 1),
                    number(ceilingRotation.getAsJsonArray("origin"), 1), 1.0E-9D,
                    label + " rotation origin Y");
        }

        JsonObject floorFaces = floor.getAsJsonObject("faces");
        JsonObject ceilingFaces = ceiling.getAsJsonObject("faces");
        if (floorFaces.has("up")) {
            assertEquals(floorFaces.get("up"), ceilingFaces.get("down"), label + " up/down face");
        }
        if (floorFaces.has("down")) {
            assertEquals(floorFaces.get("down"), ceilingFaces.get("up"), label + " down/up face");
        }
        for (String direction : new String[]{"north", "east", "south", "west"}) {
            if (!floorFaces.has(direction)) {
                continue;
            }
            JsonArray floorUv = floorFaces.getAsJsonObject(direction).getAsJsonArray("uv");
            JsonArray ceilingUv = ceilingFaces.getAsJsonObject(direction).getAsJsonArray("uv");
            assertEquals(number(floorUv, 0), number(ceilingUv, 0), label + " " + direction + " U1");
            assertEquals(number(floorUv, 3), number(ceilingUv, 1), label + " " + direction + " V2");
            assertEquals(number(floorUv, 2), number(ceilingUv, 2), label + " " + direction + " U2");
            assertEquals(number(floorUv, 1), number(ceilingUv, 3), label + " " + direction + " V1");
        }
    }

    private static JsonObject read(String relative) throws IOException {
        Path path = ASSETS.resolve(relative);
        assertTrue(Files.isRegularFile(path), "missing resource " + relative);
        try (var reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.getAsJsonObject();
        }
    }

    private static double number(JsonArray array, int index) {
        return array.get(index).getAsDouble();
    }
}

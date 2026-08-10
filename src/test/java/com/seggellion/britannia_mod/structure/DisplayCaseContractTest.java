package com.seggellion.britannia_mod.structure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DisplayCaseContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void oneFinalDecorativeIdHasNoStorageImplementation() throws Exception {
        String blockRegistry = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        assertTrue(blockRegistry.contains("BLOCKS.register(\n            \"display_case\""));
        assertFalse(blockRegistry.contains("display_case_end"));
        assertFalse(blockRegistry.contains("display_case_middle"));
        assertFalse(blockRegistry.contains("display_case_corner"));

        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/block/DisplayCaseBlock.java"));
        assertFalse(source.contains("EntityBlock"));
        assertFalse(source.contains("BlockEntity"));
        assertFalse(source.contains("Container"));
        assertTrue(source.contains("ConnectionForm.INDEPENDENT"));
        assertTrue(source.contains("ConnectionForm.END"));
        assertTrue(source.contains("ConnectionForm.MIDDLE"));
        assertTrue(source.contains("ConnectionForm.CORNER"));
    }

    @Test
    void multipartResourcesCoverEverySideAndStayWithinTwoBlocks() throws Exception {
        Path resources = PROJECT.resolve("src/main/resources");
        String[] relatives = {
            "assets/britannia_mod/blockstates/display_case.json",
            "assets/britannia_mod/models/block/new_assets/display_case_base.json",
            "assets/britannia_mod/models/block/new_assets/display_case_side_north.json",
            "assets/britannia_mod/models/block/new_assets/display_case_side_east.json",
            "assets/britannia_mod/models/block/new_assets/display_case_side_south.json",
            "assets/britannia_mod/models/block/new_assets/display_case_side_west.json",
            "assets/britannia_mod/models/block/new_assets/display_case_independent.json",
            "assets/britannia_mod/models/item/display_case.json",
            "data/britannia_mod/loot_table/blocks/display_case.json"
        };
        for (String relative : relatives) {
            JsonParser.parseString(Files.readString(resources.resolve(relative)));
        }

        String blockstate = Files.readString(resources.resolve(
                "assets/britannia_mod/blockstates/display_case.json"));
        for (String side : new String[] {"north", "east", "south", "west"}) {
            assertTrue(blockstate.contains("\"" + side + "\": \"false\""),
                    "missing conditional side model for " + side);
        }

        for (String relative : relatives) {
            if (!relative.contains("models/block")) continue;
            JsonObject model = JsonParser.parseString(Files.readString(resources.resolve(relative)))
                    .getAsJsonObject();
            if (!model.has("elements")) continue;
            model.getAsJsonArray("elements").forEach(value -> {
                JsonObject element = value.getAsJsonObject();
                for (String bound : new String[] {"from", "to"}) {
                    var coordinates = element.getAsJsonArray(bound);
                    double[] maximum = {16.0D, 32.0D, 16.0D};
                    for (int axis = 0; axis < coordinates.size(); axis++) {
                        double number = coordinates.get(axis).getAsDouble();
                        assertTrue(number >= 0.0D && number <= maximum[axis],
                                "display-case model escaped its 16x16x32 envelope");
                    }
                }
            });
        }
    }
}

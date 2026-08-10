package com.seggellion.britannia_mod.textile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TextileProcessingContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void conversionRatiosAndSilkBoundaryRemainExplicit() throws Exception {
        assertEquals(1, TextileProcessing.SPIN_INPUT_COUNT);
        assertEquals(5, TextileProcessing.WEAVE_INPUT_COUNT);

        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/textile/TextileProcessing.java"));
        assertTrue(source.contains("ItemTags.WOOL"));
        assertTrue(source.contains("ItemRegistry.COTTON"));
        assertTrue(source.contains("ItemRegistry.FLAX"));
        assertFalse(source.contains("SPIDERS_SILK"));

        String registry = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        assertTrue(registry.contains("ITEMS.register(\"ball_of_yarn\""));
        assertTrue(registry.contains("ITEMS.register(\"spool_of_thread\""));
        assertFalse(registry.contains("ITEMS.register(\"silk\""));
    }

    @Test
    void everyNewResourceParsesAndPlaceholderModelStaysInsideOneBlock() throws Exception {
        for (String relative : new String[] {
                "assets/britannia_mod/blockstates/spinning_wheel.json",
                "assets/britannia_mod/models/block/new_assets/spinning_wheel.json",
                "assets/britannia_mod/models/item/spinning_wheel.json",
                "assets/britannia_mod/models/item/ball_of_yarn.json",
                "assets/britannia_mod/models/item/spool_of_thread.json",
                "data/britannia_mod/loot_table/blocks/spinning_wheel.json"}) {
            Path path = PROJECT.resolve("src/main/resources").resolve(relative);
            JsonParser.parseString(Files.readString(path));
        }

        var model = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/block/new_assets/spinning_wheel.json")))
                .getAsJsonObject();
        model.getAsJsonArray("elements").forEach(value -> {
            var element = value.getAsJsonObject();
            for (String bound : new String[] {"from", "to"}) {
                element.getAsJsonArray(bound).forEach(coordinate -> {
                    double number = coordinate.getAsDouble();
                    assertTrue(number >= 0.0D && number <= 16.0D,
                            "placeholder spinning-wheel coordinate escaped its one-block envelope");
                });
            }
        });
    }
}

package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The standalone grape vine is retired: grapes exist only as a farming plot crop, and the old block
 * survives solely so old saves can be read and converted.
 *
 * <p>These cover the parts that can be judged without a world — the deterministic variety recovery
 * and the absence of any remaining way to create or see a legacy vine. The world behaviour itself
 * (conversion, drops, ownership) is covered by {@code GrapeArborGameTests}.
 */
class LegacyGrapeVineRetirementTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MAIN = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrapeVarietyManager.loadFromBootstrap(varietyPerColor());
    }

    @Test
    void everyLegacyColourRecoversAVarietyOfThatColourDeterministically() {
        Set<String> recovered = new HashSet<>();
        for (GrapeColor color : GrapeColor.values()) {
            String first = LegacyGrapeVineMigration.resolveVarietyId(null, color);
            assertEquals(color, GrapeVarietyManager.getVariety(first).colorType(),
                "colour " + color + " recovered a variety of a different colour: " + first);
            for (int repeat = 0; repeat < 8; repeat++) {
                assertEquals(first, LegacyGrapeVineMigration.resolveVarietyId(null, color),
                    "recovery for " + color + " is not deterministic");
            }
            recovered.add(first);
        }
        assertEquals(GrapeColor.values().length, recovered.size(),
            "all eight legacy colours must recover distinct varieties");
    }

    /**
     * The registry's own colour lookup walks a hash map, so a colour with more than one variety can
     * answer differently depending on what else is loaded. Migration must not inherit that: the
     * built-in Concords guarantee green and red each have a competing variety in every world.
     */
    @Test
    void aColourWithSeveralVarietiesStillMigratesTheSameWayEveryTime() {
        String chosen = LegacyGrapeVineMigration.resolveVarietyId(null, GrapeColor.GREEN);
        assertTrue(GrapeVarietyManager.getAllVarieties().stream()
                .filter(variety -> variety.colorType() == GrapeColor.GREEN)
                .count() > 1,
            "this test is meaningless unless green really does have competing varieties");
        assertEquals(GrapeVarietyManager.getAllVarieties().stream()
                .filter(variety -> variety.colorType() == GrapeColor.GREEN)
                .map(GrapeVariety::id)
                .sorted()
                .findFirst()
                .orElseThrow(),
            chosen,
            "migration must pick a green variety by a rule that does not depend on map order");
    }

    @Test
    void aStoredVarietyWinsOnlyWhenItAgreesWithTheRecordedColour() {
        Map<GrapeColor, String> byColor = varietyIdsByColor();
        // The block entity's id is authoritative when consistent...
        assertEquals(byColor.get(GrapeColor.RED),
            LegacyGrapeVineMigration.resolveVarietyId(byColor.get(GrapeColor.RED), GrapeColor.RED));
        // ...but a vine whose stored id contradicts its blockstate falls back to the colour, which is
        // the field that actually drove what the player saw. Expected values are derived from the
        // rule rather than named, because the built-in Concords also compete for green and red.
        assertEquals(lowestIdForColour(GrapeColor.BLUE),
            LegacyGrapeVineMigration.resolveVarietyId(byColor.get(GrapeColor.RED), GrapeColor.BLUE));
        assertEquals(lowestIdForColour(GrapeColor.GREEN),
            LegacyGrapeVineMigration.resolveVarietyId("a_variety_that_no_longer_exists", GrapeColor.GREEN));
        assertEquals(lowestIdForColour(GrapeColor.YELLOW),
            LegacyGrapeVineMigration.resolveVarietyId("   ", GrapeColor.YELLOW));
    }

    private static String lowestIdForColour(GrapeColor color) {
        return GrapeVarietyManager.getAllVarieties().stream()
            .filter(variety -> variety.colorType() == color)
            .map(GrapeVariety::id)
            .sorted()
            .findFirst()
            .orElseThrow();
    }

    @Test
    void grapeSeedsCanNoLongerCreateTheStandaloneVine() throws Exception {
        String seeds = Files.readString(MAIN.resolve("item/GrapeSeedsItem.java"), StandardCharsets.UTF_8);
        assertFalse(seeds.contains("super.useOn(context)"),
            "the BlockItem fallback is what used to place the standalone vine on vanilla farmland");
        assertTrue(seeds.contains("mayPlantHere"), "grape planting must go through the plot ownership gate");
    }

    @Test
    void theLegacyBlockKeepsNoGameplayBeyondConversion() throws Exception {
        String vine = Files.readString(MAIN.resolve("block/GrapeVineBlock.java"), StandardCharsets.UTF_8);
        assertTrue(vine.contains("LegacyGrapeVineMigration"), "the legacy block must convert itself");
        // Its old life as a crop: growth, upward propagation, and its own harvest. All of that now
        // belongs to the farming plot, and leaving any of it here would be a second grape system.
        assertFalse(vine.contains("propagateUpwards"), "legacy vertical growth must not survive retirement");
        assertFalse(vine.contains("createGrapeStack"), "legacy harvesting must not survive retirement");
        assertFalse(vine.contains("SCISSORS"), "legacy scissor harvesting must not survive retirement");
        assertTrue(vine.contains("COLOR"), "the legacy colour property must survive so old saves stay readable");
    }

    @Test
    void nothingRendersTheRetiredVineOrItsTrellisArtwork() throws Exception {
        String blockstate = Files.readString(
            ASSETS.resolve("blockstates/grape_vine_block.json"), StandardCharsets.UTF_8);
        assertFalse(blockstate.contains("trellis"), "the retired vine still references trellis artwork");
        assertFalse(blockstate.contains("grape_vine_stage"), "the retired vine still references arbor models");

        Path models = ASSETS.resolve("models/block/crops");
        for (GrapeColor color : GrapeColor.values()) {
            String name = color.getSerializedName();
            assertFalse(Files.exists(models.resolve("grape_" + name + "_trellis_5.json")),
                "superseded trellis model still present for " + name);
            assertFalse(Files.exists(models.resolve("grape_" + name + "_trellis_6.json")),
                "superseded trellis model still present for " + name);
        }
        for (int stage = 1; stage <= 4; stage++) {
            assertFalse(Files.exists(models.resolve("grape_vine_stage" + stage + ".json")),
                "superseded trellis stage model still present: " + stage);
        }
        // The replacements must be there, so this cannot pass by deleting everything.
        assertTrue(Files.isRegularFile(models.resolve("grapes/grape_vine_stage_7_purple.json")));
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

package com.seggellion.britannia_mod.grabbyhands;

import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbySources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural guarantees around the wine bottle that the round-trip tests cannot express.
 *
 * <p>Wine is the epic's highest-risk object because its state is worth real money — the economy
 * serialises all six fields on sale, alcohol traders filter on them, and the bank envelope round-trips
 * them. So the rules that keep it safe are checked here rather than left to convention.
 */
class GrabbyWineContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MOD_ROOT = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    @Test
    void grabbyHandsKnowsNothingAboutWine() throws IOException {
        // Generic transport, specialized behavior. The transport layer captures through the block's
        // own clone-stack path; if it ever starts naming wine fields, adding a seventh field becomes a
        // silent data-loss bug.
        Path grabbyRoot = MOD_ROOT.resolve("grabbyhands");
        try (var files = Files.walk(grabbyRoot)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = GrabbySources.stripComments(Files.readString(file, StandardCharsets.UTF_8));
                for (String forbidden : List.of(
                        "WineData", "wineryName", "grapeType", "labelColor", "WINE_DATA", "WineBottle")) {
                    assertFalse(source.contains(forbidden),
                            file.getFileName() + " names " + forbidden
                                    + "; wine state must stay opaque to the transport layer");
                }
            }
        }
    }

    @Test
    void pickupRestoresTheOriginalItemRatherThanRebuildingIt() throws IOException {
        String block = GrabbySources.stripComments(modSource("block/WineBottleBlock.java"));
        assertTrue(block.contains("portableStack("),
                "the clone-stack path must delegate to the block entity's restore decision");
        assertFalse(block.contains("WineBottleBlockItem.setWineData("),
                "rebuilding field by field in the clone path is exactly what lost custom names");
    }

    @Test
    void placementRecordsTheExactSourceStack() throws IOException {
        String block = GrabbySources.stripComments(modSource("block/WineBottleBlock.java"));
        assertTrue(block.contains("setOriginStack(stack)"),
                "without recording the source stack there is nothing to restore from");
        assertTrue(block.contains("setWineData("),
                "the wine fields still drive the label block state and the renderer");
    }

    @Test
    void theOriginStackIsPersistedButNotBroadcastToClients() throws IOException {
        String entity = GrabbySources.stripComments(modSource("block/entity/WineBottleBlockEntity.java"));
        assertTrue(entity.contains("tag.put(TAG_ORIGIN_STACK"), "it must survive a chunk reload");
        assertTrue(entity.contains("tag.remove(TAG_ORIGIN_STACK)"),
                "clients render from the block state and wine fields; they do not need the source item");
    }

    @Test
    void theWineBottleStaysUsableByEveryone() throws IOException {
        // Provenance is a mobility record, never an access control list. Nothing in the wine path may
        // consult who placed a bottle.
        for (String file : List.of("block/WineBottleBlock.java", "block/entity/WineBottleBlockEntity.java",
                "item/WineBottleBlockItem.java", "client/renderer/WineBottleBlockEntityRenderer.java")) {
            String source = GrabbySources.stripComments(modSource(file));
            for (String ownership : List.of("placerUuid", "GrabbyPolicy", "grabbyManaged", "getUUID()")) {
                assertFalse(source.contains(ownership),
                        file + " consults " + ownership + "; a placed bottle must stay public");
            }
        }
    }

    @Test
    void theEconomyStillReadsAllSixFields() throws IOException {
        // If a field stops reaching Rails, a bottle's sale value silently changes. This is the reason
        // wine state loss would matter beyond cosmetics.
        String economy = modSource("economy/ServerEconomyService.java");
        for (String field : List.of("winery_name", "grape_type", "\"year\"", "\"region\"", "\"quality\"",
                "label_color")) {
            assertTrue(economy.contains(field), "ServerEconomyService no longer sends " + field);
        }
    }

    @Test
    void allFourBottleColoursAreEnrolled() throws IOException {
        Set<String> enrolled = new LinkedHashSet<>();
        JsonParser.parseString(Files.readString(
                        PROJECT.resolve("src/main/resources/data/britannia_mod/tags/block/grabby_movable.json"),
                        StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("values")
                .forEach(element -> enrolled.add(element.getAsString()));

        for (String colour : List.of("green", "brown", "blue", "clear")) {
            assertTrue(enrolled.contains("britannia_mod:wine_bottle_" + colour),
                    "wine_bottle_" + colour + " must be movable");
        }
    }

    @Test
    void theBottleKeepsACompactShapeAndItsOwnSupportRule() throws IOException {
        String block = modSource("block/WineBottleBlock.java");
        assertTrue(block.contains("Block.box(6, 0, 6, 10, 10, 10)"),
                "a bottle must not occupy a full block; it sits on a surface");
        assertTrue(block.contains("canSupportCenter"), "and it must not float");
    }

    private static String modSource(String relative) throws IOException {
        return Files.readString(MOD_ROOT.resolve(relative), StandardCharsets.UTF_8);
    }
}

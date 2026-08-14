package com.seggellion.britannia_mod.grabbyhands;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M11: the production enrollment is explainable, and stays that way.
 *
 * <p>Enrollment is a tag edit by design, which is what keeps content growth out of the code. The
 * price is that a tag edit is also the easiest way to ship something nobody thought about. These
 * tests encode the review that each candidate was supposed to pass, so a future addition either
 * satisfies it or fails loudly.
 *
 * <h2>The rule that shapes the item list</h2>
 *
 * <p>A placed object can be picked up by anyone — provenance marks "Grabby-managed", never "yours".
 * That is harmless for a wheel of cheese and unacceptable for a house key: place one, have it taken,
 * and house access has changed hands. So nothing carrying economic or identity value is placeable.
 */
class GrabbyEnrollmentPolicyTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path BLOCK_TAGS = PROJECT.resolve("src/main/resources/data/britannia_mod/tags/block");
    private static final Path ITEM_TAGS = PROJECT.resolve("src/main/resources/data/britannia_mod/tags/item");
    private static final Path REGISTRY_DIR = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry");

    // ------------------------------------------------------------------
    // Items: nothing worth stealing
    // ------------------------------------------------------------------

    /** Placing one of these and having it taken would transfer money or access, not decoration. */
    private static final List<String> VALUE_OR_IDENTITY = List.of(
            "bank_cheque", "chest_key", "house_key", "deed_item",
            "blue_tent_deed", "purple_tent_deed",
            "copper_coin", "silver_coin", "gold_coin");

    @Test
    void noItemCarryingValueOrIdentityIsPlaceable() throws IOException {
        Set<String> placeable = tagValues(ITEM_TAGS, "grabby_placeable_items.json");
        for (String risky : VALUE_OR_IDENTITY) {
            assertFalse(placeable.contains("britannia_mod:" + risky),
                    risky + " can be picked up by anyone once placed; that is a theft vector,"
                            + " not clutter");
        }
    }

    @Test
    void noIngotOrRefinedMaterialIsPlaceable() throws IOException {
        // A deliberate line rather than a technical one: ingots are fungible wealth. Moving this line
        // is a content decision, so it should be made on purpose.
        Set<String> placeable = tagValues(ITEM_TAGS, "grabby_placeable_items.json");
        for (String id : placeable) {
            assertFalse(id.endsWith("_ingot"),
                    id + " is refined wealth; the placeable set is decorative only");
        }
    }

    @Test
    void noSpawnEggIsPlaceable() throws IOException {
        Set<String> placeable = tagValues(ITEM_TAGS, "grabby_placeable_items.json");
        for (String id : placeable) {
            assertFalse(id.contains("spawn_egg"), id + " is a gameplay item, not decoration");
        }
    }

    @Test
    void noBuilderOrAdminToolIsPlaceable() throws IOException {
        Set<String> placeable = tagValues(ITEM_TAGS, "grabby_placeable_items.json");
        for (String tool : List.of("interior_decorator_tool", "moongate_linking_wand")) {
            assertFalse(placeable.contains("britannia_mod:" + tool),
                    tool + " is builder equipment; leaving one lying around hands out the tool");
        }
    }

    @Test
    void everyPlaceableItemIsARealPureItemRatherThanABlock() throws IOException {
        // A BlockItem must place as its own block. Routing one through the generic host would replace
        // a real block with a stand-in and lose its behaviour.
        Set<String> registered = pureItemIds();
        for (String id : tagValues(ITEM_TAGS, "grabby_placeable_items.json")) {
            String path = id.substring(id.indexOf(':') + 1);
            assertTrue(registered.contains(path),
                    id + " is not a registered item with no block form; it cannot be hosted");
        }
    }

    @Test
    void theProductionItemSetIsBroaderThanThePilotButStillDeliberate() throws IOException {
        Set<String> placeable = tagValues(ITEM_TAGS, "grabby_placeable_items.json");

        assertTrue(placeable.size() >= 25, "the production set should be more than a pilot");
        assertTrue(placeable.size() <= 45, "an unbounded list stops being a reviewed decision");
        // One representative from each approved category, so a whole category cannot silently vanish.
        for (String expected : List.of("cheese", "blood_moss", "hops", "lute")) {
            assertTrue(placeable.contains("britannia_mod:" + expected),
                    expected + " represents an approved category and should be placeable");
        }
    }

    // ------------------------------------------------------------------
    // Blocks: nothing from an unrelated system
    // ------------------------------------------------------------------

    @Test
    void noInfrastructureBlockIsEnrolled() throws IOException {
        // Quest, shrine, spawn and service-NPC blocks are other systems' machinery. Being a block is
        // not a reason to make something furniture.
        Set<String> enrolled = enrolledBlockIds();
        for (String id : enrolled) {
            String path = id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
            for (String infrastructure : List.of(
                    "quest", "shrine", "monolith", "spawn", "npc", "house_lot", "house_sign",
                    "store_sign", "moongate", "teleport", "farm", "structure")) {
                assertFalse(path.contains(infrastructure),
                        id + " belongs to the " + infrastructure + " system, not to furniture");
            }
        }
    }

    @Test
    void everyBlockInAProvenanceCapableFamilyIsEitherEnrolledOrExcludedOnPurpose() throws IOException {
        // The completeness claim: no candidate is sitting unreviewed. Every block that COULD be
        // enrolled either is, or appears in one of the recorded exclusion sets.
        Set<String> enrolled = enrolledBlockIds();
        Set<String> deedPlaced = tagValues(BLOCK_TAGS, "grabby_deed_placed.json");

        // Excluded for reasons recorded in GrabbyEnrollmentPreconditionTest and the milestone reports.
        Set<String> otherExclusions = Set.of(
                "britannia_mod:lord_british_throne",   // a landmark, not furniture
                "britannia_mod:candelabra_tall",       // RaisedBlockItem repositions placement
                "britannia_mod:villa_lamp_post");      // RaisedBlockItem repositions placement

        Set<String> candidates = provenanceCapableBlockIds();
        // Guards the guard: an empty candidate set would make the loop below pass trivially.
        assertTrue(candidates.size() >= 35,
                "expected the six provenance-capable families to yield ~40 blocks, found " + candidates.size());

        for (String candidate : candidates) {
            boolean accountedFor = enrolled.contains(candidate)
                    || deedPlaced.contains(candidate)
                    || otherExclusions.contains(candidate);
            assertTrue(accountedFor,
                    candidate + " is provenance-capable but neither enrolled nor recorded as excluded;"
                            + " every candidate must be a decision");
        }
    }

    @Test
    void theTwoBlockTagsStillAgree() throws IOException {
        // Movable and axe-destroyable are separate concepts, deliberately given the same membership.
        // Diverging them is a design decision rather than an edit.
        assertEquals(tagValues(BLOCK_TAGS, "grabby_movable.json"),
                tagValues(BLOCK_TAGS, "grabby_axe_destroyable.json"));
    }

    @Test
    void nothingIsBothEnrolledAndExcluded() throws IOException {
        Set<String> enrolled = enrolledBlockIds();
        for (String excluded : tagValues(BLOCK_TAGS, "grabby_deed_placed.json")) {
            assertFalse(enrolled.contains(excluded),
                    excluded + " appears in both the enrollment and exclusion tags");
        }
    }

    // ------------------------------------------------------------------
    // Registry parsing
    // ------------------------------------------------------------------

    /** Every block id named inside a block-entity builder that carries Grabby provenance. */
    private static Set<String> provenanceCapableBlockIds() throws IOException {
        Set<String> constants = new LinkedHashSet<>();
        for (String field : List.of("CHAIR", "ROTATABLE_FURNITURE", "CANDELABRA", "WINE_BOTTLE_BE",
                "BRITANNIA_CHEST_BLOCK_ENTITY_TYPE", "ARMOIRE_BLOCK_ENTITY_TYPE")) {
            for (String registryFile : List.of("BlockEntityRegistry.java", "BlockRegistry.java")) {
                String source = read(REGISTRY_DIR.resolve(registryFile));
                int start = source.indexOf(field + " =");
                if (start < 0) {
                    continue;
                }
                int end = source.indexOf("build(null)", start);
                Matcher matcher = Pattern.compile("(?:BlockRegistry\\.)?([A-Z][A-Z0-9_]{2,})\\.get\\(")
                        .matcher(source.substring(start, end < 0 ? source.length() : end));
                while (matcher.find()) {
                    constants.add(matcher.group(1));
                }
            }
        }

        String blockRegistry = read(REGISTRY_DIR.resolve("BlockRegistry.java"));
        Matcher registrations = Pattern.compile(
                        "([A-Z][A-Z0-9_]*)\\s*=\\s*BLOCKS\\.register\\(\\s*\"([a-z0-9_]+)\"", Pattern.DOTALL)
                .matcher(blockRegistry);
        Set<String> ids = new LinkedHashSet<>();
        while (registrations.find()) {
            if (constants.contains(registrations.group(1))) {
                ids.add("britannia_mod:" + registrations.group(2));
            }
        }
        return ids;
    }

    /** Registered items that have neither a block form nor a BlockItem. */
    private static Set<String> pureItemIds() throws IOException {
        Set<String> items = new LinkedHashSet<>();
        Set<String> blockItems = new LinkedHashSet<>();
        for (String file : List.of("ItemRegistry.java", "ToolRegistry.java", "WeaponRegistry.java",
                "FishRegistry.java", "BlacksmithItemRegistry.java")) {
            Path path = REGISTRY_DIR.resolve(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            Matcher matcher = Pattern.compile(
                            "\\.register\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*\\(\\)\\s*->\\s*new\\s+"
                                    + "(?:[\\w.]*\\.)?(\\w+)\\(", Pattern.DOTALL)
                    .matcher(read(path));
            while (matcher.find()) {
                items.add(matcher.group(1));
                if (matcher.group(2).contains("BlockItem")) {
                    blockItems.add(matcher.group(1));
                }
            }
        }
        Matcher blocks = Pattern.compile("BLOCKS\\.register\\(\\s*\"([a-z0-9_]+)\"")
                .matcher(read(REGISTRY_DIR.resolve("BlockRegistry.java")));
        Set<String> blockIds = new LinkedHashSet<>();
        while (blocks.find()) {
            blockIds.add(blocks.group(1));
        }
        // An id that is explicitly registered as a plain Item stays pure even when a block of the
        // same name exists. The concern this guard exists for is a BlockItem being routed through
        // the generic host, which would swap a real block for a stand-in; blockItems already
        // catches that. Since the wild-reagent work landed, blood_moss is both a reagent Item and
        // a BloodMossBlock the vegetation manager places itself, and stripping every id that
        // shares a name with a block would wrongly disqualify the reagent.
        Set<String> explicitPlainItems = new LinkedHashSet<>(items);
        explicitPlainItems.removeAll(blockItems);
        blockIds.removeAll(explicitPlainItems);

        items.removeAll(blockItems);
        items.removeAll(blockIds);
        return items;
    }

    private static Set<String> enrolledBlockIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>(tagValues(BLOCK_TAGS, "grabby_movable.json"));
        ids.addAll(tagValues(BLOCK_TAGS, "grabby_axe_destroyable.json"));
        return ids;
    }

    private static Set<String> tagValues(Path directory, String fileName) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        JsonParser.parseString(read(directory.resolve(fileName)))
                .getAsJsonObject()
                .getAsJsonArray("values")
                .forEach(element -> values.add(element.getAsString()));
        return values;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

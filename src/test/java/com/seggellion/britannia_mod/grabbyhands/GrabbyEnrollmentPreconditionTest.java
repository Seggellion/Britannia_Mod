package com.seggellion.britannia_mod.grabbyhands;

import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbySources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The three preconditions any block must satisfy before it may be enrolled.
 *
 * <p>Enrollment is a tag edit — deliberately, so content can grow without code. The cost of that is
 * that a tag edit alone can produce a broken object in-game with nothing to stop it. These tests are
 * that stop: they read the real registries and fail in CI rather than letting a player discover the
 * problem by placing something that cannot be picked back up.
 *
 * <p>Each rule below exists because a specific block in this repository would violate it.
 */
class GrabbyEnrollmentPreconditionTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path TAG_DIR = PROJECT.resolve("src/main/resources/data/britannia_mod/tags/block");
    private static final Path REGISTRY_DIR = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry");

    /**
     * Block-entity families that carry {@code GrabbyInstanceState}.
     *
     * <p>Everything reachable from {@code NudgeableBlockEntity} plus the wine bottle. A block outside
     * these families places fine and is then permanently protected, because provenance has nowhere to
     * live — the object would be unpickable by the player who just placed it.
     */
    private static final List<String> PROVENANCE_CAPABLE_BE_FIELDS = List.of(
            "CHAIR", "ROTATABLE_FURNITURE", "CANDELABRA", "WINE_BOTTLE_BE",
            "BRITANNIA_CHEST_BLOCK_ENTITY_TYPE", "ARMOIRE_BLOCK_ENTITY_TYPE",
            // The crate family is multi-cell, but only its anchor carries the block entity, so
            // provenance has exactly one home per crate just as it does for the single-cell families.
            "CRATE_BLOCK_ENTITY_TYPE",
            // Grabby Hands' own generic host for items with no block form of their own. It lives in
            // GrabbyRegistry rather than the shared registries, which is why this list and the
            // scans below had to learn about a third registry file -- and why the host was not
            // enrollable at all until they did: every precondition here reported it as an
            // unregistered block.
            "PLACED_ITEM_BLOCK_ENTITY");

    /** Block-entity builders live in three registries; all of them have to be searched. */
    private static final List<String> REGISTRY_FILES =
            List.of("BlockEntityRegistry.java", "BlockRegistry.java", "GrabbyRegistry.java");

    /** Block and item registrations, likewise. */
    private static final List<String> CONTENT_REGISTRY_FILES =
            List.of("BlockRegistry.java", "GrabbyRegistry.java");

    /**
     * Item classes Grabby Hands can place without landing the object somewhere unintended.
     *
     * <p>Two ways to qualify. Most items land exactly where {@code BlockPlaceContext} says they will.
     * {@code DecorativeMultiblockItem} does not — it builds a cell structure around an anchor — but it
     * implements {@code GrabbyStructurePlacementItem}, so Grabby runs the item's own placement path
     * and asks it where the anchor went instead of assuming the clicked position.
     */
    private static final Set<String> NON_REPOSITIONING_ITEM_CLASSES =
            Set.of("BlockItem", "WineBottleBlockItem", "DecorativeMultiblockItem");

    @Test
    void everyEnrolledBlockIsBackedByAProvenanceCapableBlockEntity() throws IOException {
        Map<String, String> constantById = blockConstantsById();
        Set<String> capable = provenanceCapableBlockConstants();

        for (String id : enrolledIds()) {
            String constant = constantById.get(path(id));
            if (constant == null) {
                fail("Enrolled block " + id + " has no BlockRegistry registration");
            }
            assertTrue(capable.contains(constant),
                    "Enrolled block " + id + " (" + constant + ") is not bound to a provenance-capable"
                            + " block entity, so placing it would produce a permanently protected object");
        }
    }

    @Test
    void everyEnrolledBlockUsesAnItemThatDoesNotRepositionPlacement() throws IOException {
        // RaisedBlockItem overrides useOn to build a LiftedPlaceContext whose getClickedPos is shifted
        // upward. Grabby Hands builds a plain BlockPlaceContext and calls BlockItem.place directly, so
        // a lifted item would land one block low - silently, since the block really is where the
        // transaction looked for it. Excluding them is the fix until an adapter handles the lift.
        Map<String, String> itemClassById = itemClassesById();

        for (String id : enrolledIds()) {
            String itemClass = itemClassById.get(path(id));
            if (itemClass == null) {
                fail("Enrolled block " + id + " has no registered item, so it can never be placed");
            }
            assertTrue(NON_REPOSITIONING_ITEM_CLASSES.contains(itemClass),
                    "Enrolled block " + id + " uses " + itemClass + ", which repositions placement;"
                            + " it would land somewhere other than where policy was checked");
        }
    }

    @Test
    void theTwoKnownRepositioningItemsStayExcluded() throws IOException {
        Set<String> enrolled = enrolledIds();
        for (String excluded : List.of("britannia_mod:candelabra_tall", "britannia_mod:villa_lamp_post")) {
            assertFalse(enrolled.contains(excluded),
                    excluded + " uses RaisedBlockItem and must not be enrolled");
        }
    }

    @Test
    void deedPlacedHouseFixturesAreNeverEnrolled() throws IOException {
        // Owner rule: anything that arrives with a deed is house content, not carried furniture.
        // Beds and chandeliers are fixtures of the house a player bought, and a deed is placed under
        // house authority rather than by the ordinary placement gesture.
        Set<String> enrolled = enrolledIds();
        Set<String> deedPlaced = tagValues("grabby_deed_placed.json");

        assertFalse(deedPlaced.isEmpty(), "the exclusion tag must actually list something");
        for (String fixture : deedPlaced) {
            assertFalse(enrolled.contains(fixture),
                    fixture + " arrives with a deed and must never be Grabby Hands content");
        }
        for (String expected : List.of("britannia_mod:double_bed",
                "britannia_mod:wooden_chandelier", "britannia_mod:large_wooden_chandelier",
                "britannia_mod:small_wooden_chandelier", "britannia_mod:large_iron_chandelier",
                "britannia_mod:small_iron_chandelier")) {
            assertTrue(deedPlaced.contains(expected), expected + " must be listed as deed-placed");
        }
    }

    @Test
    void theDeedPlacedVetoWinsOverTheMovableTag() throws IOException {
        // Enrollment is a hand-edited file. The rule must not depend on nobody ever adding a fixture
        // to the movable tag by mistake, so eligibility checks the veto first.
        String eligibility = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/grabbyhands/GrabbyEligibility.java"),
                StandardCharsets.UTF_8);
        assertTrue(eligibility.contains("!deedPlaced(state) && state.is(ModTags.Blocks.GRABBY_MOVABLE)"),
                "movability must be vetoed by the deed-placed tag");
        assertTrue(eligibility.contains("!deedPlaced(state) && state.is(ModTags.Blocks.GRABBY_AXE_DESTROYABLE)"),
                "axe destruction must be vetoed by the deed-placed tag");
    }

    @Test
    void otherDeliberateContentExclusionsStand() throws IOException {
        Set<String> enrolled = enrolledIds();
        assertFalse(enrolled.contains("britannia_mod:trash_barrel"), "voids whatever is put in it");
        assertFalse(enrolled.contains("britannia_mod:lord_british_throne"),
                "a landmark, not furniture");
    }

    @Test
    void theRepositioningItemClassStillBehavesTheWayThisExclusionAssumes() throws IOException {
        // If RaisedBlockItem ever stops shifting placement, the exclusion above becomes unnecessary
        // and this test is where that gets noticed.
        String raised = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/item/RaisedBlockItem.java"),
                StandardCharsets.UTF_8);
        assertTrue(raised.contains("getClickedPos().above(this.lift)"),
                "RaisedBlockItem no longer lifts placement; revisit the Grabby enrollment exclusions");
    }

    @Test
    void enrollmentCoversMateriallyDifferentCategoriesWithNoPerObjectJava() throws IOException {
        // The M4 claim: one native adapter, several genuinely different kinds of furniture, and not a
        // single per-object branch anywhere in the Grabby sources.
        Map<String, String> constantById = blockConstantsById();
        Set<String> capable = provenanceCapableBlockConstants();
        Set<String> enrolled = enrolledIds();

        assertTrue(enrolled.contains("britannia_mod:wooden_chair"), "seating category");
        assertTrue(enrolled.contains("britannia_mod:yew_table"), "surface category");
        assertTrue(enrolled.contains("britannia_mod:wine_bottle_green"), "stateful item category");
        assertTrue(enrolled.contains("britannia_mod:wall_sconce"), "wall-attached lighting category");
        assertTrue(enrolled.contains("britannia_mod:brazier_small"), "floor-standing lighting category");
        assertTrue(enrolled.contains("britannia_mod:chest_wooden"), "container category");
        assertTrue(enrolled.size() >= 20, "expected a representative set, found " + enrolled.size());

        for (String id : enrolled) {
            assertTrue(capable.contains(constantById.get(path(id))), id);
        }

        // No Grabby source may name an individual enrolled block: that would be the per-object
        // business logic this milestone exists to prove unnecessary.
        Path grabbyRoot = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/grabbyhands");
        try (var files = Files.walk(grabbyRoot)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                // Comments are stripped: the doc comments name ChairBlock and WineBottleBlock to
                // explain why delegating to them works. Explaining a thing is not branching on it.
                String source = GrabbySources.stripComments(Files.readString(file, StandardCharsets.UTF_8));
                for (String constant : List.of("WOODEN_CHAIR", "YEW_TABLE", "WINE_BOTTLE", "WALL_SCONCE",
                        "BRAZIER_SMALL", "ChairBlock", "RotatableFurnitureBlock", "CandelabraBlock",
                        "WineBottleBlock")) {
                    assertFalse(source.contains(constant),
                            file.getFileName() + " names " + constant + "; enrollment must stay data-driven");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Registry parsing
    // ------------------------------------------------------------------

    private static final Pattern BLOCK_REGISTRATION = Pattern.compile(
            "([A-Z][A-Z0-9_]*)\\s*=\\s*BLOCKS\\.register\\(\\s*\"([a-z0-9_]+)\"", Pattern.DOTALL);
    private static final Pattern ITEM_REGISTRATION = Pattern.compile(
            "ITEMS\\.register\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*\\(\\)\\s*->\\s*new\\s+(?:[a-zA-Z0-9_.]*\\.)?([A-Za-z0-9_]+)\\(",
            Pattern.DOTALL);

    private static Map<String, String> blockConstantsById() throws IOException {
        Map<String, String> byId = new HashMap<>();
        for (String registryFile : CONTENT_REGISTRY_FILES) {
            Matcher matcher = BLOCK_REGISTRATION.matcher(read(REGISTRY_DIR.resolve(registryFile)));
            while (matcher.find()) {
                byId.put(matcher.group(2), matcher.group(1));
            }
        }
        return byId;
    }

    private static Map<String, String> itemClassesById() throws IOException {
        Map<String, String> byId = new HashMap<>();
        for (String registryFile : List.of("ItemRegistry.java", "GrabbyRegistry.java")) {
            Matcher matcher = ITEM_REGISTRATION.matcher(read(REGISTRY_DIR.resolve(registryFile)));
            while (matcher.find()) {
                byId.put(matcher.group(1), matcher.group(2));
            }
        }
        return byId;
    }

    /** Block constants named inside the block-entity builders that carry Grabby provenance. */
    private static Set<String> provenanceCapableBlockConstants() throws IOException {
        Set<String> constants = new HashSet<>();
        for (String field : PROVENANCE_CAPABLE_BE_FIELDS) {
            boolean found = false;
            for (String registryFile : REGISTRY_FILES) {
                String source = read(REGISTRY_DIR.resolve(registryFile));
                int start = source.indexOf(field + " =");
                if (start < 0) {
                    continue;
                }
                found = true;
                int end = source.indexOf("build(null)", start);
                String builder = source.substring(start, end < 0 ? source.length() : end);
                // Entries in BlockRegistry name their siblings bare; entries in BlockEntityRegistry
                // qualify them. Accept either form.
                Matcher matcher = Pattern.compile("(?:BlockRegistry\\.)?([A-Z][A-Z0-9_]{2,})\\.get\\(")
                        .matcher(builder);
                while (matcher.find()) {
                    constants.add(matcher.group(1));
                }
            }
            if (!found) {
                fail("No registry declares " + field + "; the provenance-capable family list is stale");
            }
        }
        return constants;
    }

    private static Set<String> enrolledIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (String file : List.of("grabby_movable.json", "grabby_axe_destroyable.json")) {
            ids.addAll(tagValues(file));
        }
        return ids;
    }

    private static Set<String> tagValues(String fileName) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        JsonParser.parseString(read(TAG_DIR.resolve(fileName)))
                .getAsJsonObject()
                .getAsJsonArray("values")
                .forEach(element -> values.add(element.getAsString()));
        return values;
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

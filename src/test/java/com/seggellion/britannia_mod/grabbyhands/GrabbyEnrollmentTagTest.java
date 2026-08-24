package com.seggellion.britannia_mod.grabbyhands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Content checks on the enrollment tags.
 *
 * <p>Tag membership is read from the JSON rather than from a loaded tag binding, because the mod has
 * no datagen and these files are hand-written — which means a typo in a registry ID would otherwise
 * fail silently at runtime as "block simply isn't enrolled".
 */
class GrabbyEnrollmentTagTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path TAG_DIR =
            PROJECT.resolve("src/main/resources/data/britannia_mod/tags/block");
    private static final Path BLOCK_REGISTRY = PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java");
    /**
     * Grabby Hands owns exactly one block outright, and it is registered here rather than in the
     * shared registry. Reading only {@code BlockRegistry} made the host look unregistered, which
     * is why the host could not be enrolled without this test rejecting it.
     */
    private static final Path GRABBY_REGISTRY = PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/registry/GrabbyRegistry.java");

    @Test
    void everyEnrolledIdIsActuallyRegisteredAsABlock() throws IOException {
        String registry = (Files.readString(BLOCK_REGISTRY, StandardCharsets.UTF_8)
                + Files.readString(GRABBY_REGISTRY, StandardCharsets.UTF_8))
                .replace("\r\n", "\n");
        for (String id : allEnrolledIds()) {
            String path = id.substring(id.indexOf(':') + 1);
            assertTrue(registry.contains("BLOCKS.register(\"" + path + "\"")
                            || registry.contains("BLOCKS.register(\n            \"" + path + "\"")
                            || registry.contains("\"" + path + "\","),
                    "Enrolled block id " + id + " has no matching BlockRegistry registration");
        }
    }

    @Test
    void everyEnrolledIdIsInTheModNamespace() throws IOException {
        for (String id : allEnrolledIds()) {
            assertTrue(id.startsWith("britannia_mod:"),
                    "Grabby Hands must not enroll blocks it does not own: " + id);
        }
    }

    @Test
    void enrollmentCoversTheIntendedFamiliesAndSkipsTheDeliberateExclusions() throws IOException {
        Set<String> movable = tagValues("grabby_movable.json");

        for (String expected : List.of(
                "britannia_mod:wooden_chair", "britannia_mod:straw_chair", "britannia_mod:stool",
                "britannia_mod:footstool", "britannia_mod:bench", "britannia_mod:yew_table",
                "britannia_mod:small_table", "britannia_mod:counter",
                "britannia_mod:wine_bottle_green", "britannia_mod:wine_bottle_clear")) {
            assertTrue(movable.contains(expected), "expected " + expected + " in grabby_movable");
        }

        // Containers are enrolled now that the spill hazard has a detach step guarding it.
        for (String container : List.of(
                "britannia_mod:chest_wooden", "britannia_mod:chest_metal",
                "britannia_mod:armoire_brown", "britannia_mod:chest_of_drawers_red")) {
            assertTrue(movable.contains(container), container + " should be portable");
        }
        // Locked chests are portable by owner decision; a thief carries off a box they still cannot open.
        assertTrue(movable.contains("britannia_mod:britannia_lockable_chest"));
        // The generic host for items that have no block form. Leaving it out made every loose item
        // a player set down unrecoverable: pickup refused it as TYPE_NOT_ENROLLED, silently, which
        // from the player's side is indistinguishable from Grabby Hands not working at all.
        assertTrue(movable.contains("britannia_mod:grabby_placed_item"),
                "the loose-item host must be pickable, or setting an item down loses it");
        // A deliberate exclusion rather than an oversight.
        assertFalse(movable.contains("britannia_mod:trash_barrel"),
                "the trash barrel voids whatever is put in it");

        // Lord British's throne is scenery in practice; enrolling it invites theft of a landmark.
        assertFalse(movable.contains("britannia_mod:lord_british_throne"));
    }

    @Test
    void theTagsLiveInTheSingularDirectoryThisPackFormatActuallyReads() {
        // pack_format 48 (1.21.1) reads data/<ns>/tags/block/. The legacy plural tags/blocks/ copies
        // elsewhere in this mod are inert; adding Grabby tags there would silently enroll nothing.
        assertTrue(Files.isRegularFile(TAG_DIR.resolve("grabby_movable.json")));
        assertTrue(Files.isRegularFile(TAG_DIR.resolve("grabby_axe_destroyable.json")));
    }

    @Test
    void axeDestroyableMatchesMovableForTheInitialContentSet() throws IOException {
        assertEquals(tagValues("grabby_movable.json"), tagValues("grabby_axe_destroyable.json"),
                "the seed sets are intended to be identical; diverging them needs a deliberate decision");
    }

    private static Set<String> allEnrolledIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>(tagValues("grabby_movable.json"));
        ids.addAll(tagValues("grabby_axe_destroyable.json"));
        return ids;
    }

    private static Set<String> tagValues(String fileName) throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(TAG_DIR.resolve(fileName), StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> values = new LinkedHashSet<>();
        json.getAsJsonArray("values").forEach(element -> values.add(element.getAsString()));
        return values;
    }
}

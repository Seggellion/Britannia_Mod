package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.client.render.PortraitFetch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M6: the farming quest giver is an archetype the existing spawn block can
 * place, dressed in clothes that already ship, and addressed by a portrait URL the existing
 * downloader already builds.
 *
 * <h2>What is pinned here and what is not</h2>
 * The entity itself needs a level, a registry and synched data, so the behaviour that depends on a
 * real Rowan -- profession, gender, wander radius, hint, and the identity after a respawn -- is a
 * GameTest ({@code RowanQuestGiverSpawnGameTests}). What is pinned here is everything that is a
 * fact about the configuration rather than about a running world, plus the two things that fail
 * silently in production: an outfit naming a texture that does not exist, and a portrait convention
 * drifting from the file the owner has to upload.
 */
class RowanFarmerArchetypeTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path CLOTHING_LAYER = PROJECT.resolve(
        "src/main/java/com/seggellion/britannia_mod/client/renderer/CitizenClothingLayer.java");
    private static final Path HUMAN_TEXTURES = PROJECT.resolve(
        "src/main/resources/assets/britannia_mod/textures/entity/human");

    /** The one string Rails joins on. Everything else about a Rowan spawner is local decoration. */
    @Test
    void rowanIsAnArchetypeTheSpawnBlockOffersUnderItsRailsIdentity() {
        assertEquals("Rowan", QuestGiverSpawnBlockEntity.ROWAN_ARCHETYPE,
            "this string is quests.origin_npc in Rails; changing it orphans the questline");
        assertTrue(QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES.contains("Rowan"));
        assertTrue(QuestGiverSpawnBlockEntity.supportsArchetype("Rowan"));
        assertEquals(1, QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES.stream()
            .filter("Rowan"::equals).count(), "one entry, so the screen cannot offer two Rowans");
    }

    /** Adding an archetype must not disturb the ones already placed in the world. */
    @Test
    void theArchetypesThatExistedBeforeAreStillOfferedInTheSameOrder() {
        List<String> preM6 = List.of("Zorathiel", "Lord British", "Iolo", "Dupre", "Shamino",
            "Generic Escort", "Generic Combat");

        assertEquals(preM6, QuestGiverSpawnBlockEntity.SUPPORTED_ARCHETYPES.stream()
                .filter(archetype -> !"Rowan".equals(archetype))
                .toList(),
            "an existing archetype was renamed, removed or reordered");
        for (String archetype : preM6) {
            assertTrue(QuestGiverSpawnBlockEntity.supportsArchetype(archetype), archetype);
        }
    }

    /**
     * The Farmer outfit is a mapping, not an asset: every texture it names is one that already
     * ships, for both genders. If this fails, the entry is asking the renderer for a file that is
     * not there -- which shows up in game as an untextured limb, and nowhere in a log.
     */
    @Test
    void theFarmerOutfitOnlyNamesTexturesThatAlreadyShip() throws IOException {
        Map<String, String> farmer = outfit("farmer");

        assertEquals(Map.of("shoes", "boots", "apron", "half_apron", "chest", "", "cape", ""), farmer,
            "the Farmer outfit is boots and a half apron, with the trader chest and cape suppressed");

        for (Map.Entry<String, String> slot : farmer.entrySet()) {
            if (slot.getValue().isEmpty()) continue;
            for (String gender : List.of("male", "female")) {
                Path texture = HUMAN_TEXTURES.resolve(gender)
                    .resolve(gender + "_" + slot.getValue() + "_1.png");
                assertTrue(Files.isRegularFile(texture),
                    "the Farmer outfit's " + slot.getKey() + " texture is missing: " + texture);
            }
        }
    }

    /** Every texture the Farmer wears is one another outfit already wears: nothing new is needed. */
    @Test
    void theFarmerOutfitIntroducesNoNewArt() throws IOException {
        Map<String, String> farmer = outfit("farmer");
        Map<String, String> woodTrader = outfit("wood_trader");

        assertEquals(woodTrader.get("shoes"), farmer.get("shoes"));
        assertEquals(woodTrader.get("apron"), farmer.get("apron"));
    }

    /**
     * The portrait convention is unchanged, so Rowan needs no code: what it needs is the file. Until
     * {@code Rowan.png} is uploaded the request 404s and {@code PortraitDownloader} keeps the
     * generic peasant it wrote into the cache before the request started.
     */
    @Test
    void rowansPortraitFollowsTheExistingConventionAndNamesTheFileTheOwnerMustUpload() {
        assertEquals("https://storage.googleapis.com/ultimacraft/portraits/female/Rowan.png",
            PortraitFetch.portraitUrl("Rowan", "female"),
            "external asset required: upload Rowan.png to the portrait bucket");
        assertEquals("https://storage.googleapis.com/ultimacraft/portraits/male/Rowan.png",
            PortraitFetch.portraitUrl("Rowan", "male"));
        assertEquals("female_rowan", PortraitFetch.cacheKey("Rowan", "female"));
        assertEquals("Rowan", PortraitFetch.sanitizeName("Rowan"),
            "a one-word personal name reaches the URL untouched");
        assertFalse(PortraitFetch.portraitUrl("Rowan", "female").contains(" "));
    }

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * The outfit as the client's clothing layer declares it. Read from source because the layer is
     * a GeckoLib render layer and its table is private: the value being checked is what ships, not
     * a copy of it kept in a test.
     */
    private static Map<String, String> outfit(String outfitKey) throws IOException {
        String source = Files.readString(CLOTHING_LAYER, StandardCharsets.UTF_8);
        Matcher entry = Pattern.compile("\"" + Pattern.quote(outfitKey) + "\"\\s*,\\s*Map\\.of\\(([^)]*)\\)")
            .matcher(source);
        assertTrue(entry.find(), "CitizenClothingLayer declares no \"" + outfitKey + "\" outfit");

        Map<String, String> slots = new LinkedHashMap<>();
        Matcher pairs = Pattern.compile("\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"").matcher(entry.group(1));
        while (pairs.find()) {
            slots.put(pairs.group(1), pairs.group(2));
        }
        assertFalse(slots.isEmpty(), "the \"" + outfitKey + "\" outfit dresses no slot");
        return slots;
    }
}

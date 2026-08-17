package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every ordinary custom seed must resolve to the crop it claims to plant.
 *
 * <p>Planting goes through {@code FarmingBlock#tryPlantSeed}, which resolves the species with
 * {@code CropRegistry.bySeed(stack.getItem())} - a linear scan comparing {@code seedItem().get()}
 * by identity. A {@code CropSeedItem} whose {@code cropId} has no matching definition, or a crop
 * whose {@code seedItem} supplier points at a different item than the one carrying that crop id,
 * produces a seed that is registered, obtainable, and silently unplantable: the scan misses, the
 * interaction passes through, and nothing tells the player why.
 *
 * <p>Driven off the registry sources rather than a second hand-maintained table, because a
 * duplicated list of "expected" pairs is exactly the thing that rots. Runtime resolution needs
 * initialised Minecraft registries, which no unit harness here can stand up, so the linkage is
 * asserted against its declaration site.
 */
class CustomSeedPlantingLinkageTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    /** {@code CONSTANT = cropSeed("item_id", "crop_id")} */
    private static final Pattern SEED_REGISTRATION = Pattern.compile(
            "([A-Z0-9_]+)\\s*=\\s*cropSeed\\(\"([a-z0-9_]+)\",\\s*\"([a-z0-9_]+)\"\\)");

    /** {@code crop("crop_id", 15.0f, "Display", <seed supplier>, ...} */
    private static final Pattern CROP_DEFINITION = Pattern.compile(
            "crop\\(\"([a-z0-9_]+)\",\\s*[0-9.]+f,\\s*\"[^\"]*\",\\s*([^,]+),");

    private static final Pattern ITEM_REGISTRY_SUPPLIER =
            Pattern.compile("ItemRegistry\\.([A-Z0-9_]+)::get");

    /**
     * Crops that deliberately do not use the ordinary {@code CropSeedItem} planting path. Exempt
     * from the linkage rule, but pinned as genuinely special by
     * {@link #grapesUseTheirOwnPlantingMechanism()} so this set cannot become cover for a real break.
     */
    private static final Set<String> SPECIAL_PROPAGATION = Set.of("grapes");

    private record SeedItem(String constant, String itemId, String cropId) {}

    private static String source(String relative) throws Exception {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/" + relative));
    }

    /** Seed constant -> registration, from ItemRegistry. */
    private static Map<String, SeedItem> seedItems() throws Exception {
        Map<String, SeedItem> seeds = new LinkedHashMap<>();
        Matcher matcher = SEED_REGISTRATION.matcher(source("registry/ItemRegistry.java"));
        while (matcher.find()) {
            seeds.put(matcher.group(1), new SeedItem(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return seeds;
    }

    /** Crop id -> seed supplier expression, from CropRegistry. */
    private static Map<String, String> cropSeedSuppliers() throws Exception {
        Map<String, String> crops = new LinkedHashMap<>();
        Matcher matcher = CROP_DEFINITION.matcher(source("farming/CropRegistry.java"));
        while (matcher.find()) {
            crops.put(matcher.group(1), matcher.group(2).trim());
        }
        return crops;
    }

    @Test
    void theAuditActuallyFoundTheRegistries() throws Exception {
        // Guards the regexes themselves: a refactor that changes the declaration shape must not
        // quietly reduce this suite to asserting nothing.
        assertTrue(seedItems().size() >= 55, "parsed too few seed registrations: " + seedItems().size());
        assertTrue(cropSeedSuppliers().size() >= 60,
                "parsed too few crop definitions: " + cropSeedSuppliers().size());
    }

    @Test
    void everyCustomSeedNamesACropThatExists() throws Exception {
        Set<String> cropIds = cropSeedSuppliers().keySet();
        List<String> orphans = new ArrayList<>();
        for (SeedItem seed : seedItems().values()) {
            if (!cropIds.contains(seed.cropId())) {
                orphans.add(seed.itemId() + " -> '" + seed.cropId() + "'");
            }
        }
        assertTrue(orphans.isEmpty(),
                "seed items naming a crop with no definition (registered but unplantable): " + orphans);
    }

    @Test
    void everyCropSuppliesTheSeedItemThatNamesIt() throws Exception {
        Map<String, SeedItem> seeds = seedItems();
        List<String> mismatches = new ArrayList<>();

        for (Map.Entry<String, String> crop : cropSeedSuppliers().entrySet()) {
            if (SPECIAL_PROPAGATION.contains(crop.getKey())) {
                continue; // not an ordinary seed; see grapesUseTheirOwnPlantingMechanism
            }
            Matcher supplier = ITEM_REGISTRY_SUPPLIER.matcher(crop.getValue());
            if (!supplier.matches()) {
                continue; // vanilla-item suppliers such as () -> Items.WHEAT_SEEDS
            }
            SeedItem seed = seeds.get(supplier.group(1));
            if (seed == null) {
                mismatches.add("crop '" + crop.getKey() + "' supplies " + supplier.group(1)
                        + ", which is not a registered CropSeedItem");
            } else if (!seed.cropId().equals(crop.getKey())) {
                mismatches.add("crop '" + crop.getKey() + "' supplies " + seed.constant()
                        + ", but that CropSeedItem plants '" + seed.cropId() + "'");
            }
        }
        assertTrue(mismatches.isEmpty(), "seed/crop linkage broken: " + mismatches);
    }

    @Test
    void noTwoCropsShareASeedItem() throws Exception {
        // CropRegistry.bySeed returns the first match, so a shared seed silently shadows a crop;
        // FarmingSkillRequirementResolver would also throw at runtime on the duplicate.
        Map<String, String> owners = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, String> crop : cropSeedSuppliers().entrySet()) {
            String previous = owners.putIfAbsent(crop.getValue(), crop.getKey());
            if (previous != null) {
                conflicts.add(crop.getValue() + " claimed by '" + previous + "' and '" + crop.getKey() + "'");
            }
        }
        assertTrue(conflicts.isEmpty(), "conflicting seed suppliers: " + conflicts);
    }

    @Test
    void cropIdsAreUnique() throws Exception {
        Matcher matcher = CROP_DEFINITION.matcher(source("farming/CropRegistry.java"));
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        while (matcher.find()) {
            if (!seen.add(matcher.group(1))) {
                duplicates.add(matcher.group(1));
            }
        }
        assertTrue(duplicates.isEmpty(), "duplicate crop definitions: " + duplicates);
    }

    @Test
    void barleyIsWiredLikeEveryOtherOrdinaryGrain() throws Exception {
        // The reported defect. Barley's linkage is sound; what stops a low-skill player planting it
        // is the Farming requirement in its definition, which is deliberate and shared with its
        // peers. Pinned so a future "fix" cannot quietly sever the mapping instead.
        SeedItem barley = seedItems().get("BARLEY_SEEDS");
        assertEquals("barley_seeds", barley.itemId());
        assertEquals("barley", barley.cropId());
        assertEquals("ItemRegistry.BARLEY_SEEDS::get", cropSeedSuppliers().get("barley"));

        for (String grain : List.of("wheat", "rye", "oats", "barley")) {
            assertTrue(cropSeedSuppliers().containsKey(grain), grain + " lost its crop definition");
        }
    }

    @Test
    void grapesUseTheirOwnPlantingMechanism() throws Exception {
        // Justifies the SPECIAL_PROPAGATION exemption. Grapes are a trellis crop planted by an
        // ItemNameBlockItem, not by CropSeedItem, and the ordinary path excludes them explicitly.
        // If any of that stops being true, grapes belong back in the linkage audit.
        String items = source("registry/ItemRegistry.java");
        assertTrue(items.contains("DeferredHolder<Item, GrapeSeedsItem> GRAPE_SEEDS"),
                "grape seeds are no longer a GrapeSeedsItem");

        String grapeItem = source("item/GrapeSeedsItem.java");
        assertTrue(grapeItem.contains("extends ItemNameBlockItem"),
                "grape seeds no longer use their own block-item placement");

        String farming = source("block/FarmingBlock.java");
        assertTrue(farming.contains("stack.getItem() instanceof GrapeSeedsItem"),
                "the ordinary planting path no longer excludes grapes");

        String gate = source("farming/FarmingCultivationGate.java");
        assertTrue(gate.contains("GRAPE_SPECIES_ID.equals(resolved.speciesId())"),
                "the cultivation gate no longer treats grapes as not-applicable");
    }

    @Test
    void plantingDenialStillExplainsItselfToThePlayer() throws Exception {
        // Suppressing routine gain chat removed the player's running commentary on skill, so the
        // refusal has to carry its own explanation rather than failing silently.
        String lang = Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"));
        assertTrue(lang.contains("You do not know how to grow this crop yet."),
                "insufficient-skill denial no longer tells the player they lack the knowledge");
        assertFalse(lang.contains("Unidentified Seeds require Farming"),
                "denial reverted to wording that reads as a problem with the seeds");

        String gate = source("farming/FarmingCultivationGate.java");
        assertTrue(gate.contains("sendDenialFeedback"), "denial feedback path removed");
        assertTrue(gate.contains("displayClientMessage"), "denial no longer reaches the player");
    }
}

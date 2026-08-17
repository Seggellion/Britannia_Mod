package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.registry.FishRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every fish the mod can put in a player's hands must resolve to a commodity Rails will price.
 *
 * <p>Rails matches a sell row on {@code (category, subcategory, commodity_key)} exactly, case
 * insensitively, with no fallback — a key that is off by one letter does not price low, it does
 * not price at all, and the player is told the trader is not interested. Two of the seeded
 * spellings differ from the mod's registry ids ({@code mackeral}, {@code kingfish}), which is
 * exactly the kind of drift that only a machine-checked list catches.
 */
class FishBuybackCommodityKeyTest {

    /**
     * The {@code fish}/{@code raw} commodity keys Rails prices, verified against the seeded
     * commodity table on 2026-08-17: all 81 carry a positive {@code current_price} with
     * {@code npc_buy_enabled}. The four with no mod item are the UO special catches.
     */
    private static final Set<String> RAILS_PRICED_FISH = Set.of(
            "abyssal_dragonfish", "amberjack", "atlantic_salmon", "autumn_dragonfish", "black_marlin",
            "black_seabass", "blue_grouper", "blue_marlin", "bluefish", "bluegill_sunfish", "bonefish",
            "bonito", "brook_trout", "bull_fish", "cape_cod", "captain_snook", "cobia", "cod",
            "crag_snapper", "crystal_fish", "cutthroat_trout", "dark_fish", "demon_trout", "drake_fish",
            "dungeon_chub", "dungeon_pike", "fairy_salmon", "fire_fish", "flying_squid", "giant_koi",
            "giant_samurai_fish", "golden_tuna", "gray_snapper", "great_barracuda", "green_catfish",
            "grim_cisco", "haddock", "halibut", "highly_peculiar_fish", "holy_mackerel", "infernal_tuna",
            "kingfish", "kokanee_salmon", "lantern_fish", "lava_fish", "lurker_fish", "mackeral",
            "mahi_mahi", "mud_puppy", "orc_bass", "pike", "prized_fish", "pumpkinseed_sunfish",
            "rainbow_fish", "rainbow_trout", "reaper_fish", "red_drum", "red_grouper", "red_herring",
            "red_snook", "redbelly_bream", "seeker_fish", "shad", "smallmouth_bass", "snaggletooth_bass",
            "spring_dragonfish", "stone_fish", "sturgeon", "summer_dragonfish", "tarpon",
            "tormented_pike", "truly_rare_fish", "uncommon_shiner", "unicorn_fish", "walleye",
            "winter_dragonfish", "wondrous_fish", "yellow_perch", "yellowfin_tuna",
            "yellowtail_barracuda", "zombie_fish");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void railsPricesEveryFishTheModCanRegister() {
        List<String> unpriceable = new java.util.ArrayList<>();
        for (String fishId : FishRegistry.fishIds()) {
            String key = CommodityMappings.fishCommodityKey(fishId);
            if (!RAILS_PRICED_FISH.contains(key)) unpriceable.add(fishId + " -> " + key);
        }
        assertTrue(unpriceable.isEmpty(),
                () -> "fish the Fish Trader could never buy: " + new TreeSet<>(unpriceable));
    }

    @Test
    void theTwoSpellingsRailsSeedsDifferentlyAreTranslated() {
        assertEquals("mackeral", CommodityMappings.fishCommodityKey("mackerel"));
        assertEquals("kingfish", CommodityMappings.fishCommodityKey("king_fish"));
        // The catch tag is a display name ("Cape Cod"); normalization has to reach the seed key.
        assertEquals("cape_cod", CommodityMappings.fishCommodityKey("Cape Cod"));
        assertEquals("mackeral", CommodityMappings.fishCommodityKey("Mackerel"));
    }

    /**
     * Only fish caught through the fishing handler carry a {@code FishType} tag. Anything from
     * {@code /give} or creative reads back {@code "unknown"}, which Rails can only answer with
     * {@code commodity_not_found} — so the registry path, which IS the canonical key, is used
     * instead of a dead sentinel.
     */
    @Test
    void anUntaggedFishFallsBackToItsRegistryPath() {
        assertEquals("cod", CommodityMappings.fishCommodityKey("unknown", "cod"));
        assertEquals("", CommodityMappings.fishCommodityKey("", ""));
        assertEquals("mackeral", CommodityMappings.fishCommodityKey("unknown", "mackerel"));
        assertEquals("kingfish", CommodityMappings.fishCommodityKey(null, "king_fish"));

        // A real tag still wins: the item is the fallback, never the override.
        assertEquals("lava_fish", CommodityMappings.fishCommodityKey("lava fish", "cod"));
    }

    /**
     * Every provenance a fish stack can have, resolved through the one mapping both the quote and
     * the settlement payload call. They must agree: a stack that quotes as {@code cod} and settles
     * as something else is a player paid for goods the city never received.
     *
     * <p>{@code CommodityMappings.fishCommodityKey} is the single resolver. Its three call sites
     * are {@code ServerEconomyService.describeSaleItem} (quote and settlement both), {@code
     * TraderRoleHandler} (legacy traders only) and {@code matchesRequest} (settlement stack
     * matching) — one implementation, not several competing ones.
     */
    @Test
    void everyFishProvenanceResolvesToTheSameKey() {
        // Caught through the fishing handler: the tag is the catalogue name, lower-cased.
        assertEquals("cod", CommodityMappings.fishCommodityKey("cod", "cod"));
        assertEquals("cape_cod", CommodityMappings.fishCommodityKey("cape cod", "cape_cod"));

        // /give and Creative: no CUSTOM_DATA at all, so getFishType reports the sentinel.
        assertEquals("cod", CommodityMappings.fishCommodityKey("unknown", "cod"));

        // A legacy stack tagged before the alias existed still lands on the seeded spelling.
        assertEquals("mackeral", CommodityMappings.fishCommodityKey("Mackerel", "mackerel"));

        // Malformed metadata: blanks, whitespace and punctuation normalize away, then fall back.
        assertEquals("cod", CommodityMappings.fishCommodityKey("   ", "cod"));
        assertEquals("cod", CommodityMappings.fishCommodityKey("!!!", "cod"));
        assertEquals("cod", CommodityMappings.fishCommodityKey(null, "cod"));

        // An unsupported fish resolves to its own key and is refused by Rails as
        // commodity_not_found -- a miss that says so, never a silent substitution.
        assertEquals("kraken", CommodityMappings.fishCommodityKey("kraken", "kraken"));
        assertFalse(RAILS_PRICED_FISH.contains("kraken"));

        // A non-fish item never reaches this resolver; describeSaleItem branches on
        // WeightedFishItem first. Were it called anyway it invents nothing.
        assertEquals("oak_log", CommodityMappings.fishCommodityKey("unknown", "oak_log"));
        assertFalse(RAILS_PRICED_FISH.contains("oak_log"));
    }

    @Test
    void everyFallbackPathAlsoResolvesToAPricedKey() {
        for (String fishId : FishRegistry.fishIds()) {
            String key = CommodityMappings.fishCommodityKey("unknown", fishId);
            assertTrue(RAILS_PRICED_FISH.contains(key),
                    () -> "untagged " + fishId + " resolved to unpriceable " + key);
        }
    }
}

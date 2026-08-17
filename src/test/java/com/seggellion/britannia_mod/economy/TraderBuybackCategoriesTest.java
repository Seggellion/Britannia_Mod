package com.seggellion.britannia_mod.economy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Trader buyback offer must contain what that Trader deals in and nothing else.
 *
 * <p>Rails cannot draw this line for us: {@code npc_buy_enabled} is a per-commodity-per-city
 * flag, not a per-NPC one, so an unfiltered offer would have a Fish Trader quoting a price for
 * logs wherever the city happens to trade wood. These expectations mirror
 * {@code TraderRoleHandler#collectSellableInventory} case for case, so the economic path offers
 * exactly what the legacy path always did.
 */
class TraderBuybackCategoriesTest {

    @Test
    void eachTraderTypeKeyResolvesToItsOwnCategories() {
        assertEquals(Set.of("fish"), TraderBuybackCategories.forTrader("fish_trader", "Fish Trader"));
        assertEquals(Set.of("wood"), TraderBuybackCategories.forTrader("wood_trader", "Wood Trader"));
        assertEquals(Set.of("ore"), TraderBuybackCategories.forTrader("ore_trader", "Ore Trader"));
        assertEquals(Set.of("stone"), TraderBuybackCategories.forTrader("stone_trader", "Stone Trader"));
        assertEquals(Set.of("grain"), TraderBuybackCategories.forTrader("grain_trader", "Grain Trader"));
        assertEquals(Set.of("produce"), TraderBuybackCategories.forTrader("produce_trader", "Produce Trader"));
        assertEquals(Set.of("meat"), TraderBuybackCategories.forTrader("meat_trader", "Meat Trader"));
        assertEquals(Set.of("fur", "leather"),
                TraderBuybackCategories.forTrader("fur_leather_trader", "Fur/Leather Trader"));
    }

    /**
     * Both of these keys contain a word an earlier branch would otherwise claim -- "salvage_trader"
     * deals in metal rather than ore, and an "alcohol_trader" must not be read as a generic. They
     * are dispatched first for the same reason {@code TraderRoleHandlers.create} dispatches them
     * first.
     */
    @Test
    void specializedTradersWinOverTheGenericSubstrings() {
        assertEquals(Set.of("metal"), TraderBuybackCategories.forTrader("salvage_trader", "Salvage Trader"));
        assertEquals(Set.of("alcohol"), TraderBuybackCategories.forTrader("alcohol_trader", "Alcohol Trader"));
        assertEquals(Set.of("alcohol"), TraderBuybackCategories.forTrader(null, "Vintner"));
    }

    @Test
    void theRoleTitleIsTheFallbackWhenTheTypeKeyIsUnrecognised() {
        assertEquals(Set.of("fish"), TraderBuybackCategories.forTrader("fishmonger_vendor_v2", "Fish Trader"));
        assertEquals(Set.of("fish"), TraderBuybackCategories.forTrader(null, "Fish Trader"));
    }

    @Test
    void anUnknownTraderOffersEverythingClassifiedAndNothingUnclassified() {
        Set<String> any = TraderBuybackCategories.forTrader("mysterious_trader", "Mysterious Trader");
        assertEquals(TraderBuybackCategories.ANY, any);
        assertTrue(TraderBuybackCategories.accepts(any, "fish"));
        assertTrue(TraderBuybackCategories.accepts(any, "wood"));

        // describeSaleItem leaves category off anything it cannot classify; those must never be
        // offered, or the quote would carry rows Rails has no way to price.
        assertFalse(TraderBuybackCategories.accepts(any, null));
        assertFalse(TraderBuybackCategories.accepts(any, ""));
        assertFalse(TraderBuybackCategories.accepts(Set.of("fish"), null));
    }

    @Test
    void aFishTraderIsOfferedNoWood() {
        Set<String> fish = TraderBuybackCategories.forTrader("fish_trader", "Fish Trader");
        assertTrue(TraderBuybackCategories.accepts(fish, "fish"));
        assertFalse(TraderBuybackCategories.accepts(fish, "wood"));
        assertFalse(TraderBuybackCategories.accepts(fish, "ore"));
    }
}

package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy.Entry;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Trader may be offered comes from the SYNCED policy — Trader Commodity Authority, M5.
 *
 * <p>The load-bearing test in this file is
 * {@link #aSyncedPolicyOverrulesTheLegacyTableInBothDirections}. Every type used here is
 * {@code fish_trader}, whose legacy name-guessed categories are {@code {fish}}, while the policy
 * handed to it says {@code wood}. The two therefore disagree on every item, and which one answered
 * is visible in the answer itself — a filter that quietly consulted the legacy table would offer
 * fish and refuse wood, so the assertions below would fail in both directions rather than in
 * neither.
 */
class TraderCommodityFilterTest {

    private static final String FISH_TRADER = "fish_trader";
    private static final String ROLE = "Fish Trader";

    /**
     * ACCEPTANCE: with a policy present the legacy table is not consulted, proven by making the
     * two disagree completely and showing the policy wins every time.
     */
    @Test
    void aSyncedPolicyOverrulesTheLegacyTableInBothDirections() {
        // Sanity: the legacy table really would answer differently, or this proves nothing.
        assertEquals(java.util.Set.of("fish"), TraderBuybackCategories.forTrader(FISH_TRADER, ROLE));

        TraderCommodityFilter filter = filterFor(policy(new Entry("wood", null, null)));

        assertEquals(TraderCommodityFilter.Source.POLICY, filter.source());
        assertTrue(filter.accepts(item("wood", "logs", "oak")),
                "the policy allows wood, so wood is offered even though the legacy table forbids it");
        assertFalse(filter.accepts(item("fish", "raw", "cod")),
                "the policy omits fish, so fish is refused even though the legacy table allows it");
    }

    /** And the other direction: on ABSENCE the legacy table is exactly what answers. */
    @Test
    void anAbsentPolicyMemberIsTheOneConditionThatReachesTheLegacyTable() {
        TraderCommodityFilter filter = filterFor(null);

        assertEquals(TraderCommodityFilter.Source.LEGACY_FALLBACK, filter.source());
        assertNull(filter.refusalCode(), "an old Rails is not a fault, it is an old Rails");
        assertTrue(filter.accepts(item("fish", "raw", "cod")));
        assertFalse(filter.accepts(item("wood", "logs", "oak")));
    }

    /**
     * The case the fallback must NOT catch. An empty policy is a real, deliberate accepts-nothing;
     * falling back here would hand a player precisely the categories the policy exists to
     * withhold, and Rails would refuse the sale at settlement anyway.
     */
    @Test
    void anEmptyPolicyAcceptsNothingAndNeverReachesTheLegacyTable() {
        TraderCommodityFilter filter = filterFor(policy());

        assertEquals(TraderCommodityFilter.Source.EMPTY_POLICY, filter.source());
        assertFalse(filter.accepts(item("fish", "raw", "cod")),
                "an empty policy must not be read as unrestricted, nor as unconfigured");
        assertFalse(filter.accepts(item("wood", "logs", "oak")));
        assertEquals(Notice.TRADER_POLICY_MISSING, filter.refusalCode(),
                "a trader that can buy nothing is a configuration fault, said out loud");
    }

    /**
     * Rails DROPS a type whose stored policy is malformed rather than publishing it broken, so a
     * trader with no registry entry is a fault, not an old Rails, and must not reach the fallback.
     */
    @Test
    void aTypeMissingFromTheRegistryFailsClosedRatherThanFallingBack() {
        TraderCommodityFilter filter = TraderCommodityFilter.resolve(null, FISH_TRADER, ROLE);

        assertEquals(TraderCommodityFilter.Source.MISSING, filter.source());
        assertFalse(filter.accepts(item("fish", "raw", "cod")));
        assertEquals(Notice.TRADER_POLICY_MISSING, filter.refusalCode());
    }

    @Test
    void aMultiCategoryTypeIsOfferedEveryCategoryItsPolicyNamesAndNoOther() {
        TraderCommodityFilter filter = filterFor(policy(
                new Entry("fur", null, null),
                new Entry("leather", null, null),
                new Entry("meat", List.of("cured"), null)));

        assertTrue(filter.accepts(item("fur", "pelt", "wolf pelt")));
        assertTrue(filter.accepts(item("leather", "cut", "leather")));
        assertTrue(filter.accepts(item("meat", "cured", "salt pork")));

        // The third entry's narrowing scopes ITS entry only -- it must not leak onto the two
        // unnarrowed ones, nor they onto it.
        assertFalse(filter.accepts(item("meat", "raw", "raw pork")));
        assertFalse(filter.accepts(item("fish", "raw", "cod")));
    }

    /**
     * The Salvage shape: two entries differing only by subcategory, one of them carrying the
     * commodity allow-list. Entry order is contractual precisely so the allow-list stays on the
     * scope that declared it.
     */
    @Test
    void anAllowListScopesOnlyTheEntryItAppearsIn() {
        TraderCommodityFilter filter = filterFor(policy(
                new Entry("metal", List.of("salvage"), null),
                new Entry("metal", List.of("ingots"), List.of("copper", "silver", "gold"))));

        assertTrue(filter.accepts(item("metal", "salvage", "broken sword")),
                "the salvage entry carries no allow-list, so its whole subcategory is in scope");
        assertTrue(filter.accepts(item("metal", "ingots", "copper")));
        assertTrue(filter.accepts(item("metal", "ingots", "COPPER")), "matched case-insensitively");
        assertFalse(filter.accepts(item("metal", "ingots", "tin")),
                "the allow-list must not be widened by the sibling entry that lacks one");
        assertFalse(filter.accepts(item("metal", "wire", "copper")),
                "neither entry covers this subcategory");
    }

    /**
     * {@code describeSaleItem} leaves the category off anything it could not classify. Rails could
     * not price such a row either, so offering it would only produce a refusal.
     */
    @Test
    void anItemWithNoCategoryIsNeverOffered() {
        TraderCommodityFilter filter = filterFor(policy(new Entry("fish", null, null)));

        assertFalse(filter.accepts(item(null, null, "mysterious_trinket")));
        assertFalse(filter.accepts(item("", "raw", "cod")));
    }

    /**
     * A narrowing the item cannot satisfy fails closed: an item naming no subcategory is outside a
     * subcategory-narrowed entry rather than inside every one of them.
     */
    @Test
    void aNarrowedEntryRefusesAnItemThatNamesNoSubcategory() {
        TraderCommodityFilter filter = filterFor(policy(new Entry("metal", List.of("ingots"), null)));

        assertFalse(filter.accepts(item("metal", null, "copper")));
        assertTrue(filter.accepts(item("metal", "ingots", "copper")));
    }

    private static TraderCommodityFilter filterFor(@Nullable AcceptedCommodityPolicy policy) {
        return TraderCommodityFilter.resolve(
                new EconomicNpcTypeDefinition(FISH_TRADER, ROLE, "trader", "fisher",
                        "britannia_mod:fish_trader", true, true, 1L, policy),
                FISH_TRADER, ROLE);
    }

    private static AcceptedCommodityPolicy policy(Entry... entries) {
        return new AcceptedCommodityPolicy(List.of(entries));
    }

    /** The three fields the filter reads out of {@code describeSaleItem}'s output. */
    private static JsonObject item(@Nullable String category, @Nullable String subcategory, String itemName) {
        JsonObject described = new JsonObject();
        if (category != null) described.addProperty("category", category);
        if (subcategory != null) described.addProperty("subcategory", subcategory);
        described.addProperty("item_name", itemName);
        return described;
    }
}

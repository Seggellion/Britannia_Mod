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
 * What a Trader may be offered comes from the SYNCED policy, and since Milestone 7 from nowhere
 * else — Trader Commodity Authority final state.
 *
 * <p>Four states, three of which fail closed. Before M7 the fourth, ABSENT, reached a table that
 * guessed a profession's trade from substrings in its name. M6 measured that guess against the
 * real seeded policies: for five of fifteen professions the table recognised nothing and returned
 * its ANY sentinel, which would have offered them everything classifiable. Those five are pinned
 * below, because they are the cases where the retired fallback was not merely imprecise but
 * inverted.
 */
class TraderCommodityFilterTest {

    private static final String FISH_TRADER = "fish_trader";

    /**
     * The synced policy decides, and the type's NAME is irrelevant to the decision. A
     * {@code fish_trader} handed a wood policy offers wood and refuses fish — which is the whole
     * claim of the project stated as one assertion.
     */
    @Test
    void aSyncedPolicyDecidesAndTheProfessionNameIsIgnored() {
        TraderCommodityFilter filter = filterFor(policy(new Entry("wood", null, null)));

        assertEquals(TraderCommodityFilter.Source.POLICY, filter.source());
        assertNull(filter.refusalCode());
        assertTrue(filter.accepts(item("wood", "logs", "oak")),
                "the policy allows wood, so wood is offered though the name says fish");
        assertFalse(filter.accepts(item("fish", "raw", "cod")),
                "the policy omits fish, so fish is refused though the name says fish");
    }

    /**
     * THE M7 BEHAVIOUR CHANGE. A registry entry with no policy member used to reach the legacy
     * table; it now offers nothing and reports a fault. Rails has emitted the member for every
     * type since M4, so absence is a shard running an old Rails — a deployment problem, and one
     * that must be visible rather than papered over with a guess.
     */
    @Test
    void anAbsentPolicyMemberNowFailsClosedInsteadOfGuessing() {
        TraderCommodityFilter filter = filterFor(null);

        assertEquals(TraderCommodityFilter.Source.ABSENT, filter.source());
        assertEquals(Notice.TRADER_POLICY_MISSING, filter.refusalCode());
        assertFalse(filter.accepts(item("fish", "raw", "cod")),
                "the retired fallback would have offered fish here, from the name alone");
        assertFalse(filter.accepts(item("wood", "logs", "oak")));
    }

    /**
     * An empty policy is a real, deliberate accepts-nothing. It was never allowed to fall back and
     * still is not; it is kept distinct from ABSENT because the two call for different repairs.
     */
    @Test
    void anEmptyPolicyAcceptsNothingAndIsItsOwnDistinctState() {
        TraderCommodityFilter filter = filterFor(policy());

        assertEquals(TraderCommodityFilter.Source.EMPTY_POLICY, filter.source());
        assertEquals(Notice.TRADER_POLICY_MISSING, filter.refusalCode());
        assertFalse(filter.accepts(item("fish", "raw", "cod")),
                "an empty policy must not be read as unrestricted, nor as unconfigured");
    }

    /** Rails drops a type whose stored policy is malformed, so no entry is a fault, not old Rails. */
    @Test
    void aTypeMissingFromTheRegistryFailsClosed() {
        TraderCommodityFilter filter = TraderCommodityFilter.resolve(null, FISH_TRADER);

        assertEquals(TraderCommodityFilter.Source.MISSING, filter.source());
        assertEquals(Notice.TRADER_POLICY_MISSING, filter.refusalCode());
        assertFalse(filter.accepts(item("fish", "raw", "cod")));
    }

    /**
     * A profession nothing has ever heard of gets no benefit of the doubt. Under the retired table
     * an unrecognised name returned ANY; there is no such sentinel left to return.
     */
    @Test
    void anUnknownProfessionCannotFallBackToAnything() {
        TraderCommodityFilter named = TraderCommodityFilter.resolve(
                definition("mysterious_trader", "Mysterious Trader", null), "mysterious_trader");

        assertEquals(TraderCommodityFilter.Source.ABSENT, named.source());
        for (JsonObject probe : List.of(item("fish", "raw", "cod"), item("wood", "logs", "oak"),
                item("metal", "salvage", "broken sword"), item("alcohol", "wine", "red wine"))) {
            assertFalse(named.accepts(probe), "an unknown profession offered " + probe);
        }
    }

    /**
     * The five professions M6 found the retired table could not recognise. It returned ANY for each
     * — the OPPOSITE of their real policy, not a conservative approximation of it. With no policy
     * synced they must now offer nothing at all.
     */
    @Test
    void theFiveProfessionsTheRetiredTableWouldHaveGivenEverythingNowOfferNothing() {
        for (String key : List.of("reagent_trader", "provision_trader", "textile_trader",
                "glass_trader", "scribe_trader")) {
            TraderCommodityFilter filter =
                    TraderCommodityFilter.resolve(definition(key, key, null), key);

            assertEquals(TraderCommodityFilter.Source.ABSENT, filter.source(), key);
            assertEquals(Notice.TRADER_POLICY_MISSING, filter.refusalCode(), key);
            for (JsonObject probe : List.of(item("fish", "raw", "cod"), item("wood", "logs", "oak"),
                    item("ore", "raw", "silver"), item("meat", "raw", "beef"),
                    item("alcohol", "wine", "red wine"))) {
                assertFalse(filter.accepts(probe), key + " offered " + probe + " with no policy");
            }
        }
    }

    /** And with their real seeded policy they take their own category and nothing else. */
    @Test
    void thoseSameFiveTakeOnlyTheirOwnCategoryWhenTheirPolicyIsSynced() {
        for (String category : List.of("reagents", "textile", "glass", "scribe")) {
            TraderCommodityFilter filter = TraderCommodityFilter.resolve(
                    definition(category + "_trader", category, policy(new Entry(category, null, null))),
                    category + "_trader");

            assertEquals(TraderCommodityFilter.Source.POLICY, filter.source());
            assertTrue(filter.accepts(item(category, "any", "something")));
            assertFalse(filter.accepts(item("fish", "raw", "cod")), category + " took fish");
            assertFalse(filter.accepts(item("wood", "logs", "oak")), category + " took wood");
        }
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

        // The third entry's narrowing scopes ITS entry only.
        assertFalse(filter.accepts(item("meat", "raw", "raw pork")));
        assertFalse(filter.accepts(item("fish", "raw", "cod")));
    }

    /**
     * The Salvage shape: two entries differing only by subcategory, one carrying the allow-list.
     * Entry order is contractual precisely so the allow-list stays on the scope that declared it.
     */
    @Test
    void anAllowListScopesOnlyTheEntryItAppearsIn() {
        TraderCommodityFilter filter = filterFor(policy(
                new Entry("metal", List.of("salvage"), null),
                new Entry("metal", List.of("ingots"), List.of("copper", "silver", "gold"))));

        assertTrue(filter.accepts(item("metal", "salvage", "broken sword")));
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

    /** Only a working policy quotes; every other state speaks the same configuration fault. */
    @Test
    void exactlyOneOfTheFourStatesQuotesAndTheOtherThreeReportAFault() {
        assertNull(filterFor(policy(new Entry("fish", null, null))).refusalCode());
        for (TraderCommodityFilter faulted : List.of(filterFor(null), filterFor(policy()),
                TraderCommodityFilter.resolve(null, FISH_TRADER))) {
            assertEquals(Notice.TRADER_POLICY_MISSING, faulted.refusalCode(),
                    faulted.source() + " must report a fault");
        }
    }

    private static TraderCommodityFilter filterFor(@Nullable AcceptedCommodityPolicy policy) {
        return TraderCommodityFilter.resolve(
                definition(FISH_TRADER, "Fish Trader", policy), FISH_TRADER);
    }

    private static EconomicNpcTypeDefinition definition(String key, String displayName,
                                                        @Nullable AcceptedCommodityPolicy policy) {
        return new EconomicNpcTypeDefinition(key, displayName, "trader", "fisher",
                "britannia_mod:" + key, true, true, 1L, policy);
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

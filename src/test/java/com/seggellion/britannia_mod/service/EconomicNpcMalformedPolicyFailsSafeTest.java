package com.seggellion.britannia_mod.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A malformed {@code accepted_commodities} fails SAFE — Trader Commodity Authority, M5.
 *
 * <p>Fail-safe here means the whole section is rejected and the snapshot comes back EMPTY, which
 * leaves every trader offering nothing until a good payload arrives. The alternative — repairing
 * a broken policy into something plausible — would put the mod back in the business of guessing
 * what a trader buys, which is the arrangement this project exists to end. Rejecting wholesale is
 * also already this parser's discipline, so a bad economic payload still cannot take Service NPCs
 * or banking down with it.
 *
 * <p>This is the deliberate counterweight to the forward-compatibility rule. Unknown members are
 * tolerated because a member the mod cannot read cannot mislead it. A KNOWN member in the wrong
 * shape is the opposite case: it can only be acted on by guessing.
 */
class EconomicNpcMalformedPolicyFailsSafeTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            // Not an array at all.
            "\"accepted_commodities\": {\"category\": \"fish\"},",
            "\"accepted_commodities\": \"fish\",",
            // Explicit null. Rails cannot emit one -- the column is NOT NULL with an empty-array
            // default -- so reading it as ABSENCE would re-enable the legacy fallback on a broken
            // payload, which is the single outcome that fallback's one condition exists to prevent.
            "\"accepted_commodities\": null,",
            // An entry that is not an object, or carries no category.
            "\"accepted_commodities\": [\"fish\"],",
            "\"accepted_commodities\": [{}],",
            "\"accepted_commodities\": [{\"subcategories\": [\"raw\"]}],",
            "\"accepted_commodities\": [{\"category\": \"\"}],",
            "\"accepted_commodities\": [{\"category\": 7}],",
            // Present-but-empty arrays. The contract says to use ABSENCE for "no restriction";
            // an empty array names neither extreme and must not be guessed either way.
            "\"accepted_commodities\": [{\"category\": \"fish\", \"subcategories\": []}],",
            "\"accepted_commodities\": [{\"category\": \"fish\", \"commodity_keys\": []}],",
            // Arrays holding something that is not a usable string.
            "\"accepted_commodities\": [{\"category\": \"fish\", \"subcategories\": [\"raw\", 3]}],",
            "\"accepted_commodities\": [{\"category\": \"fish\", \"commodity_keys\": [\"cod\", \"  \"]}],",
            "\"accepted_commodities\": [{\"category\": \"fish\", \"subcategories\": \"raw\"}],",
    })
    void aMalformedPolicyEmptiesTheSnapshotRatherThanBeingRepaired(String policyMember) {
        EconomicNpcRegistrySnapshot snapshot = EconomicNpcRegistryParser.parseBootstrapRoot(
                EconomicNpcAcceptedCommodityPolicyParserTest.root(policyMember + "\n"));

        assertTrue(snapshot.isEmpty(),
                "a malformed policy must offer nothing, never a repaired guess: " + policyMember);
    }

    /**
     * The wholesale catch in {@code parseBootstrapRoot} turns a rejection into an empty snapshot,
     * so "the snapshot is empty" alone cannot tell a rejection from a section that never had any
     * types. Reaching the strict parser directly proves the rejection is real.
     */
    @Test
    void theStrictSectionParserActuallyRejectsRatherThanSilentlyDropping() {
        var section = EconomicNpcAcceptedCommodityPolicyParserTest
                .root("\"accepted_commodities\": [{\"category\": \"fish\", \"commodity_keys\": []}],\n")
                .getAsJsonObject(EconomicNpcRegistryParser.ROOT_KEY);

        assertThrows(IllegalArgumentException.class,
                () -> EconomicNpcRegistryParser.parseSection(section));
    }

    /** The value type refuses the same repairs on its own, so no future caller can invent them. */
    @Test
    void theEntryTypeRefusesAnEmptyOrBlankNarrowingOnItsOwn() {
        assertThrows(IllegalArgumentException.class,
                () -> new AcceptedCommodityPolicy.Entry("fish", java.util.List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new AcceptedCommodityPolicy.Entry("fish", null, java.util.List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AcceptedCommodityPolicy.Entry("fish", java.util.List.of("raw", " "), null));
        assertThrows(IllegalArgumentException.class,
                () -> new AcceptedCommodityPolicy.Entry("  ", null, null));
    }
}

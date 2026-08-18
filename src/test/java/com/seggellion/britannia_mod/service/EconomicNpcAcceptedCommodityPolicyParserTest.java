package com.seggellion.britannia_mod.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading {@code accepted_commodities} off the registry — Trader Commodity Authority, M5.
 *
 * <p>Three properties, and the third is the one worth stating out loud. The member PARSES; its
 * ABSENCE stays legal, because Rails shipped the field before the mod learned to read it and a
 * shard may still be running that Rails; and a MALFORMED policy fails SAFE — the section is
 * rejected wholesale, which offers nothing, rather than being repaired into a guess about what a
 * trader will pay for.
 */
class EconomicNpcAcceptedCommodityPolicyParserTest {

    private static final String REVISION =
            "3f786850e387550fdab836ed7e6dc881de23001b1b1b1b1b1b1b1b1b1b1b1b1b";

    @Test
    void theSalvagePolicyParsesWithItsEntriesAndOptionalArraysIntact() {
        AcceptedCommodityPolicy policy = policyOf("""
                "accepted_commodities": [
                  { "category": "metal", "subcategories": ["salvage"] },
                  { "category": "metal",
                    "subcategories": ["ingots"],
                    "commodity_keys": ["copper", "silver", "gold"] }
                ],
                """);

        assertNotNull(policy);
        assertEquals(2, policy.entries().size());
        assertFalse(policy.isEmpty());

        // Entry ORDER is contractual: these two differ only by subcategory, so an unordered read
        // would land the commodity_keys allow-list on the wrong scope.
        AcceptedCommodityPolicy.Entry salvage = policy.entries().get(0);
        assertEquals("metal", salvage.category());
        assertEquals(List.of("salvage"), salvage.subcategories());
        assertNull(salvage.commodityKeys(), "absent commodity_keys is every commodity in scope");

        AcceptedCommodityPolicy.Entry ingots = policy.entries().get(1);
        assertEquals(List.of("ingots"), ingots.subcategories());
        assertEquals(List.of("copper", "silver", "gold"), ingots.commodityKeys(),
                "string order inside an array is preserved too");
    }

    @Test
    void aWholeCategoryEntryLeavesBothOptionalArraysAbsent() {
        AcceptedCommodityPolicy policy = policyOf("""
                "accepted_commodities": [{ "category": "fish" }],
                """);

        assertNotNull(policy);
        assertEquals(1, policy.entries().size());
        assertNull(policy.entries().get(0).subcategories());
        assertNull(policy.entries().get(0).commodityKeys());
    }

    /** An empty policy is a REAL value meaning accepts-nothing. It must never read as absence. */
    @Test
    void anEmptyPolicyParsesAsAPresentPolicyThatAcceptsNothing() {
        AcceptedCommodityPolicy policy = policyOf("""
                "accepted_commodities": [],
                """);

        assertNotNull(policy, "[] is a policy, not the absence of one");
        assertTrue(policy.isEmpty());
        assertFalse(policy.accepts("fish", "raw", "cod"));
    }

    /**
     * The pre-M4 Rails shape. This must stay legal for as long as any supported Rails can emit it;
     * it is the sole condition under which the legacy category table is consulted.
     */
    @Test
    void anAbsentMemberIsLegalAndDistinguishableFromAnEmptyPolicy() {
        EconomicNpcTypeDefinition definition = parseType("");

        assertNull(definition.acceptedCommodities(),
                "absence must survive as null, not be flattened into an empty policy");
    }

    @Test
    void tolerantOfUnknownMembersInsideAPolicyEntry() {
        // Rails rejects unknown keys at validation, so one arriving is a FUTURE member rather than
        // a corruption -- the same reading the parser gives unknown members everywhere else.
        AcceptedCommodityPolicy policy = policyOf("""
                "accepted_commodities": [
                  { "category": "fish", "future_narrowing": ["deep"], "weight_cap": 12 }
                ],
                """);

        assertNotNull(policy);
        assertEquals("fish", policy.entries().get(0).category());
    }

    private static AcceptedCommodityPolicy policyOf(String policyMember) {
        return parseType(policyMember).acceptedCommodities();
    }

    private static EconomicNpcTypeDefinition parseType(String policyMember) {
        EconomicNpcRegistrySnapshot snapshot =
                EconomicNpcRegistryParser.parseBootstrapRoot(root(policyMember));
        assertFalse(snapshot.isEmpty(), "expected the section to be accepted");
        return snapshot.economicNpcTypes().get("salvage_trader");
    }

    static JsonObject root(String policyMember) {
        return JsonParser.parseString("""
                {
                  "economic_npc_registry": {
                    "schema_version": 1,
                    "economic_npc_types": [
                      {
                        "key": "salvage_trader",
                        "display_name": "Salvage Trader",
                        "kind": "trader",
                        "profession_key": "salvager",
                        "active": true,
                        "spawnable": true,
                        %s
                        "definition_revision": 4
                      }
                    ],
                    "revision": "%s"
                  }
                }
                """.formatted(policyMember, REVISION)).getAsJsonObject();
    }
}

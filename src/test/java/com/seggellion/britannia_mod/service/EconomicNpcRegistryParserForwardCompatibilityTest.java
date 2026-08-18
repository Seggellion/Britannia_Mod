package com.seggellion.britannia_mod.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trader Commodity Authority, Milestone 4 Phase A.
 *
 * <p>Rails ships the accepted-commodity policy BEFORE this mod learns to read
 * it (Decision 6), so a Rails deploy will hand the CURRENT parser registry
 * members it has never heard of. That the parser ignores them was asserted only
 * by a code comment. This pins it.
 *
 * <p>The wholesale catch in {@code parseBootstrapRoot} converts a rejection into
 * an EMPTY snapshot rather than a thrown exception, so "no exception escaped"
 * proves nothing on its own. Each case therefore checks tolerance twice: the
 * public entry point must return the fully populated snapshot, AND
 * {@code parseSection} must be reached directly without throwing, so a
 * rejection cannot hide inside the catch.
 *
 * <p>This test must pass against the parser UNMODIFIED. If it ever fails, the
 * Rails-first deployment order is invalid — re-plan it; do not relax the test.
 */
class EconomicNpcRegistryParserForwardCompatibilityTest {

    private static final String REVISION =
            "3f786850e387550fdab836ed7e6dc881de23001b1b1b1b1b1b1b1b1b1b1b1b1b";

    @Test
    void parsesUnknownMembersOnATypeWithoutDisturbingTheKnownOnes() {
        EconomicNpcRegistrySnapshot baseline = parse(bootstrapRoot(KNOWN_MEMBERS_ONLY));
        EconomicNpcRegistrySnapshot withUnknowns = parse(bootstrapRoot(WITH_FUTURE_MEMBERS));

        // 1. Parsing succeeded — not the empty snapshot the catch would yield.
        assertFalse(withUnknowns.isEmpty(), "unknown members must not empty the snapshot");
        assertEquals(1, withUnknowns.economicNpcTypes().size());

        // 2. The known fields carry their expected values.
        EconomicNpcTypeDefinition fishTrader = withUnknowns.economicNpcTypes().get("fish_trader");
        assertNotNull(fishTrader, "fish_trader must survive the unknown members");
        assertEquals("fish_trader", fishTrader.key());
        assertEquals("Fish Trader", fishTrader.displayName());
        assertEquals("trader", fishTrader.kind());
        assertEquals("fisher", fishTrader.professionKey());
        assertEquals("britannia_mod:fish_trader", fishTrader.minecraftEntityTypeKey());
        assertTrue(fishTrader.active());
        assertTrue(fishTrader.spawnable());
        assertEquals(7L, fishTrader.definitionRevision());
        assertEquals(EconomicNpcRegistrySnapshot.SUPPORTED_SCHEMA_VERSION, withUnknowns.schemaVersion());
        assertEquals(REVISION, withUnknowns.revision());

        // 3. The unknown members changed NOTHING. Records compare by value, so
        //    one equality covers every parsed member at once — including any
        //    member a future reader adds to the definition.
        assertEquals(baseline, withUnknowns,
                "a payload differing only by unknown members must parse identically");
    }

    @Test
    void reachesTheStrictSectionParserWithoutThrowingOnUnknownMembers() {
        JsonObject section = bootstrapRoot(WITH_FUTURE_MEMBERS)
                .getAsJsonObject(EconomicNpcRegistryParser.ROOT_KEY);

        // 4. No exception, and therefore no rejection silently absorbed by the
        //    wholesale catch in parseBootstrapRoot.
        EconomicNpcRegistrySnapshot snapshot =
                assertDoesNotThrow(() -> EconomicNpcRegistryParser.parseSection(section));

        assertEquals(1, snapshot.economicNpcTypes().size());
        assertEquals("Fish Trader", snapshot.economicNpcTypes().get("fish_trader").displayName());
    }

    @Test
    void toleratesUnknownMembersOnTheSectionItselfNotOnlyOnTypes() {
        JsonObject root = bootstrapRoot(KNOWN_MEMBERS_ONLY);
        JsonObject section = root.getAsJsonObject(EconomicNpcRegistryParser.ROOT_KEY);
        section.add("future_section_member", JsonParser.parseString("{\"policy_digest\":\"abc\"}"));
        section.addProperty("another_future_scalar", 42);

        EconomicNpcRegistrySnapshot snapshot = parse(root);

        assertFalse(snapshot.isEmpty());
        assertEquals(parse(bootstrapRoot(KNOWN_MEMBERS_ONLY)), snapshot);
    }

    @Test
    void toleratesTheShardMembersTheSerializerAlreadyEmits() {
        // EconomicNpcRegistrySerializer#serialize_type already merges these
        // three whenever a shard is present, so this property is load-bearing
        // in production TODAY, not only for the coming policy field.
        EconomicNpcRegistrySnapshot snapshot = parse(bootstrapRoot("""
                ,
                "shard_enabled": false,
                "policy_revision": 12,
                "effective_revision": 34
                """));

        assertFalse(snapshot.isEmpty());
        assertEquals(parse(bootstrapRoot(KNOWN_MEMBERS_ONLY)), snapshot);
    }

    private static EconomicNpcRegistrySnapshot parse(JsonObject root) {
        return EconomicNpcRegistryParser.parseBootstrapRoot(root);
    }

    private static final String KNOWN_MEMBERS_ONLY = "";

    /**
     * The accepted-commodity policy in the exact shape frozen at M2 — both the
     * single-entry form and the two-entry Salvage form with the optional
     * {@code subcategories} and {@code commodity_keys} arrays — plus an
     * arbitrary nested member, so tolerance is proven against real nesting and
     * not merely against a flat scalar.
     */
    private static final String WITH_FUTURE_MEMBERS = """
            ,
            "accepted_commodities": [
              { "category": "metal", "subcategories": ["salvage"] },
              { "category": "metal",
                "subcategories": ["ingots"],
                "commodity_keys": ["copper", "silver", "gold"] }
            ],
            "some_future_member": { "arbitrary": true, "nested": [1, 2, 3] },
            "future_scalar": null
            """;

    private static JsonObject bootstrapRoot(String extraTypeMembers) {
        String json = """
                {
                  "cities": [],
                  "economic_npc_registry": {
                    "schema_version": 1,
                    "economic_npc_types": [
                      {
                        "key": "fish_trader",
                        "display_name": "Fish Trader",
                        "kind": "trader",
                        "profession_key": "fisher",
                        "minecraft_entity_type_key": "britannia_mod:fish_trader",
                        "active": true,
                        "spawnable": true,
                        "definition_revision": 7%s
                      }
                    ],
                    "revision": "%s"
                  }
                }
                """.formatted(extraTypeMembers, REVISION);
        return JsonParser.parseString(json).getAsJsonObject();
    }
}

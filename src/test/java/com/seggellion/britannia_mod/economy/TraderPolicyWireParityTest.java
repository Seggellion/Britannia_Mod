package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryParser;
import com.seggellion.britannia_mod.service.EconomicNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Trader Commodity Authority Milestone 6: one policy definition, the same meaning on both sides.
 *
 * <p>M3 pinned what Rails does with a policy and M5 pinned what this mod does with one. Neither
 * proved they AGREE, and they are independent implementations of one contract, so agreement is a
 * real risk rather than a formality.
 *
 * <p>The fixture is not hand-written. It is produced by Rails'
 * {@code TraderPolicyCrossRepositoryParityTest} from the REAL seeded trader definitions, through
 * the REAL {@code EconomicNpcRegistrySerializer}, and it carries Rails' OWN verdict for every
 * trader against every probe commodity. This test replays those bytes through the real
 * {@link EconomicNpcRegistryParser} and the real {@link TraderCommodityFilter} and demands the same
 * answer in every cell. A fixture written by hand to match the mod would have proven nothing except
 * that the mod agrees with itself.
 *
 * <p>SCOPE OF THE CLAIM — worth stating precisely, because the half that is NOT proven here is
 * where the known defect lives. This proves POLICY SEMANTICS parity: given the same
 * (category, subcategory, item_name) triple, both sides return the same verdict. It does NOT prove
 * RESOLUTION parity — that the triple the mod derives from an ItemStack equals the triple Rails
 * derives from the resolved {@code city_commodities} row. See
 * {@link TraderPolicyResolutionKeyParityTest} for that second half.
 *
 * <p>Regenerating the fixture is documented in the Rails test's header comment; the playbook's M6
 * section records the procedure and why the transfer is a copied file rather than a live call.
 */
class TraderPolicyWireParityTest {
    private static final String FIXTURE = "wire_contract/trader_policy_parity.json";

    private static JsonObject fixture;
    private static EconomicNpcRegistrySnapshot snapshot;
    private static List<Probe> probes;

    private record Probe(String category, String subcategory, String itemName) {
        JsonObject asDescribedItem() {
            JsonObject described = new JsonObject();
            if (category != null) described.addProperty("category", category);
            if (subcategory != null) described.addProperty("subcategory", subcategory);
            described.addProperty("item_name", itemName);
            return described;
        }

        @Override
        public String toString() {
            return category + "/" + subcategory + "/" + itemName;
        }
    }

    @BeforeAll
    static void loadRailsFixture() {
        fixture = JsonParser.parseString(readFixture()).getAsJsonObject();

        // The registry section arrives as Rails emits it. Wrapping it in a bootstrap root is the
        // only adaptation made, and it is the same wrapping the live bootstrap performs.
        JsonObject root = new JsonObject();
        root.add(EconomicNpcRegistryParser.ROOT_KEY, fixture.getAsJsonObject("registry"));
        snapshot = EconomicNpcRegistryParser.parseBootstrapRoot(root);

        probes = new ArrayList<>();
        for (JsonElement element : fixture.getAsJsonArray("probes")) {
            JsonObject probe = element.getAsJsonObject();
            probes.add(new Probe(string(probe, "category"), string(probe, "subcategory"),
                    string(probe, "item_name")));
        }
    }

    @Test
    void railsRealSerializerOutputParsesWholesale() {
        assertFalse(snapshot.isEmpty(),
                "Rails' own registry output must parse; an empty snapshot means the parser rejected"
                        + " the very bytes production will hand it");
        assertEquals(fixture.getAsJsonObject("registry").getAsJsonArray("economic_npc_types").size(),
                snapshot.economicNpcTypes().size(),
                "every type Rails published must survive parsing");
        assertEquals(EconomicNpcRegistrySnapshot.SUPPORTED_SCHEMA_VERSION, snapshot.schemaVersion());
    }

    /** THE parity assertion: every trader, every probe, both sides. */
    @Test
    void everyTraderAgreesWithRailsOnEveryProbe() {
        JsonObject verdicts = fixture.getAsJsonObject("verdicts");
        assertFalse(verdicts.entrySet().isEmpty(), "the fixture carries no verdicts to compare");

        int compared = 0;
        List<String> disagreements = new ArrayList<>();
        for (String traderKey : verdicts.keySet()) {
            EconomicNpcTypeDefinition definition = snapshot.economicNpcTypes().get(traderKey);
            assertNotNull(definition, "Rails published a verdict for " + traderKey
                    + " but the parser produced no definition for it");
            TraderCommodityFilter filter = TraderCommodityFilter.resolve(definition, traderKey);

            JsonArray expected = verdicts.getAsJsonArray(traderKey);
            assertEquals(probes.size(), expected.size(),
                    "verdict row for " + traderKey + " is not aligned with the probe list");

            for (int index = 0; index < probes.size(); index++) {
                Probe probe = probes.get(index);
                boolean rails = expected.get(index).getAsBoolean();
                boolean mod = filter.accepts(probe.asDescribedItem());
                compared++;
                if (rails != mod) {
                    disagreements.add(traderKey + " " + probe + ": Rails=" + rails + " mod=" + mod);
                }
            }
        }

        // Reported together rather than one at a time: a systematic divergence shows its shape in
        // the whole list, and stopping at the first cell would hide it.
        assertTrue(disagreements.isEmpty(),
                () -> disagreements.size() + " of " + probes.size() + " cells disagree:\n"
                        + String.join("\n", disagreements));
        assertEquals(verdicts.size() * probes.size(), compared, "the whole matrix must be walked");
    }

    /**
     * The parked profession is the case the whole ABSENT-vs-EMPTY distinction exists for: Rails
     * publishes a real empty policy for it, and the mod must fail closed rather than reach for the
     * legacy table that would happily hand it goods.
     */
    @Test
    void railsEmptyPolicyFailsClosedHereAndNeverReachesTheLegacyTable() {
        EconomicNpcTypeDefinition parked = snapshot.economicNpcTypes().get("provision_trader");
        assertNotNull(parked, "the parked trader must still be published; [] is a value, not a gap");
        assertNotNull(parked.acceptedCommodities(),
                "Rails emits the member for every type, so it must parse as PRESENT-and-empty");
        assertTrue(parked.acceptedCommodities().isEmpty());

        TraderCommodityFilter filter = TraderCommodityFilter.resolve(parked, "provision_trader");

        assertEquals(TraderCommodityFilter.Source.EMPTY_POLICY, filter.source());
        for (Probe probe : probes) {
            assertFalse(filter.accepts(probe.asDescribedItem()),
                    "the parked trader accepted " + probe);
        }
    }

    /**
     * Wire-shape parity, asserted against Rails' real bytes rather than a description of them.
     * ABSENT and {@code []} must stay distinguishable, or the fallback condition loses its meaning.
     */
    @Test
    void everyPublishedTypeCarriesThePolicyMemberSoAbsenceKeepsItsOneMeaning() {
        JsonArray types = fixture.getAsJsonObject("registry").getAsJsonArray("economic_npc_types");

        for (JsonElement element : types) {
            JsonObject type = element.getAsJsonObject();
            String key = type.get("key").getAsString();
            assertTrue(type.has("accepted_commodities"),
                    key + " is missing the policy member; absence must mean 'old Rails' and nothing"
                            + " else, which only holds if a current Rails always emits it");
            assertTrue(type.get("accepted_commodities").isJsonArray(),
                    key + " must carry the policy as an array");
            assertNotNull(snapshot.economicNpcTypes().get(key).acceptedCommodities(),
                    key + " parsed as ABSENT, which would wrongly arm the legacy fallback");
        }
    }

    /**
     * Object KEY order is explicitly not a contract. Re-serialising every type's members in
     * reverse and re-parsing must change nothing — if it did, some reader would be depending on
     * position rather than on name.
     */
    @Test
    void noConsumerDependsOnJsonObjectKeyOrder() {
        JsonObject registry = fixture.getAsJsonObject("registry").deepCopy();
        JsonArray reordered = new JsonArray();
        for (JsonElement element : registry.getAsJsonArray("economic_npc_types")) {
            reordered.add(reverseKeys(element.getAsJsonObject()));
        }
        registry.add("economic_npc_types", reordered);
        JsonObject root = new JsonObject();
        root.add(EconomicNpcRegistryParser.ROOT_KEY, registry);

        assertEquals(snapshot, EconomicNpcRegistryParser.parseBootstrapRoot(root),
                "member order changed the parse, so something is reading by position");
    }

    /**
     * ARRAY order, by contrast, IS contractual. The Salvage policy's two entries differ only by
     * subcategory, so reversing them moves the commodity_keys allow-list onto the scope it was
     * written to leave alone — and the mod must be sensitive to that, not blind to it.
     */
    @Test
    void arrayOrderIsLoadBearingWhereTheContractSaysItIs() {
        EconomicNpcTypeDefinition salvage = snapshot.economicNpcTypes().get("salvage_trader");
        assertNotNull(salvage);
        var entries = salvage.acceptedCommodities().entries();

        assertEquals(2, entries.size());
        assertEquals(List.of("salvage"), entries.get(0).subcategories());
        assertEquals(List.of("ingots"), entries.get(1).subcategories());
        assertEquals(List.of("copper", "silver", "gold"), entries.get(1).commodityKeys(),
                "string order inside the allow-list survives the round trip too");
        assertEquals(null, entries.get(0).commodityKeys(),
                "the salvage entry carries no allow-list; absence must not become an empty list");
    }

    /** Future registry members must still be ignored now that one of them has been adopted. */
    @Test
    void unknownFutureMembersOnRailsRealPayloadAreStillTolerated() {
        JsonObject registry = fixture.getAsJsonObject("registry").deepCopy();
        registry.addProperty("a_future_section_member", 42);
        for (JsonElement element : registry.getAsJsonArray("economic_npc_types")) {
            element.getAsJsonObject().addProperty("a_future_type_member", "ignored");
        }
        JsonObject root = new JsonObject();
        root.add(EconomicNpcRegistryParser.ROOT_KEY, registry);

        assertEquals(snapshot, EconomicNpcRegistryParser.parseBootstrapRoot(root));
    }

    private static JsonObject reverseKeys(JsonObject original) {
        List<String> keys = new ArrayList<>(original.keySet());
        JsonObject reversed = new JsonObject();
        for (int index = keys.size() - 1; index >= 0; index--) {
            reversed.add(keys.get(index), original.get(keys.get(index)));
        }
        return reversed;
    }

    private static String string(JsonObject holder, String member) {
        return holder.has(member) && !holder.get(member).isJsonNull()
                ? holder.get(member).getAsString() : null;
    }

    private static String readFixture() {
        try (InputStream in = TraderPolicyWireParityTest.class.getClassLoader()
                .getResourceAsStream(FIXTURE)) {
            if (in == null) {
                fail("missing checked-in Rails parity fixture: " + FIXTURE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}

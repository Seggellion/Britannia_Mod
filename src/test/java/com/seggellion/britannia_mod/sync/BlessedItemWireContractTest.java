package com.seggellion.britannia_mod.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starfarer M10. This shard's half of the {@code GET /api/blessed_items} contract.
 *
 * <p>Until M10 the payload shape was pinned on the Rails side only, by
 * {@code test/controllers/api/blessed_items_contract_test.rb}, and by nothing here -- the parse
 * lived inline inside a network call and could not be exercised without a socket. A renamed or
 * dropped key would therefore have surfaced for the first time in production, as a player
 * receiving nothing and a warning in a log nobody was reading.
 *
 * <p>{@link #RAILS_PAYLOAD} is not hand-written. It was captured verbatim from the running Rails
 * application, and deliberately contains BOTH row shapes the feed really produces:
 *
 * <ul>
 *   <li>a Redeem entitlement Rails materialized for this shard, carrying the M4 lifecycle
 *       fields;</li>
 *   <li>a legacy purchased deed Rails declined to materialize, carrying both of them as an
 *       explicit JSON {@code null} -- which is the case {@code optionalString} exists for, and
 *       which no hand-written fixture would have thought to include.</li>
 * </ul>
 *
 * <p>Note what is absent: {@code redeem_key}. Redeem campaign membership is Rails/account
 * semantics, and this shard neither receives nor needs it. If it ever appears in this payload,
 * the {@code unknownKeysAreIgnored} test below says what happens: nothing.
 */
class BlessedItemWireContractTest {

    /** Captured verbatim from Rails. Do not hand-edit; re-capture if the contract changes. */
    private static final String RAILS_PAYLOAD = """
        [{"item":"britannia_mod:starfarers_medallion",\
        "deed_id":"d0155a9b-1679-4215-9c59-7f3245172f05","used":false,\
        "instance_uuid":"28c7251f-ef77-4ace-b452-94bdc2e9bf52","state":"pending"},\
        {"item":"Castle Deed","deed_id":"0d5786fa-80c4-46b7-b119-93d66486a1ce","used":true,\
        "instance_uuid":null,"state":null}]""";

    @Test
    @DisplayName("a real Rails payload parses into exactly the rows it describes")
    void aRealRailsPayloadParsesIntoExactlyTheRowsItDescribes() {
        List<BlessedItemSyncAPI.BlessedRow> rows = BlessedItemSyncAPI.parse(RAILS_PAYLOAD);

        assertEquals(2, rows.size());

        BlessedItemSyncAPI.BlessedRow medallion = rows.get(0);
        assertEquals("britannia_mod:starfarers_medallion", medallion.itemName());
        assertEquals("d0155a9b-1679-4215-9c59-7f3245172f05", medallion.deedId(),
            "deed_id is the ENTITLEMENT identity and is never the instance identity");
        assertFalse(medallion.used());
        assertEquals("28c7251f-ef77-4ace-b452-94bdc2e9bf52", medallion.instanceUuid());
        assertEquals("pending", medallion.state());
        assertTrue(medallion.hasLifecycle(), "a materialized row is deliverable");
    }

    @Test
    @DisplayName("a purchase Rails declined to materialize carries no lifecycle and is not deliverable")
    void aPurchaseRailsDeclinedToMaterializeCarriesNoLifecycle() {
        BlessedItemSyncAPI.BlessedRow deed = BlessedItemSyncAPI.parse(RAILS_PAYLOAD).get(1);

        assertEquals("Castle Deed", deed.itemName());
        assertTrue(deed.used());
        assertNull(deed.instanceUuid(), "an explicit JSON null is a null, not the string \"null\"");
        assertNull(deed.state());
        assertFalse(deed.hasLifecycle(),
            "no materialization identity means nothing here authorises creating an item");
    }

    @Test
    @DisplayName("a pre-M4 Rails build, which sends only the three legacy keys, still parses")
    void aPreM4RailsBuildStillParses() {
        List<BlessedItemSyncAPI.BlessedRow> rows = BlessedItemSyncAPI.parse(
            "[{\"item\":\"britannia_mod:starfarers_medallion\",\"deed_id\":\"abc\",\"used\":false}]");

        assertEquals(1, rows.size());
        assertNull(rows.get(0).instanceUuid());
        assertFalse(rows.get(0).hasLifecycle(),
            "an older Rails cannot authorise a lifecycle delivery it does not know about");
    }

    @Test
    @DisplayName("a blank instance_uuid is treated as absent rather than as an identity")
    void aBlankInstanceUuidIsTreatedAsAbsent() {
        BlessedItemSyncAPI.BlessedRow row = BlessedItemSyncAPI.parse(
            "[{\"item\":\"x\",\"deed_id\":\"d\",\"used\":false,"
                + "\"instance_uuid\":\"   \",\"state\":\"pending\"}]").get(0);

        assertNull(row.instanceUuid());
        assertFalse(row.hasLifecycle());
    }

    @Test
    @DisplayName("unknown keys are ignored, so Rails may add fields without breaking older shards")
    void unknownKeysAreIgnored() {
        BlessedItemSyncAPI.BlessedRow row = BlessedItemSyncAPI.parse(
            "[{\"item\":\"x\",\"deed_id\":\"d\",\"used\":false,\"instance_uuid\":null,"
                + "\"state\":null,\"redeem_key\":\"starfarer\",\"future_field\":42}]").get(0);

        assertEquals("x", row.itemName());
        assertNull(row.state());
    }

    @Test
    @DisplayName("an empty catalogue is an empty list, never a failure")
    void anEmptyCatalogueIsAnEmptyList() {
        assertTrue(BlessedItemSyncAPI.parse("[]").isEmpty());
    }

    @Test
    @DisplayName("a row missing a required legacy key is a protocol violation, not a guess")
    void aRowMissingARequiredLegacyKeyIsAProtocolViolation() {
        assertThrows(RuntimeException.class,
            () -> BlessedItemSyncAPI.parse("[{\"deed_id\":\"d\",\"used\":false}]"),
            "item is required; inventing one would deliver the wrong thing");
        assertThrows(RuntimeException.class,
            () -> BlessedItemSyncAPI.parse("[{\"item\":\"x\",\"used\":false}]"),
            "deed_id is required; without it the row names no entitlement");
    }
}

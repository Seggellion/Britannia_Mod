package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BankAccountOpenedS2CPayloadTest {
    @Test
    void roundTripsWithACityDisplayName() {
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void roundTripsTheRefreshFlag() {
        // Milestone 17: the one bit that stops a late refresh re-opening a dismissed interface.
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(), true
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        BankAccountOpenedS2CPayload decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertEquals(true, decoded.refresh());
    }

    @Test
    void roundTripsWithNoCityDisplayNameForGlobalMode() {
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, null, 250, 0.0, 0, 0, 0, List.of(), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        BankAccountOpenedS2CPayload decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertNull(decoded.cityDisplayName());
    }

    @Test
    void roundTripsWithARealBankItemsList() {
        BankItemSummary first = BankItemSummary.withoutIdentity(UUID.randomUUID(), 2.5);
        BankItemSummary second = BankItemSummary.withoutIdentity(UUID.randomUUID(), 0.0);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(first, second), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        BankAccountOpenedS2CPayload decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded);
        assertEquals(List.of(first, second), decoded.bankItems());
        assertEquals(42, decoded.entityId());
    }

    // ---------- The stored-cheque link across the wire ----------

    @Test
    void roundTripsTheChequeLinkInAllThreeStates() {
        BankItemSummary cashable = new BankItemSummary(UUID.randomUUID(), 1.0, "Bank Cheque", 1, null, true);
        BankItemSummary spent = new BankItemSummary(UUID.randomUUID(), 1.0, "Bank Cheque", 1, null, false);
        BankItemSummary ordinary = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, "Diamond", 5, null);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(cashable, spent, ordinary), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        List<BankItemSummary> decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems();
        assertEquals(Boolean.TRUE, decoded.get(0).chequeRedeemable());
        assertEquals(Boolean.FALSE, decoded.get(1).chequeRedeemable());
        // The distinction that matters: "Rails said no" and "Rails said nothing" must not
        // collapse into each other on the wire, because only one of them can ever become true.
        assertNull(decoded.get(2).chequeRedeemable(), "an ordinary item must arrive with no link at all");
        assertEquals(payload, BankAccountOpenedS2CPayload.STREAM_CODEC.decode(
                encodeFresh(payload)), "the whole payload must survive intact");
    }

    private static FriendlyByteBuf encodeFresh(BankAccountOpenedS2CPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);
        return buffer;
    }

    // ---------- Milestone 18: item identity across the wire ----------

    @Test
    void roundTripsItemIdentityIntact() {
        BankItemSummary named = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 2.5, "Gilded Arrow", 64, "minecraft:arrow");
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(named), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        BankItemSummary decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems().get(0);
        assertEquals("Gilded Arrow", decoded.displayName());
        assertEquals(64, decoded.count());
        assertEquals("Gilded Arrow x64", decoded.describe());
        // Milestone 10: the registry id crosses the wire too.
        assertEquals("minecraft:arrow", decoded.itemKey());
    }

    @Test
    void anAbsentItemKeySurvivesTheWireAsAbsent() {
        BankItemSummary keyless = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, "Old Deposit", 3, null);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(keyless), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        BankItemSummary decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems().get(0);
        assertNull(decoded.itemKey(), "absent must not arrive as an empty string");
        assertEquals("Old Deposit", decoded.displayName());
    }

    /**
     * The case that must never regress: a row deposited before item identity existed. Absent has
     * to survive the wire as absent, not arrive as an empty string that renders as a blank line.
     */
    @Test
    void roundTripsAnItemWithNoIdentityAsStillHavingNone() {
        BankItemSummary nameless = BankItemSummary.withoutIdentity(UUID.randomUUID(), 2.5);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(nameless), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        BankItemSummary decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems().get(0);
        assertNull(decoded.displayName());
        assertNull(decoded.count());
        assertEquals("Stored item", decoded.describe());
    }

    @Test
    void roundTripsItemsWhoseIdentityIsOnlyPartlyKnown() {
        BankItemSummary nameOnly = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, "Solitary Ledger", null, null);
        BankItemSummary countOnly = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, null, 12, null);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(nameOnly, countOnly), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        List<BankItemSummary> decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems();
        assertEquals("Solitary Ledger", decoded.get(0).describe());
        assertNull(decoded.get(0).count());
        assertEquals("Stored item x12", decoded.get(1).describe());
        assertNull(decoded.get(1).displayName());
    }

    @Test
    void roundTripsAMultiByteNameWithoutTruncatingIt() {
        String name = "鉄の剣 §cCursed";
        BankItemSummary item = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, name, 1, null);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(item), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        BankItemSummary decoded = BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems().get(0);
        assertEquals(name, decoded.displayName());
        // A count of exactly one distinguishes nothing and is deliberately not shown.
        assertEquals(name, decoded.describe());
    }

    /** Rails' longest permitted name, in four-byte characters -- the packet bound must clear it. */
    @Test
    void roundTripsANameAtRailsOwnLengthLimit() {
        String name = "𝕬".repeat(255);
        BankItemSummary item = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, name, null, null);
        BankAccountOpenedS2CPayload payload = new BankAccountOpenedS2CPayload(
                "Aldric", "female", 42, "Britain", 250, 12.5, 3, 47, 92, List.of(item), false
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankAccountOpenedS2CPayload.STREAM_CODEC.encode(buffer, payload);

        assertEquals(name, BankAccountOpenedS2CPayload.STREAM_CODEC.decode(buffer).bankItems().get(0).displayName());
    }

    @Test
    void treatsABlankNameAndANonPositiveCountAsNoIdentityAtAll() {
        BankItemSummary item = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, "   ", 0, "  ");
        assertNull(item.displayName());
        assertNull(item.count());
        // Milestone 10: a blank key canonicalizes to absent, same rule as the name.
        assertNull(item.itemKey());
        assertEquals("Stored item", item.describe());
    }
}

package com.seggellion.britannia_mod.network.payload;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Milestone 18, security matrix: the decode-time guards on every banking request payload that
 * carries a <em>value</em> rather than only a selection reference.
 *
 * <p>These are the "false amount" and "unsupported currency key" rows of the matrix, tested at
 * the layer that actually owns them. A modified client can put any bytes on the wire; the codec
 * is the first thing that reads them, and rejecting there means the malformed value never
 * reaches a proxy service, a Rails request, or an inventory at all.
 *
 * <p>{@code BankDepositRequestC2SPayloadTest} covers the slot-index guard that established this
 * precedent. This class covers everything added since, which had none.
 */
class BankRequestPayloadGuardTest {

    // ---------- Currency withdrawal: the amount ----------

    @Test
    void currencyWithdrawalRoundTripsAndRejectsNonPositiveAmounts() {
        BankCurrencyWithdrawalRequestC2SPayload valid = new BankCurrencyWithdrawalRequestC2SPayload(42, "gold", 500);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankCurrencyWithdrawalRequestC2SPayload.STREAM_CODEC.encode(buffer, valid);
        assertEquals(valid, BankCurrencyWithdrawalRequestC2SPayload.STREAM_CODEC.decode(buffer));

        for (int bad : new int[]{0, -1, Integer.MIN_VALUE}) {
            FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
            hostile.writeVarInt(42);
            net.minecraft.network.codec.ByteBufCodecs.stringUtf8(16).encode(hostile, "gold");
            hostile.writeVarInt(bad);
            assertThrows(IllegalArgumentException.class,
                    () -> BankCurrencyWithdrawalRequestC2SPayload.STREAM_CODEC.decode(hostile),
                    "an amount of " + bad + " must never decode");
        }
    }

    @Test
    void currencyWithdrawalAlsoRefusesToEncodeANonPositiveAmount() {
        // Symmetric guard: a bug on this side must not put a value on the wire that the other
        // side is required to reject.
        BankCurrencyWithdrawalRequestC2SPayload payload = new BankCurrencyWithdrawalRequestC2SPayload(42, "gold", 0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        assertThrows(IllegalArgumentException.class,
                () -> BankCurrencyWithdrawalRequestC2SPayload.STREAM_CODEC.encode(buffer, payload));
    }

    /**
     * The currency key deliberately is <b>not</b> validated at decode here, unlike cheque
     * issuance's. This test pins that difference so it stays a decision rather than drift: an
     * unknown key is refused locally by {@code BankingCurrencyWithdrawalProxyService} before any
     * Rails call ({@code unsupported_currency_key}), which is a clean rejection either way, and
     * keeping the codec free of registry lookups keeps it a pure wire concern.
     */
    @Test
    void currencyWithdrawalCarriesAnUnknownKeyThroughToTheServersOwnRefusal() {
        BankCurrencyWithdrawalRequestC2SPayload payload =
                new BankCurrencyWithdrawalRequestC2SPayload(42, "platinum", 5);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankCurrencyWithdrawalRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals("platinum", BankCurrencyWithdrawalRequestC2SPayload.STREAM_CODEC.decode(buffer).currencyKey());
    }

    // ---------- Cheque issuance: the amount and the denomination ----------

    @Test
    void chequeIssuanceRoundTripsAndRejectsNonPositiveAmounts() {
        BankChequeIssuanceRequestC2SPayload valid = new BankChequeIssuanceRequestC2SPayload(42, 500, "gold");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankChequeIssuanceRequestC2SPayload.STREAM_CODEC.encode(buffer, valid);
        assertEquals(valid, BankChequeIssuanceRequestC2SPayload.STREAM_CODEC.decode(buffer));

        for (int bad : new int[]{0, -1}) {
            FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
            hostile.writeVarInt(42);
            hostile.writeVarInt(bad);
            hostile.writeUtf("gold", 16);
            assertThrows(IllegalArgumentException.class,
                    () -> BankChequeIssuanceRequestC2SPayload.STREAM_CODEC.decode(hostile));
        }
    }

    @Test
    void chequeIssuanceRejectsADenominationThatDoesNotExist() {
        // This one DOES validate at decode, because the key selects a conversion unit -- an
        // unknown one has no meaning to resolve later.
        FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
        hostile.writeVarInt(42);
        hostile.writeVarInt(500);
        hostile.writeUtf("platinum", 16);
        assertThrows(IllegalArgumentException.class,
                () -> BankChequeIssuanceRequestC2SPayload.STREAM_CODEC.decode(hostile));
    }

    // ---------- The two cheque-cashing packets ----------

    @Test
    void packSideRedemptionRejectsANegativeSlotIndex() {
        FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
        hostile.writeVarInt(42);
        hostile.writeVarInt(-1);
        assertThrows(IllegalArgumentException.class,
                () -> BankChequeRedemptionRequestC2SPayload.STREAM_CODEC.decode(hostile));
    }

    @Test
    void packSideRedemptionRoundTripsAValidSlot() {
        BankChequeRedemptionRequestC2SPayload payload = new BankChequeRedemptionRequestC2SPayload(42, 7);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankChequeRedemptionRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);
        assertEquals(payload, BankChequeRedemptionRequestC2SPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void storedRedemptionRoundTripsTheRowIdAndCarriesNothingElse() {
        UUID bankItemPublicId = UUID.randomUUID();
        BankStoredChequeRedemptionRequestC2SPayload payload =
                new BankStoredChequeRedemptionRequestC2SPayload(42, bankItemPublicId);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BankStoredChequeRedemptionRequestC2SPayload.STREAM_CODEC.encode(buffer, payload);

        BankStoredChequeRedemptionRequestC2SPayload decoded =
                BankStoredChequeRedemptionRequestC2SPayload.STREAM_CODEC.decode(buffer);
        assertEquals(bankItemPublicId, decoded.bankItemPublicId());
        // No amount and no cheque id: this packet cannot express a value claim at all, which is
        // why there is nothing here to forge. Rails derives both from the row it already holds.
        assertEquals(2, BankStoredChequeRedemptionRequestC2SPayload.class.getRecordComponents().length);
    }
}

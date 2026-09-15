package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TraderSaleReceiptContractTest {
    private static final UUID PLAYER = UUID.fromString("4af2cc45-dc5a-4880-9cc3-5509286cff12");
    private static final String KEY = "sale:player:trader:tick:signature:request";

    @Test
    void exactProductionEnvelopeNormalizesNestedIdentityAndThreeCopperGrant() {
        JsonObject response = productionResponse();

        var parsed = TraderSaleReceiptContract.parse(response);
        assertTrue(parsed.accepted(), parsed.rejection());
        assertEquals("1474", parsed.receipt().transactionId());
        assertEquals(KEY, parsed.receipt().idempotencyKey());
        assertEquals(PLAYER, parsed.receipt().playerUuid());
        assertEquals("sell", parsed.receipt().transactionType());
        assertEquals(0, parsed.receipt().gold());
        assertEquals(0, parsed.receipt().silver());
        assertEquals(3, parsed.receipt().copper());

        var sale = ServerEconomyService.resultFromReceipt(response);
        assertTrue(sale.success());
        assertEquals("1474", sale.receiptId());
        assertEquals(3, sale.copper());
    }

    @Test
    void flatAuthoritativeReceiptRemainsCompatibleAndNormalizesCompactUuid() {
        JsonObject response = JsonParser.parseString("""
                {
                  "id": 1474,
                  "transaction_id": 1474,
                  "idempotency_key": "%s",
                  "player_uuid": "4af2cc45dc5a48809cc35509286cff12",
                  "transaction_type": "SELL",
                  "currency_grant": {"copper": 3},
                  "idempotent_replay": true
                }
                """.formatted(KEY)).getAsJsonObject();

        var parsed = TraderSaleReceiptContract.parse(response);
        assertTrue(parsed.accepted(), parsed.rejection());
        assertEquals(PLAYER, parsed.receipt().playerUuid());
        assertTrue(parsed.receipt().idempotentReplay());
    }

    @Test
    void intendedLegacyPayoutAliasesRemainCompatibleWhenIdentityIsAuthoritative() {
        for (String payout : new String[]{
                "\"payout\":{\"copper\":3}",
                "\"currency\":{\"copper\":3}",
                "\"total_copper\":3"
        }) {
            JsonObject response = JsonParser.parseString("""
                    {
                      "transaction_id": 1474,
                      "idempotency_key": "%s",
                      "player_uuid": "%s",
                      "transaction_type": "sell",
                      %s
                    }
                    """.formatted(KEY, PLAYER, payout)).getAsJsonObject();
            var parsed = TraderSaleReceiptContract.parse(response);
            assertTrue(parsed.accepted(), parsed.rejection());
            assertEquals(3, parsed.receipt().copper());
        }
    }

    @Test
    void receiptWrapperContainingTransactionIsSupportedWithoutChoosingConflictingFallbacks() {
        JsonObject response = JsonParser.parseString("""
                {"found": true, "receipt": {"transaction": {
                  "transaction_id": 1474,
                  "idempotency_key": "%s",
                  "player_uuid": "%s",
                  "transaction_type": "sell",
                  "currency_grant": {"copper": 3}
                }}}
                """.formatted(KEY, PLAYER)).getAsJsonObject();

        var parsed = TraderSaleReceiptContract.parse(response);
        assertTrue(parsed.accepted(), parsed.rejection());
        assertEquals(3, parsed.receipt().copper());
    }

    @Test
    void duplicateIdentityFieldsMustAgreeAcrossRootAndTransaction() {
        JsonObject wrongKey = productionResponse();
        wrongKey.addProperty("idempotency_key", KEY + ":other");
        assertRejected(wrongKey, "conflicting idempotency key fields");

        JsonObject wrongPlayer = productionResponse();
        wrongPlayer.addProperty("player_uuid", UUID.randomUUID().toString());
        assertRejected(wrongPlayer, "conflicting player UUID fields");

        JsonObject wrongType = productionResponse();
        wrongType.addProperty("transaction_type", "purchase");
        assertRejected(wrongType, "conflicting transaction type fields");

        JsonObject wrongTransaction = productionResponse();
        wrongTransaction.addProperty("transaction_id", 1475);
        assertRejected(wrongTransaction, "conflicting transaction identity fields");
    }

    @Test
    void ownershipMustMatchTheReservationAfterUuidNormalization() {
        var parsed = TraderSaleReceiptContract.parse(productionResponse());
        assertTrue(parsed.accepted(), parsed.rejection());
        assertEquals("", parsed.receipt().mismatch(KEY, PLAYER));
        assertEquals("player UUID does not match reservation",
                parsed.receipt().mismatch(KEY, UUID.randomUUID()));
        assertEquals("idempotency key does not match reservation",
                parsed.receipt().mismatch(KEY + ":wrong", PLAYER));
    }

    @Test
    void purchaseReceiptCannotSettleASaleReservation() {
        JsonObject response = productionResponse();
        response.getAsJsonObject("transaction").addProperty("transaction_type", "purchase");
        assertRejected(response, "transaction type is not sell");
    }

    @Test
    void missingOrMalformedIdentityIsRejected() {
        JsonObject missingPlayer = productionResponse();
        missingPlayer.getAsJsonObject("transaction").remove("player_uuid");
        assertRejected(missingPlayer, "player UUID is missing");

        JsonObject malformedPlayer = productionResponse();
        malformedPlayer.getAsJsonObject("transaction").addProperty("player_uuid", "not-a-uuid");
        assertRejected(malformedPlayer, "player UUID is malformed");

        JsonObject malformedTransaction = productionResponse();
        malformedTransaction.addProperty("transaction", "not-an-object");
        assertRejected(malformedTransaction, "root.transaction is not an object");

        JsonObject unconfirmed = productionResponse();
        unconfirmed.addProperty("success", false);
        assertRejected(unconfirmed, "success is false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2.5", "2305", "2147483648"})
    void unsafePayoutDenominationsAreRejected(String copper) {
        JsonObject response = productionResponse();
        response.getAsJsonObject("currency_grant").addProperty("copper", JsonParser.parseString(copper).getAsNumber());
        response.getAsJsonObject("transaction").getAsJsonObject("currency_grant")
                .addProperty("copper", JsonParser.parseString(copper).getAsNumber());

        assertFalse(TraderSaleReceiptContract.parse(response).accepted());
        assertFalse(ServerEconomyService.resultFromReceipt(response).success());
    }

    @Test
    void duplicatePayoutSourcesMustAgree() {
        JsonObject response = productionResponse();
        response.getAsJsonObject("transaction").getAsJsonObject("currency_grant").addProperty("copper", 4);
        assertRejected(response, "conflicting payout fields");
    }

    private static JsonObject productionResponse() {
        return JsonParser.parseString("""
                {
                  "success": true,
                  "transaction": {
                    "transaction_id": 1474,
                    "idempotency_key": "%s",
                    "player_uuid": "%s",
                    "transaction_type": "sell",
                    "currency_grant": {"copper": 3}
                  },
                  "transaction_id": 1474,
                  "idempotency_key": "%s",
                  "currency_grant": {"copper": 3}
                }
                """.formatted(KEY, PLAYER, KEY)).getAsJsonObject();
    }

    private static void assertRejected(JsonObject response, String reasonFragment) {
        var parsed = TraderSaleReceiptContract.parse(response);
        assertFalse(parsed.accepted());
        assertTrue(parsed.rejection().contains(reasonFragment), parsed.rejection());
    }
}

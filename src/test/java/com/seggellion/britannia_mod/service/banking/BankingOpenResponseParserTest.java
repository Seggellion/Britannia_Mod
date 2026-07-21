package com.seggellion.britannia_mod.service.banking;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per docs/banking_open.md outcome code, proving each parses into the correct
 * {@link BankingOpenClientResult} variant — including the two outcomes this slice's own
 * Rails verification pass added ({@code TELLER_WRONG_SERVER}) and the pre-existing
 * {@code TELLER_WRONG_SHARD} — plus that {@code SERVICE_UNAVAILABLE} parses as retryable.
 */
class BankingOpenResponseParserTest {
    @Test
    void opensParsesTheFullAccountAndBankItemsEnvelope() {
        String body = """
                {
                  "protocol_version": 1,
                  "success": true,
                  "outcome": "OPENED",
                  "retryable": false,
                  "account": {
                    "public_id": "5c1e4e1a-0000-4000-8000-000000000001",
                    "banking_mode": "global",
                    "city_public_id": null,
                    "weight_limit": 250,
                    "current_weight": 0.0,
                    "gold_balance": 0,
                    "silver_balance": 0,
                    "copper_balance": 0,
                    "revision": 1
                  },
                  "bank_items": { "items": [], "next_cursor": null }
                }
                """;
        BankingOpenClientResult result = parse(200, body);
        BankingOpenClientResult.Success success = assertInstanceOf(BankingOpenClientResult.Success.class, result);
        assertFalse(success.retryable());
        BankingOpenAccount account = success.account();
        assertEquals(UUID.fromString("5c1e4e1a-0000-4000-8000-000000000001"), account.publicId());
        assertEquals("global", account.bankingMode());
        assertEquals(null, account.cityPublicId());
        assertEquals(250, account.weightLimit());
        assertEquals(0.0, account.currentWeight());
        assertEquals(0, account.goldBalance());
        assertEquals(0, account.silverBalance());
        assertEquals(0, account.copperBalance());
        assertEquals(1, account.revision());
    }

    @Test
    void opensParsesARealNonEmptyBankItemsList() {
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        String body = """
                {
                  "protocol_version": 1,
                  "success": true,
                  "outcome": "OPENED",
                  "retryable": false,
                  "account": {
                    "public_id": "5c1e4e1a-0000-4000-8000-000000000001",
                    "banking_mode": "global",
                    "city_public_id": null,
                    "weight_limit": 250,
                    "current_weight": 4.5,
                    "gold_balance": 0,
                    "silver_balance": 0,
                    "copper_balance": 0,
                    "revision": 1
                  },
                  "bank_items": {
                    "items": [
                      { "public_id": "%s", "status": "available", "revision": 1, "schema_version": 1,
                        "fingerprint": "deadbeef", "weight": 2.5 },
                      { "public_id": "%s", "status": "available", "revision": 1, "schema_version": 1,
                        "fingerprint": "cafebabe", "weight": 2.0 }
                    ],
                    "next_cursor": null
                  }
                }
                """.formatted(firstItem, secondItem);
        BankingOpenClientResult result = parse(200, body);
        BankingOpenClientResult.Success success = assertInstanceOf(BankingOpenClientResult.Success.class, result);
        assertEquals(2, success.bankItems().size());
        assertEquals(firstItem, success.bankItems().get(0).publicId());
        assertEquals(2.5, success.bankItems().get(0).weight());
        assertEquals(secondItem, success.bankItems().get(1).publicId());
        assertEquals(2.0, success.bankItems().get(1).weight());
    }

    @Test
    void missingBankItemPublicIdIsATransportFailure() {
        String body = "{ \"protocol_version\": 1, \"success\": true, \"outcome\": \"OPENED\", \"retryable\": false, "
                + "\"account\": { \"public_id\": \"" + UUID.randomUUID() + "\", \"banking_mode\": \"global\", "
                + "\"city_public_id\": null, \"weight_limit\": 250, \"current_weight\": 0.0, \"gold_balance\": 0, "
                + "\"silver_balance\": 0, \"copper_balance\": 0, \"revision\": 1 }, "
                + "\"bank_items\": { \"items\": [ { \"weight\": 1.0 } ], \"next_cursor\": null } }";
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, parse(200, body));
    }

    @Test
    void negativeBankItemWeightIsATransportFailure() {
        String body = "{ \"protocol_version\": 1, \"success\": true, \"outcome\": \"OPENED\", \"retryable\": false, "
                + "\"account\": { \"public_id\": \"" + UUID.randomUUID() + "\", \"banking_mode\": \"global\", "
                + "\"city_public_id\": null, \"weight_limit\": 250, \"current_weight\": 0.0, \"gold_balance\": 0, "
                + "\"silver_balance\": 0, \"copper_balance\": 0, \"revision\": 1 }, "
                + "\"bank_items\": { \"items\": [ { \"public_id\": \"" + UUID.randomUUID() + "\", \"weight\": -1.0 } ], "
                + "\"next_cursor\": null } }";
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, parse(200, body));
    }

    @Test
    void opensCityLocalParsesTheCityPublicId() {
        UUID city = UUID.randomUUID();
        String body = accountBody("city_local", city, 250, 12.5, 3, 40, 7, 2);
        BankingOpenClientResult result = parse(200, body);
        BankingOpenAccount account = assertInstanceOf(BankingOpenClientResult.Success.class, result).account();
        assertEquals("city_local", account.bankingMode());
        assertEquals(city, account.cityPublicId());
        assertEquals(12.5, account.currentWeight());
        assertEquals(3, account.goldBalance());
        assertEquals(40, account.silverBalance());
        assertEquals(7, account.copperBalance());
        assertEquals(2, account.revision());
    }

    @Test
    void unauthorizedParsesAsRejectedNotRetryable() {
        assertRejected(401, "UNAUTHORIZED", false);
    }

    @Test
    void serverNotAuthorizedParsesAsRejectedNotRetryable() {
        assertRejected(403, "SERVER_NOT_AUTHORIZED", false);
    }

    @Test
    void malformedRequestParsesAsRejected() {
        assertRejected(400, "MALFORMED_REQUEST", false);
    }

    @Test
    void playerNotFoundParsesAsRejected() {
        assertRejected(422, "PLAYER_NOT_FOUND", false);
    }

    @Test
    void tellerNotFoundParsesAsRejected() {
        assertRejected(422, "TELLER_NOT_FOUND", false);
    }

    @Test
    void tellerWrongShardParsesAsRejected() {
        assertRejected(422, "TELLER_WRONG_SHARD", false);
    }

    @Test
    void tellerWrongServerParsesAsRejected() {
        assertRejected(422, "TELLER_WRONG_SERVER", false);
    }

    @Test
    void tellerNotActiveParsesAsRejected() {
        assertRejected(422, "TELLER_NOT_ACTIVE", false);
    }

    @Test
    void tellerNotAssignedParsesAsRejected() {
        assertRejected(422, "TELLER_NOT_ASSIGNED", false);
    }

    @Test
    void postRemovedParsesAsRejected() {
        assertRejected(422, "POST_REMOVED", false);
    }

    @Test
    void postDisabledParsesAsRejected() {
        assertRejected(422, "POST_DISABLED", false);
    }

    @Test
    void tellerServiceNotSupportedParsesAsRejected() {
        assertRejected(422, "TELLER_SERVICE_NOT_SUPPORTED", false);
    }

    @Test
    void invalidCityForBankingModeParsesAsRejected() {
        assertRejected(422, "INVALID_CITY_FOR_BANKING_MODE", false);
    }

    @Test
    void cityShardMismatchParsesAsRejected() {
        assertRejected(422, "CITY_SHARD_MISMATCH", false);
    }

    @Test
    void serviceUnavailableParsesAsRejectedAndRetryable() {
        BankingOpenClientResult result = assertRejected(503, "SERVICE_UNAVAILABLE", true);
        assertTrue(((BankingOpenClientResult.Rejected) result).retryable());
    }

    @Test
    void statusOutcomeMismatchIsATransportFailureNotATrustedRejection() {
        // OPENED's own contract demands HTTP 200; a server that sent 200 with a
        // mismatched outcome string must never be trusted as if it were consistent.
        String body = errorBody("UNAUTHORIZED", false);
        BankingOpenClientResult result = parse(200, body);
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, result);
    }

    @Test
    void unknownOutcomeIsATransportFailure() {
        String body = errorBody("SOMETHING_NEW_RAILS_ADDED", false);
        BankingOpenClientResult result = parse(422, body);
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, result);
    }

    @Test
    void unsupportedProtocolVersionIsATransportFailure() {
        String body = """
                { "protocol_version": 2, "success": false, "outcome": "MALFORMED_REQUEST", "retryable": false }
                """;
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, parse(400, body));
    }

    @Test
    void malformedJsonIsATransportFailure() {
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, parse(200, "not json"));
    }

    @Test
    void notFoundStatusIsATransportFailure() {
        assertInstanceOf(BankingOpenClientResult.TransportFailure.class, parse(404, ""));
    }

    private static BankingOpenClientResult assertRejected(int status, String outcome, boolean retryable) {
        BankingOpenClientResult result = parse(status, errorBody(outcome, retryable));
        BankingOpenClientResult.Rejected rejected = assertInstanceOf(BankingOpenClientResult.Rejected.class, result);
        assertEquals(BankingOpenOutcome.valueOf(outcome), rejected.outcome());
        assertEquals(retryable, rejected.retryable());
        return result;
    }

    private static String errorBody(String outcome, boolean retryable) {
        return "{ \"protocol_version\": 1, \"success\": false, \"outcome\": \"" + outcome
                + "\", \"retryable\": " + retryable + " }";
    }

    private static String accountBody(
            String bankingMode, UUID cityPublicId, int weightLimit, double currentWeight,
            int gold, int silver, int copper, long revision
    ) {
        String city = cityPublicId == null ? "null" : "\"" + cityPublicId + "\"";
        return "{ \"protocol_version\": 1, \"success\": true, \"outcome\": \"OPENED\", \"retryable\": false, "
                + "\"account\": { \"public_id\": \"" + UUID.randomUUID() + "\", \"banking_mode\": \"" + bankingMode
                + "\", \"city_public_id\": " + city + ", \"weight_limit\": " + weightLimit
                + ", \"current_weight\": " + currentWeight + ", \"gold_balance\": " + gold
                + ", \"silver_balance\": " + silver + ", \"copper_balance\": " + copper
                + ", \"revision\": " + revision + " }, "
                + "\"bank_items\": { \"items\": [], \"next_cursor\": null } }";
    }

    private static BankingOpenClientResult parse(int status, String body) {
        return BankingOpenResponseParser.parse(status, body.getBytes(StandardCharsets.UTF_8));
    }
}

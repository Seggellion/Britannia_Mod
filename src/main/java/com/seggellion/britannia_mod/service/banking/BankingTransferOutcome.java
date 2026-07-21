package com.seggellion.britannia_mod.service.banking;

import javax.annotation.Nullable;

/**
 * Closed outcome vocabulary for {@code POST /api/banking/deposit/prepare}, {@code
 * POST /api/banking/withdrawal/prepare}, {@code POST /api/banking/confirm}, and {@code
 * POST /api/banking/cancel} -- mirroring {@link BankingOpenOutcome}'s own approach (a direct
 * wire-name match for every Rails outcome string, exhaustively mapped to its expected HTTP
 * status), but shared across all four actions in a single enum rather than one per action.
 * This mirrors Rails' own design: {@code Banking::Protocol::OUTCOMES}
 * (docs/banking_item_transfer.md) is itself one flat outcome list shared by every banking
 * action, not a separate vocabulary per endpoint -- this enum follows that same precedent
 * rather than inventing a per-action split Rails itself does not have.
 *
 * <p>{@code ITEM_NOT_FOUND} and {@code ITEM_NOT_AVAILABLE} are withdrawal-prepare-only (Slice
 * 1 deliberately excluded them since deposit never returns them; Slice 2 adds them here now
 * that a real caller exists, extending the same shared list rather than starting a parallel
 * one).
 */
public enum BankingTransferOutcome {
    PREPARED,
    CONFIRMED,
    CANCELLED,
    RECONCILIATION_REQUIRED,
    INVALID_TRANSITION,
    CAPACITY_EXCEEDED,
    ITEM_NOT_FOUND,
    ITEM_NOT_AVAILABLE,
    OPERATION_NOT_FOUND,
    UNAUTHORIZED,
    SERVER_NOT_AUTHORIZED,
    MALFORMED_REQUEST,
    PLAYER_NOT_FOUND,
    TELLER_NOT_FOUND,
    TELLER_WRONG_SHARD,
    TELLER_WRONG_SERVER,
    TELLER_NOT_ACTIVE,
    TELLER_NOT_ASSIGNED,
    POST_REMOVED,
    POST_DISABLED,
    TELLER_SERVICE_NOT_SUPPORTED,
    INVALID_CITY_FOR_BANKING_MODE,
    CITY_SHARD_MISMATCH,
    UNEXPECTED_FIELD,
    MISSING_FIELD,
    UNSUPPORTED_SCHEMA_VERSION,
    PAYLOAD_TOO_LARGE,
    MALFORMED_PAYLOAD,
    MALFORMED_FINGERPRINT,
    INVALID_WEIGHT,
    SERVICE_UNAVAILABLE;

    @Nullable
    public static BankingTransferOutcome parse(@Nullable String wireName) {
        if (wireName == null) return null;
        for (BankingTransferOutcome outcome : values()) {
            if (outcome.name().equals(wireName)) return outcome;
        }
        return null;
    }

    /** The Rails-defined HTTP status this outcome is always paired with (docs/banking_item_transfer.md). */
    public int expectedHttpStatus() {
        return switch (this) {
            case PREPARED, CONFIRMED, CANCELLED -> 200;
            case UNAUTHORIZED -> 401;
            case SERVER_NOT_AUTHORIZED -> 403;
            case MALFORMED_REQUEST, UNEXPECTED_FIELD, MISSING_FIELD, MALFORMED_PAYLOAD, MALFORMED_FINGERPRINT -> 400;
            case PLAYER_NOT_FOUND, TELLER_NOT_FOUND, TELLER_WRONG_SHARD, TELLER_WRONG_SERVER,
                 TELLER_NOT_ACTIVE, TELLER_NOT_ASSIGNED, POST_REMOVED, POST_DISABLED,
                 TELLER_SERVICE_NOT_SUPPORTED, INVALID_CITY_FOR_BANKING_MODE, CITY_SHARD_MISMATCH,
                 CAPACITY_EXCEEDED, UNSUPPORTED_SCHEMA_VERSION, PAYLOAD_TOO_LARGE, INVALID_WEIGHT,
                 OPERATION_NOT_FOUND, INVALID_TRANSITION, RECONCILIATION_REQUIRED,
                 ITEM_NOT_FOUND, ITEM_NOT_AVAILABLE -> 422;
            case SERVICE_UNAVAILABLE -> 503;
        };
    }

    public boolean expectedRetryable() {
        return this == SERVICE_UNAVAILABLE;
    }
}

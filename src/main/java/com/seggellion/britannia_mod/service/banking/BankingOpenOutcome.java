package com.seggellion.britannia_mod.service.banking;

import javax.annotation.Nullable;

/**
 * Closed outcome vocabulary for {@code POST /api/banking/open}, mirroring
 * docs/banking_open.md (Rails Milestone 7 Slices 1+2) exactly. No parallel codes are
 * invented here; every value is a direct wire-name match for a Rails outcome string.
 */
public enum BankingOpenOutcome {
    OPENED,
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
    SERVICE_UNAVAILABLE;

    @Nullable
    public static BankingOpenOutcome parse(@Nullable String wireName) {
        if (wireName == null) return null;
        for (BankingOpenOutcome outcome : values()) {
            if (outcome.name().equals(wireName)) return outcome;
        }
        return null;
    }

    /** The Rails-defined HTTP status this outcome is always paired with. */
    public int expectedHttpStatus() {
        return switch (this) {
            case OPENED -> 200;
            case UNAUTHORIZED -> 401;
            case SERVER_NOT_AUTHORIZED -> 403;
            case MALFORMED_REQUEST -> 400;
            case PLAYER_NOT_FOUND, TELLER_NOT_FOUND, TELLER_WRONG_SHARD, TELLER_WRONG_SERVER,
                 TELLER_NOT_ACTIVE, TELLER_NOT_ASSIGNED, POST_REMOVED, POST_DISABLED,
                 TELLER_SERVICE_NOT_SUPPORTED, INVALID_CITY_FOR_BANKING_MODE, CITY_SHARD_MISMATCH -> 422;
            case SERVICE_UNAVAILABLE -> 503;
        };
    }

    public boolean expectedRetryable() {
        return this == SERVICE_UNAVAILABLE;
    }
}

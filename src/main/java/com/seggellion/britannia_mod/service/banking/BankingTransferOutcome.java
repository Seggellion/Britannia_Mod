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
 *
 * <p>{@code UNSUPPORTED_CURRENCY_KEY} and {@code INVALID_AMOUNT} are currency-prepare-only
 * (Milestone 10, docs/banking_currency_transfer.md), added by that milestone's own NeoForge
 * deposit slice under the exact same when-a-real-caller-exists rule. {@code
 * INSUFFICIENT_BALANCE} is currency-withdrawal-prepare-only, added by that milestone's own
 * NeoForge withdrawal slice now that a real caller exists, matching how {@code ITEM_NOT_FOUND}
 * waited for item withdrawal. {@code INVALID_CHEQUE_AMOUNT} is cheque-issuance-prepare-only
 * (Milestone 11 Rails Slice 1, docs/banking_bank_cheque_issuance.md), added by this milestone's
 * own NeoForge issuance slice under the same rule.
 *
 * <p>{@code CHEQUE_REDEEMED} and {@code CHEQUE_NOT_FOUND}/{@code CHEQUE_ALREADY_REDEEMED}/
 * {@code CHEQUE_CANCELLED}/{@code CHEQUE_VOIDED} are {@code banking/cheque/redeem}-only
 * (Milestone 11 Rails Slice 2, docs/banking_bank_cheque_redemption.md), added by this
 * milestone's own NeoForge redemption slice under the same rule. {@code CHEQUE_REDEEMED} is
 * this endpoint's one success outcome -- there is no {@code PREPARED} for redemption (see
 * {@link BankingChequeRedemptionResult}'s own docs for why this endpoint is a single call, not
 * a prepare/confirm pair) -- so {@link #parse} treating it as an ordinary wire name and {@link
 * BankingTransferResponseParser}'s envelope success-check both had to be extended for it
 * explicitly, the same way {@code CONFIRMED}/{@code CANCELLED} already are.
 */
public enum BankingTransferOutcome {
    PREPARED,
    CONFIRMED,
    CANCELLED,
    CHEQUE_REDEEMED,
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
    UNSUPPORTED_CURRENCY_KEY,
    INVALID_AMOUNT,
    INSUFFICIENT_BALANCE,
    INVALID_CHEQUE_AMOUNT,
    CHEQUE_NOT_FOUND,
    CHEQUE_ALREADY_REDEEMED,
    CHEQUE_CANCELLED,
    CHEQUE_VOIDED,
    /**
     * Bank interface rebuild, Milestone 6a/6b: a Deposit All Coins sweep found nothing. Rails
     * answers this as a clean 422 <em>before any operation row exists</em> -- nothing created,
     * nothing changed, never a confirmable operation that credits zero
     * (docs/banking_bulk_currency_deposit.md, "The no-coins result").
     *
     * <p>NeoForge may skip the call when its own sweep comes up empty, and does; this exists
     * because it must not <em>have</em> to, and because a modified client can send the request
     * regardless.
     */
    NO_COINS,
    /**
     * Bank interface rebuild, Milestone 6a/6b: crediting a swept total would push that
     * denomination's balance past what its int32 column can hold.
     *
     * <p>Deliberately distinct from {@link #INSUFFICIENT_BALANCE}, which is the opposite problem
     * on the withdrawal side. Rails checks this at prepare as a courtesy, under the account row
     * lock, so the common case is caught <em>before</em> NeoForge destroys anything -- the
     * authoritative guard is the column itself at confirm time.
     */
    BALANCE_CAPACITY_EXCEEDED,
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
            case PREPARED, CONFIRMED, CANCELLED, CHEQUE_REDEEMED -> 200;
            case UNAUTHORIZED -> 401;
            case SERVER_NOT_AUTHORIZED -> 403;
            case MALFORMED_REQUEST, UNEXPECTED_FIELD, MISSING_FIELD, MALFORMED_PAYLOAD, MALFORMED_FINGERPRINT -> 400;
            case PLAYER_NOT_FOUND, TELLER_NOT_FOUND, TELLER_WRONG_SHARD, TELLER_WRONG_SERVER,
                 TELLER_NOT_ACTIVE, TELLER_NOT_ASSIGNED, POST_REMOVED, POST_DISABLED,
                 TELLER_SERVICE_NOT_SUPPORTED, INVALID_CITY_FOR_BANKING_MODE, CITY_SHARD_MISMATCH,
                 CAPACITY_EXCEEDED, UNSUPPORTED_SCHEMA_VERSION, PAYLOAD_TOO_LARGE, INVALID_WEIGHT,
                 OPERATION_NOT_FOUND, INVALID_TRANSITION, RECONCILIATION_REQUIRED,
                 ITEM_NOT_FOUND, ITEM_NOT_AVAILABLE,
                 UNSUPPORTED_CURRENCY_KEY, INVALID_AMOUNT, INSUFFICIENT_BALANCE, INVALID_CHEQUE_AMOUNT,
                 CHEQUE_NOT_FOUND, CHEQUE_ALREADY_REDEEMED, CHEQUE_CANCELLED, CHEQUE_VOIDED,
                 // Milestone 6a: both are 422 clean rejections, raised before any operation row
                 // exists (docs/banking_bulk_currency_deposit.md, "Outcomes").
                 NO_COINS, BALANCE_CAPACITY_EXCEEDED -> 422;
            case SERVICE_UNAVAILABLE -> 503;
        };
    }

    public boolean expectedRetryable() {
        return this == SERVICE_UNAVAILABLE;
    }
}

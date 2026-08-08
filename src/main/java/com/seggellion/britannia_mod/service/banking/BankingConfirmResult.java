package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;

/**
 * Result of one {@code banking/confirm} attempt. {@link ReconciliationRequired} is its own
 * top-level case, not folded into {@link Rejected}: Rails reports it as a distinct, legitimate
 * (if unhappy) terminal outcome of a well-formed request -- "the confirm was processed, but
 * this operation is now ambiguously held and nothing may auto-resolve it" -- not a request
 * error the way {@code INVALID_TRANSITION} or {@code OPERATION_NOT_FOUND} are. Keeping it a
 * distinct sealed variant (rather than {@code Rejected(RECONCILIATION_REQUIRED, ...)}) forces
 * every caller's exhaustive switch to handle it explicitly, matching Section A.6's own
 * insistence that this state never be silently treated as ordinary success or ordinary
 * failure.
 */
public sealed interface BankingConfirmResult permits
        BankingConfirmResult.Confirmed,
        BankingConfirmResult.ReconciliationRequired,
        BankingConfirmResult.Rejected,
        BankingConfirmResult.TransportFailure,
        BankingConfirmResult.LocalFailure {

    boolean retryable();

    /** {@code CONFIRMED}. */
    record Confirmed() implements BankingConfirmResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }

    /**
     * {@code RECONCILIATION_REQUIRED}: the item must never be returned to the player, and the
     * local receipt must remain unresolved (see {@link BankingDepositProxyService} for how this
     * is wired). Not retryable -- nothing about retrying resolves an ambiguous crash-recovery
     * state (matches Rails' own {@code retryable: false} for this outcome).
     */
    record ReconciliationRequired() implements BankingConfirmResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }

    /** Any other well-formed non-success outcome (INVALID_TRANSITION, OPERATION_NOT_FOUND, PLAYER_NOT_FOUND, auth failures, ...). */
    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingConfirmResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == BankingTransferOutcome.CONFIRMED || outcome == BankingTransferOutcome.RECONCILIATION_REQUIRED) {
                throw new IllegalArgumentException(outcome + " has its own dedicated result case, not Rejected");
            }
        }
    }

    record TransportFailure(String safeCode) implements BankingConfirmResult {
        @Override
        public boolean retryable() {
            return true;
        }
    }

    record LocalFailure(String safeCode) implements BankingConfirmResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }
}

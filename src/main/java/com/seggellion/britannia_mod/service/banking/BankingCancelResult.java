package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;

/**
 * Result of one {@code banking/cancel} attempt. No {@code RECONCILIATION_REQUIRED} case exists
 * here (unlike {@link BankingConfirmResult}): Rails' own {@code Cancel} never produces it --
 * by protocol, cancel is only ever called before any risky mutation, so nothing ambiguous can
 * exist for it to resolve (docs/banking_item_transfer.md).
 */
public sealed interface BankingCancelResult permits
        BankingCancelResult.Cancelled,
        BankingCancelResult.Rejected,
        BankingCancelResult.TransportFailure,
        BankingCancelResult.LocalFailure {

    boolean retryable();

    /** {@code CANCELLED}. */
    record Cancelled() implements BankingCancelResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }

    /** Any other well-formed non-success outcome (INVALID_TRANSITION, OPERATION_NOT_FOUND, PLAYER_NOT_FOUND, auth failures, ...). */
    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingCancelResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == BankingTransferOutcome.CANCELLED) {
                throw new IllegalArgumentException("CANCELLED is a Cancelled, not a Rejected outcome");
            }
        }
    }

    record TransportFailure(String safeCode) implements BankingCancelResult {
        @Override
        public boolean retryable() {
            return true;
        }
    }

    record LocalFailure(String safeCode) implements BankingCancelResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }
}

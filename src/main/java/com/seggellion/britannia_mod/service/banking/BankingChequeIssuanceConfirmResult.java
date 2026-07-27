package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of one {@code banking/confirm} attempt for a cheque issuance operation -- a distinct
 * type from the generic {@link BankingConfirmResult}, not a reuse of it, because this is the
 * one confirm call in this codebase whose response body actually matters beyond the outcome:
 * {@link BankingTransferResponseParser}'s existing {@code parseConfirm} deliberately never
 * parses {@code operation}'s nested fields (deposit/withdrawal/currency all already know their
 * own identity from prepare), but a cheque's {@code BankCheque} does not exist until this exact
 * response -- Rails Milestone 11 Slice 1's own deliberate design (see {@code
 * docs/banking_bank_cheque_issuance.md}). {@link Confirmed} therefore carries the real,
 * Rails-issued {@code bank_cheque.public_id}/{@code amount}, the first and only point that
 * identity becomes known.
 */
public sealed interface BankingChequeIssuanceConfirmResult permits
        BankingChequeIssuanceConfirmResult.Confirmed,
        BankingChequeIssuanceConfirmResult.ReconciliationRequired,
        BankingChequeIssuanceConfirmResult.Rejected,
        BankingChequeIssuanceConfirmResult.TransportFailure,
        BankingChequeIssuanceConfirmResult.LocalFailure {

    /** {@code CONFIRMED}, carrying the newly-created {@code BankCheque}'s real identity and amount (copper). */
    record Confirmed(UUID chequePublicId, int amount) implements BankingChequeIssuanceConfirmResult {
        public Confirmed {
            Objects.requireNonNull(chequePublicId, "chequePublicId");
            if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        }
    }

    /**
     * {@code RECONCILIATION_REQUIRED}: the gold may or may not have been debited and a cheque
     * may or may not exist -- Rails itself cannot auto-resolve this operation any further. The
     * local receipt must remain unresolved (escalated, not resolved) exactly like every other
     * flow's identical handling of this outcome.
     */
    record ReconciliationRequired() implements BankingChequeIssuanceConfirmResult {
    }

    /** Any other well-formed non-success outcome (INVALID_TRANSITION, OPERATION_NOT_FOUND, auth failures, ...). */
    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingChequeIssuanceConfirmResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == BankingTransferOutcome.CONFIRMED || outcome == BankingTransferOutcome.RECONCILIATION_REQUIRED) {
                throw new IllegalArgumentException(outcome + " has its own dedicated result case, not Rejected");
            }
        }
    }

    record TransportFailure(String safeCode) implements BankingChequeIssuanceConfirmResult {
        public TransportFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    record LocalFailure(String safeCode) implements BankingChequeIssuanceConfirmResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

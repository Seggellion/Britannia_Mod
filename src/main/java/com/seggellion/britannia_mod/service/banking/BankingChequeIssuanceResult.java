package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one full cheque issuance attempt. Structurally different from every other
 * banking result in this codebase in one deliberate way: {@link PendingDelivery} has no
 * equivalent in {@link BankingDepositResult}/{@link BankingWithdrawalResult}/{@link
 * BankingCurrencyWithdrawalResult}. Those flows never reach a state of "Rails has already,
 * irrevocably committed, but the physical side has not yet completed and a plain retry is
 * safe" -- their own committed action either fully succeeds locally or the whole attempt is
 * cleanly cancelled beforehand. A cheque issuance can reach exactly that state (Rails confirm
 * always precedes physical delivery here -- see {@code docs/banking_bank_cheque_issuance.md}),
 * and it is safe specifically because redeeming a cheque is gated by Rails' own single-use
 * {@code BankCheque} state, not by how many physical copies exist -- a second delivery attempt
 * can never double-spend the value. See {@link BankingChequeIssuanceProxyService} for exactly
 * when this is reached and how it differs from both an ordinary {@link Confirmed} and a
 * withdrawal-style {@link ReconciliationRequired}.
 */
public sealed interface BankingChequeIssuanceResult permits
        BankingChequeIssuanceResult.Confirmed,
        BankingChequeIssuanceResult.PendingDelivery,
        BankingChequeIssuanceResult.ReconciliationRequired,
        BankingChequeIssuanceResult.RejectedLocally,
        BankingChequeIssuanceResult.Rejected,
        BankingChequeIssuanceResult.TransportFailure,
        BankingChequeIssuanceResult.LocalFailure {

    /** The full sequence completed cleanly: gold debited, BankCheque created, physical item delivered and durable. */
    record Confirmed(UUID operationPublicId, UUID chequePublicId, int amount) implements BankingChequeIssuanceResult {
        public Confirmed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(chequePublicId, "chequePublicId");
        }
    }

    /**
     * Rails confirmed (gold debited, {@code BankCheque} created and real), but the physical
     * item was not delivered this attempt -- either the player's inventory filled during the
     * confirm round trip (the final, no-yield-point capacity check failed) or a detected
     * forced-save failure required backing the just-inserted item back out (see {@link
     * BankingChequeIssuanceProxyService}'s own docs for the full "abort-and-restore" reasoning).
     * The local receipt is deliberately left {@code PENDING_LOCAL_ACTION} (neither resolved nor
     * escalated): a later automatic retry (startup reconciliation, or a future re-trigger) can
     * safely re-confirm (idempotent -- returns this exact same cheque) and re-attempt delivery,
     * with no value-duplication risk. Not reported as {@link ReconciliationRequired}: that name
     * is reserved for a state nothing may auto-resolve, which this is not.
     */
    record PendingDelivery(UUID operationPublicId, UUID chequePublicId, int amount) implements BankingChequeIssuanceResult {
        public PendingDelivery {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(chequePublicId, "chequePublicId");
        }
    }

    /**
     * Rails itself reports {@code RECONCILIATION_REQUIRED} (a late confirm on an operation that
     * had already expired -- the one case Rails cannot resolve automatically even though nothing
     * physical has necessarily happened locally yet). The local receipt is escalated, not
     * resolved or left ordinary-pending -- this is the one case in this flow that genuinely
     * mirrors every other flow's identical handling of the same Rails response.
     */
    record ReconciliationRequired(UUID operationPublicId) implements BankingChequeIssuanceResult {
        public ReconciliationRequired {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /** Rejected before any network call was made -- zero Rails calls, nothing to clean up. */
    record RejectedLocally(BankingChequeIssuanceLocalRejectionReason reason) implements BankingChequeIssuanceResult {
        public RejectedLocally {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** A well-formed Rails rejection at either stage (teller chain, invalid amount, insufficient balance, ...). */
    record Rejected(BankingChequeIssuanceStage stage, BankingTransferOutcome outcome, boolean retryable) implements BankingChequeIssuanceResult {
        public Rejected {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Network/transport-level failure. */
    record TransportFailure(BankingChequeIssuanceStage stage, String safeCode) implements BankingChequeIssuanceResult {
        public TransportFailure {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    /** A local precondition failed before prepare was even attempted (duplicate in-flight attempt, unresolved teller). */
    record LocalFailure(String safeCode) implements BankingChequeIssuanceResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one full cheque redemption attempt. Structurally simpler than every other
 * sealed result in this package in one deliberate way: there is no {@link
 * BankingChequeIssuanceResult.PendingDelivery}-style third state and no {@code
 * ReconciliationRequired} case at all.
 *
 * <p>Neither applies here because redemption is not a phase of the
 * {@code BankTransferOperations::{Create,Confirm}} state machine in the first place -- Rails'
 * own {@code POST /api/banking/cheque/redeem} (docs/banking_bank_cheque_redemption.md) is a
 * single, atomic call that both validates and commits (credits the balance, transitions the
 * {@code BankCheque}) in one step, with no separate reservation phase to leave dangling and
 * therefore no {@code RECONCILIATION_REQUIRED} outcome that endpoint can ever return. This
 * mirrors the Rails-side architecture decision directly (see that doc's own "Architecture
 * decision: why redemption is a standalone service, not prepare/confirm"), not an
 * implementation shortcut on this side.
 *
 * <p>{@link Rejected} covers both this endpoint's four cheque-specific outcomes ({@code
 * CHEQUE_NOT_FOUND}/{@code CHEQUE_ALREADY_REDEEMED}/{@code CHEQUE_CANCELLED}/{@code
 * CHEQUE_VOIDED}) and every shared teller/account outcome ({@code PLAYER_NOT_FOUND},
 * {@code TELLER_NOT_ASSIGNED}, ...) uniformly, carrying the raw {@link BankingTransferOutcome}
 * -- exactly like every sibling {@code Rejected} case in this package. The two categories are
 * NOT equivalent in what they imply about the physical cheque item already removed by the time
 * either arrives (see {@link BankingChequeRedemptionProxyService}'s own docs for the full
 * item-disposition/receipt-lifecycle reasoning), but that distinction is a caller-side decision
 * made by inspecting {@code outcome()}, not a reason to split this into two result cases.
 */
public sealed interface BankingChequeRedemptionResult permits
        BankingChequeRedemptionResult.Confirmed,
        BankingChequeRedemptionResult.RejectedLocally,
        BankingChequeRedemptionResult.Rejected,
        BankingChequeRedemptionResult.TransportFailure,
        BankingChequeRedemptionResult.LocalFailure {

    /** The cheque was redeemed: Rails credited the resolved account and transitioned the {@code BankCheque} to {@code redeemed}. */
    record Confirmed(UUID chequePublicId) implements BankingChequeRedemptionResult {
        public Confirmed {
            Objects.requireNonNull(chequePublicId, "chequePublicId");
        }
    }

    /** Rejected before any network call was made -- zero Rails calls, nothing to clean up, no item ever removed. */
    record RejectedLocally(BankingChequeRedemptionLocalRejectionReason reason) implements BankingChequeRedemptionResult {
        public RejectedLocally {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A well-formed Rails rejection -- one of the four cheque-specific outcomes, or a shared
     * teller/account outcome. By the time this is ever reached, the physical cheque item has
     * already been removed (see {@link BankingChequeRedemptionProxyService}'s own docs for why
     * removal precedes the Rails call, mirroring {@link BankingCurrencyDepositProxyService}'s
     * ordering) -- there is no post-dispatch abort-and-restore path for either category.
     */
    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingChequeRedemptionResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Network/transport-level failure. The item, if already removed, stays removed; the receipt (if written) stays pending for a resume. */
    record TransportFailure(String safeCode) implements BankingChequeRedemptionResult {
        public TransportFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    /** A local precondition failed before the Rails call was even attempted (duplicate in-flight attempt, unresolved teller). */
    record LocalFailure(String safeCode) implements BankingChequeRedemptionResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

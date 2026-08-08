package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one full withdrawal attempt, mirroring {@link BankingDepositResult}'s closed
 * sealed-hierarchy shape -- most importantly {@link ReconciliationRequired}, which must never
 * be handled the same way as ordinary success or failure, exactly matching deposit's own
 * established requirement (see {@link BankingConfirmResult.ReconciliationRequired}).
 */
public sealed interface BankingWithdrawalResult permits
        BankingWithdrawalResult.Confirmed,
        BankingWithdrawalResult.ReconciliationRequired,
        BankingWithdrawalResult.Aborted,
        BankingWithdrawalResult.Rejected,
        BankingWithdrawalResult.TransportFailure,
        BankingWithdrawalResult.LocalFailure {

    /** The full sequence completed cleanly: item reconstructed, inserted, Rails confirmed, local receipt resolved. */
    record Confirmed(UUID operationPublicId, UUID bankItemPublicId) implements BankingWithdrawalResult {
        public Confirmed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(bankItemPublicId, "bankItemPublicId");
        }
    }

    /**
     * The item was already inserted into the player's inventory and confirm was sent, but
     * Rails reports {@code RECONCILIATION_REQUIRED}. The item must never be reclaimed -- it is
     * already, physically, the player's. The local receipt is escalated (not resolved), exactly
     * mirroring deposit's handling of this same case.
     */
    record ReconciliationRequired(UUID operationPublicId) implements BankingWithdrawalResult {
        public ReconciliationRequired {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * Rails prepared/reserved the item, but the withdrawal was aborted before the item was ever
     * given to the player (protocol steps 2, 3, or 5b -- see {@link BankingWithdrawalAbortReason}).
     * Cancel was called against Rails; no item was created, duplicated, or lost.
     */
    record Aborted(UUID operationPublicId, BankingWithdrawalAbortReason reason) implements BankingWithdrawalResult {
        public Aborted {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** A well-formed Rails rejection at either stage (teller chain, item not found/available, invalid_transition, ...). */
    record Rejected(BankingWithdrawalStage stage, BankingTransferOutcome outcome, boolean retryable) implements BankingWithdrawalResult {
        public Rejected {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Network/transport-level failure. See {@link BankingWithdrawalStage} for why the stage matters here. */
    record TransportFailure(BankingWithdrawalStage stage, String safeCode) implements BankingWithdrawalResult {
        public TransportFailure {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    /** A local precondition failed before prepare was even attempted -- always pre-{@link BankingWithdrawalStage#PREPARE}. */
    record LocalFailure(String safeCode) implements BankingWithdrawalResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one full currency withdrawal attempt, mirroring {@link BankingWithdrawalResult}'s
 * closed sealed-hierarchy shape with the same structural difference {@link
 * BankingCurrencyDepositResult} has from {@link BankingDepositResult}: no case carries a {@code
 * bankItemPublicId}, because a currency operation never has one.
 */
public sealed interface BankingCurrencyWithdrawalResult permits
        BankingCurrencyWithdrawalResult.Confirmed,
        BankingCurrencyWithdrawalResult.ReconciliationRequired,
        BankingCurrencyWithdrawalResult.Aborted,
        BankingCurrencyWithdrawalResult.RejectedLocally,
        BankingCurrencyWithdrawalResult.Rejected,
        BankingCurrencyWithdrawalResult.TransportFailure,
        BankingCurrencyWithdrawalResult.LocalFailure {

    /** The full sequence completed cleanly: coins inserted, Rails confirmed, local receipt resolved. */
    record Confirmed(UUID operationPublicId) implements BankingCurrencyWithdrawalResult {
        public Confirmed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * The coins were already inserted into the player's inventory and confirm was sent, but
     * Rails reports {@code RECONCILIATION_REQUIRED} (or a detected forced-save failure carried
     * this same doubt through to confirm). The coins must never be reclaimed -- they are
     * already, physically, the player's. The local receipt is escalated (not resolved).
     */
    record ReconciliationRequired(UUID operationPublicId) implements BankingCurrencyWithdrawalResult {
        public ReconciliationRequired {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * Rails prepared/reserved the amount, but the withdrawal was aborted before any coins were
     * ever given to the player (the post-prepare capacity re-check, or the defensive
     * insertion-failed path). Cancel was called against Rails; no coins were created, duplicated,
     * or lost.
     */
    record Aborted(UUID operationPublicId, BankingCurrencyWithdrawalAbortReason reason) implements BankingCurrencyWithdrawalResult {
        public Aborted {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Rejected before any network call was made -- zero Rails calls, nothing to clean up. */
    record RejectedLocally(BankingCurrencyWithdrawalLocalRejectionReason reason) implements BankingCurrencyWithdrawalResult {
        public RejectedLocally {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** A well-formed Rails rejection at either stage (teller chain, unsupported key, invalid amount, insufficient balance, ...). */
    record Rejected(BankingWithdrawalStage stage, BankingTransferOutcome outcome, boolean retryable) implements BankingCurrencyWithdrawalResult {
        public Rejected {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Network/transport-level failure. See {@link BankingWithdrawalStage} for why the stage matters. */
    record TransportFailure(BankingWithdrawalStage stage, String safeCode) implements BankingCurrencyWithdrawalResult {
        public TransportFailure {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    /** A local precondition failed before prepare was even attempted (duplicate in-flight attempt, unresolved teller). */
    record LocalFailure(String safeCode) implements BankingCurrencyWithdrawalResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

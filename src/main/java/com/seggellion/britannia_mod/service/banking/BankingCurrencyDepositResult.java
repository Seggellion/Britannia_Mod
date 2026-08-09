package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one full currency deposit attempt, mirroring {@link BankingDepositResult}'s
 * closed sealed hierarchy case-for-case with one structural difference: no case carries a
 * {@code bankItemPublicId}, because a currency operation never has one (coin stacks become
 * integer balances, not Bank Item rows -- Section A.6). This is precisely why currency could
 * not reuse {@link BankingDepositResult}: its {@code Confirmed} case hard-requires a non-null
 * {@code bankItemPublicId} by contract, and weakening that contract to accommodate currency
 * would have stripped a real guarantee from the item path.
 */
public sealed interface BankingCurrencyDepositResult permits
        BankingCurrencyDepositResult.Confirmed,
        BankingCurrencyDepositResult.ReconciliationRequired,
        BankingCurrencyDepositResult.RemovalFailed,
        BankingCurrencyDepositResult.RejectedLocally,
        BankingCurrencyDepositResult.Rejected,
        BankingCurrencyDepositResult.TransportFailure,
        BankingCurrencyDepositResult.LocalFailure {

    /** The full sequence completed cleanly: coins removed, Rails confirmed, local receipt resolved. */
    record Confirmed(UUID operationPublicId) implements BankingCurrencyDepositResult {
        public Confirmed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * The coins were removed and confirm was sent, but Rails reports {@code
     * RECONCILIATION_REQUIRED}: the operation is now ambiguously held and must never be treated
     * as ordinary success. The coins are NOT returned to the player, and the local receipt is
     * escalated (not resolved) -- identical semantics to {@link
     * BankingDepositResult.ReconciliationRequired}, deliberately not a weaker version.
     */
    record ReconciliationRequired(UUID operationPublicId) implements BankingCurrencyDepositResult {
        public ReconciliationRequired {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * Rails prepared the operation, but the live slot no longer held the same coin item and
     * exact count captured at selection time (the playbook's "revalidate the exact
     * slot/item/count after prepare"). Cancel was called (safe -- nothing physical happened),
     * no coins were removed, no receipt was written.
     */
    record RemovalFailed(UUID operationPublicId) implements BankingCurrencyDepositResult {
        public RemovalFailed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /** Rejected before any network call was made -- zero Rails calls, nothing to clean up. */
    record RejectedLocally(BankingCurrencyDepositLocalRejectionReason reason) implements BankingCurrencyDepositResult {
        public RejectedLocally {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** A well-formed Rails rejection at either stage (teller chain, unsupported key, invalid amount, ...). */
    record Rejected(BankingDepositStage stage, BankingTransferOutcome outcome, boolean retryable) implements BankingCurrencyDepositResult {
        public Rejected {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Network/transport-level failure. See {@link BankingDepositStage} for why the stage matters. */
    record TransportFailure(BankingDepositStage stage, String safeCode) implements BankingCurrencyDepositResult {
        public TransportFailure {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    /** A local precondition failed before prepare was even attempted (duplicate in-flight attempt, unresolved teller). */
    record LocalFailure(String safeCode) implements BankingCurrencyDepositResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

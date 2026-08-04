package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of a whole Deposit All Coins sequence, mirroring {@link
 * BankingCurrencyDepositResult}'s shape because the protocol is the same shape: prepare,
 * physically destroy, confirm.
 *
 * <p>The one addition is {@link RejectedLocally} carrying {@link
 * BankingDepositAllCoinsLocalRejectionReason} instead of the single-deposit vocabulary, and the
 * one deliberate omission is any notion of a partial sweep. A sweep either removes every stack it
 * counted or removes none of them -- see {@link BankingDepositAllCoinsProxyService}'s disposal
 * step for why a half-removed sweep is not a state this protocol can produce.
 */
public sealed interface BankingDepositAllCoinsResult permits
        BankingDepositAllCoinsResult.Confirmed,
        BankingDepositAllCoinsResult.ReconciliationRequired,
        BankingDepositAllCoinsResult.RemovalFailed,
        BankingDepositAllCoinsResult.RejectedLocally,
        BankingDepositAllCoinsResult.Rejected,
        BankingDepositAllCoinsResult.TransportFailure,
        BankingDepositAllCoinsResult.LocalFailure {

    /** Rails credited all three denominations in one update. The defined completion point. */
    record Confirmed(UUID operationPublicId) implements BankingDepositAllCoinsResult {
        public Confirmed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * The coins are gone and Rails cannot say whether the credit landed. Never treated as
     * success, never auto-resolved; the local receipt is escalated and an operator resolves it.
     */
    record ReconciliationRequired(UUID operationPublicId) implements BankingDepositAllCoinsResult {
        public ReconciliationRequired {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * Rails prepared, but the sweep could not be removed and the operation was cancelled.
     * Nothing was credited and nothing was destroyed -- the player keeps their coins.
     */
    record RemovalFailed(UUID operationPublicId) implements BankingDepositAllCoinsResult {
        public RemovalFailed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /** Refused before any network call. Today that means only an empty sweep. */
    record RejectedLocally(BankingDepositAllCoinsLocalRejectionReason reason) implements BankingDepositAllCoinsResult {
        public RejectedLocally {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Rejected(BankingDepositStage stage, BankingTransferOutcome outcome, boolean retryable)
            implements BankingDepositAllCoinsResult {
        public Rejected {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record TransportFailure(BankingDepositStage stage, String safeCode) implements BankingDepositAllCoinsResult {
        public TransportFailure {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    record LocalFailure(String safeCode) implements BankingDepositAllCoinsResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

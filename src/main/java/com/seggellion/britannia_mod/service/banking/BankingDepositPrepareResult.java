package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of one {@code banking/deposit/prepare} attempt, mirroring {@link
 * BankingOpenClientResult}'s sealed-hierarchy shape: a closed, exhaustively-matched set of
 * cases rather than a nullable-field grab bag.
 */
public sealed interface BankingDepositPrepareResult permits
        BankingDepositPrepareResult.Success,
        BankingDepositPrepareResult.Rejected,
        BankingDepositPrepareResult.TransportFailure,
        BankingDepositPrepareResult.LocalFailure {

    boolean retryable();

    /** {@code PREPARED}: only the two identifiers {@link BankingDepositProxyService} actually needs downstream. */
    record Success(UUID operationPublicId, UUID bankItemPublicId) implements BankingDepositPrepareResult {
        public Success {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(bankItemPublicId, "bankItemPublicId");
        }

        @Override
        public boolean retryable() {
            return false;
        }
    }

    /** Any well-formed non-success protocol outcome Rails returned (never {@code PREPARED}). */
    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingDepositPrepareResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == BankingTransferOutcome.PREPARED) {
                throw new IllegalArgumentException("PREPARED is a Success, not a Rejected outcome");
            }
        }
    }

    /** Network/transport-level failure: timeout, connection reset, malformed/unexpected response body. */
    record TransportFailure(String safeCode) implements BankingDepositPrepareResult {
        @Override
        public boolean retryable() {
            return true;
        }
    }

    /** A local precondition failed before any request left this process (e.g. no server key configured). */
    record LocalFailure(String safeCode) implements BankingDepositPrepareResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }
}

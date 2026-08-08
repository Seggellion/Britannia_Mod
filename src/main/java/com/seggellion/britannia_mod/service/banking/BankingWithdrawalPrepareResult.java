package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of one {@code banking/withdrawal/prepare} attempt, mirroring {@link
 * BankingDepositPrepareResult}'s sealed-hierarchy shape. Unlike deposit prepare's success
 * (which only needs two identifiers back, since Minecraft already has the payload it sent),
 * withdrawal prepare's {@link Success} carries the full canonical item Rails is handing back --
 * schema version, payload, fingerprint, weight -- exactly what {@link
 * BankingWithdrawalProxyService} needs to reconstruct and verify the stack.
 */
public sealed interface BankingWithdrawalPrepareResult permits
        BankingWithdrawalPrepareResult.Success,
        BankingWithdrawalPrepareResult.Rejected,
        BankingWithdrawalPrepareResult.TransportFailure,
        BankingWithdrawalPrepareResult.LocalFailure {

    boolean retryable();

    /** {@code PREPARED}: the reserved item's canonical payload, as Rails now holds it. */
    record Success(
            UUID operationPublicId, UUID bankItemPublicId, int schemaVersion, byte[] payload, String fingerprint, double weight
    ) implements BankingWithdrawalPrepareResult {
        public Success {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(bankItemPublicId, "bankItemPublicId");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(fingerprint, "fingerprint");
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean retryable() {
            return false;
        }
    }

    /** Any well-formed non-success protocol outcome Rails returned (never {@code PREPARED}). */
    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingWithdrawalPrepareResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == BankingTransferOutcome.PREPARED) {
                throw new IllegalArgumentException("PREPARED is a Success, not a Rejected outcome");
            }
        }
    }

    /** Network/transport-level failure: timeout, connection reset, malformed/unexpected response body. */
    record TransportFailure(String safeCode) implements BankingWithdrawalPrepareResult {
        @Override
        public boolean retryable() {
            return true;
        }
    }

    /** A local precondition failed before any request left this process (e.g. no server key configured). */
    record LocalFailure(String safeCode) implements BankingWithdrawalPrepareResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }
}

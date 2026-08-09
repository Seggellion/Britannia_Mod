package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one full deposit attempt reported to whoever called {@link
 * BankingDepositProxyService#triggerDepositForTesting}, mirroring {@link
 * BankingOpenClientResult}'s closed-sealed-hierarchy shape so a caller cannot forget to handle
 * a case -- most importantly {@link ReconciliationRequired}, which must never be handled the
 * same way as ordinary success or ordinary failure (docs/banking_item_transfer.md's Rails-side
 * documentation of the same distinction; see {@link BankingConfirmResult.ReconciliationRequired}
 * for the wire-level version of this same requirement).
 */
public sealed interface BankingDepositResult permits
        BankingDepositResult.Confirmed,
        BankingDepositResult.ReconciliationRequired,
        BankingDepositResult.RemovalFailed,
        BankingDepositResult.RejectedLocally,
        BankingDepositResult.Rejected,
        BankingDepositResult.TransportFailure,
        BankingDepositResult.LocalFailure {

    /** The full sequence completed cleanly: item removed, Rails confirmed, local receipt resolved. */
    record Confirmed(UUID operationPublicId, UUID bankItemPublicId) implements BankingDepositResult {
        public Confirmed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
            Objects.requireNonNull(bankItemPublicId, "bankItemPublicId");
        }
    }

    /**
     * The item was removed and confirm was sent, but Rails reports {@code
     * RECONCILIATION_REQUIRED}: the operation is now ambiguously held and must never be treated
     * as ordinary success. The item is NOT returned to the player. The local receipt is left
     * unresolved on purpose (see {@link BankingDepositProxyService} for the full reasoning) so
     * a future startup scan / admin reconciliation review can find it.
     */
    record ReconciliationRequired(UUID operationPublicId) implements BankingDepositResult {
        public ReconciliationRequired {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /**
     * Rails prepared the operation, but the live slot no longer matched what was captured at
     * selection time by the time of the removal-time revalidation check (protocol step 4).
     * Cancel was called against Rails (safe -- nothing physical had happened yet). No item was
     * lost, no receipt was ever written.
     */
    record RemovalFailed(UUID operationPublicId) implements BankingDepositResult {
        public RemovalFailed {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    /** Rejected before any network call was made (protocol step 2) -- zero Rails calls, nothing to clean up. */
    record RejectedLocally(BankingDepositLocalRejectionReason reason) implements BankingDepositResult {
        public RejectedLocally {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** A well-formed Rails rejection at either stage (teller chain, capacity, invalid_transition, ...). */
    record Rejected(BankingDepositStage stage, BankingTransferOutcome outcome, boolean retryable) implements BankingDepositResult {
        public Rejected {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Network/transport-level failure. See {@link BankingDepositStage} for why the stage matters here. */
    record TransportFailure(BankingDepositStage stage, String safeCode) implements BankingDepositResult {
        public TransportFailure {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    /**
     * A local precondition failed before prepare was even attempted -- always pre-{@link
     * BankingDepositStage#PREPARE}, so no stage tag is needed (e.g. the same player/slot
     * already has a deposit in flight, or the teller could not be resolved).
     */
    record LocalFailure(String safeCode) implements BankingDepositResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

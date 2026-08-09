package com.seggellion.britannia_mod.service.banking;

import java.util.List;
import java.util.Objects;

/**
 * Result of one {@code banking/open} attempt. Mirrors the shape of
 * {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClientResult}: a closed,
 * exhaustively-matched sealed hierarchy rather than a single class with a nullable-field
 * grab bag, so a caller (here, {@link BankingProxyService}) cannot forget to handle a case.
 */
public sealed interface BankingOpenClientResult permits
        BankingOpenClientResult.Success,
        BankingOpenClientResult.Rejected,
        BankingOpenClientResult.TransportFailure,
        BankingOpenClientResult.LocalFailure {

    boolean retryable();

    /**
     * {@code OPENED}: the account was resolved. {@code bankItems} is Rails' real {@code
     * bank_items.items} array (Milestone 9 Rails Slice 1) -- a sibling of {@code account} in
     * the wire envelope, not a field on it, matching Rails' own envelope shape exactly.
     */
    record Success(BankingOpenAccount account, List<BankItemSummary> bankItems) implements BankingOpenClientResult {
        public Success {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(bankItems, "bankItems");
            bankItems = List.copyOf(bankItems);
        }

        @Override
        public boolean retryable() {
            return false;
        }
    }

    /** Any well-formed non-success protocol outcome Rails returned (never {@code OPENED}). */
    record Rejected(BankingOpenOutcome outcome, boolean retryable) implements BankingOpenClientResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == BankingOpenOutcome.OPENED) {
                throw new IllegalArgumentException("OPENED is a Success, not a Rejected outcome");
            }
        }
    }

    /** Network/transport-level failure: timeout, connection reset, malformed/unexpected response body. */
    record TransportFailure(String safeCode) implements BankingOpenClientResult {
        @Override
        public boolean retryable() {
            return true;
        }
    }

    /** A local precondition failed before any request left this process (e.g. no server key configured). */
    record LocalFailure(String safeCode) implements BankingOpenClientResult {
        @Override
        public boolean retryable() {
            return false;
        }
    }
}

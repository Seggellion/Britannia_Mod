package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * What {@code banking/currency/deposit/all/prepare} answered. Structurally identical to {@link
 * BankingCurrencyDepositPrepareResult} -- the response carries only the {@code operation} object,
 * and the caller already knows the totals it asked for, so there is nothing else worth parsing.
 *
 * <p>{@code NO_COINS} and {@code BALANCE_CAPACITY_EXCEEDED} arrive as ordinary {@link Rejected}
 * outcomes rather than as their own cases: both are clean pre-disposal refusals with nothing
 * created on either side, which is exactly what {@code Rejected} already means. Only the proxy
 * service's reporting distinguishes them, and only so the player reads something useful.
 */
public sealed interface BankingDepositAllCoinsPrepareResult permits
        BankingDepositAllCoinsPrepareResult.Success,
        BankingDepositAllCoinsPrepareResult.Rejected,
        BankingDepositAllCoinsPrepareResult.TransportFailure,
        BankingDepositAllCoinsPrepareResult.LocalFailure {

    /** Rails created one {@code bulk_currency_deposit} operation in {@code prepared}. Nothing is credited or reserved yet. */
    record Success(UUID operationPublicId) implements BankingDepositAllCoinsPrepareResult {
        public Success {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingDepositAllCoinsPrepareResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record TransportFailure(String safeCode) implements BankingDepositAllCoinsPrepareResult {
        public TransportFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    record LocalFailure(String safeCode) implements BankingDepositAllCoinsPrepareResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

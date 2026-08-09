package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * Parsed result of {@code POST /api/banking/currency/withdrawal/prepare}, mirroring {@link
 * BankingCurrencyDepositPrepareResult}'s sealed shape exactly: {@link Success} carries only the
 * operation's public id, since a currency response never has a {@code bank_item}
 * (docs/banking_currency_transfer.md) -- the caller already knows the denomination and amount
 * it asked for, so there is nothing else to parse back.
 */
public sealed interface BankingCurrencyWithdrawalPrepareResult permits
        BankingCurrencyWithdrawalPrepareResult.Success,
        BankingCurrencyWithdrawalPrepareResult.Rejected,
        BankingCurrencyWithdrawalPrepareResult.TransportFailure,
        BankingCurrencyWithdrawalPrepareResult.LocalFailure {

    record Success(UUID operationPublicId) implements BankingCurrencyWithdrawalPrepareResult {
        public Success {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingCurrencyWithdrawalPrepareResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record TransportFailure(String safeCode) implements BankingCurrencyWithdrawalPrepareResult {
        public TransportFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    record LocalFailure(String safeCode) implements BankingCurrencyWithdrawalPrepareResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

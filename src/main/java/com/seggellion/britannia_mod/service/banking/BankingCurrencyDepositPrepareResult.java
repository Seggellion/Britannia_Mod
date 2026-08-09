package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * Parsed result of {@code POST /api/banking/currency/deposit/prepare}, mirroring {@link
 * BankingDepositPrepareResult}'s sealed shape with one deliberate difference: {@link Success}
 * carries only the operation's public id -- a currency prepare response has no {@code
 * bank_item} at all (docs/banking_currency_transfer.md: "No bank_item key -- a currency
 * operation never has one"), so there is no second identifier to parse or thread through.
 */
public sealed interface BankingCurrencyDepositPrepareResult permits
        BankingCurrencyDepositPrepareResult.Success,
        BankingCurrencyDepositPrepareResult.Rejected,
        BankingCurrencyDepositPrepareResult.TransportFailure,
        BankingCurrencyDepositPrepareResult.LocalFailure {

    record Success(UUID operationPublicId) implements BankingCurrencyDepositPrepareResult {
        public Success {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingCurrencyDepositPrepareResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record TransportFailure(String safeCode) implements BankingCurrencyDepositPrepareResult {
        public TransportFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    record LocalFailure(String safeCode) implements BankingCurrencyDepositPrepareResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * Parsed result of {@code POST /api/banking/cheque/issue/prepare}, mirroring {@link
 * BankingCurrencyWithdrawalPrepareResult}'s sealed shape: {@link Success} carries only the
 * operation's public id -- a cheque issuance prepare response never has a {@code bank_cheque}
 * (that does not exist until confirm; see {@code docs/banking_bank_cheque_issuance.md}) and
 * never has a {@code bank_item} either.
 */
public sealed interface BankingChequeIssuancePrepareResult permits
        BankingChequeIssuancePrepareResult.Success,
        BankingChequeIssuancePrepareResult.Rejected,
        BankingChequeIssuancePrepareResult.TransportFailure,
        BankingChequeIssuancePrepareResult.LocalFailure {

    record Success(UUID operationPublicId) implements BankingChequeIssuancePrepareResult {
        public Success {
            Objects.requireNonNull(operationPublicId, "operationPublicId");
        }
    }

    record Rejected(BankingTransferOutcome outcome, boolean retryable) implements BankingChequeIssuancePrepareResult {
        public Rejected {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record TransportFailure(String safeCode) implements BankingChequeIssuancePrepareResult {
        public TransportFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }

    record LocalFailure(String safeCode) implements BankingChequeIssuancePrepareResult {
        public LocalFailure {
            Objects.requireNonNull(safeCode, "safeCode");
        }
    }
}

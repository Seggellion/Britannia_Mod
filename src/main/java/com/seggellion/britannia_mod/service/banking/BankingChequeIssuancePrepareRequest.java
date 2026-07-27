package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/cheque/issue/prepare} needs
 * (docs/banking_bank_cheque_issuance.md): the same player/teller/idempotency identification
 * every prepare carries, plus a {@code cheque: { amount }} envelope. Unlike {@link
 * BankingCurrencyWithdrawalPrepareRequest}, there is no {@code currency_key} -- a cheque is
 * currency-agnostic (ADR-012), not gold/silver/copper-keyed. {@code amount} is expressed in
 * copper, the wire-level unit Rails' {@code ChequePayloadValidator}/{@code BankCheque} actually
 * validate and store against (ADR-018/019), not gold.
 */
public record BankingChequeIssuancePrepareRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    String idempotencyKey,
    int amount
) {
    public BankingChequeIssuancePrepareRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}

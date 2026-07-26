package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/currency/withdrawal/prepare} needs
 * (docs/banking_currency_transfer.md): the same player/teller/idempotency identification every
 * prepare carries, plus a {@code currency: { currency_key, amount }} envelope. Unlike {@link
 * BankingWithdrawalPrepareRequest}, there is no {@code bank_item_public_id} to reference --
 * currency has no per-unit identity, so the request identifies what it wants purely by
 * denomination and amount, exactly matching Rails' own documented request shape.
 */
public record BankingCurrencyWithdrawalPrepareRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    String idempotencyKey,
    String currencyKey,
    int amount
) {
    private static final Set<String> SUPPORTED_KEYS = Set.of("gold", "silver", "copper");

    public BankingCurrencyWithdrawalPrepareRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        Objects.requireNonNull(currencyKey, "currencyKey");
        if (!SUPPORTED_KEYS.contains(currencyKey)) {
            throw new IllegalArgumentException("unsupported currency key: " + currencyKey);
        }
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}

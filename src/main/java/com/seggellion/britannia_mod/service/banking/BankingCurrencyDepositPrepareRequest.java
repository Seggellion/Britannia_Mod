package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/currency/deposit/prepare} needs
 * (docs/banking_currency_transfer.md): the same player/teller/idempotency identification every
 * prepare carries, plus a {@code currency: { currency_key, amount }} envelope in place of the
 * item flow's serialized-stack envelope. {@code currencyKey} is one of the three stable wire
 * keys {@link com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry} produces;
 * {@code amount} is the exact live coin count captured from the selected slot (Rails validates
 * it as a positive integer -- a zero or negative amount is a programming error here, not a
 * request to send).
 */
public record BankingCurrencyDepositPrepareRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    String idempotencyKey,
    String currencyKey,
    int amount
) {
    private static final Set<String> SUPPORTED_KEYS = Set.of("gold", "silver", "copper");

    public BankingCurrencyDepositPrepareRequest {
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

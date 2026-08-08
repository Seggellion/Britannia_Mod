package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/currency/deposit/all/prepare} needs
 * (docs/banking_bulk_currency_deposit.md): the same player/teller/idempotency identification
 * every prepare carries, plus a {@code coins: { gold, silver, copper }} envelope.
 *
 * <p><b>All three amounts are always sent, including zeros.</b> Rails validates {@code coins} as
 * a strict three-key envelope and rejects both a missing key and an unknown one. Its reasoning is
 * worth repeating here because it constrains this record: a zero is a real statement -- "the
 * sweep found no gold" -- and letting absence express it would make a dropped field
 * indistinguishable from one. So the constructor permits zeros and forbids negatives, rather than
 * the positive-only rule {@link BankingCurrencyDepositPrepareRequest} uses for a
 * single-denomination deposit where a zero would be meaningless.
 *
 * <p>What it does forbid is all three being zero. That is {@code NO_COINS}, and the sweep decides
 * it locally before ever building a request -- reaching this constructor with an empty sweep is a
 * programming error, not a request to send.
 */
public record BankingDepositAllCoinsPrepareRequest(
        UUID playerUuid,
        UUID worldNpcPublicId,
        String idempotencyKey,
        int gold,
        int silver,
        int copper
) {
    public BankingDepositAllCoinsPrepareRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        if (gold < 0 || silver < 0 || copper < 0) {
            throw new IllegalArgumentException("coin totals must not be negative");
        }
        if (gold == 0 && silver == 0 && copper == 0) {
            throw new IllegalArgumentException("an empty sweep must be rejected locally, never sent");
        }
    }
}

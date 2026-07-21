package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/withdrawal/prepare} needs (docs/banking_item_transfer.md):
 * the same player/teller identification {@link BankingDepositPrepareRequest} carries (prepare
 * revalidates the full teller/assignment/account chain fresh, same reasoning as deposit -- see
 * that class's own docs), plus the target {@code bank_item_public_id} instead of a serialized
 * payload -- withdrawal reserves an existing item, it does not submit one.
 */
public record BankingWithdrawalPrepareRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    String idempotencyKey,
    UUID bankItemPublicId
) {
    public BankingWithdrawalPrepareRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        Objects.requireNonNull(bankItemPublicId, "bankItemPublicId");
    }
}

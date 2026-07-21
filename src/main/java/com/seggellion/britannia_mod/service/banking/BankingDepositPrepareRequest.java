package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/deposit/prepare} needs (docs/banking_item_transfer.md).
 * {@code payload} is the raw {@link com.seggellion.britannia_mod.bank.item.BankItemCodec}
 * bytes -- base64 wire-encoding happens in {@link BankingDepositClient}, not here, matching
 * how {@link BankingOpenRequest} carries plain typed fields rather than pre-serialized ones.
 */
public record BankingDepositPrepareRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    String idempotencyKey,
    int schemaVersion,
    byte[] payload,
    String fingerprint,
    double weight
) {
    public BankingDepositPrepareRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) throw new IllegalArgumentException("payload must not be empty");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.isBlank()) throw new IllegalArgumentException("fingerprint must not be blank");
        if (weight < 0.0 || !Double.isFinite(weight)) throw new IllegalArgumentException("weight must be finite and non-negative");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}

package com.seggellion.britannia_mod.service.banking;

import com.seggellion.britannia_mod.bank.item.BankItemIdentity;

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
    double weight,
    BankItemIdentity identity,
    /*
     * The cheque inside the opaque payload, when the deposited stack is one -- null for every
     * ordinary item, and null for a cheque when this build does not emit the link. Rails stores
     * it verbatim and uses it to answer "can this stored row be cashed?"; it is the only way the
     * link can exist at all, because the payload is opaque to Rails and decoding it requires
     * withdrawing the item first.
     */
    @javax.annotation.Nullable UUID chequePublicId
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
        // Never null, so no caller downstream has to null-check before asking what it holds --
        // "no identity resolved" is BankItemIdentity.EMPTY, which serializes to nothing at all.
        if (identity == null) identity = BankItemIdentity.EMPTY;
        payload = payload.clone();
    }

    /**
     * Milestone 17 convenience for the many existing call sites (and GameTests) that predate item
     * identity: builds exactly the v1 request they always built.
     */
    public static BankingDepositPrepareRequest withoutIdentity(
            UUID playerUuid, UUID worldNpcPublicId, String idempotencyKey,
            int schemaVersion, byte[] payload, String fingerprint, double weight
    ) {
        return new BankingDepositPrepareRequest(
                playerUuid, worldNpcPublicId, idempotencyKey, schemaVersion, payload, fingerprint, weight,
                BankItemIdentity.EMPTY, null
        );
    }

    /** The v2 shape: identity, no cheque link. Keeps every pre-v3 call site building exactly it. */
    public static BankingDepositPrepareRequest withoutChequeLink(
            UUID playerUuid, UUID worldNpcPublicId, String idempotencyKey,
            int schemaVersion, byte[] payload, String fingerprint, double weight, BankItemIdentity identity
    ) {
        return new BankingDepositPrepareRequest(
                playerUuid, worldNpcPublicId, idempotencyKey, schemaVersion, payload, fingerprint, weight,
                identity, null
        );
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}

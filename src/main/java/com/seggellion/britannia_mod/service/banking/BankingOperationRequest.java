package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/confirm} and {@code POST /api/banking/cancel} both need
 * (docs/banking_item_transfer.md): just the player and the target operation. Neither action
 * revalidates the teller/assignment chain the way prepare does (see that doc's own "Prepare:
 * the same teller/assignment/account chain" section for why this asymmetry is deliberate), so
 * no world_npc_public_id is carried here. {@code reason} is cancel-only and optional; Rails
 * accepts it but does not yet persist it (a documented pre-existing Milestone 8 gap, not this
 * slice's to fix) -- left {@code null} for a confirm request.
 */
public record BankingOperationRequest(UUID playerUuid, UUID operationPublicId, String reason) {
    public BankingOperationRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(operationPublicId, "operationPublicId");
    }

    public static BankingOperationRequest confirm(UUID playerUuid, UUID operationPublicId) {
        return new BankingOperationRequest(playerUuid, operationPublicId, null);
    }

    public static BankingOperationRequest cancel(UUID playerUuid, UUID operationPublicId, String reason) {
        return new BankingOperationRequest(playerUuid, operationPublicId, reason);
    }
}

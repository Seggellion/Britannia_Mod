package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/cheque/redeem} needs (docs/banking_bank_cheque_redemption.md):
 * player/teller identification (the same {@code resolve_account_for_teller} chain every action
 * uses) plus the cheque's own {@code public_id} -- nothing else. Unlike every prepare request,
 * there is no {@code idempotency_key}: this endpoint is not a phase of the
 * {@code BankTransferOperations::{Create,Confirm}} state machine at all (see {@link
 * BankingChequeRedemptionResult}'s own docs), and the cheque's own state is Rails' idempotency
 * key instead (Rails doc's own "Idempotency" section).
 */
public record BankingChequeRedemptionRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    UUID chequePublicId
) {
    public BankingChequeRedemptionRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(chequePublicId, "chequePublicId");
    }
}

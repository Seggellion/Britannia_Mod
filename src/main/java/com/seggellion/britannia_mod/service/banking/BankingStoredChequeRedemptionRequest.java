package com.seggellion.britannia_mod.service.banking;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/cheque/redeem_stored} needs
 * (docs/banking_bank_cheque_stored_redemption.md): player/teller identification plus the stored
 * row's own {@code public_id} -- and deliberately <b>no cheque id</b>.
 *
 * <p>That absence is the point. Rails derives the cheque from the vault row it already holds,
 * never from a client claim, which is the same "clients send selection references, never facts"
 * rule the whole banking surface follows. This client could not send the cheque id honestly
 * anyway: the id lives inside the stored payload, and reading it would mean withdrawing the item
 * first, which is exactly the non-atomic sequence this endpoint exists to replace.
 *
 * <p>No {@code idempotency_key} either, for the same reason {@link BankingChequeRedemptionRequest}
 * carries none: this is not a phase of the {@code BankTransferOperations} state machine, and
 * replay safety comes from state -- a second call finds the row no longer available, or the
 * cheque already redeemed.
 */
public record BankingStoredChequeRedemptionRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    UUID bankItemPublicId
) {
    public BankingStoredChequeRedemptionRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(bankItemPublicId, "bankItemPublicId");
    }
}

package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * One call, {@code redeemStored}, to {@code POST /api/banking/cheque/redeem_stored}.
 *
 * <p>Its own port rather than a second method on {@link BankingChequeRedemptionClientPort},
 * matching this package's one-port-per-flow shape -- and so the two flows' fakes stay
 * independent, which is what lets a test assert that cashing from the vault never touched the
 * pack-side endpoint and vice versa.
 *
 * <p>Like the pack-side port it has no confirm/cancel: the single call already commits, and
 * nothing was ever reserved to release. Stored redemption is in fact the simplest flow in the
 * whole banking surface -- it materialises nothing in any inventory, so there is no step after
 * Rails commits that can fail (docs/banking_bank_cheque_stored_redemption.md, "Why this is
 * single-shot rather than prepare/confirm").
 */
public interface BankingStoredChequeRedemptionClientPort {
    CompletableFuture<BankingChequeRedemptionResult> redeemStored(
            MinecraftServer server, BankingStoredChequeRedemptionRequest request);
}

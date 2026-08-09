package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * What {@link BankingChequeRedemptionProxyService} actually needs from its client: one call,
 * {@code redeem}, to {@code POST /api/banking/cheque/redeem}. Deliberately has no {@code
 * confirm}/{@code cancel} methods at all -- unlike every sibling port in this package, this is
 * not a departure kept only "for interface symmetry"; there is genuinely nothing to confirm
 * (the one call already commits) and nothing to cancel (no prepare ever reserved anything to
 * release). See {@link BankingChequeRedemptionResult}'s own docs for the full reasoning.
 */
public interface BankingChequeRedemptionClientPort {
    CompletableFuture<BankingChequeRedemptionResult> redeem(MinecraftServer server, BankingChequeRedemptionRequest request);
}

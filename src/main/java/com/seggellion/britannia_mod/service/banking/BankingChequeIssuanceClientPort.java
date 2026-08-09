package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * What {@link BankingChequeIssuanceProxyService} actually needs from its client: a
 * cheque-specific prepare, a cheque-specific confirm (unlike every other flow, this one must
 * parse the response body -- see {@link BankingChequeIssuanceConfirmResult}'s own docs for why),
 * and the exact same operation-type-agnostic cancel every transfer flow shares. Cancel is
 * shared, not cheque-specific, because this flow never actually calls it (see {@link
 * BankingChequeIssuanceProxyService}'s own docs for why there is no post-prepare,
 * pre-confirm abort path) -- kept on the port only for interface symmetry with every sibling
 * client, not because production code invokes it.
 */
public interface BankingChequeIssuanceClientPort {
    CompletableFuture<BankingChequeIssuancePrepareResult> prepareChequeIssuance(
            MinecraftServer server, BankingChequeIssuancePrepareRequest request);

    CompletableFuture<BankingChequeIssuanceConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request);

    CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request);
}

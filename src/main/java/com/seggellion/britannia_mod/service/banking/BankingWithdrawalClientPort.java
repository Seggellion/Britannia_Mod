package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * What {@link BankingWithdrawalProxyService} actually needs from a withdrawal client, mirroring
 * {@link BankingDepositClientPort}'s shape exactly -- three independently-callable actions, one
 * seam per action so tests can return different canned results for each. Confirm/cancel share
 * the exact same request/result types deposit uses ({@link BankingOperationRequest}, {@link
 * BankingConfirmResult}, {@link BankingCancelResult}) since those two actions are entirely
 * operation-type-agnostic on the wire -- only prepare differs (a different request shape, and a
 * result carrying back the full item payload rather than just two identifiers).
 */
public interface BankingWithdrawalClientPort {
    CompletableFuture<BankingWithdrawalPrepareResult> prepareWithdrawal(MinecraftServer server, BankingWithdrawalPrepareRequest request);

    CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request);

    CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request);
}

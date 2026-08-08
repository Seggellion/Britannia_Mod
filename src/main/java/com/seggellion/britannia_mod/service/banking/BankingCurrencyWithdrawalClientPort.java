package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * What {@link BankingCurrencyWithdrawalProxyService} actually needs from its client: a
 * currency-specific prepare, plus the exact same operation-type-agnostic confirm/cancel every
 * transfer flow shares. Mirrors {@link BankingCurrencyDepositClientPort}'s established shape.
 */
public interface BankingCurrencyWithdrawalClientPort {
    CompletableFuture<BankingCurrencyWithdrawalPrepareResult> prepareCurrencyWithdrawal(
            MinecraftServer server, BankingCurrencyWithdrawalPrepareRequest request);

    CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request);

    CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request);
}

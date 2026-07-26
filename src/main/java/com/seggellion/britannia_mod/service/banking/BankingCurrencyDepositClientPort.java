package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * What {@link BankingCurrencyDepositProxyService} actually needs from its client: a
 * currency-specific prepare, plus the exact same operation-type-agnostic confirm/cancel every
 * transfer flow shares. Mirrors {@link BankingWithdrawalClientPort}'s established shape (own
 * prepare + shared confirm/cancel signatures), and tests substitute it with a per-method fake
 * at this level for the same reason {@link BankingDepositClientPort}'s own docs give.
 */
public interface BankingCurrencyDepositClientPort {
    CompletableFuture<BankingCurrencyDepositPrepareResult> prepareCurrencyDeposit(
            MinecraftServer server, BankingCurrencyDepositPrepareRequest request);

    CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request);

    CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request);
}

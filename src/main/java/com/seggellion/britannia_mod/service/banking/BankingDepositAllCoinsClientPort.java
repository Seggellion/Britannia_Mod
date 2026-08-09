package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * The Rails calls a Deposit All Coins sequence makes: one bulk prepare, then the shared
 * type-agnostic confirm and cancel.
 *
 * <p>Its own port rather than a method on an existing one, matching how every transfer flow in
 * this package owns its own -- it keeps each flow's GameTests able to substitute a client without
 * also stubbing the four actions they do not exercise.
 */
public interface BankingDepositAllCoinsClientPort {

    CompletableFuture<BankingDepositAllCoinsPrepareResult> prepareDepositAllCoins(
            MinecraftServer server, BankingDepositAllCoinsPrepareRequest request
    );

    CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request);

    CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request);
}

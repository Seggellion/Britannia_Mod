package com.seggellion.britannia_mod.service.banking;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * What {@link BankingDepositProxyService} actually needs from a deposit client: three
 * independently-callable actions. {@link BankingDepositClient} (the real HTTP implementation)
 * implements this, but tests substitute it directly with a simple per-method fake rather than
 * going through {@link BankingDepositClient}'s own lower-level transport seams -- those seams
 * share one {@code TransportSubmitter} across all three HTTP actions (by design, matching
 * {@link BankingOpenClient}'s established shape for its one endpoint), which cannot itself
 * distinguish which action a given call belongs to. A test that needs prepare, confirm, and
 * cancel to each return different canned results at different times needs a seam at this
 * level, not that one.
 */
public interface BankingDepositClientPort {
    CompletableFuture<BankingDepositPrepareResult> prepare(MinecraftServer server, BankingDepositPrepareRequest request);

    CompletableFuture<BankingConfirmResult> confirm(MinecraftServer server, BankingOperationRequest request);

    CompletableFuture<BankingCancelResult> cancel(MinecraftServer server, BankingOperationRequest request);
}

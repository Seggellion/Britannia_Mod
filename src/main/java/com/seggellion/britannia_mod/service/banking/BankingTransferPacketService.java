package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * Milestone 9 NeoForge Slice 3a: the one and only production entry point into {@link
 * BankingDepositProxyService#triggerDeposit} and {@link
 * BankingWithdrawalProxyService#triggerWithdrawal} -- reached from {@link
 * BankDepositRequestC2SPayload}/{@link BankWithdrawalRequestC2SPayload}, registered in {@code
 * NetworkHandler}. Structurally mirrors {@link BankingProxyService#handle}: the teller is
 * re-resolved fresh from the live world by entity id (never trusted from anything captured
 * earlier), exactly the same {@link BankingProxyService#resolve} fresh-revalidation this
 * codebase already uses for {@code bank.open} itself -- a stale, dead, moved, or
 * capability-revoked teller silently drops the request the same way an invalid {@code
 * bank.open} interaction already does, rather than inventing a new, differently-shaped failure
 * mode for this one packet type.
 *
 * <p>The client-supplied {@code entityId} is only ever a selection reference ("the player says
 * this is the teller they're talking to"), never trusted as fact -- {@link
 * BankingProxyService#resolve} independently re-checks liveness, distance, and {@code
 * bank.open} capability support every time, precisely the same trust model {@code
 * SellItemsC2SPayload}'s handling already established for trader entity ids.
 */
public final class BankingTransferPacketService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BankingTransferPacketService() {
    }

    /**
     * Test-only seam mirroring {@link BankingProxyService.AccountScreenSender} exactly: lets a
     * GameTest observe precisely which clean-rejection/reconciliation-required result would
     * have been sent, without needing a real client connection to capture the packet.
     */
    @FunctionalInterface
    public interface ResultSender {
        void send(ServerPlayer player, BankTransferResultS2CPayload.Operation operation, BankTransferResultS2CPayload.Kind kind);
    }

    private static ResultSender resultSender = BankTransferResultS2CPayload::send;

    public static void useResultSenderForTesting(ResultSender sender) {
        resultSender = sender;
    }

    public static void resetResultSenderForTesting() {
        resultSender = BankTransferResultS2CPayload::send;
    }

    public static void handleDeposit(ServerPlayer player, BankDepositRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingDepositProxyService.triggerDeposit(player, teller, payload.slotIndex())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking deposit trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingDepositResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingDepositResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    public static void handleWithdrawal(ServerPlayer player, BankWithdrawalRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingWithdrawalProxyService.triggerWithdrawal(player, teller, payload.bankItemPublicId())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking withdrawal trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingWithdrawalResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingWithdrawalResult.ReconciliationRequired ignored -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.RECONCILIATION_REQUIRED
                        );
                        default -> resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                    }
                }));
    }

    /**
     * Same fresh, from-scratch teller resolution {@code bank.open} itself uses -- see {@link
     * BankingProxyService#resolve}'s own docs for exactly what "fresh" means here (never trusts
     * anything captured earlier). {@code entityId} is resolved to a live {@link Entity} first,
     * purely to translate the client's transient network id into an object {@code resolve} can
     * validate; {@code resolve} itself is what actually decides whether this request proceeds.
     */
    private static ServiceNpcEntity resolveTeller(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof ServiceNpcEntity candidate)) return null;
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, candidate);
        return resolved == null ? null : candidate;
    }

    /**
     * On a clean confirm, re-runs {@code bank.open}'s own real fetch-and-send flow rather than
     * inventing a second, parallel "refresh" mechanism -- the freshly re-fetched account and
     * bank_items list this pushes to the client are exactly as correct as opening the screen
     * from scratch would be, satisfying Slice 3a's "refresh... rather than requiring the player
     * to close and reopen" without trusting any locally-computed delta.
     */
    private static void refreshAccount(ServerPlayer player, ServiceNpcEntity teller) {
        BankingProxyService.handle(player, teller);
    }
}

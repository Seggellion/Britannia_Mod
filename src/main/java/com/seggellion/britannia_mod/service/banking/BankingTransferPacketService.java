package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankCurrencyWithdrawalRequestC2SPayload;
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

    /**
     * Milestone 10: one deposit packet, two protocols -- the routing decision is made HERE,
     * server-side, from the live slot's own contents, never from anything the client claimed. A
     * bare coin stack ({@link CurrencyItemRegistry#isCurrencyStack}: top-level item identity
     * only -- a container holding coins is not a coin stack) routes to the currency balance
     * protocol; everything else takes the Milestone 9 item path unchanged. The item path's own
     * {@code CURRENCY} eligibility rejection is deliberately untouched underneath: if a coin
     * stack ever reached it directly (it cannot through this router), it still rejects cleanly
     * -- the redirect lives in this dispatch layer, not in a weakened eligibility rule.
     *
     * <p>The slot is read here purely to pick a protocol; whichever proxy service receives the
     * dispatch re-reads and re-validates the slot from scratch as its own step 1, so a swap
     * between this check and the proxy's capture resolves exactly like any other mid-flight
     * slot mutation: a clean local rejection or revalidation cancel, never a misrouted removal.
     */
    public static void handleDeposit(ServerPlayer player, BankDepositRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        if (CurrencyItemRegistry.isCurrencyStack(player.getInventory().getItem(payload.slotIndex()))) {
            handleCurrencyDeposit(player, teller, payload.slotIndex());
            return;
        }

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

    /**
     * The currency side of {@link #handleDeposit}'s routing, mapped to the client identically
     * to an item deposit (same {@code Operation.DEPOSIT} result channel, same {@code
     * refreshAccount} on a clean confirm -- which re-runs {@code bank.open}'s real fetch, so the
     * freshly-mutated gold/silver/copper balances land on the client through the exact same
     * path every balance display already uses).
     */
    private static void handleCurrencyDeposit(ServerPlayer player, ServiceNpcEntity teller, int slotIndex) {
        MinecraftServer server = player.server;
        BankingCurrencyDepositProxyService.triggerCurrencyDeposit(player, teller, slotIndex)
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking currency deposit trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.DEPOSIT,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingCurrencyDepositResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingCurrencyDepositResult.ReconciliationRequired ignored -> resultSender.send(
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
     * Milestone 10 Slice 2: currency withdrawal's own production entry point, mirroring {@link
     * #handleWithdrawal}'s shape exactly -- the same {@code Operation.WITHDRAWAL} result
     * channel, the same {@code refreshAccount} on a clean confirm. Unlike deposit's routing
     * (which decides currency-vs-item from the live slot's own contents), there is no ambiguity
     * to resolve here: this packet only ever means a currency withdrawal (an item withdrawal
     * always arrives via {@link BankWithdrawalRequestC2SPayload} instead), so there is no
     * routing decision to make -- straight to {@link
     * BankingCurrencyWithdrawalProxyService#triggerCurrencyWithdrawal}.
     */
    public static void handleCurrencyWithdrawal(ServerPlayer player, BankCurrencyWithdrawalRequestC2SPayload payload) {
        ServiceNpcEntity teller = resolveTeller(player, payload.entityId());
        if (teller == null) return;

        MinecraftServer server = player.server;
        BankingCurrencyWithdrawalProxyService.triggerCurrencyWithdrawal(player, teller, payload.currencyKey(), payload.amount())
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null || result == null) {
                        LOGGER.warn("banking currency withdrawal trigger for {} completed exceptionally", player.getStringUUID(), error);
                        resultSender.send(
                                player, BankTransferResultS2CPayload.Operation.WITHDRAWAL,
                                BankTransferResultS2CPayload.Kind.CLEAN_REJECTION
                        );
                        return;
                    }
                    switch (result) {
                        case BankingCurrencyWithdrawalResult.Confirmed ignored -> refreshAccount(player, teller);
                        case BankingCurrencyWithdrawalResult.ReconciliationRequired ignored -> resultSender.send(
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

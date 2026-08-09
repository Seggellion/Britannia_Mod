package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cashing a cheque that is already stored in the vault
 * (docs/banking_bank_cheque_stored_redemption.md).
 *
 * <h2>The simplest flow in this package, and why</h2>
 * Every other banking mutation is two-phase because something must materialise in a Minecraft
 * inventory, which can fail after Rails has already committed; prepare/confirm exists to make
 * that survivable. <b>Stored redemption materialises nothing.</b> The vault row goes terminal and
 * the balance rises, both entirely inside one Rails transaction. So there is no capture, no
 * revalidation, no removal, no durable receipt, no confirm and no cancel -- and, unlike even the
 * pack-side redemption this delegates the credit to, no item to dispose of or restore.
 *
 * <p>What remains is: resolve the teller fresh, dedupe, dispatch, and refresh on success. The
 * refresh is what tells the player anything happened -- the row disappears from the vault (Rails
 * lists only {@code available} items) and the balance rises, both in one authoritative snapshot.
 *
 * <h2>Nothing is decided here</h2>
 * This service deliberately does <b>not</b> pre-check whether the selected row is a cheque, or a
 * redeemable one. It cannot: the cheque link lives in Rails, and the client's {@code
 * cheque_redeemable} flag is a possibly-stale snapshot used only to decide whether to <em>offer</em>
 * the gesture. Rails re-derives every fact under its own row locks and answers {@code
 * ITEM_NOT_FOUND} / {@code CHEQUE_NOT_FOUND} / {@code CHEQUE_ALREADY_REDEEMED} / … accordingly.
 * A modified client aiming this at an ordinary item gets a clean rejection, not a misroute.
 */
public final class BankingStoredChequeRedemptionProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BankingStoredChequeRedemptionClientPort client = new BankingStoredChequeRedemptionClient();

    /**
     * Keyed by (player, bank item): two different stored cheques may legitimately be cashed
     * concurrently, but a rapid double-send for the same row must not spend two round trips.
     * Rails is safe against the duplicate regardless -- the second call finds the row no longer
     * available -- so this is courtesy, not correctness, exactly like the deposit path's own.
     */
    private static final Set<ItemKey> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingStoredChequeRedemptionProxyService() {
    }

    public static void useClientForTesting(BankingStoredChequeRedemptionClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingStoredChequeRedemptionClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    /**
     * The one production entry point, reached from a real {@code
     * BankStoredChequeRedemptionRequestC2SPayload} via {@link BankingTransferPacketService}.
     * Must be called from the main server thread, like every sibling trigger.
     */
    public static CompletableFuture<BankingChequeRedemptionResult> triggerStoredChequeRedemption(
            ServerPlayer player, ServiceNpcEntity teller, UUID bankItemPublicId
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(
                    new BankingChequeRedemptionResult.LocalFailure("teller_no_longer_valid"));
        }

        ItemKey key = new ItemKey(player.getUUID(), bankItemPublicId);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new BankingChequeRedemptionResult.LocalFailure("stored_redemption_already_in_flight"));
        }

        MinecraftServer server = player.server;
        BankingStoredChequeRedemptionRequest request = new BankingStoredChequeRedemptionRequest(
                player.getUUID(), resolved.worldNpcPublicId(), bankItemPublicId);

        final CompletableFuture<BankingChequeRedemptionResult> call;
        try {
            call = client.redeemStored(server, request);
        } catch (RuntimeException synchronousFailure) {
            // Never leak the in-flight marker when submit() throws before returning a future --
            // the same guard every sibling service keeps.
            IN_FLIGHT.remove(key);
            LOGGER.warn("banking/cheque/redeem_stored submission threw synchronously", synchronousFailure);
            return CompletableFuture.completedFuture(
                    new BankingChequeRedemptionResult.TransportFailure("synchronous_submission_failure"));
        }

        return call
                .exceptionally(error -> {
                    LOGGER.warn("banking/cheque/redeem_stored completed exceptionally", error);
                    return new BankingChequeRedemptionResult.TransportFailure("transport_error");
                })
                .whenComplete((result, error) -> IN_FLIGHT.remove(key));
    }

    private record ItemKey(UUID playerId, UUID bankItemPublicId) {
    }
}

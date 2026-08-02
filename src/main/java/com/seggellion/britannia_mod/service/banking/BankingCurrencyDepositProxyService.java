package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 10 NeoForge Slice 1: the currency deposit path -- coin-stack capture, revalidation,
 * removal, durable receipt sequencing, and confirmation. A structural sibling of {@link
 * BankingDepositProxyService} (the same resolve-fresh / dedupe-in-flight / dispatch-off-thread /
 * marshal-back-via-{@code server.execute} skeleton, step for step), not an extension of it:
 * every step's interior differs (a coin capture is a currency key and an exact count, not a
 * serialized payload/fingerprint/weight; revalidation is exact item+count identity, not a
 * fingerprint; the receipt carries {@code currencyAmount}, not {@code itemPayload}; no {@code
 * bankItemPublicId} exists anywhere in this flow), and {@link BankingDepositResult}'s own
 * non-null {@code bankItemPublicId} contracts made reuse impossible without weakening the item
 * path's guarantees. See {@link BankingCurrencyDepositResult} for that reasoning.
 *
 * <h2>Protocol sequencing (docs/banking_currency_transfer.md; Section A.6; playbook M10)</h2>
 * <ol>
 *   <li>Capture the source slot's currency key and exact live count at selection time. A slot
 *       that is empty or not a bare coin stack rejects locally, before any network call --
 *       unreachable through the real packet router (which only dispatches genuine coin stacks
 *       here), kept as this service's own self-contained guard.</li>
 *   <li>No further local validation exists, deliberately: currency has no payload to oversize,
 *       no nesting to bound, no weight to resolve, and no capacity/weight check of any kind --
 *       Rails Slice 1 confirmed a currency deposit reserves nothing (Section A.6: currency
 *       consumes no bank weight), so a pre-check here would be inventing a rule Rails does not
 *       have.</li>
 *   <li>Call Rails currency/deposit/prepare with {@code (currency_key, amount)}.</li>
 *   <li>On success, revalidate the LIVE slot against the capture: same coin {@link Item} and
 *       the exact same count (the playbook's own "revalidate the exact slot/item/count after
 *       prepare" -- item identity only, not component identity, matching how {@code
 *       MerchantEconomyService}/{@code ServerEconomyService} already treat coins as fungible by
 *       item). A mismatch calls Cancel (safe -- nothing physical has happened yet) and reports
 *       {@link BankingCurrencyDepositResult.RemovalFailed}. No receipt is written on this
 *       path.</li>
 *   <li>On a match: remove the exact captured count, <b>then immediately</b> write the durable
 *       local receipt (synchronous flush) with {@code currencyAmount} set -- the first real
 *       exercise of the field {@link com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt}
 *       carried as a forward-compat placeholder since Milestone 9. The removal-then-receipt
 *       ordering is the item deposit's own deliberate ordering, inherited with its reasoning
 *       intact: a receipt written before removal is certain would record an event that may
 *       never have happened, and any later gap than immediately-after-removal reopens the
 *       silently-unrecoverable crash window the receipt exists to close.</li>
 *   <li>Call Rails confirm (the exact same type-agnostic endpoint item transfers use). On
 *       {@code CONFIRMED}: resolve the local receipt.</li>
 *   <li>On {@code RECONCILIATION_REQUIRED}: never treat as ordinary success -- the coins are
 *       not returned (Rails now holds the operation ambiguously), and the receipt is escalated
 *       to its own persisted {@code RECONCILIATION_REQUIRED} status, identical semantics to the
 *       item deposit's, never a weaker version.</li>
 *   <li>On any other confirm failure/timeout: the receipt remains unresolved by design --
 *       {@code scanUnresolved()} on a later startup catches it, and {@link
 *       #resumeConfirmCurrencyDeposit} resumes it through this same confirm core.</li>
 * </ol>
 *
 * <h2>IN_FLIGHT: its own set, same key shape</h2>
 * Keyed by {@code (player, slot)} exactly like the item deposit's -- the recon confirmed this
 * key generalizes to currency unchanged -- but a separate set, matching how each transfer flow
 * owns its own dedup state ({@link BankingWithdrawalProxyService} likewise). A cross-flow race
 * (currency deposit in flight for a slot, the slot's contents swapped to a non-coin item, an
 * item deposit triggered for the same slot) needs no shared set to be safe: each flow
 * revalidates its own capture against the live slot immediately before its own removal, so at
 * most one of the two can find its expected contents and remove them; the other cancels
 * cleanly -- the exact guarantee the item flow already proves for any mid-flight slot mutation.
 */
public final class BankingCurrencyDepositProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BankingCurrencyDepositClientPort client = new BankingCurrencyDepositClient();

    private static final Set<SlotKey> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingCurrencyDepositProxyService() {
    }

    public static void useClientForTesting(BankingCurrencyDepositClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingCurrencyDepositClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID playerId, int slotIndex) {
        return IN_FLIGHT.contains(new SlotKey(playerId, slotIndex));
    }

    /**
     * The full currency deposit sequence, steps 1-8 -- the one and only production entry point,
     * reached from a real {@code BankDepositRequestC2SPayload} via {@link
     * BankingTransferPacketService}'s currency routing. Must be called from the main server
     * thread, matching every other live-inventory-touching entry point in this mod.
     */
    public static CompletableFuture<BankingCurrencyDepositResult> triggerCurrencyDeposit(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        SlotKey key = new SlotKey(player.getUUID(), slotIndex);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new BankingCurrencyDepositResult.LocalFailure("currency_deposit_already_in_flight"));
        }
        return prepareAndRemoveInternal(player, teller, slotIndex)
                .thenCompose(outcome -> continueToConfirm(player, outcome))
                .whenComplete((result, error) -> IN_FLIGHT.remove(key));
    }

    /** Test-support alias for {@link #triggerCurrencyDeposit}, mirroring the item deposit's own. */
    public static CompletableFuture<BankingCurrencyDepositResult> triggerCurrencyDepositForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        return triggerCurrencyDeposit(player, teller, slotIndex);
    }

    /**
     * Runs protocol steps 1-5 only (prepare, revalidate, remove, write receipt) and
     * deliberately stops -- never calls confirm. The "simulate a crash between removal and
     * confirmation" test hook, with exactly the item deposit's semantics: a successful {@link
     * PrepareAndRemoveOutcome.Removed} leaves this slot's {@link #IN_FLIGHT} entry in place
     * (a real crash would not clean it up either); any other outcome clears its own entry.
     * Call {@link #resetInFlightTrackingForTesting()} afterward to simulate the restart.
     */
    public static CompletableFuture<PrepareAndRemoveOutcome> prepareAndRemoveForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        SlotKey key = new SlotKey(player.getUUID(), slotIndex);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.LocalFailure("currency_deposit_already_in_flight"));
        }
        return prepareAndRemoveInternal(player, teller, slotIndex).whenComplete((outcome, error) -> {
            if (error != null || !(outcome instanceof PrepareAndRemoveOutcome.Removed)) {
                IN_FLIGHT.remove(key);
            }
        });
    }

    /**
     * Runs protocol steps 6-8 given an operation that has already been prepared and had its
     * coins removed -- the counterpart to {@link #prepareAndRemoveForTesting}, for resuming
     * confirm after a simulated crash. Does not touch {@link #IN_FLIGHT} itself.
     */
    public static CompletableFuture<BankingCurrencyDepositResult> confirmCurrencyDepositForTesting(
            ServerPlayer player, UUID operationPublicId
    ) {
        return confirmCurrencyDeposit(player.server, player.getUUID(), operationPublicId);
    }

    /**
     * The startup-reconciliation entry point for a currency deposit receipt still {@code
     * PENDING_LOCAL_ACTION} -- resuming steps 6-8 with no live {@code ServerPlayer} on hand,
     * exactly like {@link BankingDepositProxyService#resumeConfirmDeposit} does for item
     * deposits. Reached from {@link BankTransferReconciliationService}'s currency-aware
     * {@code DEPOSIT} branch (a receipt whose {@code currencyAmount} is present is a currency
     * deposit -- the receipt schema's own item-xor-currency discriminator, no new field
     * needed). Like the item deposit's resume, no {@code IN_FLIGHT} registration is needed:
     * by the time a deposit reaches confirm the coins are already gone from every slot, so a
     * fresh live trigger on the same slot index operates on different contents and is a
     * genuinely independent operation, not a duplicate of the one being resumed.
     */
    public static CompletableFuture<BankingCurrencyDepositResult> resumeConfirmCurrencyDeposit(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId
    ) {
        return confirmCurrencyDeposit(server, playerUuid, operationPublicId);
    }

    private static CompletableFuture<BankingCurrencyDepositResult> continueToConfirm(
            ServerPlayer player, PrepareAndRemoveOutcome outcome
    ) {
        return switch (outcome) {
            case PrepareAndRemoveOutcome.Removed removed ->
                    confirmCurrencyDeposit(player.server, player.getUUID(), removed.operationPublicId());
            case PrepareAndRemoveOutcome.RemovalFailed removalFailed ->
                    CompletableFuture.completedFuture(new BankingCurrencyDepositResult.RemovalFailed(removalFailed.operationPublicId()));
            case PrepareAndRemoveOutcome.RejectedLocally rejectedLocally ->
                    CompletableFuture.completedFuture(new BankingCurrencyDepositResult.RejectedLocally(rejectedLocally.reason()));
            case PrepareAndRemoveOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                    new BankingCurrencyDepositResult.Rejected(BankingDepositStage.PREPARE, rejected.outcome(), rejected.retryable())
            );
            case PrepareAndRemoveOutcome.TransportFailure failure -> CompletableFuture.completedFuture(
                    new BankingCurrencyDepositResult.TransportFailure(failure.stage(), failure.safeCode())
            );
            case PrepareAndRemoveOutcome.LocalFailure failure ->
                    CompletableFuture.completedFuture(new BankingCurrencyDepositResult.LocalFailure(failure.safeCode()));
        };
    }

    // ---- Steps 1-5: capture, prepare, revalidate, remove + write receipt ----

    private static CompletableFuture<PrepareAndRemoveOutcome> prepareAndRemoveInternal(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PrepareAndRemoveOutcome.LocalFailure("teller_not_resolved"));
        }

        ItemStack live = player.getInventory().getItem(slotIndex);
        if (live.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.RejectedLocally(BankingCurrencyDepositLocalRejectionReason.EMPTY_SLOT));
        }
        Optional<String> currencyKey = CurrencyItemRegistry.currencyKeyOf(live);
        if (currencyKey.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.RejectedLocally(BankingCurrencyDepositLocalRejectionReason.NOT_CURRENCY));
        }
        CoinCapture capture = new CoinCapture(live.getItem(), currencyKey.get(), live.getCount());

        MinecraftServer server = player.server;
        BankingCurrencyDepositPrepareRequest prepareRequest = new BankingCurrencyDepositPrepareRequest(
                player.getUUID(), resolved.worldNpcPublicId(), UUID.randomUUID().toString(),
                capture.currencyKey(), capture.count()
        );

        final CompletableFuture<BankingCurrencyDepositPrepareResult> prepareFuture;
        try {
            prepareFuture = client.prepareCurrencyDeposit(server, prepareRequest);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/currency/deposit/prepare submission threw synchronously", synchronousFailure);
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, "synchronous_submission_failure")
            );
        }

        CompletableFuture<PrepareAndRemoveOutcome> outcome = new CompletableFuture<>();
        prepareFuture.whenComplete((prepareResult, prepareError) -> server.execute(() -> {
            if (prepareError != null || prepareResult == null) {
                outcome.complete(new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, "unexpected_client_error"));
                return;
            }
            switch (prepareResult) {
                case BankingCurrencyDepositPrepareResult.Rejected rejected ->
                        outcome.complete(new PrepareAndRemoveOutcome.Rejected(rejected.outcome(), rejected.retryable()));
                case BankingCurrencyDepositPrepareResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, failure.safeCode()));
                case BankingCurrencyDepositPrepareResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndRemoveOutcome.LocalFailure(failure.safeCode()));
                case BankingCurrencyDepositPrepareResult.Success success ->
                        revalidateAndRemove(server, player, teller, slotIndex, capture, success, outcome);
            }
        }));
        return outcome;
    }

    /**
     * Step 4 (revalidate exact item + count) and step 5 (remove, then write the receipt),
     * always on the main server thread (marshaled there by the caller). See the class docs for
     * why the removal-then-receipt ordering is inherited from the item deposit unchanged, and
     * why revalidation is item+count identity rather than a fingerprint.
     *
     * <p>Milestone 14 priority 2 (context enforcement, dimension 5): also re-resolves the
     * teller via {@link BankingProxyService#resolve}, folded into the same "revalidation
     * failed, cancel the already-prepared Rails operation" path the item+count mismatch below
     * already uses -- mirrors {@link BankingDepositProxyService#revalidateAndRemove}'s own
     * identical fix exactly.
     */
    private static void revalidateAndRemove(
            MinecraftServer server, ServerPlayer player, ServiceNpcEntity teller, int slotIndex, CoinCapture capture,
            BankingCurrencyDepositPrepareResult.Success success, CompletableFuture<PrepareAndRemoveOutcome> outcome
    ) {
        boolean tellerStillValid = BankingProxyService.resolve(player, teller) != null;
        ItemStack live = player.getInventory().getItem(slotIndex);
        boolean matches = tellerStillValid && !live.isEmpty()
                && live.getItem() == capture.coinItem()
                && live.getCount() == capture.count();

        if (!matches) {
            String cancelReason = tellerStillValid ? "removal_revalidation_failed" : "teller_no_longer_valid";
            if (!tellerStillValid) {
                LOGGER.info("Discarding banking/currency_deposit result: teller is no longer live/in range for {}", player.getUUID());
            }
            final CompletableFuture<BankingCancelResult> cancelFuture;
            try {
                cancelFuture = client.cancel(
                        server, BankingOperationRequest.cancel(player.getUUID(), success.operationPublicId(), cancelReason)
                );
            } catch (RuntimeException synchronousFailure) {
                LOGGER.warn("banking/cancel submission threw synchronously after a currency removal-revalidation mismatch", synchronousFailure);
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
                return;
            }
            cancelFuture.whenComplete((cancelResult, cancelError) -> {
                if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                    LOGGER.warn(
                            "banking/cancel after a currency removal-revalidation mismatch did not cleanly confirm: operation={} result={} error={}",
                            success.operationPublicId(), cancelResult, cancelError
                    );
                }
                // Reported regardless of whether Rails' own cleanup succeeded -- nothing
                // physical happened here, and Cancel is Rails' own idempotent, retryable
                // operation, exactly as the item deposit's identical branch reasons.
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
            });
            return;
        }

        live.shrink(capture.count());
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        // Forces this player's own removal durably to disk before the receipt is written --
        // see BankTransferPlayerDurability's own docs for why this ordering (not the reverse)
        // is what keeps a crash in the remaining gap a non-duplicating loss, never a
        // duplication. Identical placement to the item deposit's own equivalent call.
        if (!BankTransferPlayerDurability.forceSave(player)) {
            // A detected failure here means the removal is still only in-memory -- nothing
            // durable or Rails-facing exists yet, so this is the last point where undoing it is
            // free. Restore the coins and cancel the prepared operation exactly like the
            // removal-revalidation-mismatch branch above. See BankTransferPlayerDurability for
            // the full policy reasoning.
            live.grow(capture.count());
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();

            final CompletableFuture<BankingCancelResult> cancelFuture;
            try {
                cancelFuture = client.cancel(
                        server, BankingOperationRequest.cancel(player.getUUID(), success.operationPublicId(), "player_save_failed")
                );
            } catch (RuntimeException synchronousFailure) {
                LOGGER.warn("banking/cancel submission threw synchronously after a currency forced-save failure", synchronousFailure);
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
                return;
            }
            cancelFuture.whenComplete((cancelResult, cancelError) -> {
                if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                    LOGGER.warn(
                            "banking/cancel after a currency forced-save failure did not cleanly confirm: operation={} result={} error={}",
                            success.operationPublicId(), cancelResult, cancelError
                    );
                }
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
            });
            return;
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, success.operationPublicId(), player.getUUID(), BankTransferOperationType.DEPOSIT,
                null, (long) capture.count(), null, System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking currency deposit receipt could not be recorded for operation {}: receipt store is read-only (unsupported future schema)",
                    success.operationPublicId()
            );
        }

        outcome.complete(new PrepareAndRemoveOutcome.Removed(success.operationPublicId(), capture.currencyKey(), capture.count()));
    }

    // ---- Steps 6-8: confirm, then resolve or (deliberately) leave unresolved ----

    private static CompletableFuture<BankingCurrencyDepositResult> confirmCurrencyDeposit(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId
    ) {
        CompletableFuture<BankingCurrencyDepositResult> result = new CompletableFuture<>();

        final CompletableFuture<BankingConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(playerUuid, operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously for currency operation {}", operationPublicId, synchronousFailure);
            // The coins are already removed and the receipt already written -- left
            // unresolved by design, exactly like any other confirm-stage transport failure.
            return CompletableFuture.completedFuture(
                    new BankingCurrencyDepositResult.TransportFailure(BankingDepositStage.CONFIRM, "synchronous_submission_failure")
            );
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                // Step 8: the receipt remains unresolved by design -- scanUnresolved() on a
                // later startup is what catches this, not any local auto-recovery here.
                result.complete(new BankingCurrencyDepositResult.TransportFailure(BankingDepositStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            ServerLevel level = server.overworld();
            switch (confirmResult) {
                case BankingConfirmResult.Confirmed ignored -> {
                    BankTransferReceipts.resolve(level, operationPublicId);
                    result.complete(new BankingCurrencyDepositResult.Confirmed(operationPublicId));
                }
                case BankingConfirmResult.ReconciliationRequired ignored -> {
                    // Identical semantics to the item deposit's step 7 -- a real, persisted
                    // escalation, never resolution, so a future scanUnresolved() can tell this
                    // apart from an ordinary still-in-flight retry candidate.
                    BankTransferReceiptStore.EscalateOutcome escalateOutcome =
                            BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
                    if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
                        LOGGER.error(
                                "banking currency deposit receipt for operation {} could not be escalated to "
                                        + "reconciliation_required: receipt store is read-only (unsupported future schema)",
                                operationPublicId
                        );
                    }
                    result.complete(new BankingCurrencyDepositResult.ReconciliationRequired(operationPublicId));
                }
                case BankingConfirmResult.Rejected rejected -> result.complete(
                        new BankingCurrencyDepositResult.Rejected(BankingDepositStage.CONFIRM, rejected.outcome(), rejected.retryable())
                );
                case BankingConfirmResult.TransportFailure failure -> result.complete(
                        new BankingCurrencyDepositResult.TransportFailure(BankingDepositStage.CONFIRM, failure.safeCode())
                );
                case BankingConfirmResult.LocalFailure failure -> result.complete(
                        new BankingCurrencyDepositResult.TransportFailure(BankingDepositStage.CONFIRM, failure.safeCode())
                );
            }
        }));
        return result;
    }

    private record SlotKey(UUID playerId, int slotIndex) {
    }

    /** What step 1 captured: the coin {@link Item} itself (for exact revalidation), its wire key, and the exact live count. */
    private record CoinCapture(Item coinItem, String currencyKey, int count) {
    }

    /**
     * Result of protocol steps 1-5 alone (see {@link #prepareAndRemoveForTesting}) -- a sealed
     * hierarchy distinct from {@link BankingCurrencyDepositResult} because {@link Removed} has
     * no equivalent there, exactly mirroring the item deposit's own split.
     */
    public sealed interface PrepareAndRemoveOutcome permits
            PrepareAndRemoveOutcome.Removed,
            PrepareAndRemoveOutcome.RemovalFailed,
            PrepareAndRemoveOutcome.RejectedLocally,
            PrepareAndRemoveOutcome.Rejected,
            PrepareAndRemoveOutcome.TransportFailure,
            PrepareAndRemoveOutcome.LocalFailure {

        /** The coins were removed and the durable receipt was written. Confirm was NOT attempted. */
        record Removed(UUID operationPublicId, String currencyKey, int amount) implements PrepareAndRemoveOutcome {
        }

        record RemovalFailed(UUID operationPublicId) implements PrepareAndRemoveOutcome {
        }

        record RejectedLocally(BankingCurrencyDepositLocalRejectionReason reason) implements PrepareAndRemoveOutcome {
        }

        record Rejected(BankingTransferOutcome outcome, boolean retryable) implements PrepareAndRemoveOutcome {
        }

        record TransportFailure(BankingDepositStage stage, String safeCode) implements PrepareAndRemoveOutcome {
        }

        record LocalFailure(String safeCode) implements PrepareAndRemoveOutcome {
        }
    }
}

package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.currency.CoinSweep;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 6b: Deposit All Coins, the NeoForge half.
 *
 * <p>A structural sibling of {@link BankingCurrencyDepositProxyService} -- same resolve-fresh /
 * dedupe-in-flight / dispatch-off-thread / marshal-back-through-{@code server.execute} skeleton --
 * differing in exactly one way that matters: it sweeps the whole inventory instead of reading one
 * slot, so every step that says "the slot" there says "every counted stack" here.
 *
 * <h2>Protocol (docs/banking_bulk_currency_deposit.md)</h2>
 * <ol>
 *   <li>Sweep the live main inventory and hotbar with {@link CoinSweep}. Nothing is removed.</li>
 *   <li>An empty sweep is refused locally -- no round trip for a player pressing the button with
 *       an empty purse. Rails' own {@code NO_COINS} still exists as the authoritative guard
 *       against a client that skips this.</li>
 *   <li>Call {@code currency/deposit/all/prepare} with the three totals. Rails creates one
 *       operation. <b>Nothing is credited or reserved.</b></li>
 *   <li>Revalidate every counted stack against the live inventory, then remove them all. This is
 *       the point of physical no-return. Write the durable receipt immediately after.</li>
 *   <li>Call the shared {@code confirm}. Rails credits all three denominations in one update.</li>
 * </ol>
 *
 * <h2>Why disposal is all-or-nothing</h2>
 * The sweep is prepared as a single amount triple, so Rails will credit all of it or none of it.
 * Removing only some of the counted stacks would credit the player for coins they still hold.
 *
 * <p>So removal is checked before it starts: every counted slot must still hold the same coin
 * item with at least the counted quantity. If any one has changed, nothing is removed at all, the
 * prepared operation is cancelled, and the player keeps everything. That check-then-act ordering
 * is what makes a partial sweep unrepresentable rather than merely unlikely -- and it costs
 * nothing, because both halves run inside one main-thread tick with no {@code await} between
 * them, exactly like {@link BankingWithdrawalProxyService}'s own pre-check reasons about
 * {@code Inventory#add}.
 *
 * <p>"At least the counted quantity" rather than "exactly" is deliberate, and it is the one place
 * this diverges from the single-slot deposit's exact-count revalidation. A sweep counts the whole
 * inventory, so a player who <i>gains</i> coins during the round trip has not invalidated
 * anything -- the extra coins are simply not part of this operation, and shrinking by the counted
 * amount leaves them alone. Requiring an exact match would fail the sweep for a player who picked
 * up a copper coin while the request was in flight, which is a worse outcome for no safety gain.
 *
 * <h2>IN_FLIGHT is keyed by player alone</h2>
 * Unlike every other deposit flow, there is no slot to key on: the operation's subject is the
 * whole inventory. One sweep per player at a time, which is also what prevents two sweeps from
 * each counting the same coins and both preparing before either removes.
 */
public final class BankingDepositAllCoinsProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BankingDepositAllCoinsClientPort client = new BankingDepositAllCoinsClient();

    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingDepositAllCoinsProxyService() {
    }

    public static void useClientForTesting(BankingDepositAllCoinsClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingDepositAllCoinsClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID playerId) {
        return IN_FLIGHT.contains(playerId);
    }

    /**
     * The full sequence -- the one and only production entry point, reached from a real
     * {@code BankDepositAllCoinsRequestC2SPayload} via {@link BankingTransferPacketService}. Must
     * be called from the main server thread, like every other live-inventory-touching entry point
     * in this mod.
     */
    public static CompletableFuture<BankingDepositAllCoinsResult> triggerDepositAllCoins(
            ServerPlayer player, ServiceNpcEntity teller
    ) {
        UUID playerId = player.getUUID();
        if (!IN_FLIGHT.add(playerId)) {
            return CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.LocalFailure("deposit_all_coins_already_in_flight"));
        }
        return prepareAndRemoveInternal(player, teller)
                .thenCompose(outcome -> continueToConfirm(player, outcome))
                .whenComplete((result, error) -> IN_FLIGHT.remove(playerId));
    }

    /** Test-support alias, mirroring the other deposit services' own. */
    public static CompletableFuture<BankingDepositAllCoinsResult> triggerDepositAllCoinsForTesting(
            ServerPlayer player, ServiceNpcEntity teller
    ) {
        return triggerDepositAllCoins(player, teller);
    }

    /**
     * Runs the sweep, prepare, revalidate and removal only, never confirm -- the "simulate a crash
     * between removal and confirmation" hook, with the same semantics as the other deposit
     * services': a successful {@link PrepareAndRemoveOutcome.Removed} leaves this player's
     * {@link #IN_FLIGHT} entry in place, because a real crash would not clean it up either.
     */
    public static CompletableFuture<PrepareAndRemoveOutcome> prepareAndRemoveForTesting(
            ServerPlayer player, ServiceNpcEntity teller
    ) {
        UUID playerId = player.getUUID();
        if (!IN_FLIGHT.add(playerId)) {
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.LocalFailure("deposit_all_coins_already_in_flight"));
        }
        return prepareAndRemoveInternal(player, teller).whenComplete((outcome, error) -> {
            if (error != null || !(outcome instanceof PrepareAndRemoveOutcome.Removed)) {
                IN_FLIGHT.remove(playerId);
            }
        });
    }

    public static CompletableFuture<BankingDepositAllCoinsResult> confirmDepositAllCoinsForTesting(
            ServerPlayer player, UUID operationPublicId
    ) {
        return confirmDepositAllCoins(player.server, player.getUUID(), operationPublicId);
    }

    /**
     * The startup-reconciliation entry point for a bulk deposit receipt still
     * {@code PENDING_LOCAL_ACTION}. Reached from {@link BankTransferReconciliationService}'s
     * {@code BULK_CURRENCY_DEPOSIT} branch.
     *
     * <p>Registers nothing in {@link #IN_FLIGHT}, for the same reason the other deposit resumes
     * do not: by the time a sweep reaches confirm its coins are already gone, so a fresh live
     * sweep would be counting different coins and is a genuinely independent operation, not a
     * duplicate of the one being resumed.
     */
    public static CompletableFuture<BankingDepositAllCoinsResult> resumeConfirmDepositAllCoins(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId
    ) {
        return confirmDepositAllCoins(server, playerUuid, operationPublicId);
    }

    private static CompletableFuture<BankingDepositAllCoinsResult> continueToConfirm(
            ServerPlayer player, PrepareAndRemoveOutcome outcome
    ) {
        return switch (outcome) {
            case PrepareAndRemoveOutcome.Removed removed ->
                    confirmDepositAllCoins(player.server, player.getUUID(), removed.operationPublicId());
            case PrepareAndRemoveOutcome.RemovalFailed removalFailed -> CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.RemovalFailed(removalFailed.operationPublicId()));
            case PrepareAndRemoveOutcome.RejectedLocally rejectedLocally -> CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.RejectedLocally(rejectedLocally.reason()));
            case PrepareAndRemoveOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.Rejected(BankingDepositStage.PREPARE, rejected.outcome(), rejected.retryable()));
            case PrepareAndRemoveOutcome.TransportFailure failure -> CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.TransportFailure(failure.stage(), failure.safeCode()));
            case PrepareAndRemoveOutcome.LocalFailure failure -> CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.LocalFailure(failure.safeCode()));
        };
    }

    // ---- Steps 1-4: sweep, prepare, revalidate, remove + write receipt ----

    private static CompletableFuture<PrepareAndRemoveOutcome> prepareAndRemoveInternal(
            ServerPlayer player, ServiceNpcEntity teller
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PrepareAndRemoveOutcome.LocalFailure("teller_not_resolved"));
        }

        CoinSweep sweep = CoinSweep.of(player.getInventory());
        if (sweep.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.RejectedLocally(BankingDepositAllCoinsLocalRejectionReason.NO_COINS));
        }

        MinecraftServer server = player.server;
        BankingDepositAllCoinsPrepareRequest prepareRequest = new BankingDepositAllCoinsPrepareRequest(
                player.getUUID(), resolved.worldNpcPublicId(), UUID.randomUUID().toString(),
                sweep.gold(), sweep.silver(), sweep.copper()
        );

        final CompletableFuture<BankingDepositAllCoinsPrepareResult> prepareFuture;
        try {
            prepareFuture = client.prepareDepositAllCoins(server, prepareRequest);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/currency/deposit/all/prepare submission threw synchronously", synchronousFailure);
            return CompletableFuture.completedFuture(
                    new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, "synchronous_submission_failure"));
        }

        CompletableFuture<PrepareAndRemoveOutcome> outcome = new CompletableFuture<>();
        prepareFuture.whenComplete((prepareResult, prepareError) -> server.execute(() -> {
            if (prepareError != null || prepareResult == null) {
                outcome.complete(new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, "unexpected_client_error"));
                return;
            }
            switch (prepareResult) {
                case BankingDepositAllCoinsPrepareResult.Rejected rejected ->
                        outcome.complete(new PrepareAndRemoveOutcome.Rejected(rejected.outcome(), rejected.retryable()));
                case BankingDepositAllCoinsPrepareResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, failure.safeCode()));
                case BankingDepositAllCoinsPrepareResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndRemoveOutcome.LocalFailure(failure.safeCode()));
                case BankingDepositAllCoinsPrepareResult.Success success ->
                        revalidateAndRemove(server, player, teller, sweep, success, outcome);
            }
        }));
        return outcome;
    }

    /**
     * Step 4, on the main server thread: re-resolve the teller, verify every counted stack is
     * still there, then remove them all and write the receipt.
     *
     * <p>The verification pass completes before the removal pass begins -- see the class docs on
     * why an all-or-nothing disposal is a structural property here rather than a hope.
     */
    private static void revalidateAndRemove(
            MinecraftServer server, ServerPlayer player, ServiceNpcEntity teller, CoinSweep sweep,
            BankingDepositAllCoinsPrepareResult.Success success, CompletableFuture<PrepareAndRemoveOutcome> outcome
    ) {
        boolean tellerStillValid = BankingProxyService.resolve(player, teller) != null;
        if (!tellerStillValid) {
            LOGGER.info("Discarding banking/deposit_all_coins result: teller is no longer live/in range for {}", player.getUUID());
            cancelAfterPrepare(server, player, success.operationPublicId(), "teller_no_longer_valid", outcome);
            return;
        }

        Inventory inventory = player.getInventory();
        if (!everyCountedStackIsStillPresent(inventory, sweep)) {
            cancelAfterPrepare(server, player, success.operationPublicId(), "removal_revalidation_failed", outcome);
            return;
        }

        for (CoinSweep.SweptStack swept : sweep.stacks()) {
            inventory.getItem(swept.slotIndex()).shrink(swept.count());
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        // Forces this player's own removal durably to disk before the receipt is written -- see
        // BankTransferPlayerDurability's docs for why this ordering keeps a crash in the
        // remaining gap a non-duplicating loss. Same placement as every other deposit flow's.
        if (!BankTransferPlayerDurability.forceSave(player)) {
            // Still only in-memory, nothing durable or Rails-facing exists yet, so undoing is
            // free. Put every stack back exactly as counted and cancel.
            for (CoinSweep.SweptStack swept : sweep.stacks()) {
                restore(inventory, swept);
            }
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();
            cancelAfterPrepare(server, player, success.operationPublicId(), "player_save_failed", outcome);
            return;
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, success.operationPublicId(), player.getUUID(), BankTransferOperationType.BULK_CURRENCY_DEPOSIT,
                null, sweep.totalCopperValue(), null, System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking deposit-all-coins receipt could not be recorded for operation {}: receipt store is read-only (unsupported future schema)",
                    success.operationPublicId()
            );
        }

        outcome.complete(new PrepareAndRemoveOutcome.Removed(success.operationPublicId()));
    }

    /**
     * Every counted slot must still hold the same coin item with at least the counted quantity.
     * See the class docs for why "at least" rather than "exactly".
     */
    private static boolean everyCountedStackIsStillPresent(Inventory inventory, CoinSweep sweep) {
        for (CoinSweep.SweptStack swept : sweep.stacks()) {
            if (swept.slotIndex() >= inventory.getContainerSize()) return false;
            ItemStack live = inventory.getItem(swept.slotIndex());
            if (live.isEmpty()) return false;
            if (!CurrencyItemRegistry.currencyKeyOf(live).map(swept.currencyKey()::equals).orElse(false)) return false;
            if (live.getCount() < swept.count()) return false;
        }
        return true;
    }

    /**
     * Puts one counted stack back after a failed durability flush. The slot is whatever it is now
     * -- growing it if the same coins are still there, or rebuilding the stack if the shrink
     * emptied it -- because {@code ItemStack#shrink} to zero leaves an empty stack behind rather
     * than one that can be grown.
     */
    private static void restore(Inventory inventory, CoinSweep.SweptStack swept) {
        ItemStack live = inventory.getItem(swept.slotIndex());
        if (live.isEmpty()) {
            CurrencyItemRegistry.itemForKey(swept.currencyKey())
                    .ifPresent(item -> inventory.setItem(swept.slotIndex(), new ItemStack(item, swept.count())));
            return;
        }
        live.grow(swept.count());
    }

    private static void cancelAfterPrepare(
            MinecraftServer server, ServerPlayer player, UUID operationPublicId, String reason,
            CompletableFuture<PrepareAndRemoveOutcome> outcome
    ) {
        final CompletableFuture<BankingCancelResult> cancelFuture;
        try {
            cancelFuture = client.cancel(server, BankingOperationRequest.cancel(player.getUUID(), operationPublicId, reason));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/cancel submission threw synchronously after a deposit-all-coins {}", reason, synchronousFailure);
            outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(operationPublicId));
            return;
        }
        cancelFuture.whenComplete((cancelResult, cancelError) -> {
            if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                LOGGER.warn(
                        "banking/cancel after a deposit-all-coins {} did not cleanly confirm: operation={} result={} error={}",
                        reason, operationPublicId, cancelResult, cancelError
                );
            }
            // Reported regardless of whether Rails' own cleanup succeeded: nothing physical
            // happened here, and Cancel is Rails' own idempotent, retryable operation -- the same
            // reasoning every other flow's identical branch uses.
            outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(operationPublicId));
        });
    }

    // ---- Step 5: confirm, then resolve or (deliberately) leave unresolved ----

    private static CompletableFuture<BankingDepositAllCoinsResult> confirmDepositAllCoins(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId
    ) {
        CompletableFuture<BankingDepositAllCoinsResult> result = new CompletableFuture<>();

        final CompletableFuture<BankingConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(playerUuid, operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously for bulk deposit {}", operationPublicId, synchronousFailure);
            return CompletableFuture.completedFuture(
                    new BankingDepositAllCoinsResult.TransportFailure(BankingDepositStage.CONFIRM, "synchronous_submission_failure"));
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                // The receipt remains unresolved by design -- scanUnresolved() on a later startup
                // is what catches this, not any local auto-recovery here.
                result.complete(new BankingDepositAllCoinsResult.TransportFailure(BankingDepositStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            ServerLevel level = server.overworld();
            switch (confirmResult) {
                case BankingConfirmResult.Confirmed ignored -> {
                    BankTransferReceipts.resolve(level, operationPublicId);
                    result.complete(new BankingDepositAllCoinsResult.Confirmed(operationPublicId));
                }
                case BankingConfirmResult.ReconciliationRequired ignored -> {
                    BankTransferReceiptStore.EscalateOutcome escalateOutcome =
                            BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
                    if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
                        LOGGER.error(
                                "banking deposit-all-coins receipt for operation {} could not be escalated to "
                                        + "reconciliation_required: receipt store is read-only (unsupported future schema)",
                                operationPublicId
                        );
                    }
                    result.complete(new BankingDepositAllCoinsResult.ReconciliationRequired(operationPublicId));
                }
                case BankingConfirmResult.Rejected rejected -> result.complete(
                        new BankingDepositAllCoinsResult.Rejected(BankingDepositStage.CONFIRM, rejected.outcome(), rejected.retryable()));
                case BankingConfirmResult.TransportFailure failure -> result.complete(
                        new BankingDepositAllCoinsResult.TransportFailure(BankingDepositStage.CONFIRM, failure.safeCode()));
                case BankingConfirmResult.LocalFailure failure -> result.complete(
                        new BankingDepositAllCoinsResult.TransportFailure(BankingDepositStage.CONFIRM, failure.safeCode()));
            }
        }));
        return result;
    }

    /** Result of the sweep/prepare/remove half alone -- see {@link #prepareAndRemoveForTesting}. */
    public sealed interface PrepareAndRemoveOutcome permits
            PrepareAndRemoveOutcome.Removed,
            PrepareAndRemoveOutcome.RemovalFailed,
            PrepareAndRemoveOutcome.RejectedLocally,
            PrepareAndRemoveOutcome.Rejected,
            PrepareAndRemoveOutcome.TransportFailure,
            PrepareAndRemoveOutcome.LocalFailure {

        /** Every counted stack was removed and the durable receipt was written. Confirm was NOT attempted. */
        record Removed(UUID operationPublicId) implements PrepareAndRemoveOutcome {
        }

        record RemovalFailed(UUID operationPublicId) implements PrepareAndRemoveOutcome {
        }

        record RejectedLocally(BankingDepositAllCoinsLocalRejectionReason reason) implements PrepareAndRemoveOutcome {
        }

        record Rejected(BankingTransferOutcome outcome, boolean retryable) implements PrepareAndRemoveOutcome {
        }

        record TransportFailure(BankingDepositStage stage, String safeCode) implements PrepareAndRemoveOutcome {
        }

        record LocalFailure(String safeCode) implements PrepareAndRemoveOutcome {
        }
    }
}

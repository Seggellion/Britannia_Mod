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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 10 NeoForge Slice 2: the currency withdrawal path -- local capacity pre-check,
 * Rails reservation, durable receipt-before-insertion sequencing, coin construction/insertion,
 * the durability fix's {@code forceSave}, and confirmation. A structural sibling of {@link
 * BankingWithdrawalProxyService} (the same resolve-fresh / dedupe-in-flight /
 * dispatch-off-thread / marshal-back-via-{@code server.execute} skeleton, and the same
 * receipt-before-insertion ordering -- Section A.6's withdrawal-specific reversal of deposit's
 * own ordering, unchanged here), not an extension of it: currency has no serialized payload to
 * reconstruct, no fingerprint to verify, and no per-unit identity at all -- the amount is known
 * locally before prepare is ever called, which is precisely why this flow's capacity checking
 * differs from the item path's (see below).
 *
 * <h2>Two capacity checks, not one -- and why</h2>
 * Item withdrawal's {@link BankingWithdrawalProxyService#hasSufficientCapacity} runs exactly
 * once, immediately before the real {@code Inventory#add} call, with no yield point between the
 * two -- which is what makes insertion failure structurally unreachable there (traced directly:
 * both run synchronously on the main server thread, back to back). Currency withdrawal cannot
 * get that same guarantee from a single check in the same place, because the amount (unlike an
 * item's reconstructed stack) is known <em>before</em> prepare is ever called, and this program's
 * own instruction is explicit: check capacity before any Rails call or reservation, since there
 * is no reason to spend a network round trip -- or let Rails reserve real funds -- for a
 * withdrawal that could never be delivered. That first check has a real yield point after it
 * (the async Rails round trip), during which the player's own inventory can genuinely change
 * (pick up items, open a container, anything). So this flow runs the check twice:
 * <ol>
 *   <li>Before prepare, in {@link #prepareAndInsertInternal} -- a pure efficiency/UX rejection,
 *       zero Rails calls, zero reservation, reported as {@link
 *       BankingCurrencyWithdrawalResult.RejectedLocally}.</li>
 *   <li>Immediately before the real insertion call, in {@link #reconstructAndInsert} -- back to
 *       back with {@code Inventory#add}, no yield point in between, exactly mirroring the item
 *       path's own timing guarantee. This is the check that actually makes insertion failure
 *       structurally unreachable; if it fails (a real, reachable race the first check cannot
 *       prevent), the withdrawal aborts via Cancel, exactly like the item path's own
 *       post-prepare {@code INSUFFICIENT_CAPACITY} abort.</li>
 * </ol>
 * Both checks call the exact same {@link BankingWithdrawalProxyService#hasSufficientCapacity}
 * primitive (public, already proven correct against a single {@link ItemStack} whose count
 * exceeds the item's own max stack size -- traced directly from {@code Inventory#add}'s real
 * source, which splits an oversized count across as many slots as needed via its own internal
 * {@code do}/{@code while} loop). No separate "multi-stack" construction exists: a single {@code
 * new ItemStack(coinItem, amount)}, even for an amount far exceeding 99, is inserted and
 * pre-checked exactly like any ordinary stack -- vanilla's own insertion mechanism already
 * handles the splitting.
 *
 * <h2>No world drop, ever</h2>
 * Exactly like item withdrawal, {@code ServerEconomyService#giveCoin}'s {@code player.drop}
 * fallback on a full inventory is deliberately not extended here -- a bank withdrawal must never
 * place coins outside the player's control that this process has already told Rails is
 * confirmed. The two capacity checks above are what make that guarantee real rather than
 * aspirational.
 *
 * <h2>IN_FLIGHT: {@code (playerId, currencyKey)}, not {@code (playerId, currencyKey, idempotencyKey)}</h2>
 * A currency withdrawal has no per-item identity for {@link BankingWithdrawalProxyService}'s own
 * {@code bankItemPublicId}-keyed dedup to generalize to (recon-confirmed). The correct key is a
 * compound {@code (playerId, currencyKey)}, mirroring the <em>shape</em> of {@link
 * BankingCurrencyDepositProxyService}'s own {@code (player, slot)} key rather than copying
 * either flow's exact fields:
 * <ul>
 *   <li><b>Same player, same denomination</b> must serialize -- a rapid double-click or retry
 *       withdrawing gold twice must not fire two independent prepares, exactly the same
 *       accidental-double-trigger concern deposit's own slot key and item withdrawal's own bank
 *       item key both guard against (Rails' own reservation locking would eventually catch a
 *       genuine overdraft, but only after an avoidable round trip and a confusing rejection for
 *       what the player experienced as one action).</li>
 *   <li><b>Same player, different denominations</b> must NOT serialize against each other --
 *       gold, silver, and copper are independent balances (Rails' own {@code
 *       reserved_gold_balance}/{@code reserved_silver_balance}/{@code reserved_copper_balance}
 *       are three separate counters) and independent physical coin stacks; forcing a silver
 *       withdrawal to wait behind an in-flight gold withdrawal would be an artificial,
 *       unjustified stall with no correctness benefit.</li>
 * </ul>
 * No idempotency-key component: {@code IN_FLIGHT} here is a simple "at most one active attempt"
 * mutex, cleared on settlement via {@code whenComplete}, exactly like every other {@code
 * IN_FLIGHT} set in this codebase (deposit's slot key, item withdrawal's bank item id) -- none
 * of which fold an idempotency key into their dedup key, since idempotency-key generation and
 * collision handling is Rails' own job (the {@code idempotency_key} field already sent on every
 * prepare), not this local mutex's.
 *
 * <h2>Resume: no {@code IN_FLIGHT} registration needed, unlike item withdrawal's own resume</h2>
 * {@link #resumeConfirmCurrencyWithdrawal} does not register in {@link #IN_FLIGHT}. Item
 * withdrawal's own resume registers specifically because a live re-trigger for the exact same
 * {@code bankItemPublicId} would target the identical physical resource the resume is also
 * confirming -- a real collision. Currency has no such shared resource: a live re-trigger for
 * the same {@code currencyKey} while a resume is in flight calls prepare for an entirely
 * independent, freshly-reserved amount against the player's own remaining available balance; it
 * is not a duplicate of whatever amount the resume is confirming, and cannot cause a confusing
 * rejection over the same identity, because no such identity exists for currency. Matches {@link
 * BankingCurrencyDepositProxyService#resumeConfirmCurrencyDeposit}'s own no-registration
 * reasoning, not item withdrawal's.
 */
public final class BankingCurrencyWithdrawalProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BankingCurrencyWithdrawalClientPort client = new BankingCurrencyWithdrawalClient();

    private static final Set<CurrencyKey> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingCurrencyWithdrawalProxyService() {
    }

    public static void useClientForTesting(BankingCurrencyWithdrawalClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingCurrencyWithdrawalClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID playerId, String currencyKey) {
        return IN_FLIGHT.contains(new CurrencyKey(playerId, currencyKey));
    }

    /**
     * The full currency withdrawal sequence, steps 1-8 -- the one and only production entry
     * point, reached from a real {@code BankCurrencyWithdrawalRequestC2SPayload} via {@link
     * BankingTransferPacketService}. Must be called from the main server thread, matching every
     * other live-inventory-touching entry point in this mod.
     */
    public static CompletableFuture<BankingCurrencyWithdrawalResult> triggerCurrencyWithdrawal(
            ServerPlayer player, ServiceNpcEntity teller, String currencyKey, int amount
    ) {
        CurrencyKey key = new CurrencyKey(player.getUUID(), currencyKey);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new BankingCurrencyWithdrawalResult.LocalFailure("currency_withdrawal_already_in_flight"));
        }
        return prepareAndInsertInternal(player, teller, currencyKey, amount)
                .thenCompose(outcome -> continueToConfirm(player, outcome))
                .whenComplete((result, error) -> IN_FLIGHT.remove(key));
    }

    /** Test-support alias for {@link #triggerCurrencyWithdrawal}, mirroring the other flows' own. */
    public static CompletableFuture<BankingCurrencyWithdrawalResult> triggerCurrencyWithdrawalForTesting(
            ServerPlayer player, ServiceNpcEntity teller, String currencyKey, int amount
    ) {
        return triggerCurrencyWithdrawal(player, teller, currencyKey, amount);
    }

    /**
     * Runs protocol steps 1-5 only (local pre-check, prepare, second pre-check, write receipt,
     * insert) and deliberately stops -- never calls confirm. The "simulate a crash between
     * insertion and confirmation" test hook, exactly like {@link
     * BankingWithdrawalProxyService#prepareAndInsertForTesting}: a successful {@link
     * PrepareAndInsertOutcome.Inserted} leaves this key's entry in {@link #IN_FLIGHT} rather
     * than clearing it, matching what a real crash would leave. Call {@link
     * #resetInFlightTrackingForTesting()} afterward to simulate the process restarting.
     */
    public static CompletableFuture<PrepareAndInsertOutcome> prepareAndInsertForTesting(
            ServerPlayer player, ServiceNpcEntity teller, String currencyKey, int amount
    ) {
        CurrencyKey key = new CurrencyKey(player.getUUID(), currencyKey);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new PrepareAndInsertOutcome.LocalFailure("currency_withdrawal_already_in_flight"));
        }
        return prepareAndInsertInternal(player, teller, currencyKey, amount).whenComplete((outcome, error) -> {
            if (error != null || !(outcome instanceof PrepareAndInsertOutcome.Inserted)) {
                IN_FLIGHT.remove(key);
            }
        });
    }

    /**
     * Runs protocol steps 6-8 given an operation that has already been prepared and had its
     * coins inserted -- the counterpart to {@link #prepareAndInsertForTesting}, for resuming
     * confirm after a simulated crash. Does not touch {@link #IN_FLIGHT} itself.
     */
    public static CompletableFuture<BankingCurrencyWithdrawalResult> confirmCurrencyWithdrawalForTesting(
            ServerPlayer player, UUID operationPublicId
    ) {
        return confirmCurrencyWithdrawal(player.server, player.getUUID(), operationPublicId, false);
    }

    /**
     * The startup-reconciliation entry point for a currency withdrawal receipt still {@code
     * PENDING_LOCAL_ACTION} -- resuming steps 6-8 with no live {@code ServerPlayer} on hand.
     * Reached from {@link BankTransferReconciliationService}'s currency-aware {@code WITHDRAWAL}
     * branch. See the class docs for why, unlike {@link
     * BankingWithdrawalProxyService#resumeConfirmWithdrawal}, no {@link #IN_FLIGHT} registration
     * is needed here.
     */
    public static CompletableFuture<BankingCurrencyWithdrawalResult> resumeConfirmCurrencyWithdrawal(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId
    ) {
        // No live ServerPlayer exists on a resume path -- forceSave has no meaning here, so this
        // is never the "already durable per the flag" case; a resumed operation's own durability
        // is whatever it already was when the receipt was written pre-restart.
        return confirmCurrencyWithdrawal(server, playerUuid, operationPublicId, false);
    }

    private static CompletableFuture<BankingCurrencyWithdrawalResult> continueToConfirm(
            ServerPlayer player, PrepareAndInsertOutcome outcome
    ) {
        return switch (outcome) {
            case PrepareAndInsertOutcome.Inserted inserted ->
                    confirmCurrencyWithdrawal(player.server, player.getUUID(), inserted.operationPublicId(), inserted.forceSaveFailed());
            case PrepareAndInsertOutcome.Aborted aborted ->
                    CompletableFuture.completedFuture(new BankingCurrencyWithdrawalResult.Aborted(aborted.operationPublicId(), aborted.reason()));
            case PrepareAndInsertOutcome.RejectedLocally rejectedLocally ->
                    CompletableFuture.completedFuture(new BankingCurrencyWithdrawalResult.RejectedLocally(rejectedLocally.reason()));
            case PrepareAndInsertOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                    new BankingCurrencyWithdrawalResult.Rejected(BankingWithdrawalStage.PREPARE, rejected.outcome(), rejected.retryable())
            );
            case PrepareAndInsertOutcome.TransportFailure failure -> CompletableFuture.completedFuture(
                    new BankingCurrencyWithdrawalResult.TransportFailure(failure.stage(), failure.safeCode())
            );
            case PrepareAndInsertOutcome.LocalFailure failure ->
                    CompletableFuture.completedFuture(new BankingCurrencyWithdrawalResult.LocalFailure(failure.safeCode()));
        };
    }

    // ---- Steps 1-5: local pre-check, prepare, second pre-check, write receipt, insert ----

    private static CompletableFuture<PrepareAndInsertOutcome> prepareAndInsertInternal(
            ServerPlayer player, ServiceNpcEntity teller, String currencyKey, int amount
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PrepareAndInsertOutcome.LocalFailure("teller_not_resolved"));
        }

        Optional<Item> coinItem = CurrencyItemRegistry.itemForKey(currencyKey);
        if (coinItem.isEmpty()) {
            return CompletableFuture.completedFuture(new PrepareAndInsertOutcome.LocalFailure("unsupported_currency_key"));
        }

        // Check 1 of 2 -- see class docs. Before any Rails call or reservation: no round trip,
        // no reserved funds, for a withdrawal that could never be delivered.
        if (!BankingWithdrawalProxyService.hasSufficientCapacity(player.getInventory(), new ItemStack(coinItem.get(), amount))) {
            return CompletableFuture.completedFuture(new PrepareAndInsertOutcome.RejectedLocally(
                    BankingCurrencyWithdrawalLocalRejectionReason.INSUFFICIENT_CAPACITY));
        }

        MinecraftServer server = player.server;
        BankingCurrencyWithdrawalPrepareRequest prepareRequest = new BankingCurrencyWithdrawalPrepareRequest(
                player.getUUID(), resolved.worldNpcPublicId(), UUID.randomUUID().toString(), currencyKey, amount
        );

        final CompletableFuture<BankingCurrencyWithdrawalPrepareResult> prepareFuture;
        try {
            prepareFuture = client.prepareCurrencyWithdrawal(server, prepareRequest);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/currency/withdrawal/prepare submission threw synchronously", synchronousFailure);
            return CompletableFuture.completedFuture(
                    new PrepareAndInsertOutcome.TransportFailure(BankingWithdrawalStage.PREPARE, "synchronous_submission_failure")
            );
        }

        CompletableFuture<PrepareAndInsertOutcome> outcome = new CompletableFuture<>();
        prepareFuture.whenComplete((prepareResult, prepareError) -> server.execute(() -> {
            if (prepareError != null || prepareResult == null) {
                outcome.complete(new PrepareAndInsertOutcome.TransportFailure(BankingWithdrawalStage.PREPARE, "unexpected_client_error"));
                return;
            }
            switch (prepareResult) {
                case BankingCurrencyWithdrawalPrepareResult.Rejected rejected ->
                        outcome.complete(new PrepareAndInsertOutcome.Rejected(rejected.outcome(), rejected.retryable()));
                case BankingCurrencyWithdrawalPrepareResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndInsertOutcome.TransportFailure(BankingWithdrawalStage.PREPARE, failure.safeCode()));
                case BankingCurrencyWithdrawalPrepareResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndInsertOutcome.LocalFailure(failure.safeCode()));
                case BankingCurrencyWithdrawalPrepareResult.Success success ->
                        reconstructAndInsert(server, player, coinItem.get(), currencyKey, amount, success, outcome);
            }
        }));
        return outcome;
    }

    /**
     * Steps 3b-5 (the second capacity check, write receipt, insert), always on the main server
     * thread (marshaled there by the caller). See the class docs for why this second check --
     * not the first -- is what makes insertion failure structurally unreachable, and why the
     * receipt is written before insertion (Section A.6's withdrawal ordering, unchanged).
     */
    private static void reconstructAndInsert(
            MinecraftServer server, ServerPlayer player, Item coinItem, String currencyKey, int amount,
            BankingCurrencyWithdrawalPrepareResult.Success success, CompletableFuture<PrepareAndInsertOutcome> outcome
    ) {
        // Check 2 of 2 -- see class docs. Back to back with the real insertion below, no yield
        // point in between, exactly mirroring item withdrawal's own timing guarantee.
        if (!BankingWithdrawalProxyService.hasSufficientCapacity(player.getInventory(), new ItemStack(coinItem, amount))) {
            abortAfterPrepare(server, player, success.operationPublicId(), BankingCurrencyWithdrawalAbortReason.INSUFFICIENT_CAPACITY, outcome);
            return;
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, success.operationPublicId(), player.getUUID(), BankTransferOperationType.WITHDRAWAL,
                null, (long) amount, null, System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking currency withdrawal receipt could not be recorded for operation {}: receipt store is read-only (unsupported future schema)",
                    success.operationPublicId()
            );
        }

        handleInsertionOutcome(server, player, coinItem, amount, success, outcome);
    }

    /**
     * Step 5's insertion attempt and its outcome handling, split out from {@link
     * #reconstructAndInsert} specifically so it is independently unit-testable with a synthetic
     * non-empty leftover, mirroring {@link BankingWithdrawalProxyService#handleInsertionOutcome}.
     * The stack passed to {@code Inventory#add} is always a fresh copy: only {@link
     * ItemStack#isEmpty()} on that copy after the call is trusted, never {@code add}'s own
     * {@code boolean} return.
     */
    private static void handleInsertionOutcome(
            MinecraftServer server, ServerPlayer player, Item coinItem, int amount,
            BankingCurrencyWithdrawalPrepareResult.Success success, CompletableFuture<PrepareAndInsertOutcome> outcome
    ) {
        ItemStack toInsert = new ItemStack(coinItem, amount);
        player.getInventory().add(toInsert);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        if (toInsert.isEmpty()) {
            // Unlike currency deposit, the receipt here was already written BEFORE insertion
            // (Section A.6's withdrawal ordering) -- what this call closes is the insertion
            // itself being durably reflected in the player's own file before Rails is ever told
            // to confirm. Identical placement and reasoning to item withdrawal's own equivalent
            // call: a detected failure here cannot be cleanly aborted (the receipt already
            // durably claims this insertion happened), so it is carried through to confirm
            // instead. See BankTransferPlayerDurability's own docs for the full policy reasoning.
            boolean saved = BankTransferPlayerDurability.forceSave(player);
            outcome.complete(new PrepareAndInsertOutcome.Inserted(success.operationPublicId(), !saved));
            return;
        }

        // Structurally unreachable in real operation (see class docs) -- a receipt now exists
        // for this operation, and it is resolved (not escalated), since nothing was actually
        // given to the player: both capacity checks passed but insertion did not, treated
        // exactly like the pre-check having failed, not like a partial success.
        LOGGER.error(
                "banking currency withdrawal insertion left a nonzero leftover for operation {} despite two passing capacity checks",
                success.operationPublicId()
        );
        ServerLevel level = player.serverLevel();
        BankTransferReceipts.resolve(level, success.operationPublicId());
        sendCancel(server, player, success.operationPublicId(), "insertion_failed_after_passing_precheck",
                () -> outcome.complete(
                        new PrepareAndInsertOutcome.Aborted(success.operationPublicId(), BankingCurrencyWithdrawalAbortReason.INSERTION_FAILED)));
    }

    /**
     * Step 3b's local-rejection path: Rails already reserved the amount (no receipt exists yet
     * -- the second capacity check runs before the receipt is written), so Cancel is always
     * called to release that reservation.
     */
    private static void abortAfterPrepare(
            MinecraftServer server, ServerPlayer player, UUID operationPublicId, BankingCurrencyWithdrawalAbortReason reason,
            CompletableFuture<PrepareAndInsertOutcome> outcome
    ) {
        sendCancel(server, player, operationPublicId, cancelReasonFor(reason),
                () -> outcome.complete(new PrepareAndInsertOutcome.Aborted(operationPublicId, reason)));
    }

    private static String cancelReasonFor(BankingCurrencyWithdrawalAbortReason reason) {
        return switch (reason) {
            case INSUFFICIENT_CAPACITY -> "insufficient_capacity";
            case INSERTION_FAILED -> "insertion_failed_after_passing_precheck";
        };
    }

    private static void sendCancel(
            MinecraftServer server, ServerPlayer player, UUID operationPublicId, String reason, Runnable onSettled
    ) {
        final CompletableFuture<BankingCancelResult> cancelFuture;
        try {
            cancelFuture = client.cancel(server, BankingOperationRequest.cancel(player.getUUID(), operationPublicId, reason));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/cancel submission threw synchronously for currency withdrawal operation {}", operationPublicId, synchronousFailure);
            onSettled.run();
            return;
        }
        cancelFuture.whenComplete((cancelResult, cancelError) -> {
            if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                LOGGER.warn(
                        "banking/cancel for currency withdrawal operation {} did not cleanly confirm: result={} error={}",
                        operationPublicId, cancelResult, cancelError
                );
            }
            // As with every other flow's identical branch: reported regardless of whether
            // Rails' own cleanup succeeded, since Cancel is Rails' own idempotent, safely
            // retryable-later operation.
            onSettled.run();
        });
    }

    // ---- Steps 6-8: confirm, then resolve or (deliberately) escalate/leave unresolved ----

    private static CompletableFuture<BankingCurrencyWithdrawalResult> confirmCurrencyWithdrawal(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId, boolean forceSaveFailed
    ) {
        CompletableFuture<BankingCurrencyWithdrawalResult> result = new CompletableFuture<>();

        final CompletableFuture<BankingConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(playerUuid, operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously for currency withdrawal operation {}", operationPublicId, synchronousFailure);
            // The coins are already inserted and the receipt already written -- left unresolved
            // by design, exactly like any other confirm-stage transport failure.
            return CompletableFuture.completedFuture(
                    new BankingCurrencyWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, "synchronous_submission_failure")
            );
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                // Step 8: the receipt remains unresolved by design -- scanUnresolved() on a
                // later startup is what catches this, not any local auto-recovery here.
                result.complete(new BankingCurrencyWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            ServerLevel level = server.overworld();
            switch (confirmResult) {
                case BankingConfirmResult.Confirmed ignored -> {
                    if (forceSaveFailed) {
                        // Rails says Confirmed, but this side already has real, detected doubt
                        // about whether the insertion is durable on this player's own file --
                        // abort was not possible at insertion time since the receipt already
                        // committed to this operation. Escalate rather than resolve, exactly
                        // mirroring item withdrawal's own identical handling.
                        BankTransferReceiptStore.EscalateOutcome escalateOutcome =
                                BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
                        if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
                            LOGGER.error(
                                    "banking currency withdrawal receipt for operation {} could not be escalated to "
                                            + "reconciliation_required after a forced-save failure: receipt store is "
                                            + "read-only (unsupported future schema)",
                                    operationPublicId
                            );
                        }
                        result.complete(new BankingCurrencyWithdrawalResult.ReconciliationRequired(operationPublicId));
                    } else {
                        BankTransferReceipts.resolve(level, operationPublicId);
                        result.complete(new BankingCurrencyWithdrawalResult.Confirmed(operationPublicId));
                    }
                }
                case BankingConfirmResult.ReconciliationRequired ignored -> {
                    // The coins are already, physically, in the player's inventory -- must
                    // never be reclaimed. Escalate (not resolve), identical semantics to every
                    // other flow's handling of this same Rails response.
                    BankTransferReceiptStore.EscalateOutcome escalateOutcome =
                            BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
                    if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
                        LOGGER.error(
                                "banking currency withdrawal receipt for operation {} could not be escalated to "
                                        + "reconciliation_required: receipt store is read-only (unsupported future schema)",
                                operationPublicId
                        );
                    }
                    result.complete(new BankingCurrencyWithdrawalResult.ReconciliationRequired(operationPublicId));
                }
                case BankingConfirmResult.Rejected rejected -> result.complete(
                        new BankingCurrencyWithdrawalResult.Rejected(BankingWithdrawalStage.CONFIRM, rejected.outcome(), rejected.retryable())
                );
                case BankingConfirmResult.TransportFailure failure -> result.complete(
                        new BankingCurrencyWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, failure.safeCode())
                );
                case BankingConfirmResult.LocalFailure failure -> result.complete(
                        new BankingCurrencyWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, failure.safeCode())
                );
            }
        }));
        return result;
    }

    private record CurrencyKey(UUID playerId, String currencyKey) {
    }

    /**
     * Result of protocol steps 1-5 alone (see {@link #prepareAndInsertForTesting}) -- a sealed
     * hierarchy distinct from {@link BankingCurrencyWithdrawalResult} because {@link Inserted}
     * has no equivalent there, exactly mirroring item withdrawal's own split.
     */
    public sealed interface PrepareAndInsertOutcome permits
            PrepareAndInsertOutcome.Inserted,
            PrepareAndInsertOutcome.Aborted,
            PrepareAndInsertOutcome.RejectedLocally,
            PrepareAndInsertOutcome.Rejected,
            PrepareAndInsertOutcome.TransportFailure,
            PrepareAndInsertOutcome.LocalFailure {

        /**
         * The coins were inserted and the durable receipt was written. Confirm was NOT
         * attempted.
         *
         * @param forceSaveFailed whether {@link BankTransferPlayerDurability#forceSave} detected
         *                        a failure forcing this insertion to the player's own file. See
         *                        {@link BankingWithdrawalProxyService.PrepareAndInsertOutcome.Inserted}'s
         *                        own doc for why this cannot cleanly abort and is instead
         *                        carried through to confirm.
         */
        record Inserted(UUID operationPublicId, boolean forceSaveFailed) implements PrepareAndInsertOutcome {
        }

        record Aborted(UUID operationPublicId, BankingCurrencyWithdrawalAbortReason reason) implements PrepareAndInsertOutcome {
        }

        record RejectedLocally(BankingCurrencyWithdrawalLocalRejectionReason reason) implements PrepareAndInsertOutcome {
        }

        record Rejected(BankingTransferOutcome outcome, boolean retryable) implements PrepareAndInsertOutcome {
        }

        record TransportFailure(BankingWithdrawalStage stage, String safeCode) implements PrepareAndInsertOutcome {
        }

        record LocalFailure(String safeCode) implements PrepareAndInsertOutcome {
        }
    }
}

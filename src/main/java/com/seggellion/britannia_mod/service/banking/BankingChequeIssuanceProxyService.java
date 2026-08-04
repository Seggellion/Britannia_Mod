package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.economy.CoinConversion;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 11 NeoForge Slice 1: the bank cheque issuance path -- local pre-checks, Rails
 * reservation, durable receipt sequencing, and (unlike every prior transfer flow) construction
 * and insertion of the physical item only AFTER confirm, not before it.
 *
 * <h2>Why confirm precedes insertion here (the reverse of withdrawal's own ordering)</h2>
 * Every prior insertion-shaped flow ({@link BankingWithdrawalProxyService}, {@link
 * BankingCurrencyWithdrawalProxyService}) knows the exact identity of what it is about to
 * insert from its own {@code prepare} response, because the thing being inserted (a {@code
 * BankItem}, or a plain denomination amount) already exists before prepare is ever called. A
 * bank cheque does not: Rails' own Milestone 11 Slice 1 design deliberately creates the {@code
 * BankCheque} row -- and therefore the real UUID a physical cheque item must carry -- only
 * inside {@code Confirm}, never at {@code Create}/prepare time (see {@code
 * docs/banking_bank_cheque_issuance.md} on the Rails side for the full reasoning: nothing
 * physical/durable exists on this side to reserve an instrument identity against until this
 * exact slice runs). There is therefore no real identity available to insert until confirm
 * actually returns one -- inserting a locally-invented placeholder UUID and swapping it in
 * later was considered and rejected as exactly the kind of shape-forcing this milestone's own
 * instructions warn against; this flow instead adapts its own ordering to the real constraint:
 * <pre>
 * prepare -&gt; write receipt -&gt; CONFIRM -&gt; construct the real item -&gt; insert -&gt; forceSave -&gt; resolve
 * </pre>
 *
 * <h2>The receipt needs no new field for this (see {@link
 * com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt}'s own docs)</h2>
 * The receipt is written <em>before</em> confirm, exactly mirroring the "write immediately
 * before the risky/committing step" principle {@link BankingWithdrawalProxyService} already
 * established -- here, CONFIRM is that step (once it succeeds, gold is spent and a cheque
 * exists, permanently). It carries only {@code currencyAmount} (the requested/confirmed copper
 * value) -- no cheque UUID, because none exists yet when it is written, and none needs to be
 * persisted afterward either: {@code confirm} is idempotent, so a resume can always safely
 * re-ask Rails for the exact same already-created cheque rather than caching its identity
 * locally.
 *
 * <h2>"Abort-and-restore" here means restoring the LOCAL side only</h2>
 * Once confirm succeeds, the Rails-side commitment (gold debited, cheque created) can never be
 * undone -- there is no post-confirm Cancel. But the LOCAL, physical delivery attempt genuinely
 * can be undone: if {@link BankTransferPlayerDurability#forceSave} detects a failure right
 * after insertion, this flow removes the just-inserted item back out of the player's inventory
 * (restoring their exact prior physical state) and leaves the receipt {@code
 * PENDING_LOCAL_ACTION} rather than resolving or escalating it. This is safe -- and genuinely
 * different from every prior withdrawal-shaped flow's own forced-save-failure handling (which
 * must escalate, never retry) -- specifically because redeeming a cheque will be gated by
 * Rails' own single-use {@code BankCheque} state, not by how many physical copies exist: a
 * later, automatic re-delivery attempt (re-confirming idempotently, then re-inserting) can
 * never double-spend the value it represents, only, at worst, hand out a harmless duplicate
 * physical reference to it. Neither deposit's full abort (nothing committed yet) nor
 * withdrawal's mandatory escalation (retry would risk real duplication) applies unmodified
 * here -- this is a genuine third case, unique to an operation whose physical instrument is
 * merely a pointer to a centrally value-gated resource rather than the value itself.
 *
 * <h2>No post-prepare, pre-confirm abort path</h2>
 * Unlike every insertion-shaped flow before it, nothing observably risky happens between a
 * successful prepare and confirm (no physical mutation, no partial state) -- so there is no
 * second local check, and no {@code Cancel} call, positioned there. The only local checks are
 * (1) before prepare is ever called (pure efficiency/UX, zero Rails calls) and (2) immediately
 * before the real insertion attempt, after confirm, with no yield point in between -- mirroring
 * exactly why that second-check timing is what makes insertion failure structurally
 * unreachable in the withdrawal flows, applied here to the window that actually exists for
 * this flow (the confirm round trip, not a pre-check-to-insert gap).
 */
public final class BankingChequeIssuanceProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The smallest cheque, counted in coins of whichever denomination funds it -- mirrors Rails'
     * {@code ChequePayloadValidator::MIN_COIN_COUNT}.
     *
     * <p>Milestone 8b, superseding ADR-018/ADR-019's absolute floor: 500 gold, 500 silver or 500
     * copper, rather than a flat 5,000,000-copper value that cost 500 gold but 5,000,000 copper
     * for the same instrument. See ADR-026 and design §12.3.1.
     */
    public static final int MIN_COIN_COUNT = 500;

    /**
     * The absolute copper floor, which is now only a sanity bound -- the smallest legal cheque is
     * 500 copper. The denomination-aware rule above is the real check, exactly as it is on the
     * Rails side.
     */
    public static final int MIN_AMOUNT_COPPER = MIN_COIN_COUNT;

    /**
     * ADR-018/ADR-019: 100,000 gold-equivalent. Unchanged by Milestone 8b, and structural rather
     * than policy -- the amount is stored and debited as an int32 copper column.
     */
    public static final int MAX_AMOUNT_COPPER = 100_000 * CoinConversion.COPPER_PER_GOLD;

    private static BankingChequeIssuanceClientPort client = new BankingChequeIssuanceClient();

    /**
     * At most one cheque issuance in flight per player -- there is no per-slot or per-item
     * identity for a second, independent attempt to legitimately race against, and the
     * teller's own single-amount confirm/cancel UI never offers a reason to allow two at once.
     */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingChequeIssuanceProxyService() {
    }

    public static void useClientForTesting(BankingChequeIssuanceClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingChequeIssuanceClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID playerId) {
        return IN_FLIGHT.contains(playerId);
    }

    /**
     * The full cheque issuance sequence -- the one and only production entry point.
     *
     * @param amountCopper the cheque's value, always in copper
     * @param currencyKey  which balance funds it. Milestone 8b: this <b>never rescales</b>
     *                     {@code amountCopper} -- 5,000,000 copper funded from gold debits 500
     *                     gold, and the same 5,000,000 funded from copper debits 5,000,000 copper.
     *                     See docs/banking_bank_cheque_issuance.md, which calls this out as the
     *                     one part of the contract that is easy to get backwards.
     */
    public static CompletableFuture<BankingChequeIssuanceResult> triggerChequeIssuance(
            ServerPlayer player, ServiceNpcEntity teller, int amountCopper, String currencyKey
    ) {
        UUID playerId = player.getUUID();
        if (!IN_FLIGHT.add(playerId)) {
            return CompletableFuture.completedFuture(new BankingChequeIssuanceResult.LocalFailure("cheque_issuance_already_in_flight"));
        }
        return prepareAndConfirmInternal(player, teller, amountCopper, currencyKey)
                .thenCompose(outcome -> continueToDelivery(player, teller, outcome))
                .whenComplete((result, error) -> IN_FLIGHT.remove(playerId));
    }

    public static CompletableFuture<BankingChequeIssuanceResult> triggerChequeIssuanceForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int amountCopper, String currencyKey
    ) {
        return triggerChequeIssuance(player, teller, amountCopper, currencyKey);
    }

    /**
     * Runs protocol steps through confirm only (local checks, prepare, write receipt, confirm)
     * and deliberately stops before ever attempting insertion -- the "simulate a crash between
     * confirm and physical delivery" test hook. A successful {@link
     * PrepareAndConfirmOutcome.Confirmed} leaves this player's entry in {@link #IN_FLIGHT}
     * rather than clearing it, matching what a real crash at that point would leave. Call
     * {@link #resetInFlightTrackingForTesting()} afterward to simulate the process restarting.
     */
    public static CompletableFuture<PrepareAndConfirmOutcome> prepareAndConfirmForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int amountCopper, String currencyKey
    ) {
        UUID playerId = player.getUUID();
        if (!IN_FLIGHT.add(playerId)) {
            return CompletableFuture.completedFuture(new PrepareAndConfirmOutcome.LocalFailure("cheque_issuance_already_in_flight"));
        }
        return prepareAndConfirmInternal(player, teller, amountCopper, currencyKey).whenComplete((outcome, error) -> {
            if (error != null || !(outcome instanceof PrepareAndConfirmOutcome.Confirmed)) {
                IN_FLIGHT.remove(playerId);
            }
        });
    }

    /**
     * Runs the delivery tail alone (final capacity check, construct, insert, forceSave,
     * resolve-or-leave-pending) given an operation whose confirm has already succeeded -- the
     * counterpart to {@link #prepareAndConfirmForTesting}, for a test that wants to drive
     * delivery separately (e.g. resuming after a simulated crash, or proving the forced-save
     * abort-and-restore path in isolation). Does not touch {@link #IN_FLIGHT} itself.
     */
    public static CompletableFuture<BankingChequeIssuanceResult> deliverForTesting(
            ServerPlayer player, UUID operationPublicId, UUID chequePublicId, int amountCopper
    ) {
        CompletableFuture<BankingChequeIssuanceResult> result = new CompletableFuture<>();
        // null teller, matching resumeConfirmChequeIssuance's own real production caller: this
        // is a crash-recovery/resume-shaped entry point, not a live interaction, so there is no
        // live teller to re-check proximity against -- see attemptDelivery's own docs.
        attemptDelivery(player.server, player, null, operationPublicId, chequePublicId, amountCopper, result);
        return result;
    }

    /**
     * The startup-reconciliation entry point for a receipt still {@code PENDING_LOCAL_ACTION}.
     * Unlike every prior flow's own resume (which only ever needs to re-run confirm, since the
     * physical action always already happened before their own receipt could exist), a cheque
     * issuance resume must also attempt delivery -- confirm may have succeeded but delivery may
     * never have been attempted at all. Re-calls confirm first (always safe: idempotent, and
     * the receipt itself carries no cheque identity to trust instead -- see the class docs). If
     * the player happens to already be online (opportunistic; real server-startup reconciliation
     * usually runs before anyone reconnects), delivery is attempted immediately; otherwise the
     * receipt is left pending for a later attempt (a known limitation of this slice -- see
     * completion notes).
     */
    public static CompletableFuture<BankingChequeIssuanceResult> resumeConfirmChequeIssuance(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId
    ) {
        CompletableFuture<BankingChequeIssuanceResult> result = new CompletableFuture<>();
        final CompletableFuture<BankingChequeIssuanceConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(playerUuid, operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously while resuming cheque issuance {}", operationPublicId, synchronousFailure);
            return CompletableFuture.completedFuture(
                    new BankingChequeIssuanceResult.TransportFailure(BankingChequeIssuanceStage.CONFIRM, "synchronous_submission_failure"));
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                result.complete(new BankingChequeIssuanceResult.TransportFailure(BankingChequeIssuanceStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            switch (confirmResult) {
                case BankingChequeIssuanceConfirmResult.Confirmed confirmed -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                    if (player == null) {
                        LOGGER.info(
                                "Cheque issuance {} confirmed on resume but player {} is not online; "
                                        + "receipt remains pending for a later delivery attempt",
                                operationPublicId, playerUuid
                        );
                        result.complete(new BankingChequeIssuanceResult.PendingDelivery(
                                operationPublicId, confirmed.chequePublicId(), confirmed.amount()));
                        return;
                    }
                    // null teller: this is the server-startup reconciliation resume path, not a
                    // live interaction -- see attemptDelivery's own docs.
                    attemptDelivery(server, player, null, operationPublicId, confirmed.chequePublicId(), confirmed.amount(), result);
                }
                case BankingChequeIssuanceConfirmResult.ReconciliationRequired ignored ->
                        result.complete(escalateAndReport(server, operationPublicId));
                case BankingChequeIssuanceConfirmResult.Rejected rejected -> result.complete(
                        new BankingChequeIssuanceResult.Rejected(BankingChequeIssuanceStage.CONFIRM, rejected.outcome(), rejected.retryable()));
                case BankingChequeIssuanceConfirmResult.TransportFailure failure -> result.complete(
                        new BankingChequeIssuanceResult.TransportFailure(BankingChequeIssuanceStage.CONFIRM, failure.safeCode()));
                case BankingChequeIssuanceConfirmResult.LocalFailure failure -> result.complete(
                        new BankingChequeIssuanceResult.TransportFailure(BankingChequeIssuanceStage.CONFIRM, failure.safeCode()));
            }
        }));
        return result;
    }

    private static CompletableFuture<BankingChequeIssuanceResult> continueToDelivery(
            ServerPlayer player, ServiceNpcEntity teller, PrepareAndConfirmOutcome outcome
    ) {
        return switch (outcome) {
            case PrepareAndConfirmOutcome.Confirmed confirmed -> {
                CompletableFuture<BankingChequeIssuanceResult> result = new CompletableFuture<>();
                attemptDelivery(player.server, player, teller, confirmed.operationPublicId(), confirmed.chequePublicId(), confirmed.amount(), result);
                yield result;
            }
            case PrepareAndConfirmOutcome.ReconciliationRequired reconciliationRequired ->
                    CompletableFuture.completedFuture(new BankingChequeIssuanceResult.ReconciliationRequired(reconciliationRequired.operationPublicId()));
            case PrepareAndConfirmOutcome.RejectedLocally rejectedLocally ->
                    CompletableFuture.completedFuture(new BankingChequeIssuanceResult.RejectedLocally(rejectedLocally.reason()));
            case PrepareAndConfirmOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                    new BankingChequeIssuanceResult.Rejected(rejected.stage(), rejected.outcome(), rejected.retryable()));
            case PrepareAndConfirmOutcome.TransportFailure failure -> CompletableFuture.completedFuture(
                    new BankingChequeIssuanceResult.TransportFailure(failure.stage(), failure.safeCode()));
            case PrepareAndConfirmOutcome.LocalFailure failure ->
                    CompletableFuture.completedFuture(new BankingChequeIssuanceResult.LocalFailure(failure.safeCode()));
        };
    }

    // ---- Steps 1-4: local checks, prepare, write receipt, confirm ----

    private static CompletableFuture<PrepareAndConfirmOutcome> prepareAndConfirmInternal(
            ServerPlayer player, ServiceNpcEntity teller, int amountCopper, String currencyKey
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PrepareAndConfirmOutcome.LocalFailure("teller_not_resolved"));
        }

        // Milestone 8b: the funding denomination decides the unit, so an unsupported key is
        // rejected here rather than reaching Rails as an amount validated against the wrong one.
        Integer unit = CurrencyItemRegistry.copperUnitFor(currencyKey).orElse(null);
        if (unit == null) {
            return CompletableFuture.completedFuture(
                    new PrepareAndConfirmOutcome.RejectedLocally(BankingChequeIssuanceLocalRejectionReason.INVALID_AMOUNT));
        }

        // The same three rules Rails' own validator applies, in the same order: storable range,
        // whole multiple of the funding denomination's unit, and at least MIN_COIN_COUNT coins of
        // it. Checking the coin count rather than the copper value is the whole point of the
        // revised floor -- 500 copper and 500 gold are both legal, and 10 gold is not.
        boolean outOfRange = amountCopper < MIN_AMOUNT_COPPER || amountCopper > MAX_AMOUNT_COPPER;
        boolean notWholeCoins = amountCopper % unit != 0;
        boolean belowFloor = amountCopper / unit < MIN_COIN_COUNT;
        if (outOfRange || notWholeCoins || belowFloor) {
            return CompletableFuture.completedFuture(
                    new PrepareAndConfirmOutcome.RejectedLocally(BankingChequeIssuanceLocalRejectionReason.INVALID_AMOUNT));
        }

        // Check 1 of 2 -- see class docs. Before any Rails call or reservation: no round trip,
        // no reserved gold, for an issuance that could never be delivered.
        if (!hasRoomForOneCheque(player)) {
            return CompletableFuture.completedFuture(
                    new PrepareAndConfirmOutcome.RejectedLocally(BankingChequeIssuanceLocalRejectionReason.INSUFFICIENT_CAPACITY));
        }

        MinecraftServer server = player.server;
        BankingChequeIssuancePrepareRequest prepareRequest = new BankingChequeIssuancePrepareRequest(
                player.getUUID(), resolved.worldNpcPublicId(), UUID.randomUUID().toString(), amountCopper, currencyKey
        );

        final CompletableFuture<BankingChequeIssuancePrepareResult> prepareFuture;
        try {
            prepareFuture = client.prepareChequeIssuance(server, prepareRequest);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/cheque/issue/prepare submission threw synchronously", synchronousFailure);
            return CompletableFuture.completedFuture(
                    new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.PREPARE, "synchronous_submission_failure"));
        }

        CompletableFuture<PrepareAndConfirmOutcome> outcome = new CompletableFuture<>();
        prepareFuture.whenComplete((prepareResult, prepareError) -> server.execute(() -> {
            if (prepareError != null || prepareResult == null) {
                outcome.complete(new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.PREPARE, "unexpected_client_error"));
                return;
            }
            switch (prepareResult) {
                case BankingChequeIssuancePrepareResult.Rejected rejected ->
                        outcome.complete(new PrepareAndConfirmOutcome.Rejected(BankingChequeIssuanceStage.PREPARE, rejected.outcome(), rejected.retryable()));
                case BankingChequeIssuancePrepareResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.PREPARE, failure.safeCode()));
                case BankingChequeIssuancePrepareResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndConfirmOutcome.LocalFailure(failure.safeCode()));
                case BankingChequeIssuancePrepareResult.Success success ->
                        writeReceiptAndConfirm(server, player, amountCopper, success.operationPublicId(), outcome);
            }
        }));
        return outcome;
    }

    /**
     * Step 3 (write the durable receipt, before confirm -- the risky/committing step for this
     * flow) and step 4 (confirm itself). See the class docs for why the receipt is written here
     * rather than after insertion.
     */
    private static void writeReceiptAndConfirm(
            MinecraftServer server, ServerPlayer player, int amountCopper, UUID operationPublicId,
            CompletableFuture<PrepareAndConfirmOutcome> outcome
    ) {
        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, operationPublicId, player.getUUID(), BankTransferOperationType.CHEQUE_ISSUANCE,
                null, (long) amountCopper, null, System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking cheque issuance receipt could not be recorded for operation {}: receipt store is read-only (unsupported future schema)",
                    operationPublicId
            );
        }

        final CompletableFuture<BankingChequeIssuanceConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(player.getUUID(), operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously for cheque issuance {}", operationPublicId, synchronousFailure);
            // The receipt already durably claims this operation was submitted for confirm --
            // left unresolved by design, exactly like any other confirm-stage transport failure.
            outcome.complete(new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.CONFIRM, "synchronous_submission_failure"));
            return;
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                outcome.complete(new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            switch (confirmResult) {
                case BankingChequeIssuanceConfirmResult.Confirmed confirmed ->
                        outcome.complete(new PrepareAndConfirmOutcome.Confirmed(operationPublicId, confirmed.chequePublicId(), confirmed.amount()));
                case BankingChequeIssuanceConfirmResult.ReconciliationRequired ignored -> {
                    escalateAndReport(server, operationPublicId);
                    outcome.complete(new PrepareAndConfirmOutcome.ReconciliationRequired(operationPublicId));
                }
                case BankingChequeIssuanceConfirmResult.Rejected rejected ->
                        outcome.complete(new PrepareAndConfirmOutcome.Rejected(BankingChequeIssuanceStage.CONFIRM, rejected.outcome(), rejected.retryable()));
                case BankingChequeIssuanceConfirmResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.CONFIRM, failure.safeCode()));
                case BankingChequeIssuanceConfirmResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndConfirmOutcome.TransportFailure(BankingChequeIssuanceStage.CONFIRM, failure.safeCode()));
            }
        }));
    }

    // ---- Delivery tail: final capacity check, construct, insert, forceSave, resolve/leave-pending ----

    /**
     * Step 5 (final capacity check, no yield point before insertion), step 6 (construct the
     * real item and insert), and step 7 (forceSave, then resolve or -- on a detected failure --
     * abort-and-restore the local insertion while leaving the receipt pending). Always runs on
     * the main server thread.
     *
     * <p>Milestone 14 priority 2 (context enforcement, dimension 5): {@code teller} is
     * {@code @Nullable} because this method has two genuinely different kinds of caller. The
     * live interaction path ({@link #continueToDelivery}) always passes the real teller, and
     * this re-resolves it via {@link BankingProxyService#resolve} before doing anything
     * physical -- the confirm round trip is exactly the window the player could have walked
     * away or the teller could have been discarded/reassigned. The resume paths ({@link
     * #resumeConfirmChequeIssuance}, {@link #deliverForTesting}) pass {@code null} on purpose:
     * unlike every insertion-shaped flow before it, Rails' confirm has already irrevocably
     * committed with no post-confirm Cancel (see class docs), so a stale/out-of-range teller
     * here cannot be treated as a cancellable failure the way it is in {@link
     * BankingDepositProxyService}/{@link BankingWithdrawalProxyService} -- it is folded into
     * the exact same {@code PendingDelivery} path this method already uses for "no room" and
     * "forceSave failed", since redemption is Rails-side value-gated, not teller-proximity-
     * gated, and a later delivery attempt (from any teller, or none at all, per resume) is
     * always safe.
     */
    private static void attemptDelivery(
            MinecraftServer server, ServerPlayer player, @Nullable ServiceNpcEntity teller,
            UUID operationPublicId, UUID chequePublicId, int amountCopper,
            CompletableFuture<BankingChequeIssuanceResult> result
    ) {
        if (teller != null && BankingProxyService.resolve(player, teller) == null) {
            LOGGER.info(
                    "Cheque issuance {} confirmed but the teller is no longer live/in range for delivery; "
                            + "receipt remains pending for a later delivery attempt", operationPublicId
            );
            result.complete(new BankingChequeIssuanceResult.PendingDelivery(operationPublicId, chequePublicId, amountCopper));
            return;
        }

        if (!hasRoomForOneCheque(player)) {
            // Rails has already, irrevocably confirmed -- there is nothing to cancel. The
            // receipt stays PENDING_LOCAL_ACTION (untouched): a later retry is always safe for
            // exactly the reason this class's own docs explain (redemption is value-gated by
            // Rails, not by physical copy count).
            LOGGER.info(
                    "Cheque issuance {} confirmed but the player's inventory has no room for delivery; "
                            + "receipt remains pending for a later delivery attempt", operationPublicId
            );
            result.complete(new BankingChequeIssuanceResult.PendingDelivery(operationPublicId, chequePublicId, amountCopper));
            return;
        }

        ItemStack cheque = buildChequeStack(chequePublicId, amountCopper, player);
        ItemStack toInsert = cheque.copy();
        player.getInventory().add(toInsert);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        if (!toInsert.isEmpty()) {
            // Structurally unreachable in real operation (the precheck above uses the same
            // primitive that makes insertion failure unreachable in every other flow), kept as
            // a real, defensive path. Nothing was actually given to the player, so this is
            // exactly like the precheck having failed.
            LOGGER.error(
                    "banking cheque issuance insertion left a nonzero leftover for operation {} despite a passing capacity pre-check",
                    operationPublicId
            );
            result.complete(new BankingChequeIssuanceResult.PendingDelivery(operationPublicId, chequePublicId, amountCopper));
            return;
        }

        if (!BankTransferPlayerDurability.forceSave(player)) {
            // Abort-and-restore, LOCAL side only -- see the class docs for the full reasoning.
            // Rails' confirm cannot be undone, but this just-inserted, possibly-not-durable
            // item can be, and safely retried later.
            removeOneChequeMatching(player, chequePublicId);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();
            LOGGER.warn(
                    "Forced save failed after inserting cheque {} for operation {}; the insertion was backed out "
                            + "and the receipt remains pending for a later delivery attempt", chequePublicId, operationPublicId
            );
            result.complete(new BankingChequeIssuanceResult.PendingDelivery(operationPublicId, chequePublicId, amountCopper));
            return;
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceipts.resolve(level, operationPublicId);
        result.complete(new BankingChequeIssuanceResult.Confirmed(operationPublicId, chequePublicId, amountCopper));
    }

    private static BankingChequeIssuanceResult escalateAndReport(MinecraftServer server, UUID operationPublicId) {
        ServerLevel level = server.overworld();
        BankTransferReceiptStore.EscalateOutcome escalateOutcome = BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
        if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking cheque issuance receipt for operation {} could not be escalated to reconciliation_required: "
                            + "receipt store is read-only (unsupported future schema)", operationPublicId
            );
        }
        return new BankingChequeIssuanceResult.ReconciliationRequired(operationPublicId);
    }

    /**
     * Whether one more, brand-new, non-stacking (max stack size 1) item would fit -- reuses
     * {@link BankingWithdrawalProxyService#hasSufficientCapacity}'s own already-proven
     * slot-selection reasoning directly rather than re-deriving it. A cheque item's {@code
     * maxStackSize} is 1 (see {@code ItemRegistry#BANK_CHEQUE}), so this always reduces to "is
     * there a free main-inventory slot" -- the same damaged-item, all-or-nothing path that
     * method already implements.
     */
    private static boolean hasRoomForOneCheque(ServerPlayer player) {
        ItemStack probe = new ItemStack(ItemRegistry.BANK_CHEQUE.get());
        return BankingWithdrawalProxyService.hasSufficientCapacity(player.getInventory(), probe);
    }

    private static ItemStack buildChequeStack(UUID chequePublicId, long amountCopper, ServerPlayer issuedBy) {
        ItemStack stack = new ItemStack(ItemRegistry.BANK_CHEQUE.get());
        String issuerText = "Britannia Bank";
        stack.set(DataComponentRegistry.BANK_CHEQUE_DATA.get(), new BankChequeData(chequePublicId, amountCopper, issuerText));
        return stack;
    }

    /**
     * Removes exactly one cheque stack carrying {@code chequePublicId} from the player's
     * inventory -- used only by the abort-and-restore path immediately after this exact stack
     * was just inserted by this same method call, so a match is always found in practice; if
     * somehow not (defensive only), this is a silent no-op rather than a crash.
     */
    private static void removeOneChequeMatching(ServerPlayer player, UUID chequePublicId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != ItemRegistry.BANK_CHEQUE.get()) continue;
            BankChequeData data = stack.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
            if (data != null && chequePublicId.equals(data.chequeId())) {
                stack.shrink(1);
                return;
            }
        }
    }

    /**
     * Result of protocol steps 1-4 alone (see {@link #prepareAndConfirmForTesting}) -- a sealed
     * hierarchy distinct from {@link BankingChequeIssuanceResult} because {@link Confirmed} here
     * has no direct equivalent there (by the time a full sequence produces a {@link
     * BankingChequeIssuanceResult}, delivery has always either completed, been left pending, or
     * the attempt never reached confirm at all).
     */
    public sealed interface PrepareAndConfirmOutcome permits
            PrepareAndConfirmOutcome.Confirmed,
            PrepareAndConfirmOutcome.ReconciliationRequired,
            PrepareAndConfirmOutcome.RejectedLocally,
            PrepareAndConfirmOutcome.Rejected,
            PrepareAndConfirmOutcome.TransportFailure,
            PrepareAndConfirmOutcome.LocalFailure {

        /** Confirm succeeded: the real cheque exists on Rails. Delivery was NOT attempted. */
        record Confirmed(UUID operationPublicId, UUID chequePublicId, int amount) implements PrepareAndConfirmOutcome {
        }

        record ReconciliationRequired(UUID operationPublicId) implements PrepareAndConfirmOutcome {
        }

        record RejectedLocally(BankingChequeIssuanceLocalRejectionReason reason) implements PrepareAndConfirmOutcome {
        }

        record Rejected(BankingChequeIssuanceStage stage, BankingTransferOutcome outcome, boolean retryable) implements PrepareAndConfirmOutcome {
        }

        record TransportFailure(BankingChequeIssuanceStage stage, String safeCode) implements PrepareAndConfirmOutcome {
        }

        record LocalFailure(String safeCode) implements PrepareAndConfirmOutcome {
        }
    }
}

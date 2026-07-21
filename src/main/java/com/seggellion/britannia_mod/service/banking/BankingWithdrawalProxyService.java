package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemDecodeResult;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import net.minecraft.core.HolderLookup;
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
 * Milestone 9 NeoForge Slice 2: the withdrawal path -- reconstruction, the full-inventory
 * pre-check, insertion, durable receipt sequencing, and confirmation. Built on the same
 * established pattern as {@link BankingDepositProxyService} (resolve fresh, dedupe in-flight
 * attempts, dispatch off the main thread, marshal every completion back onto the main thread
 * via {@code server.execute} before touching player/level state) -- see that class for the
 * primitives this mirrors rather than re-derives.
 *
 * <p><b>No live trigger exists yet.</b> This slice is exercised via {@link
 * #triggerWithdrawalForTesting} and {@link #prepareAndInsertForTesting} only -- neither is wired
 * to a real screen, packet, or entity interaction. That wiring is Slice 3's job.
 *
 * <h2>Insertion atomicity (real, decompiled {@code Inventory#add} source, not assumed)</h2>
 * {@code Inventory#add(ItemStack)} is <b>not atomic</b> for an ordinary (undamaged) stack: it
 * repeatedly calls {@code addResource}, mutating the passed-in stack's count down after each
 * slot it fills, until either the stack is empty or an iteration makes no further progress --
 * meaning a large stack can legitimately fill some slots and still have a nonzero remainder.
 * Its {@code boolean} return value is also not what it appears to be: it reports whether the
 * count decreased <i>at all</i> (partial progress), not whether the stack was fully consumed --
 * this class never inspects that return value for that reason, and instead checks {@code
 * ItemStack#isEmpty()} on the (copied) stack after the call, which is the only way to know
 * whether insertion was actually complete. A <i>damaged</i> item takes a genuinely different,
 * atomic path: a single free slot is located first and the whole stack is placed there in one
 * step, or nothing happens at all.
 *
 * <h2>Why the receipt is written before insertion (unlike deposit's after-removal ordering)</h2>
 * Section A.6 states withdrawal's step 2 (write the local receipt) precedes step 3 (reconstruct
 * and insert) -- the reverse of deposit's ordering, and this is deliberate, not an inconsistency
 * to reconcile: deposit's receipt exists to recover a physical removal that already happened
 * (the risk is behind the receipt), but withdrawal's risk is the {@code add()} call itself,
 * which -- per the atomicity finding above -- can leave the player holding part or all of an
 * item this process has no other record of ever having reserved. Writing the receipt
 * immediately before attempting insertion, rather than after, means that even a crash occurring
 * mid-{@code add()} (partial insertion, process killed before this method returns) leaves a
 * durable local record that operation existed and must be reconciled -- exactly the same
 * "receipt before risk" principle deposit follows, applied to withdrawal's own, differently-
 * shaped risk window. Writing it any earlier (before the pre-check) would risk a receipt for an
 * item that could never have fit in the first place; any later (after insertion) reopens the
 * unrecoverable window this ordering exists to close.
 *
 * <h2>Full-inventory handling: a deliberate divergence from {@code giveCoin}/{@code
 * Reservation.refund}</h2>
 * {@code ServerEconomyService#giveCoin} and {@code Reservation#refund} both fall back to {@code
 * player.drop(refund, false)} when {@code Inventory#add} does not fully consume a stack --
 * dropping the remainder on the ground rather than losing it. That precedent is deliberately
 * <b>not</b> extended here: a bank withdrawal must never place an item outside the player's
 * control that this process has already told Rails is "confirmed, in the player's inventory."
 * A world drop is exactly as far from that guarantee as never inserting the item at all -- worse,
 * in fact, since it is silently loseable (despawn, lava, another player) in a way an un-confirmed
 * operation is not. Instead: a synchronous pre-check ({@link #hasSufficientCapacity}) runs
 * <i>before</i> any receipt is written and before insertion is ever attempted; if it fails, the
 * withdrawal is rejected cleanly (Cancel is called, nothing physical happens, no receipt exists).
 * See {@link #hasSufficientCapacity} and {@link #handleInsertionOutcome} for exactly how a
 * pre-check pass is reconciled against the real, possibly-partial {@code add()} result.
 *
 * <h2>Is "insertion fails despite a passing pre-check" reachable?</h2>
 * Structurally, no -- not in real operation. The pre-check and the real {@code add()} call both
 * run synchronously on the main server thread, back to back, with no yield point (no {@code
 * server.execute}, no awaited future) between them, exactly like every other inventory-mutating
 * code path in this mod. Nothing can observe or mutate the player's inventory between the two
 * calls. Given that {@link #hasSufficientCapacity} implements the same slot-selection rules
 * {@code Inventory#add} itself uses ({@code getFreeSlot} for damaged items -- the 36 main slots
 * only, never offhand; per-slot headroom against {@code getMaxStackSize} for ordinary stacks
 * across those same 36 slots, plus the offhand slot but <b>only</b> when it already holds a
 * matching stack with room -- an empty offhand slot is never a landing target for a new item,
 * since {@code getSlotWithRemainingSpace}'s offhand check requires a non-empty slot and {@code
 * getFreeSlot}'s empty-slot fallback never looks at offhand at all), a passing pre-check makes
 * a full {@code add()} success deterministic. The defensive path ({@link
 * BankingWithdrawalAbortReason#INSERTION_FAILED}) is
 * kept anyway -- not as a speculative "just in case," but as real, exercised code: {@link
 * #handleInsertionOutcome} is unit-testable in isolation with a synthetic non-empty leftover
 * stack, and the GameTest suite cross-verifies the pre-check's prediction against {@code add()}'s
 * actual outcome across many adversarial inventory shapes to demonstrate the two never disagree.
 *
 * <h2>Proven safe regardless of player game mode</h2>
 * {@link #hasSufficientCapacity} takes only {@code (Inventory, ItemStack)} -- no {@code Player},
 * no game-mode signal of any kind -- so its prediction is, by construction, identical for a
 * creative/instabuild player and a survival one given the same inventory shape; it structurally
 * cannot be creative-mode-aware. What genuinely differs for a creative player is {@code
 * Inventory#add} itself: whenever the ordinary placement loop stops making progress while {@code
 * player.hasInfiniteMaterials()} is true, vanilla force-zeroes whatever count remains (silently
 * destroying it, matching creative mode's own "giving an item always succeeds" behavior) instead
 * of leaving a genuine leftover -- and critically, this can fire after <i>partial</i> genuine
 * placement, not only when nothing could be placed at all: a stack that partially fits gets that
 * portion genuinely stored and the un-fitting remainder silently destroyed, both reported through
 * the exact same {@code ItemStack#isEmpty()} outcome the ordinary "fully succeeded" case reports.
 * {@code isEmpty()} is therefore not a trustworthy "genuinely, fully placed" signal for a
 * creative/instabuild player at all. This is proven harmless here, not merely assumed: the
 * pre-check's prediction and the real, orchestrated production sequence are checked directly
 * against a creative/instabuild player across the same adversarial inventory-shape battery used
 * above (see {@code BankingWithdrawalProxyServiceGameTests#capacityPreCheckReasoningHoldsForCreativePlayersToo}
 * and {@code #fullInventoryIsRejectedByPreCheckEvenForCreativePlayersWithNoItemDestroyed}) -- the
 * silent-discard branch (full or partial) is real and reachable if {@code add()} is called
 * directly after a false prediction, but production never does that: the pre-check runs, and
 * rejects, strictly before {@code add()} is ever invoked (see {@link #reconstructAndInsert}), for
 * every player regardless
 * of game mode. The guarantee does not rest on an assumption that a real bank customer is never
 * in that state -- it holds even when one is.
 */
public final class BankingWithdrawalProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BankingWithdrawalClientPort client = new BankingWithdrawalClient();

    /**
     * Withdrawal attempts currently in flight, keyed by the target {@code bankItemPublicId}
     * alone (unlike deposit's compound {@code (player, slot)} key) -- a bank item has exactly
     * one owner and can be the target of at most one in-flight withdrawal at a time regardless
     * of which player or teller initiates it, so the item's own identity is the entire dedup
     * key. Held for the entire sequence (prepare through confirm settling), matching deposit.
     */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingWithdrawalProxyService() {
    }

    public static void useClientForTesting(BankingWithdrawalClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingWithdrawalClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID bankItemPublicId) {
        return IN_FLIGHT.contains(bankItemPublicId);
    }

    /**
     * The full withdrawal sequence, steps 1-8 -- the one and only production entry point (Slice
     * 3a), reached from a real {@code BankWithdrawalRequestC2SPayload} via {@link
     * BankingTransferPacketService}. Must be called from the main server thread, matching every
     * other live-inventory-touching entry point in this mod.
     */
    public static CompletableFuture<BankingWithdrawalResult> triggerWithdrawal(
            ServerPlayer player, ServiceNpcEntity teller, UUID bankItemPublicId
    ) {
        if (!IN_FLIGHT.add(bankItemPublicId)) {
            return CompletableFuture.completedFuture(new BankingWithdrawalResult.LocalFailure("withdrawal_already_in_flight"));
        }
        return prepareAndInsertInternal(player, teller, bankItemPublicId)
                .thenCompose(outcome -> continueToConfirm(player, outcome))
                .whenComplete((result, error) -> IN_FLIGHT.remove(bankItemPublicId));
    }

    /**
     * Test-support alias for {@link #triggerWithdrawal} -- kept so every existing test written
     * against this name keeps compiling and exercising the exact same real logic, not a
     * parallel copy of it.
     */
    public static CompletableFuture<BankingWithdrawalResult> triggerWithdrawalForTesting(
            ServerPlayer player, ServiceNpcEntity teller, UUID bankItemPublicId
    ) {
        return triggerWithdrawal(player, teller, bankItemPublicId);
    }

    /**
     * Runs protocol steps 1-5 only (prepare, decode/verify, pre-check, write receipt, insert)
     * and deliberately stops -- never calls confirm. This is the "simulate a crash between
     * insertion and confirmation" test hook: a real crash at that point would not run confirm
     * either, and would not clean up any in-memory state (including {@link #IN_FLIGHT}) -- so
     * unlike {@link #triggerWithdrawalForTesting}, a successful {@link
     * PrepareAndInsertOutcome.Inserted} leaves this item's entry in {@link #IN_FLIGHT} rather
     * than clearing it, matching that reality. Call {@link #resetInFlightTrackingForTesting()}
     * afterward to simulate the process restarting. Any other outcome (nothing physical
     * happened, or nothing recoverable happened) clears its own entry immediately.
     */
    public static CompletableFuture<PrepareAndInsertOutcome> prepareAndInsertForTesting(
            ServerPlayer player, ServiceNpcEntity teller, UUID bankItemPublicId
    ) {
        if (!IN_FLIGHT.add(bankItemPublicId)) {
            return CompletableFuture.completedFuture(new PrepareAndInsertOutcome.LocalFailure("withdrawal_already_in_flight"));
        }
        return prepareAndInsertInternal(player, teller, bankItemPublicId).whenComplete((outcome, error) -> {
            if (error != null || !(outcome instanceof PrepareAndInsertOutcome.Inserted)) {
                IN_FLIGHT.remove(bankItemPublicId);
            }
        });
    }

    /**
     * Runs protocol steps 6-8 (confirm, then resolve/escalate/leave-unresolved) given an
     * operation that has already been prepared and inserted -- the counterpart to {@link
     * #prepareAndInsertForTesting}, for resuming after a simulated crash. Does not touch {@link
     * #IN_FLIGHT} itself; callers driving a real crash-recovery scenario are expected to have
     * already called {@link #resetInFlightTrackingForTesting()} to simulate the restart.
     */
    public static CompletableFuture<BankingWithdrawalResult> confirmWithdrawalForTesting(
            ServerPlayer player, UUID operationPublicId, UUID bankItemPublicId
    ) {
        return confirmWithdrawal(player, operationPublicId, bankItemPublicId);
    }

    private static CompletableFuture<BankingWithdrawalResult> continueToConfirm(ServerPlayer player, PrepareAndInsertOutcome outcome) {
        return switch (outcome) {
            case PrepareAndInsertOutcome.Inserted inserted ->
                    confirmWithdrawal(player, inserted.operationPublicId(), inserted.bankItemPublicId());
            case PrepareAndInsertOutcome.Aborted aborted ->
                    CompletableFuture.completedFuture(new BankingWithdrawalResult.Aborted(aborted.operationPublicId(), aborted.reason()));
            case PrepareAndInsertOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                    new BankingWithdrawalResult.Rejected(BankingWithdrawalStage.PREPARE, rejected.outcome(), rejected.retryable())
            );
            case PrepareAndInsertOutcome.TransportFailure failure -> CompletableFuture.completedFuture(
                    new BankingWithdrawalResult.TransportFailure(failure.stage(), failure.safeCode())
            );
            case PrepareAndInsertOutcome.LocalFailure failure ->
                    CompletableFuture.completedFuture(new BankingWithdrawalResult.LocalFailure(failure.safeCode()));
        };
    }

    // ---- Steps 1-5: prepare, decode/verify, pre-check, write receipt, insert ----

    private static CompletableFuture<PrepareAndInsertOutcome> prepareAndInsertInternal(
            ServerPlayer player, ServiceNpcEntity teller, UUID bankItemPublicId
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PrepareAndInsertOutcome.LocalFailure("teller_not_resolved"));
        }

        MinecraftServer server = player.server;
        BankingWithdrawalPrepareRequest prepareRequest = new BankingWithdrawalPrepareRequest(
                player.getUUID(), resolved.worldNpcPublicId(), UUID.randomUUID().toString(), bankItemPublicId
        );

        final CompletableFuture<BankingWithdrawalPrepareResult> prepareFuture;
        try {
            prepareFuture = client.prepareWithdrawal(server, prepareRequest);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/withdrawal/prepare submission threw synchronously", synchronousFailure);
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
                case BankingWithdrawalPrepareResult.Rejected rejected ->
                        outcome.complete(new PrepareAndInsertOutcome.Rejected(rejected.outcome(), rejected.retryable()));
                case BankingWithdrawalPrepareResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndInsertOutcome.TransportFailure(BankingWithdrawalStage.PREPARE, failure.safeCode()));
                case BankingWithdrawalPrepareResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndInsertOutcome.LocalFailure(failure.safeCode()));
                case BankingWithdrawalPrepareResult.Success success ->
                        reconstructAndInsert(server, player, success, outcome);
            }
        }));
        return outcome;
    }

    /**
     * Steps 2-5 (decode/verify, pre-check, write receipt, insert), always running on the main
     * server thread (marshaled there by the caller). See the class docs for why the receipt is
     * written before insertion, and why a full pre-check pass makes insertion failure
     * structurally unreachable here.
     */
    private static void reconstructAndInsert(
            MinecraftServer server, ServerPlayer player, BankingWithdrawalPrepareResult.Success success,
            CompletableFuture<PrepareAndInsertOutcome> outcome
    ) {
        HolderLookup.Provider registries = player.registryAccess();
        BankItemDecodeResult decoded = BankItemCodec.deserialize(success.payload(), registries);
        ItemStack reconstructed;
        switch (decoded) {
            case BankItemDecodeResult.Success decodeSuccess -> reconstructed = decodeSuccess.stack();
            case BankItemDecodeResult.Corrupt corrupt -> {
                abortAfterPrepare(server, player, success.operationPublicId(), BankingWithdrawalAbortReason.DECODE_FAILED, outcome);
                return;
            }
            case BankItemDecodeResult.UnsupportedSchemaVersion unsupported -> {
                abortAfterPrepare(server, player, success.operationPublicId(), BankingWithdrawalAbortReason.DECODE_FAILED, outcome);
                return;
            }
        }

        String actualFingerprint = BankItemFingerprint.fingerprint(reconstructed, registries);
        if (!actualFingerprint.equals(success.fingerprint())) {
            abortAfterPrepare(server, player, success.operationPublicId(), BankingWithdrawalAbortReason.FINGERPRINT_MISMATCH, outcome);
            return;
        }

        Inventory inventory = player.getInventory();
        if (!hasSufficientCapacity(inventory, reconstructed)) {
            abortAfterPrepare(server, player, success.operationPublicId(), BankingWithdrawalAbortReason.INSUFFICIENT_CAPACITY, outcome);
            return;
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, success.operationPublicId(), BankTransferOperationType.WITHDRAWAL, success.payload(), null,
                System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking withdrawal receipt could not be recorded for operation {}: receipt store is read-only (unsupported future schema)",
                    success.operationPublicId()
            );
        }

        handleInsertionOutcome(server, player, success, reconstructed, outcome);
    }

    /**
     * Step 5's insertion attempt and its outcome handling, split out from {@link
     * #reconstructAndInsert} specifically so it is independently unit-testable with a synthetic
     * non-empty leftover -- see the class docs' "is insertion failure reachable" discussion.
     * The stack passed to {@code Inventory#add} is always a fresh copy: the return value is
     * never trusted (see class docs on why), only {@link ItemStack#isEmpty()} on that copy
     * after the call.
     */
    private static void handleInsertionOutcome(
            MinecraftServer server, ServerPlayer player, BankingWithdrawalPrepareResult.Success success,
            ItemStack reconstructed, CompletableFuture<PrepareAndInsertOutcome> outcome
    ) {
        ItemStack toInsert = reconstructed.copy();
        player.getInventory().add(toInsert);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        if (toInsert.isEmpty()) {
            outcome.complete(new PrepareAndInsertOutcome.Inserted(success.operationPublicId(), success.bankItemPublicId()));
            return;
        }

        // Structurally unreachable in real operation (see class docs) -- a receipt now exists
        // for this operation, and it is resolved (not escalated), since nothing was actually
        // given to the player: the pre-check passed but insertion did not, which is treated
        // exactly like the pre-check having failed, not like a partial success.
        LOGGER.error(
                "banking withdrawal insertion left a nonzero leftover for operation {} despite a passing capacity pre-check",
                success.operationPublicId()
        );
        ServerLevel level = player.serverLevel();
        BankTransferReceipts.resolve(level, success.operationPublicId());
        sendCancel(server, player, success.operationPublicId(), "insertion_failed_after_passing_precheck",
                () -> outcome.complete(
                        new PrepareAndInsertOutcome.Aborted(success.operationPublicId(), BankingWithdrawalAbortReason.INSERTION_FAILED)));
    }

    /**
     * Steps 2-4's local-rejection path: Rails already reserved the item (a receipt may or may
     * not exist yet, depending which check failed), so Cancel is always called to release that
     * reservation. No receipt is ever written for {@link BankingWithdrawalAbortReason#DECODE_FAILED},
     * {@link BankingWithdrawalAbortReason#FINGERPRINT_MISMATCH}, or {@link
     * BankingWithdrawalAbortReason#INSUFFICIENT_CAPACITY} -- all three are detected before step 4's
     * receipt write.
     */
    private static void abortAfterPrepare(
            MinecraftServer server, ServerPlayer player, UUID operationPublicId, BankingWithdrawalAbortReason reason,
            CompletableFuture<PrepareAndInsertOutcome> outcome
    ) {
        sendCancel(server, player, operationPublicId, cancelReasonFor(reason),
                () -> outcome.complete(new PrepareAndInsertOutcome.Aborted(operationPublicId, reason)));
    }

    private static String cancelReasonFor(BankingWithdrawalAbortReason reason) {
        return switch (reason) {
            case DECODE_FAILED -> "payload_decode_failed";
            case FINGERPRINT_MISMATCH -> "fingerprint_mismatch";
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
            LOGGER.warn("banking/cancel submission threw synchronously for operation {}", operationPublicId, synchronousFailure);
            onSettled.run();
            return;
        }
        cancelFuture.whenComplete((cancelResult, cancelError) -> {
            if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                LOGGER.warn(
                        "banking/cancel for withdrawal operation {} did not cleanly confirm: result={} error={}",
                        operationPublicId, cancelResult, cancelError
                );
            }
            // As with deposit's own removal-revalidation-mismatch handling: the overall result
            // is reported regardless of whether Rails' own cleanup succeeded, since Cancel is
            // Rails' own idempotent, safely-retryable-later operation.
            onSettled.run();
        });
    }

    /**
     * Step 3's synchronous pre-check: could {@code stack} plausibly fit in {@code inventory}
     * right now, using the exact same slot-selection rules {@code Inventory#add} itself follows
     * (traced against the real decompiled source, not assumed) -- {@code getFreeSlot} alone for
     * a damaged item (its insertion is all-or-nothing into one of the 36 main slots; never
     * offhand, never armor), or per-slot headroom against {@code getMaxStackSize(stack)} summed
     * across the 36 main slots for an ordinary stack, plus the offhand slot but only when it
     * already holds a matching, non-full stack (an empty offhand slot is never a landing target
     * for a brand-new item -- {@code getSlotWithRemainingSpace}'s offhand check requires the
     * slot to be non-empty, and {@code getFreeSlot}'s empty-slot fallback never looks at offhand
     * at all; armor slots are correctly excluded too -- neither method ever considers them). See
     * the class docs for why this makes insertion failure, after this returns {@code true},
     * structurally unreachable.
     *
     * <p>Public (not test-only) deliberately: unlike the {@code *ForTesting} entry points
     * elsewhere in this class, this is real production pre-check logic that also happens to be
     * a pure function worth exercising directly -- the GameTest suite calls it to cross-verify
     * its prediction against {@code Inventory#add}'s actual outcome (see the class docs' "is
     * insertion failure reachable" discussion).
     */
    public static boolean hasSufficientCapacity(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) return true;

        if (stack.isDamaged()) {
            return inventory.getFreeSlot() != -1;
        }

        int needed = stack.getCount();
        int perSlotCap = inventory.getMaxStackSize(stack);
        int available = 0;
        for (ItemStack slot : inventory.items) {
            if (slot.isEmpty()) {
                available += perSlotCap;
            } else if (slot.isStackable() && ItemStack.isSameItemSameComponents(slot, stack)) {
                available += Math.max(0, perSlotCap - slot.getCount());
            }
            if (available >= needed) return true;
        }
        // An empty offhand slot is deliberately NOT counted: getSlotWithRemainingSpace only
        // tops up an offhand slot already holding a matching stack (hasRemainingSpaceForItem
        // requires the slot to be non-empty), and the empty-slot fallback getFreeSlot() only
        // ever scans the 36 main slots, never offhand -- so a brand-new item can never land in
        // an empty offhand slot via ordinary insertion, only continue an existing one there.
        ItemStack offhandStack = inventory.offhand.get(0);
        if (!offhandStack.isEmpty() && offhandStack.isStackable() && ItemStack.isSameItemSameComponents(offhandStack, stack)) {
            available += Math.max(0, perSlotCap - offhandStack.getCount());
        }
        return available >= needed;
    }

    // ---- Steps 6-8: confirm, then resolve or (deliberately) escalate/leave unresolved ----

    private static CompletableFuture<BankingWithdrawalResult> confirmWithdrawal(
            ServerPlayer player, UUID operationPublicId, UUID bankItemPublicId
    ) {
        MinecraftServer server = player.server;
        CompletableFuture<BankingWithdrawalResult> result = new CompletableFuture<>();

        final CompletableFuture<BankingConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(player.getUUID(), operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously for operation {}", operationPublicId, synchronousFailure);
            // The item is already inserted and the receipt is already written -- left
            // unresolved by design, exactly like any other confirm-stage transport failure.
            return CompletableFuture.completedFuture(
                    new BankingWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, "synchronous_submission_failure")
            );
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                // Step 8: the receipt remains unresolved by design -- scanUnresolved() on a
                // later startup is what catches this, not any local auto-recovery here.
                result.complete(new BankingWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            ServerLevel level = player.serverLevel();
            switch (confirmResult) {
                case BankingConfirmResult.Confirmed ignored -> {
                    BankTransferReceipts.resolve(level, operationPublicId);
                    result.complete(new BankingWithdrawalResult.Confirmed(operationPublicId, bankItemPublicId));
                }
                case BankingConfirmResult.ReconciliationRequired ignored -> {
                    // Step 6/7: the item is already, physically, in the player's inventory --
                    // it must never be reclaimed. Escalate (not resolve) the receipt, exactly
                    // mirroring deposit's own handling of this same Rails response.
                    BankTransferReceiptStore.EscalateOutcome escalateOutcome =
                            BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
                    if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
                        LOGGER.error(
                                "banking withdrawal receipt for operation {} could not be escalated to "
                                        + "reconciliation_required: receipt store is read-only (unsupported future schema)",
                                operationPublicId
                        );
                    }
                    result.complete(new BankingWithdrawalResult.ReconciliationRequired(operationPublicId));
                }
                case BankingConfirmResult.Rejected rejected -> result.complete(
                        new BankingWithdrawalResult.Rejected(BankingWithdrawalStage.CONFIRM, rejected.outcome(), rejected.retryable())
                );
                case BankingConfirmResult.TransportFailure failure -> result.complete(
                        new BankingWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, failure.safeCode())
                );
                case BankingConfirmResult.LocalFailure failure -> result.complete(
                        new BankingWithdrawalResult.TransportFailure(BankingWithdrawalStage.CONFIRM, failure.safeCode())
                );
            }
        }));
        return result;
    }

    /**
     * Result of protocol steps 1-5 alone (see {@link #prepareAndInsertForTesting}) -- a sealed
     * hierarchy distinct from {@link BankingWithdrawalResult} because {@link Inserted} has no
     * equivalent there (by the time a full sequence produces a {@link BankingWithdrawalResult},
     * insertion has always either failed cleanly or been followed all the way through confirm).
     */
    public sealed interface PrepareAndInsertOutcome permits
            PrepareAndInsertOutcome.Inserted,
            PrepareAndInsertOutcome.Aborted,
            PrepareAndInsertOutcome.Rejected,
            PrepareAndInsertOutcome.TransportFailure,
            PrepareAndInsertOutcome.LocalFailure {

        /** The item was inserted and the durable receipt was written. Confirm was NOT attempted. */
        record Inserted(UUID operationPublicId, UUID bankItemPublicId) implements PrepareAndInsertOutcome {
        }

        record Aborted(UUID operationPublicId, BankingWithdrawalAbortReason reason) implements PrepareAndInsertOutcome {
        }

        record Rejected(BankingTransferOutcome outcome, boolean retryable) implements PrepareAndInsertOutcome {
        }

        record TransportFailure(BankingWithdrawalStage stage, String safeCode) implements PrepareAndInsertOutcome {
        }

        record LocalFailure(String safeCode) implements PrepareAndInsertOutcome {
        }
    }
}

package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemEligibility;
import com.seggellion.britannia_mod.bank.item.BankItemEnvelopeVersion;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.item.BankItemIdentity;
import com.seggellion.britannia_mod.bank.item.BankItemWeight;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.HolderLookup;
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
 * Milestone 9 NeoForge Slice 1: the deposit path -- exact-slot capture, revalidation, removal,
 * durable receipt sequencing, and confirmation. Built directly on {@link BankingProxyService}'s
 * established pattern (resolve fresh, dedupe in-flight attempts, dispatch off the main thread
 * via the {@code ServerHttpExecutor}-backed client, marshal every completion back onto the main
 * thread via {@code server.execute} before touching player/level state, never leak an in-flight
 * marker on a synchronous submission failure) -- see that class and {@link BankingDepositClient}
 * for the primitives this extends rather than re-derives.
 *
 * <p><b>No live trigger exists yet.</b> This slice is exercised via {@link
 * #triggerDepositForTesting} and {@link #prepareAndRemoveForTesting} only -- neither is wired
 * to a real screen, packet, or entity interaction. That wiring is Slice 3's job, once
 * withdrawal (Slice 2) also exists and the real Bank Screen has both actions to offer.
 *
 * <h2>Protocol sequencing (docs/banking_item_transfer.md; Section A.6)</h2>
 * <ol>
 *   <li>Capture the source slot's exact fingerprint/payload/weight at selection time.</li>
 *   <li>Reject locally (eligibility, oversized payload, excessive nesting) before any network
 *       call -- fail fast rather than waste a round trip on something already known-invalid.</li>
 *   <li>Call Rails deposit/prepare.</li>
 *   <li>On success, revalidate the LIVE slot against the originally captured fingerprint --
 *       {@link BankItemFingerprint}, full identity, not the count-only check {@code
 *       ServerEconomyService.reserveItems} is documented to lack. A mismatch (different item,
 *       different count, different NBT, or now-empty) calls Cancel (safe -- nothing physical
 *       has happened yet) and reports {@link BankingDepositResult.RemovalFailed}. No receipt is
 *       written on this path.</li>
 *   <li>On a match: remove the exact stack, <b>then immediately</b> write the durable local
 *       receipt (synchronous flush). This order is deliberate, not incidental -- see {@link
 *       #revalidateAndRemove} for the restated reasoning.</li>
 *   <li>Call Rails confirm. On {@code CONFIRMED}: resolve the local receipt.</li>
 *   <li>On {@code RECONCILIATION_REQUIRED}: never treat this as ordinary success -- the item is
 *       not returned to the player (Rails now holds it ambiguously), and the local receipt is
 *       deliberately left unresolved (see {@link #confirmDeposit}) rather than resolved, so a
 *       future scan still finds it. Reported via its own distinct {@link
 *       BankingDepositResult.ReconciliationRequired} case, never folded into ordinary
 *       success/failure.</li>
 *   <li>On any other confirm failure/timeout: the receipt remains unresolved by design -- this
 *       is exactly what {@code BankTransferReceiptStore#scanUnresolved} exists to catch on a
 *       later startup. No local auto-recovery is attempted here.</li>
 * </ol>
 */
public final class BankingDepositProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Mirrors Rails' {@code PayloadValidator::MAX_PAYLOAD_BYTES} exactly
     * (docs/banking_item_transfer.md) -- rejecting an oversized payload locally, before ever
     * dispatching it, is strictly an optimization (Rails would reject it too), not a new rule.
     */
    static final int MAX_PAYLOAD_BYTES = 262_144;

    private static BankingDepositClientPort client = new BankingDepositClient();

    /**
     * Deposit attempts currently in flight, keyed by (player, slot) -- not by player alone
     * (unlike {@link BankingProxyService}'s own {@code IN_FLIGHT}, which only ever has one
     * bank.open in flight per player to worry about). Two concurrent deposit attempts from the
     * same player for two different slots are legitimately independent and must not collide;
     * only a second attempt for the exact same slot while the first is still in flight is a
     * true duplicate. Held for the entire sequence (prepare through confirm settling), not just
     * the prepare call -- unlike bank.open, this is not merely a read; a rapid duplicate trigger
     * mid-sequence would otherwise race the same physical item.
     */
    private static final Set<SlotKey> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingDepositProxyService() {
    }

    public static void useClientForTesting(BankingDepositClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingDepositClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID playerId, int slotIndex) {
        return IN_FLIGHT.contains(new SlotKey(playerId, slotIndex));
    }

    /**
     * The full deposit sequence, steps 1-8 -- the one and only production entry point (Slice
     * 3a), reached from a real {@code BankDepositRequestC2SPayload} via {@link
     * BankingTransferPacketService}. Must be called from the main server thread, matching every
     * other live-inventory-touching entry point in this mod.
     */
    public static CompletableFuture<BankingDepositResult> triggerDeposit(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        SlotKey key = new SlotKey(player.getUUID(), slotIndex);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(new BankingDepositResult.LocalFailure("deposit_already_in_flight"));
        }
        return prepareAndRemoveInternal(player, teller, slotIndex)
                .thenCompose(outcome -> continueToConfirm(player, outcome))
                .whenComplete((result, error) -> IN_FLIGHT.remove(key));
    }

    /**
     * Test-support alias for {@link #triggerDeposit} -- kept so every existing test written
     * against this name keeps compiling and exercising the exact same real logic, not a
     * parallel copy of it.
     */
    public static CompletableFuture<BankingDepositResult> triggerDepositForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        return triggerDeposit(player, teller, slotIndex);
    }

    /**
     * Runs protocol steps 1-5 only (prepare, revalidate, remove, write receipt) and
     * deliberately stops -- never calls confirm. This is the "simulate a crash between removal
     * and confirmation" test hook: a real crash at that point would not run confirm either, and
     * would not clean up any in-memory state (including {@link #IN_FLIGHT}) -- so unlike {@link
     * #triggerDepositForTesting}, a successful {@link PrepareAndRemoveOutcome.Removed} leaves
     * this slot's entry in {@link #IN_FLIGHT} rather than clearing it, matching that reality.
     * Call {@link #resetInFlightTrackingForTesting()} afterward to simulate the process
     * restarting. Any other outcome (nothing physical happened) clears its own entry
     * immediately, the same as the full sequence always does.
     */
    public static CompletableFuture<PrepareAndRemoveOutcome> prepareAndRemoveForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        SlotKey key = new SlotKey(player.getUUID(), slotIndex);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(new PrepareAndRemoveOutcome.LocalFailure("deposit_already_in_flight"));
        }
        return prepareAndRemoveInternal(player, teller, slotIndex).whenComplete((outcome, error) -> {
            if (error != null || !(outcome instanceof PrepareAndRemoveOutcome.Removed)) {
                IN_FLIGHT.remove(key);
            }
        });
    }

    /**
     * Runs protocol steps 6-8 (confirm, then resolve/leave-unresolved) given an operation that
     * has already been prepared and had its item removed -- the counterpart to {@link
     * #prepareAndRemoveForTesting}, for a test that wants to drive confirm separately (for
     * example, resuming after a simulated crash). Does not touch {@link #IN_FLIGHT} itself;
     * callers driving a real crash-recovery scenario are expected to have already called {@link
     * #resetInFlightTrackingForTesting()} to simulate the restart.
     */
    public static CompletableFuture<BankingDepositResult> confirmDepositForTesting(
            ServerPlayer player, UUID operationPublicId, UUID bankItemPublicId
    ) {
        return confirmDeposit(player.server, player.getUUID(), operationPublicId, bankItemPublicId);
    }

    /**
     * Milestone 9 NeoForge Slice 3b: the startup-reconciliation entry point for a receipt still
     * {@code PENDING_LOCAL_ACTION} -- resuming steps 6-8 for an operation whose risky physical
     * action (removal) already happened, with no live {@code ServerPlayer} on hand (the player
     * may not even be online at startup). This is the exact same {@link #confirmDeposit} core
     * every live confirm already runs through -- not a reimplementation -- parameterized by
     * {@code playerUuid} (now carried on the receipt itself) instead of extracted from a
     * {@code ServerPlayer}, and by {@code server} directly instead of {@code player.server}.
     */
    public static CompletableFuture<BankingDepositResult> resumeConfirmDeposit(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId, UUID bankItemPublicId
    ) {
        return confirmDeposit(server, playerUuid, operationPublicId, bankItemPublicId);
    }

    private static CompletableFuture<BankingDepositResult> continueToConfirm(ServerPlayer player, PrepareAndRemoveOutcome outcome) {
        return switch (outcome) {
            case PrepareAndRemoveOutcome.Removed removed ->
                    confirmDeposit(player.server, player.getUUID(), removed.operationPublicId(), removed.bankItemPublicId());
            case PrepareAndRemoveOutcome.RemovalFailed removalFailed ->
                    CompletableFuture.completedFuture(new BankingDepositResult.RemovalFailed(removalFailed.operationPublicId()));
            case PrepareAndRemoveOutcome.RejectedLocally rejectedLocally ->
                    CompletableFuture.completedFuture(new BankingDepositResult.RejectedLocally(rejectedLocally.reason()));
            case PrepareAndRemoveOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                    new BankingDepositResult.Rejected(BankingDepositStage.PREPARE, rejected.outcome(), rejected.retryable())
            );
            case PrepareAndRemoveOutcome.TransportFailure failure -> CompletableFuture.completedFuture(
                    new BankingDepositResult.TransportFailure(failure.stage(), failure.safeCode())
            );
            case PrepareAndRemoveOutcome.LocalFailure failure ->
                    CompletableFuture.completedFuture(new BankingDepositResult.LocalFailure(failure.safeCode()));
        };
    }

    // ---- Steps 1-5: capture, local rejection, prepare, revalidate, remove + write receipt ----

    private static CompletableFuture<PrepareAndRemoveOutcome> prepareAndRemoveInternal(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PrepareAndRemoveOutcome.LocalFailure("teller_not_resolved"));
        }

        ItemStack live = player.getInventory().getItem(slotIndex);
        HolderLookup.Provider registries = player.registryAccess();

        LocalCapture capture;
        try {
            capture = captureAndValidateLocally(live, registries);
        } catch (LocalRejection rejection) {
            return CompletableFuture.completedFuture(new PrepareAndRemoveOutcome.RejectedLocally(rejection.reason()));
        }

        MinecraftServer server = player.server;
        // Milestone 17. Two separate versions, deliberately: the payload carries
        // BankItemSchemaVersion inside itself (unchanged -- the payload format did not change),
        // while the envelope around it carries BankItemEnvelopeVersion, which is what moves to 2
        // when this build is configured to send identity. See BankItemEnvelopeVersion for why
        // collapsing these back into one constant would strand items on older clients.
        //
        // Identity comes from capture.snapshot() -- the exact stack the payload, fingerprint and
        // weight were all computed from -- so count cannot disagree with what was weighed.
        BankItemIdentity identity = BankItemEnvelopeVersion.emitsIdentity()
                ? BankItemIdentity.resolve(capture.snapshot())
                : BankItemIdentity.EMPTY;
        // Envelope v3: link a deposited cheque to its own row so it can be cashed from the vault
        // later. Read from the same capture snapshot everything else was computed from, so the
        // link cannot name a cheque other than the one actually being stored.
        UUID chequePublicId = BankItemEnvelopeVersion.emitsChequeLink()
                ? resolveChequeLink(capture.snapshot())
                : null;
        BankingDepositPrepareRequest prepareRequest = new BankingDepositPrepareRequest(
                player.getUUID(), resolved.worldNpcPublicId(), UUID.randomUUID().toString(),
                BankItemEnvelopeVersion.emitted(), capture.payload(), capture.fingerprint(), capture.weight(),
                identity, chequePublicId
        );

        final CompletableFuture<BankingDepositPrepareResult> prepareFuture;
        try {
            prepareFuture = client.prepare(server, prepareRequest);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/deposit/prepare submission threw synchronously", synchronousFailure);
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
                case BankingDepositPrepareResult.Rejected rejected ->
                        outcome.complete(new PrepareAndRemoveOutcome.Rejected(rejected.outcome(), rejected.retryable()));
                case BankingDepositPrepareResult.TransportFailure failure ->
                        outcome.complete(new PrepareAndRemoveOutcome.TransportFailure(BankingDepositStage.PREPARE, failure.safeCode()));
                case BankingDepositPrepareResult.LocalFailure failure ->
                        outcome.complete(new PrepareAndRemoveOutcome.LocalFailure(failure.safeCode()));
                case BankingDepositPrepareResult.Success success ->
                        revalidateAndRemove(server, player, teller, slotIndex, capture, success, outcome);
            }
        }));
        return outcome;
    }

    /**
     * Step 4 (revalidate) and step 5 (remove, then write receipt), always running on the main
     * server thread (marshaled there by the caller). The removal-then-receipt ordering is
     * deliberate: a crash occurring after removal but before the receipt is durably written
     * would be silently unrecoverable on both sides (Rails' own deposit-expiry-with-no-confirm
     * path is a clean dead end -- the item is simply lost from its bookkeeping, per Rails
     * Slice 1's documented deposit/withdrawal expiry asymmetry) -- the receipt existing before
     * any further risk is what makes that scenario recoverable at all. Writing it any earlier
     * (before removal is certain) would risk a receipt for an item that was never actually
     * taken; any later than immediately after removal reopens exactly the unrecoverable window
     * this ordering exists to close.
     *
     * <p>Milestone 14 priority 2 (context enforcement, dimension 5): re-resolves the teller
     * via {@link BankingProxyService#resolve} as part of the same revalidation this method
     * already performs on the live slot -- the prepare HTTP round trip is exactly the window
     * where the player could have walked out of range or the teller could have been
     * discarded/reassigned, mirroring {@link BankingProxyService#handle}'s own second
     * resolve() immediately before its mutating action. A failed teller re-resolve is folded
     * into the exact same "revalidation failed, cancel the already-prepared Rails operation"
     * path the fingerprint mismatch below already uses, not a separate bare local failure --
     * a real Rails-side PREPARED operation exists by this point and must be actively
     * cancelled, not left to expire on its own.
     */
    private static void revalidateAndRemove(
            MinecraftServer server, ServerPlayer player, ServiceNpcEntity teller, int slotIndex, LocalCapture capture,
            BankingDepositPrepareResult.Success success, CompletableFuture<PrepareAndRemoveOutcome> outcome
    ) {
        boolean tellerStillValid = BankingProxyService.resolve(player, teller) != null;
        ItemStack live = player.getInventory().getItem(slotIndex);
        boolean matches = tellerStillValid && !live.isEmpty()
                && capture.fingerprint().equals(BankItemFingerprint.fingerprint(live, player.registryAccess()));

        if (!matches) {
            String cancelReason = tellerStillValid ? "removal_revalidation_failed" : "teller_no_longer_valid";
            if (!tellerStillValid) {
                LOGGER.info("Discarding banking/deposit result: teller is no longer live/in range for {}", player.getUUID());
            }
            final CompletableFuture<BankingCancelResult> cancelFuture;
            try {
                cancelFuture = client.cancel(
                        server, BankingOperationRequest.cancel(player.getUUID(), success.operationPublicId(), cancelReason)
                );
            } catch (RuntimeException synchronousFailure) {
                LOGGER.warn("banking/cancel submission threw synchronously after a removal-revalidation mismatch", synchronousFailure);
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
                return;
            }
            cancelFuture.whenComplete((cancelResult, cancelError) -> {
                if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                    LOGGER.warn(
                            "banking/cancel after a removal-revalidation mismatch did not cleanly confirm: operation={} result={} error={}",
                            success.operationPublicId(), cancelResult, cancelError
                    );
                }
                // The overall result is REMOVAL_FAILED regardless of whether Rails' own cleanup
                // succeeded -- from this process's perspective nothing physical ever happened,
                // and Cancel is Rails' own idempotent, safely-retryable-later operation, not
                // something this player-facing result should block on.
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
            });
            return;
        }

        int count = capture.snapshot().getCount();
        live.shrink(count);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        // Forces this player's own removal durably to disk before the receipt is written --
        // see BankTransferPlayerDurability's own docs for why this ordering (not the reverse)
        // is what keeps a crash in the remaining gap a non-duplicating loss, never a
        // duplication.
        if (!BankTransferPlayerDurability.forceSave(player)) {
            // A detected failure here means the removal is still only in-memory -- nothing
            // durable or Rails-facing exists yet, so this is the last point where undoing it is
            // free. Restore the item and cancel the prepared operation exactly like the
            // removal-revalidation-mismatch branch above, rather than proceed into the
            // duplication window this whole fix exists to close. See BankTransferPlayerDurability
            // for the full policy reasoning.
            live.grow(count);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();

            final CompletableFuture<BankingCancelResult> cancelFuture;
            try {
                cancelFuture = client.cancel(
                        server, BankingOperationRequest.cancel(player.getUUID(), success.operationPublicId(), "player_save_failed")
                );
            } catch (RuntimeException synchronousFailure) {
                LOGGER.warn("banking/cancel submission threw synchronously after a forced-save failure", synchronousFailure);
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
                return;
            }
            cancelFuture.whenComplete((cancelResult, cancelError) -> {
                if (cancelError != null || !(cancelResult instanceof BankingCancelResult.Cancelled)) {
                    LOGGER.warn(
                            "banking/cancel after a forced-save failure did not cleanly confirm: operation={} result={} error={}",
                            success.operationPublicId(), cancelResult, cancelError
                    );
                }
                // Reported regardless of whether Rails' own cleanup succeeded -- nothing
                // physical happened here (the removal was restored above), and Cancel is
                // Rails' own idempotent, retryable operation.
                outcome.complete(new PrepareAndRemoveOutcome.RemovalFailed(success.operationPublicId()));
            });
            return;
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, success.operationPublicId(), player.getUUID(), BankTransferOperationType.DEPOSIT, capture.payload(), null,
                success.bankItemPublicId(), System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking deposit receipt could not be recorded for operation {}: receipt store is read-only (unsupported future schema)",
                    success.operationPublicId()
            );
        }

        outcome.complete(new PrepareAndRemoveOutcome.Removed(success.operationPublicId(), success.bankItemPublicId()));
    }

    // ---- Steps 6-8: confirm, then resolve or (deliberately) leave unresolved ----

    private static CompletableFuture<BankingDepositResult> confirmDeposit(
            MinecraftServer server, UUID playerUuid, UUID operationPublicId, UUID bankItemPublicId
    ) {
        CompletableFuture<BankingDepositResult> result = new CompletableFuture<>();

        final CompletableFuture<BankingConfirmResult> confirmFuture;
        try {
            confirmFuture = client.confirm(server, BankingOperationRequest.confirm(playerUuid, operationPublicId));
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/confirm submission threw synchronously for operation {}", operationPublicId, synchronousFailure);
            // The item is already removed and the receipt is already written -- left
            // unresolved by design, exactly like any other confirm-stage transport failure.
            return CompletableFuture.completedFuture(
                    new BankingDepositResult.TransportFailure(BankingDepositStage.CONFIRM, "synchronous_submission_failure")
            );
        }

        confirmFuture.whenComplete((confirmResult, confirmError) -> server.execute(() -> {
            if (confirmError != null || confirmResult == null) {
                // Step 8: the receipt remains unresolved by design -- scanUnresolved() on a
                // later startup is what catches this, not any local auto-recovery here.
                result.complete(new BankingDepositResult.TransportFailure(BankingDepositStage.CONFIRM, "unexpected_client_error"));
                return;
            }
            ServerLevel level = server.overworld();
            switch (confirmResult) {
                case BankingConfirmResult.Confirmed ignored -> {
                    BankTransferReceipts.resolve(level, operationPublicId);
                    result.complete(new BankingDepositResult.Confirmed(operationPublicId, bankItemPublicId));
                }
                case BankingConfirmResult.ReconciliationRequired ignored -> {
                    // Step 7: never resolve the receipt here -- resolving would hide exactly the
                    // evidence this system exists to preserve. Instead, a real, persisted
                    // transition to RECONCILIATION_REQUIRED (not just leaving it as an ordinary
                    // pending entry): Minecraft's own local record that it removed this item
                    // still correctly reflects reality (removal did happen), but Rails has
                    // independently decided this operation can never be auto-resolved, and a
                    // future scanUnresolved() must be able to tell this apart from an ordinary
                    // still-in-flight retry candidate, not lump both together. The outcome is
                    // not otherwise branched on here: NOT_FOUND/ALREADY_ESCALATED are both
                    // harmless (the receipt was already escalated by an earlier attempt, or was
                    // never written in the first place -- neither changes what this result
                    // reports to the caller), and READ_ONLY_SCHEMA is logged, not swallowed,
                    // since it means this escalation could not be durably recorded at all.
                    BankTransferReceiptStore.EscalateOutcome escalateOutcome =
                            BankTransferReceipts.escalateToReconciliationRequired(level, operationPublicId);
                    if (escalateOutcome == BankTransferReceiptStore.EscalateOutcome.READ_ONLY_SCHEMA) {
                        LOGGER.error(
                                "banking deposit receipt for operation {} could not be escalated to "
                                        + "reconciliation_required: receipt store is read-only (unsupported future schema)",
                                operationPublicId
                        );
                    }
                    result.complete(new BankingDepositResult.ReconciliationRequired(operationPublicId));
                }
                case BankingConfirmResult.Rejected rejected -> result.complete(
                        new BankingDepositResult.Rejected(BankingDepositStage.CONFIRM, rejected.outcome(), rejected.retryable())
                );
                case BankingConfirmResult.TransportFailure failure -> result.complete(
                        new BankingDepositResult.TransportFailure(BankingDepositStage.CONFIRM, failure.safeCode())
                );
                case BankingConfirmResult.LocalFailure failure -> result.complete(
                        new BankingDepositResult.TransportFailure(BankingDepositStage.CONFIRM, failure.safeCode())
                );
            }
        }));
        return result;
    }

    // ---- Local capture and rejection (steps 1-2) ----

    private static LocalCapture captureAndValidateLocally(ItemStack live, HolderLookup.Provider registries) throws LocalRejection {
        if (live.isEmpty()) {
            throw new LocalRejection(BankingDepositLocalRejectionReason.EMPTY_SLOT);
        }

        try {
            BankItemEligibility.checkEligible(live);
        } catch (BankItemEligibility.IneligibleItemException ineligible) {
            throw new LocalRejection(mapIneligibilityReason(ineligible.reason()));
        }

        ItemStack snapshot = live.copy();
        byte[] payload;
        try {
            payload = BankItemCodec.serialize(snapshot, registries);
        } catch (IllegalArgumentException nestedTooDeep) {
            // Empty-stack is already screened above, so the only remaining IllegalArgumentException
            // BankItemCodec.serialize can throw here is the nesting-depth guard.
            throw new LocalRejection(BankingDepositLocalRejectionReason.NESTING_TOO_DEEP);
        }
        if (BankItemCodec.measurePayloadSize(payload) > MAX_PAYLOAD_BYTES) {
            throw new LocalRejection(BankingDepositLocalRejectionReason.PAYLOAD_TOO_LARGE);
        }

        String fingerprint = BankItemFingerprint.fingerprint(snapshot, registries);
        double weight = BankItemWeight.resolve(snapshot);
        return new LocalCapture(snapshot, payload, fingerprint, weight);
    }

    /**
     * The cheque id carried by {@code stack}, or {@code null} if it is not a cheque or carries no
     * readable cheque data. Never throws and never rejects a deposit: a cheque whose component is
     * missing or malformed still stores perfectly well as an ordinary item -- it simply cannot be
     * cashed from the vault afterwards, which is exactly the legacy-row story.
     */
    @Nullable
    private static UUID resolveChequeLink(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != ItemRegistry.BANK_CHEQUE.get()) {
            return null;
        }
        BankChequeData data = stack.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
        return data == null ? null : data.chequeId();
    }

    private static BankingDepositLocalRejectionReason mapIneligibilityReason(BankItemEligibility.IneligibilityReason reason) {
        return switch (reason) {
            case CURRENCY_MUST_USE_BALANCE_PROTOCOL -> BankingDepositLocalRejectionReason.CURRENCY;
            case QUEST_BOUND -> BankingDepositLocalRejectionReason.QUEST_BOUND;
            case UNSUPPORTED_ORIGIN -> BankingDepositLocalRejectionReason.UNSUPPORTED_ORIGIN;
        };
    }

    private record SlotKey(UUID playerId, int slotIndex) {
    }

    private record LocalCapture(ItemStack snapshot, byte[] payload, String fingerprint, double weight) {
    }

    private static final class LocalRejection extends Exception {
        private final BankingDepositLocalRejectionReason reason;

        private LocalRejection(BankingDepositLocalRejectionReason reason) {
            this.reason = reason;
        }

        private BankingDepositLocalRejectionReason reason() {
            return reason;
        }
    }

    /**
     * Result of protocol steps 1-5 alone (see {@link #prepareAndRemoveForTesting}) -- a sealed
     * hierarchy distinct from {@link BankingDepositResult} because {@link Removed} has no
     * equivalent there (by the time a full sequence produces a {@link BankingDepositResult},
     * removal has always either failed cleanly or been followed all the way through confirm).
     */
    public sealed interface PrepareAndRemoveOutcome permits
            PrepareAndRemoveOutcome.Removed,
            PrepareAndRemoveOutcome.RemovalFailed,
            PrepareAndRemoveOutcome.RejectedLocally,
            PrepareAndRemoveOutcome.Rejected,
            PrepareAndRemoveOutcome.TransportFailure,
            PrepareAndRemoveOutcome.LocalFailure {

        /** The item was removed and the durable receipt was written. Confirm was NOT attempted. */
        record Removed(UUID operationPublicId, UUID bankItemPublicId) implements PrepareAndRemoveOutcome {
        }

        record RemovalFailed(UUID operationPublicId) implements PrepareAndRemoveOutcome {
        }

        record RejectedLocally(BankingDepositLocalRejectionReason reason) implements PrepareAndRemoveOutcome {
        }

        record Rejected(BankingTransferOutcome outcome, boolean retryable) implements PrepareAndRemoveOutcome {
        }

        record TransportFailure(BankingDepositStage stage, String safeCode) implements PrepareAndRemoveOutcome {
        }

        record LocalFailure(String safeCode) implements PrepareAndRemoveOutcome {
        }
    }
}

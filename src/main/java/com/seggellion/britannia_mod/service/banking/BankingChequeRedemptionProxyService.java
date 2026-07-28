package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 11 NeoForge Slice 2: bank cheque redemption -- selection revalidation, physical
 * removal, durable receipt sequencing, and dispatch to Rails' single, atomic {@code
 * POST /api/banking/cheque/redeem} (docs/banking_bank_cheque_redemption.md).
 *
 * <h2>Deposit-shaped, per ADR-016 -- {@link BankingCurrencyDepositProxyService} is the real
 * structural precedent, not {@link BankingWithdrawalProxyService}</h2>
 * Redemption removes a physical item and credits a balance -- the same shape as a deposit, not
 * a withdrawal (which removes a balance/reservation and inserts a physical item). This class's
 * ordering therefore mirrors {@link BankingCurrencyDepositProxyService} exactly: remove the
 * physical item, force the removal durably to disk, write the durable receipt, THEN dispatch to
 * Rails, then resolve. On a detected forced-save failure, this class aborts and restores the
 * local side -- currency deposit's own policy -- never withdrawal's mandatory-escalate policy,
 * because at that exact point nothing has been dispatched to Rails yet (see {@link
 * BankTransferPlayerDurability}'s own "Deposit (item and currency): a detected failure aborts"
 * policy section, which this flow is a third real caller of, alongside item and currency
 * deposit).
 *
 * <h2>Why "capture" and "revalidate the cheque's identity immediately before consuming it"
 * collapse into one synchronous step here</h2>
 * Every prior deposit-shaped flow's revalidation exists to catch a slot mutation that happened
 * <em>during</em> an async Rails round trip between capture and removal ({@link
 * BankingCurrencyDepositProxyService}'s own "prepare" call creates exactly that window). This
 * flow has no such window: unlike every other action, {@code banking/cheque/redeem} is a single
 * call with no separate prepare/reservation phase to precede removal (see {@link
 * BankingChequeRedemptionResult}'s own docs for why), so removal happens BEFORE the one network
 * call is ever made, not after a round trip. The one, single, synchronous read of the live
 * slot's real {@link BankChequeData} -- performed here immediately before {@code shrink(1)}, on
 * the same uninterrupted main-thread step, with no yield point between them -- is therefore
 * simultaneously the capture AND the revalidation: there is no earlier server-side capture for
 * it to have possibly drifted from. (The CLIENT side still keeps its own, separate, purely
 * cosmetic capture at double-click-select time, for the confirmation status message -- see
 * {@code BankScreen}'s own docs -- but that captured value is never sent to or trusted by the
 * server, exactly like every other selection reference in this mod's trust model.) The display
 * amount ({@link BankChequeData#displayAmount()}) is never read here at all: the credited value
 * is entirely Rails-determined from the cheque's own stored record, and the redeem request
 * itself carries no amount field of any kind (docs/banking_bank_cheque_redemption.md's own
 * request shape) -- there is nothing for this class to pass or trust it for.
 *
 * <h2>Item disposition on a Rails rejection -- a real, considered decision, not uniform
 * "always restore" or "always discard"</h2>
 * By the time ANY Rails rejection (cheque-specific or shared teller/account) is received, the
 * physical cheque item has already been removed -- there is no post-dispatch abort-and-restore
 * path here, the same as every deposit-shaped flow's own confirm-stage rejection handling. What
 * differs is what happens to the durable receipt, split into two genuinely different cases:
 * <ul>
 *   <li>{@code CHEQUE_NOT_FOUND}/{@code CHEQUE_ALREADY_REDEEMED}/{@code CHEQUE_CANCELLED}/
 *       {@code CHEQUE_VOIDED} are DEFINITIVE, authoritative statements from Rails that THIS
 *       specific cheque identity is permanently unredeemable -- for {@code
 *       CHEQUE_ALREADY_REDEEMED} specifically, this proves the physical stack the player held
 *       was a worthless duplicate the instant Rails processed the request (its value, if any,
 *       was already paid out to whichever bearer redeemed first -- see {@link
 *       BankingChequeIssuanceProxyService}'s own docs on a cheque being "merely a pointer to a
 *       centrally value-gated resource rather than the value itself"). The item was already
 *       removed and stays removed (restoring a permanently-dead "zombie" cheque that can never
 *       redeem again would only mislead the player, not preserve any real value), and the
 *       receipt is resolved -- nothing further can or should happen for this operationId.</li>
 *   <li>Every OTHER {@code Rejected} outcome (a teller/account-context failure -- {@code
 *       PLAYER_NOT_FOUND}, {@code TELLER_NOT_ASSIGNED}, ...) says NOTHING about the cheque's
 *       own validity -- only that this particular request's surrounding context could not be
 *       validated, a genuinely different, much rarer race (the teller/account context breaking
 *       between this class's own already-passed local resolve and Rails' independent
 *       re-validation, in the same tick). Discarding the receipt here would be wrong: the
 *       cheque itself might still be perfectly redeemable. The receipt is therefore left
 *       exactly as {@code record()} wrote it (PENDING_LOCAL_ACTION, untouched -- no explicit
 *       resolve call, mirroring {@link BankingCurrencyDepositProxyService}'s own handling of an
 *       (in practice, unreachable) {@code Rejected} confirm outcome), so a resume can safely
 *       retry the exact same idempotent request later.</li>
 * </ul>
 * "Nothing should ever locally trust a rejection" (Codex Prompt 11) is honored by this split,
 * not violated by it: Rails' rejection IS trusted as the authoritative answer for what it
 * actually asserts (the cheque's own state, for the four definitive outcomes) -- what is never
 * done is treating an UNRELATED context failure as if it were also an authoritative statement
 * about the cheque, or crediting anything locally on any outcome whatsoever.
 *
 * <h2>Crash recovery needs no on-login hook, unlike cheque issuance</h2>
 * {@link BankingChequeIssuanceProxyService#resumeConfirmChequeIssuance} needs a live {@code
 * ServerPlayer} on resume because ITS OWN risky step (physical construction and insertion)
 * happens AFTER Rails commits. This flow's physical action (removal) already happened BEFORE
 * the one Rails call, exactly like every other deposit-shaped flow -- resuming redemption is
 * therefore a plain "re-issue the exact same idempotent request, then resolve/leave-pending
 * based on the response" operation needing only the receipt's own persisted UUIDs, no live
 * player, no inventory access at all. See {@link #resumeRedeemCheque} and {@link
 * com.seggellion.britannia_mod.service.banking.BankTransferReconciliationService}'s own {@code
 * CHEQUE_REDEMPTION} branch -- an ordinary {@code resumePending} case, not a
 * {@code PlayerLoggedInEvent} listener the way issuance needed.
 */
public final class BankingChequeRedemptionProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static BankingChequeRedemptionClientPort client = new BankingChequeRedemptionClient();

    /** Keyed by {@code (player, slot)}, mirroring {@link BankingCurrencyDepositProxyService}'s own {@code SlotKey}. */
    private static final Set<SlotKey> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingChequeRedemptionProxyService() {
    }

    public static void useClientForTesting(BankingChequeRedemptionClientPort testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingChequeRedemptionClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    public static boolean isInFlightForTesting(UUID playerId, int slotIndex) {
        return IN_FLIGHT.contains(new SlotKey(playerId, slotIndex));
    }

    /**
     * The full cheque redemption sequence -- the one and only production entry point, reached
     * from a real {@code BankDepositRequestC2SPayload} whose live slot holds a bank cheque, via
     * {@link BankingTransferPacketService}'s own routing. Must be called from the main server
     * thread, matching every other live-inventory-touching entry point in this mod.
     */
    public static CompletableFuture<BankingChequeRedemptionResult> triggerChequeRedemption(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        SlotKey key = new SlotKey(player.getUUID(), slotIndex);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new BankingChequeRedemptionResult.LocalFailure("cheque_redemption_already_in_flight"));
        }
        return removeAndDispatchInternal(player, teller, slotIndex)
                .whenComplete((result, error) -> IN_FLIGHT.remove(key));
    }

    /** Test-support alias for {@link #triggerChequeRedemption}, mirroring every sibling flow's own. */
    public static CompletableFuture<BankingChequeRedemptionResult> triggerChequeRedemptionForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        return triggerChequeRedemption(player, teller, slotIndex);
    }

    /**
     * Runs the capture/revalidate/remove/forceSave/write-receipt step alone and deliberately
     * stops -- never dispatches to Rails. The "simulate a crash between removal and dispatch"
     * test hook, mirroring {@link BankingCurrencyDepositProxyService#prepareAndRemoveForTesting}:
     * a successful {@link CaptureOutcome.Removed} leaves this slot's {@link #IN_FLIGHT} entry in
     * place (a real crash would not clean it up either); any other outcome clears its own entry.
     * Call {@link #resetInFlightTrackingForTesting()} afterward to simulate the restart.
     */
    public static CompletableFuture<CaptureOutcome> captureAndRemoveForTesting(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        SlotKey key = new SlotKey(player.getUUID(), slotIndex);
        if (!IN_FLIGHT.add(key)) {
            return CompletableFuture.completedFuture(
                    new CaptureOutcome.LocalFailure("cheque_redemption_already_in_flight"));
        }
        CaptureOutcome outcome = captureAndRemove(player, teller, slotIndex);
        if (!(outcome instanceof CaptureOutcome.Removed)) {
            IN_FLIGHT.remove(key);
        }
        return CompletableFuture.completedFuture(outcome);
    }

    /**
     * Dispatches the redeem call alone, given a cheque whose physical removal has already
     * happened (a real trigger's own tail, or a resumed one) -- the counterpart to {@link
     * #captureAndRemoveForTesting}, for a test that wants to drive dispatch separately (resuming
     * after a simulated crash, or proving the rejection/item-disposition split in isolation).
     * Does not touch {@link #IN_FLIGHT} itself.
     */
    public static CompletableFuture<BankingChequeRedemptionResult> dispatchRedeemForTesting(
            ServerPlayer player, UUID chequePublicId, UUID worldNpcPublicId
    ) {
        return dispatchRedeem(player.server, player.getUUID(), chequePublicId, worldNpcPublicId);
    }

    /**
     * The startup-reconciliation entry point for a redemption receipt still {@code
     * PENDING_LOCAL_ACTION} -- see this class's own "Crash recovery needs no on-login hook"
     * docs for why this needs no live {@code ServerPlayer}, unlike cheque issuance's own resume.
     * Reached from {@link BankTransferReconciliationService}'s {@code CHEQUE_REDEMPTION} branch.
     */
    public static CompletableFuture<BankingChequeRedemptionResult> resumeRedeemCheque(
            MinecraftServer server, UUID playerUuid, UUID chequePublicId, UUID worldNpcPublicId
    ) {
        return dispatchRedeem(server, playerUuid, chequePublicId, worldNpcPublicId);
    }

    private static CompletableFuture<BankingChequeRedemptionResult> removeAndDispatchInternal(
            ServerPlayer player, ServiceNpcEntity teller, int slotIndex
    ) {
        CaptureOutcome outcome = captureAndRemove(player, teller, slotIndex);
        return switch (outcome) {
            case CaptureOutcome.Removed removed ->
                    dispatchRedeem(player.server, player.getUUID(), removed.chequePublicId(), removed.worldNpcPublicId());
            case CaptureOutcome.RejectedLocally rejectedLocally ->
                    CompletableFuture.completedFuture(new BankingChequeRedemptionResult.RejectedLocally(rejectedLocally.reason()));
            case CaptureOutcome.LocalFailure failure ->
                    CompletableFuture.completedFuture(new BankingChequeRedemptionResult.LocalFailure(failure.safeCode()));
        };
    }

    /**
     * Capture, revalidate, remove, forceSave (abort-and-restore on a detected failure) -- see
     * the class docs for why capture and revalidation are one synchronous step here, and why a
     * forced-save failure aborts (currency deposit's policy) rather than escalates (withdrawal's
     * policy): nothing has been dispatched to Rails yet at this point, so undoing the removal is
     * free. Always runs on the main server thread (the caller's own contract).
     */
    private static CaptureOutcome captureAndRemove(ServerPlayer player, ServiceNpcEntity teller, int slotIndex) {
        BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, teller);
        if (resolved == null) {
            return new CaptureOutcome.LocalFailure("teller_not_resolved");
        }

        ItemStack live = player.getInventory().getItem(slotIndex);
        if (live.isEmpty()) {
            return new CaptureOutcome.RejectedLocally(BankingChequeRedemptionLocalRejectionReason.EMPTY_SLOT);
        }
        if (live.getItem() != ItemRegistry.BANK_CHEQUE.get()) {
            return new CaptureOutcome.RejectedLocally(BankingChequeRedemptionLocalRejectionReason.NOT_A_CHEQUE);
        }
        BankChequeData data = live.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
        if (data == null) {
            return new CaptureOutcome.RejectedLocally(BankingChequeRedemptionLocalRejectionReason.NOT_A_CHEQUE);
        }
        UUID chequePublicId = data.chequeId();
        UUID worldNpcPublicId = resolved.worldNpcPublicId();

        live.shrink(1);
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.broadcastFullState();

        // Forces this player's own removal durably to disk before the receipt is written --
        // see BankTransferPlayerDurability's own docs. Identical placement to item/currency
        // deposit's own equivalent call.
        if (!BankTransferPlayerDurability.forceSave(player)) {
            // Abort-and-restore: nothing dispatched to Rails yet, so this is the last point
            // where undoing the removal is free. See the class docs for why this mirrors
            // deposit's policy, never withdrawal's mandatory-escalate one.
            live.grow(1);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();
            LOGGER.warn(
                    "Forced save failed after removing cheque {} for redemption; the removal was backed out "
                            + "and nothing was dispatched to Rails", chequePublicId
            );
            return new CaptureOutcome.LocalFailure("player_save_failed");
        }

        ServerLevel level = player.serverLevel();
        BankTransferReceiptStore.RecordOutcome recordOutcome = BankTransferReceipts.record(
                level, chequePublicId, player.getUUID(), BankTransferOperationType.CHEQUE_REDEMPTION,
                null, null, null, worldNpcPublicId, System.currentTimeMillis()
        );
        if (recordOutcome == BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            LOGGER.error(
                    "banking cheque redemption receipt could not be recorded for cheque {}: receipt store is read-only (unsupported future schema)",
                    chequePublicId
            );
        }

        return new CaptureOutcome.Removed(chequePublicId, worldNpcPublicId);
    }

    // ---- Dispatch: the one Rails call, then resolve or (deliberately) leave unresolved ----

    private static CompletableFuture<BankingChequeRedemptionResult> dispatchRedeem(
            MinecraftServer server, UUID playerUuid, UUID chequePublicId, UUID worldNpcPublicId
    ) {
        CompletableFuture<BankingChequeRedemptionResult> result = new CompletableFuture<>();
        BankingChequeRedemptionRequest request = new BankingChequeRedemptionRequest(playerUuid, worldNpcPublicId, chequePublicId);

        final CompletableFuture<BankingChequeRedemptionResult> redeemFuture;
        try {
            redeemFuture = client.redeem(server, request);
        } catch (RuntimeException synchronousFailure) {
            LOGGER.warn("banking/cheque/redeem submission threw synchronously for cheque {}", chequePublicId, synchronousFailure);
            // The cheque is already removed and (if this is the live path) the receipt already
            // written -- left unresolved by design, exactly like any other dispatch-stage
            // transport failure; scanUnresolved() on a later startup catches it.
            return CompletableFuture.completedFuture(new BankingChequeRedemptionResult.TransportFailure("synchronous_submission_failure"));
        }

        redeemFuture.whenComplete((redeemResult, redeemError) -> server.execute(() -> {
            if (redeemError != null || redeemResult == null) {
                result.complete(new BankingChequeRedemptionResult.TransportFailure("unexpected_client_error"));
                return;
            }
            switch (redeemResult) {
                case BankingChequeRedemptionResult.Confirmed confirmed -> {
                    BankTransferReceipts.resolve(server.overworld(), chequePublicId);
                    result.complete(confirmed);
                }
                case BankingChequeRedemptionResult.Rejected rejected -> {
                    // See the class docs' "Item disposition on a Rails rejection" section: only
                    // the four definitive cheque-specific outcomes resolve the receipt. Every
                    // other Rejected outcome is deliberately left untouched (still
                    // PENDING_LOCAL_ACTION), matching BankingCurrencyDepositProxyService's own
                    // handling of its (in practice unreachable) confirm-stage Rejected case.
                    if (isDefinitiveChequeRejection(rejected.outcome())) {
                        BankTransferReceipts.resolve(server.overworld(), chequePublicId);
                    }
                    result.complete(rejected);
                }
                case BankingChequeRedemptionResult.TransportFailure failure -> result.complete(failure);
                case BankingChequeRedemptionResult.LocalFailure failure -> result.complete(failure);
                case BankingChequeRedemptionResult.RejectedLocally rejectedLocally ->
                        // Unreachable from a real BankingChequeRedemptionClientPort implementation
                        // (RejectedLocally is only ever produced by this class's own pre-dispatch
                        // checks, never by parsing a Rails response) -- kept only for exhaustiveness.
                        result.complete(rejectedLocally);
            }
        }));
        return result;
    }

    private static boolean isDefinitiveChequeRejection(BankingTransferOutcome outcome) {
        return outcome == BankingTransferOutcome.CHEQUE_NOT_FOUND
                || outcome == BankingTransferOutcome.CHEQUE_ALREADY_REDEEMED
                || outcome == BankingTransferOutcome.CHEQUE_CANCELLED
                || outcome == BankingTransferOutcome.CHEQUE_VOIDED;
    }

    private record SlotKey(UUID playerId, int slotIndex) {
    }

    /**
     * Result of the capture/revalidate/remove/forceSave step alone (see {@link
     * #captureAndRemoveForTesting}) -- a sealed hierarchy distinct from {@link
     * BankingChequeRedemptionResult} because {@link Removed} has no equivalent there, mirroring
     * every sibling deposit-shaped flow's own identical split.
     */
    public sealed interface CaptureOutcome permits
            CaptureOutcome.Removed,
            CaptureOutcome.RejectedLocally,
            CaptureOutcome.LocalFailure {

        /** The cheque was removed and the durable receipt was written. Dispatch was NOT attempted. */
        record Removed(UUID chequePublicId, UUID worldNpcPublicId) implements CaptureOutcome {
        }

        record RejectedLocally(BankingChequeRedemptionLocalRejectionReason reason) implements CaptureOutcome {
        }

        record LocalFailure(String safeCode) implements CaptureOutcome {
        }
    }
}

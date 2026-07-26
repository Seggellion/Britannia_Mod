package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Milestone 9 NeoForge Slice 3b: the startup reconciliation hook, called once from {@code
 * BritanniaMod#onServerStarted} -- the exact same real event {@code
 * ServiceNpcSpawnDeliveryProcessor#start} already uses to kick off its own startup-time work,
 * not an invented convention.
 *
 * <h2>What each {@link BankTransferReceiptStore.ScanResult} bucket gets, precisely</h2>
 * <ul>
 *   <li>{@code pending} -- the physical action (removal or insertion) already happened locally
 *       before some earlier crash, and this process never learned whether Rails' confirm
 *       succeeded. Resumed automatically: {@link BankingDepositProxyService#resumeConfirmDeposit}
 *       / {@link BankingWithdrawalProxyService#resumeConfirmWithdrawal} run the exact same
 *       confirm core the live path already uses (never a reimplementation), which -- entirely
 *       unchanged from the live path -- already does the right thing for every outcome: {@code
 *       CONFIRMED} (whether this is truly the first successful confirm, or an idempotent replay
 *       of one that actually succeeded before the crash -- Rails' own confirm idempotency, M8,
 *       makes both cases indistinguishable and equally safe) resolves the receipt;{@code
 *       RECONCILIATION_REQUIRED} escalates it; a transport failure or timeout leaves it
 *       untouched, still {@code PENDING_LOCAL_ACTION}, for the next startup (or a future retry
 *       mechanism) to find via {@code scanUnresolved()} again -- never escalated just because
 *       this one resume attempt could not reach Rails.</li>
 *   <li>{@code reconciliationRequired} -- already terminal. Never touched: no confirm call, no
 *       resolve, no retry. Only logged clearly so an operator sees it.</li>
 *   <li>{@code unreadable} -- corrupt or future-schema data. Never parsed or acted on. Only
 *       logged distinctly, with whatever partial identifying info ({@code reason}) is safely
 *       available.</li>
 * </ul>
 *
 * <h2>Shares the live path's dedup, not a second key space</h2>
 * A withdrawal resume (verification pass finding, not the original design) registers in {@link
 * BankingWithdrawalProxyService}'s own {@code IN_FLIGHT} set for the receipt's {@code
 * bankItemPublicId} -- the same set, the same key, {@link
 * BankingWithdrawalProxyService#triggerWithdrawal} already uses -- so a live player reconnecting
 * while their own resume is still in flight and re-triggering a withdrawal for that exact item
 * is cleanly deduped locally, not raced against the resume. A deposit resume does not register
 * anything: {@code IN_FLIGHT} there is keyed by {@code (playerId, slotIndex)}, and by the time a
 * deposit reaches confirm the item is already gone from every slot, so there is no slot a live
 * trigger could collide with -- a fresh deposit using that same slot index is a genuinely
 * independent operation on a different item, not a duplicate of the one being resumed.
 *
 * <h2>Never blocks server startup</h2>
 * {@link #runStartupReconciliation} itself only ever does one synchronous, already-in-memory
 * {@code scanUnresolved()} read (the receipt store is loaded {@code SavedData}, not a network
 * call) and, for each pending entry, fires off an async {@code resumeConfirm*} call whose
 * network dispatch is backed by {@code ServerHttpExecutor} (a dedicated pool, never the caller's
 * thread) -- exactly like every other Rails call in this mod. This method returns immediately
 * regardless of how many pending receipts exist or how long Rails takes to answer; nothing here
 * ever calls {@code .join()}/{@code .get()} on the resulting futures.
 */
public final class BankTransferReconciliationService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BankTransferReconciliationService() {
    }

    public static void runStartupReconciliation(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BankTransferReceiptStore.ScanResult scan = BankTransferReceipts.scanUnresolved(level);
        reconcile(server, scan);
    }

    /**
     * Split out from {@link #runStartupReconciliation} specifically so it is directly testable
     * against a manually-constructed {@link BankTransferReceiptStore.ScanResult} rather than the
     * real {@code scanUnresolved()} -- public (not package-private) for the same reason this
     * program's other cross-package test seams are (e.g. {@code BankingProxyService#resolve}):
     * GameTests exercising this class live in the {@code gametest} package, not this one.
     *
     * <p>This matters specifically because {@link BankTransferReceiptStore} is a single,
     * server-wide store shared across up to 50 concurrently-batched GameTests (this class's own
     * docs) -- calling {@link #runStartupReconciliation} directly in a GameTest would scan
     * (and, for any pending entry found, dispatch a real confirm against) <em>every</em>
     * receipt currently in that shared store, including ones another, unrelated, concurrently
     * running test deliberately left pending for its own later assertions. Passing a
     * manually-constructed {@code ScanResult} containing only the receipt(s) a given test itself
     * created sidesteps that entirely: {@code resolve}/{@code escalateToReconciliationRequired}
     * still act on the real store (so a test can verify the real outcome afterward via {@code
     * find(operationId)}), but only ever for the operation id that test actually owns.
     */
    public static void reconcile(MinecraftServer server, BankTransferReceiptStore.ScanResult scan) {
        if (scan.isEmpty()) {
            // The common case: no meaningful delay, no log noise. server is never touched.
            return;
        }

        for (BankTransferReceipt receipt : scan.reconciliationRequired()) {
            LOGGER.warn(
                    "Bank transfer receipt requires manual reconciliation (operator action needed, never auto-resumed): "
                            + "operation_id={} player_uuid={} operation_type={} created_at_epoch_millis={}",
                    receipt.operationId(), receipt.playerUuid(), receipt.operationType(), receipt.createdAtEpochMillis()
            );
        }
        for (BankTransferReceiptStore.UnreadableEntry entry : scan.unreadable()) {
            LOGGER.warn(
                    "Unreadable bank transfer receipt entry found at startup (operator action needed, never parsed or acted on): reason={}",
                    entry.reason()
            );
        }
        for (BankTransferReceipt receipt : scan.pending()) {
            resumePending(server, receipt);
        }
    }

    private static void resumePending(MinecraftServer server, BankTransferReceipt receipt) {
        LOGGER.info(
                "Resuming pending bank transfer confirm at startup: operation_id={} player_uuid={} operation_type={}",
                receipt.operationId(), receipt.playerUuid(), receipt.operationType()
        );
        switch (receipt.operationType()) {
            // Milestone 10: a DEPOSIT receipt is either an item deposit or a currency deposit,
            // discriminated by the receipt's own schema invariant (exactly one of itemPayload
            // or currencyAmount is ever present -- enforced in BankTransferReceipt's
            // constructor since Milestone 9, precisely so this branch would not need a schema
            // change when the currency path arrived). A currency receipt carries no
            // bankItemPublicId at all, which the item resume's Confirmed result would reject
            // by its own non-null contract -- routing on the discriminator, not a new field,
            // keeps every already-written item receipt resuming exactly as before.
            case DEPOSIT -> {
                if (receipt.currencyAmount() != null) {
                    BankingCurrencyDepositProxyService.resumeConfirmCurrencyDeposit(
                            server, receipt.playerUuid(), receipt.operationId()
                    ).whenComplete((result, error) -> logOutcome(receipt.operationId(), result, error));
                } else {
                    BankingDepositProxyService.resumeConfirmDeposit(
                            server, receipt.playerUuid(), receipt.operationId(), receipt.bankItemPublicId()
                    ).whenComplete((result, error) -> logOutcome(receipt.operationId(), result, error));
                }
            }
            case WITHDRAWAL -> BankingWithdrawalProxyService.resumeConfirmWithdrawal(
                    server, receipt.playerUuid(), receipt.operationId(), receipt.bankItemPublicId()
            ).whenComplete((result, error) -> logOutcome(receipt.operationId(), result, error));
        }
    }

    private static void logOutcome(java.util.UUID operationId, Object result, Throwable error) {
        if (error != null) {
            LOGGER.warn(
                    "Startup reconciliation confirm for operation {} did not complete cleanly; receipt remains pending "
                            + "for a future reconciliation attempt", operationId, error
            );
            return;
        }
        LOGGER.info("Startup reconciliation confirm for operation {} completed: {}", operationId, result);
    }
}

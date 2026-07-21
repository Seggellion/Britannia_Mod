package com.seggellion.britannia_mod.bank.transfer;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * The actual durability guarantee for {@link BankTransferReceiptStore} lives here, not on the
 * store itself -- {@code record}/{@code resolve} below are the only two lifecycle entry points
 * this slice needs (Section A.6's "persist a local transfer receipt before ownership is
 * exposed to the player", and "cleanly completed or cancelled before any risk"), and each one
 * forces a real, synchronous, immediate disk write before returning, rather than leaving the
 * write for the next autosave or server-shutdown flush the way an ordinary {@code SavedData}
 * (including this codebase's own {@code ServiceNpcSpawnPendingData}) does.
 *
 * The mechanism, traced against the real NeoForge/vanilla API rather than assumed:
 * {@link net.minecraft.world.level.saveddata.SavedData#save(java.io.File, net.minecraft.core.HolderLookup.Provider)}
 * is a public instance method (not merely an internal detail of the normal autosave cycle) that,
 * when the data is dirty, writes it out via NeoForge's
 * {@code net.neoforged.neoforge.common.IOUtilities#writeNbtCompressed}: a temp file is written
 * in the same directory, {@code FileChannel#force(true)} (a real {@code fsync}) is called on it
 * to push the bytes past the OS page cache to the storage controller, and only then is the temp
 * file atomically renamed onto the real target path ({@code Files.move} with
 * {@code ATOMIC_MOVE}, falling back to a non-atomic replace only if the filesystem does not
 * support atomic rename). This method is invoked, for every currently-cached {@code SavedData}
 * in a level's storage, by {@link net.minecraft.world.level.storage.DimensionDataStorage#save()}
 * -- also public -- which is exactly what the game's own autosave loop and shutdown path already
 * call. This class simply calls that same public method immediately after a mutation, instead
 * of waiting for the next scheduled tick.
 *
 * Precise durability claim, not aspirational: because the write is confirmed {@code fsync}'d
 * past the OS page cache before the atomic rename swaps it into place, a receipt written this
 * way survives both a JVM crash (trivially -- the bytes already left process memory) and an
 * unclean kill of the Minecraft server process (SIGKILL, a forced stop) occurring at any point
 * after this method returns, because the OS itself -- not the JVM -- is holding the durably
 * committed bytes by then, and the atomic rename means a reader never observes a half-written
 * file. What this does NOT guarantee is survival of a genuine hardware power-loss event where
 * the physical storage device's own write cache does not honestly acknowledge the fsync/flush
 * command it was given -- a well-known limitation inherent to any fsync-based durability claim
 * on commodity hardware, not something any JVM-level API can close off.
 */
public final class BankTransferReceipts {
    private BankTransferReceipts() {
    }

    public static BankTransferReceiptStore.RecordOutcome record(
            ServerLevel level,
            UUID operationId,
            BankTransferOperationType operationType,
            byte[] itemPayload,
            Long currencyAmount,
            long nowEpochMillis
    ) {
        BankTransferReceiptStore store = BankTransferReceiptStore.get(level);
        BankTransferReceipt receipt = new BankTransferReceipt(
            operationId, operationType, itemPayload, currencyAmount,
            BankTransferReceiptStatus.PENDING_LOCAL_ACTION, nowEpochMillis
        );
        BankTransferReceiptStore.RecordOutcome outcome = store.record(receipt);
        if (outcome != BankTransferReceiptStore.RecordOutcome.READ_ONLY_SCHEMA) {
            forceSynchronousFlush(level);
        }
        return outcome;
    }

    public static boolean resolve(ServerLevel level, UUID operationId) {
        BankTransferReceiptStore store = BankTransferReceiptStore.get(level);
        boolean resolved = store.resolve(operationId);
        if (resolved) forceSynchronousFlush(level);
        return resolved;
    }

    /**
     * Milestone 9's deposit path calls this on a Rails confirm response of {@code
     * RECONCILIATION_REQUIRED} (Section A.6): a real, persisted transition to {@link
     * BankTransferReceiptStatus#RECONCILIATION_REQUIRED}, not just leaving the receipt as an
     * ordinary pending entry. Flushes synchronously only when an actual transition happened
     * ({@link BankTransferReceiptStore.EscalateOutcome#ESCALATED}), matching {@link #resolve}'s
     * own pattern of only forcing a write when something actually changed.
     */
    public static BankTransferReceiptStore.EscalateOutcome escalateToReconciliationRequired(ServerLevel level, UUID operationId) {
        BankTransferReceiptStore store = BankTransferReceiptStore.get(level);
        BankTransferReceiptStore.EscalateOutcome outcome = store.escalateToReconciliationRequired(operationId);
        if (outcome == BankTransferReceiptStore.EscalateOutcome.ESCALATED) {
            forceSynchronousFlush(level);
        }
        return outcome;
    }

    /**
     * The startup-scan capability: every cleanly-parsed receipt still present when this is
     * called is a candidate needing reconciliation -- the exact "crash after possible
     * insertion" scenario Section A.6 describes -- and every entry that exists but could not be
     * parsed at all is surfaced too, as its own distinct category (see
     * {@link BankTransferReceiptStore.ScanResult}), since an unreadable entry might just as
     * easily be a real in-flight operation. This slice only returns the candidate list; it does
     * not call Rails or resolve anything itself. Wiring this list to an actual Rails
     * reconciliation call is explicitly Milestone 9's job -- no such endpoint exists yet.
     */
    public static BankTransferReceiptStore.ScanResult scanUnresolved(ServerLevel level) {
        return BankTransferReceiptStore.get(level).scanUnresolved();
    }

    private static void forceSynchronousFlush(ServerLevel level) {
        level.getServer().overworld().getDataStorage().save();
    }
}

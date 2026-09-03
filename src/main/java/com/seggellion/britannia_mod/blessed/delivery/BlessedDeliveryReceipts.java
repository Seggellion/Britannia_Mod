package com.seggellion.britannia_mod.blessed.delivery;

import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * The actual durability guarantee for {@link BlessedDeliveryReceiptStore} lives here, not on the
 * store itself. Every mutating entry point below calls the store and then forces a real,
 * synchronous, immediate disk write before returning, rather than leaving the write for the next
 * autosave or the server-shutdown flush the way an ordinary {@code SavedData} does. That
 * distinction is the entire reason this class exists: a receipt that is only {@code setDirty()}d
 * is worth nothing against the exact failure it is meant to survive -- an unclean kill in the
 * window between "Rails authorised this materialization" and "the item is in the player's
 * inventory".
 *
 * <p>The mechanism, traced against the real NeoForge/vanilla API rather than assumed:
 * {@link net.minecraft.world.level.saveddata.SavedData#save(java.io.File, net.minecraft.core.HolderLookup.Provider)}
 * is a public instance method (not merely an internal detail of the normal autosave cycle) that,
 * when the data is dirty, writes it out via NeoForge's
 * {@code net.neoforged.neoforge.common.IOUtilities#writeNbtCompressed}: a temp file is written in
 * the same directory, {@code FileChannel#force(true)} (a real {@code fsync}) is called on it to
 * push the bytes past the OS page cache to the storage controller, and only then is the temp file
 * atomically renamed onto the real target path ({@code Files.move} with {@code ATOMIC_MOVE},
 * falling back to a non-atomic replace only if the filesystem does not support atomic rename).
 * That method is invoked, for every currently-cached {@code SavedData} in a level's storage, by
 * {@link net.minecraft.world.level.storage.DimensionDataStorage#save()} -- also public -- which is
 * exactly what the game's own autosave loop and shutdown path already call. This class simply
 * calls that same public method immediately after a mutation instead of waiting for the next
 * scheduled tick.
 *
 * <p>Precise durability claim, not aspirational: because the write is confirmed {@code fsync}'d
 * past the OS page cache before the atomic rename swaps it into place, a receipt written this way
 * survives both a JVM crash (trivially -- the bytes already left process memory) and an unclean
 * kill of the Minecraft server process (SIGKILL, a forced stop) occurring at any point after the
 * call returns, because the OS itself -- not the JVM -- is holding the durably committed bytes by
 * then, and the atomic rename means a reader never observes a half-written file. What this does
 * NOT guarantee is survival of a genuine hardware power-loss event where the physical storage
 * device's own write cache does not honestly acknowledge the fsync/flush command it was given --
 * a well-known limitation inherent to any fsync-based durability claim on commodity hardware, not
 * something any JVM-level API can close off.
 *
 * <p>Note also what the flush is scoped to: {@code DimensionDataStorage#save()} flushes every
 * dirty {@code SavedData} cached in the overworld's storage, not just this one. That is a
 * deliberate consequence of using the public API rather than reaching into per-file internals,
 * and it is the same trade the banking precedent already accepted.
 *
 * <h2>Ordering is the caller's responsibility, and it is not optional</h2>
 * {@link #recordPendingDelivery} must be called and must return {@link
 * BlessedDeliveryReceiptStore.RecordOutcome#CREATED} <em>before</em> any physical item is
 * created, and {@link #markDelivered} only <em>after</em> it demonstrably exists. Any other
 * outcome from {@code recordPendingDelivery} means the caller must not deliver: see {@link
 * BlessedDeliveryReceiptStore.RecordOutcome} for why each one is a refusal rather than a warning.
 */
public final class BlessedDeliveryReceipts {
    private BlessedDeliveryReceipts() {
    }

    /**
     * Durably records the intent to materialize a blessed item, before the item exists. Flushes
     * synchronously only when something actually changed -- a {@link
     * BlessedDeliveryReceiptStore.RecordOutcome#READ_ONLY_SCHEMA} outcome wrote nothing, so there
     * is nothing to force to disk.
     *
     * <p>{@code IDEMPOTENT_REPLAY} and {@code FINGERPRINT_MISMATCH} likewise wrote nothing and are
     * likewise not flushed; they are still returned to the caller unchanged, because the decision
     * they imply (deliver only on {@code CREATED}, and on a replay only after re-reading the
     * stored status via {@link #find}) belongs to the delivery path, not to this facade.
     */
    public static BlessedDeliveryReceiptStore.RecordOutcome recordPendingDelivery(
            ServerLevel level,
            UUID instanceUuid,
            String deedId,
            String itemId,
            UUID ownerUuid,
            long nowEpochMillis
    ) {
        BlessedDeliveryReceiptStore store = BlessedDeliveryReceiptStore.get(level);
        BlessedDeliveryReceipt receipt = new BlessedDeliveryReceipt(
            instanceUuid, deedId, itemId, ownerUuid,
            BlessedDeliveryReceiptStatus.PENDING_PHYSICAL_DELIVERY, nowEpochMillis
        );
        BlessedDeliveryReceiptStore.RecordOutcome outcome = store.record(receipt);
        if (outcome == BlessedDeliveryReceiptStore.RecordOutcome.CREATED) {
            forceSynchronousFlush(level);
        }
        return outcome;
    }

    /**
     * Durably records that the physical item now exists. Flushes synchronously only on an actual
     * transition ({@link BlessedDeliveryReceiptStore.MarkDeliveredOutcome#MARKED}); an
     * already-delivered replay, an unknown instance, and a read-only store all changed nothing.
     */
    public static BlessedDeliveryReceiptStore.MarkDeliveredOutcome markDelivered(
            ServerLevel level, UUID instanceUuid, long nowEpochMillis
    ) {
        BlessedDeliveryReceiptStore store = BlessedDeliveryReceiptStore.get(level);
        BlessedDeliveryReceiptStore.MarkDeliveredOutcome outcome = store.markDelivered(instanceUuid, nowEpochMillis);
        if (outcome == BlessedDeliveryReceiptStore.MarkDeliveredOutcome.MARKED) {
            forceSynchronousFlush(level);
        }
        return outcome;
    }

    /** Convenience overload stamping {@link System#currentTimeMillis()}. */
    public static BlessedDeliveryReceiptStore.MarkDeliveredOutcome markDelivered(ServerLevel level, UUID instanceUuid) {
        return markDelivered(level, instanceUuid, System.currentTimeMillis());
    }

    public static Optional<BlessedDeliveryReceipt> find(ServerLevel level, UUID instanceUuid) {
        return BlessedDeliveryReceiptStore.get(level).find(instanceUuid);
    }

    /**
     * The startup/reconciliation candidate list. This milestone only returns it; deciding what to
     * do about a {@code pendingDelivery} entry (establishing whether the physical item actually
     * exists, and re-sending any acknowledgement Rails never received) is a later milestone's job.
     */
    public static BlessedDeliveryReceiptStore.ScanResult scan(ServerLevel level) {
        return BlessedDeliveryReceiptStore.get(level).scan();
    }

    private static void forceSynchronousFlush(ServerLevel level) {
        level.getServer().overworld().getDataStorage().save();
    }
}

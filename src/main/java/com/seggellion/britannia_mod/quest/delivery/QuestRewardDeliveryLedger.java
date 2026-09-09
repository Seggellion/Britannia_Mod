package com.seggellion.britannia_mod.quest.delivery;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The durability guarantee for {@link QuestRewardDeliveryLedgerStore}, exactly as
 * {@link com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipts} provides it for
 * the blessed-item store: every mutating entry point calls the store and then forces a real,
 * synchronous, {@code fsync}'d write through the public
 * {@code DimensionDataStorage#save()} path before returning. A ledger row that is only
 * {@code setDirty()}d is worth nothing against the failure it exists to survive -- an unclean
 * kill between "Rails published this delivery" and "the items are in the inventory".
 *
 * <p>The same honest limits apply: the flush is scoped to every dirty {@code SavedData} in the
 * overworld storage, and a storage device that lies about {@code fsync} is not covered.
 *
 * <p>Ordering is the caller's responsibility: {@link #recordPendingLocal} must return
 * {@code CREATED} before any item is inserted, {@link #transition} to {@code applied} only after
 * the items and the player marker exist. {@link QuestRewardDeliveryService} is that caller.
 */
public final class QuestRewardDeliveryLedger {
    private QuestRewardDeliveryLedger() {}

    public static QuestRewardDeliveryLedgerStore store(MinecraftServer server) {
        return QuestRewardDeliveryLedgerStore.get(server);
    }

    public static Optional<QuestRewardDeliveryLedgerEntry> find(MinecraftServer server, UUID deliveryUuid) {
        return store(server).find(deliveryUuid);
    }

    public static List<QuestRewardDeliveryLedgerEntry> openEntriesFor(MinecraftServer server, UUID playerUuid) {
        return store(server).openEntriesFor(playerUuid);
    }

    /**
     * Durably records a new row -- normally the {@code pending_local} intent to grant, before any
     * item exists; on a marker-only repair, an already-{@code applied} row. Flushes only on
     * {@code CREATED}.
     */
    public static QuestRewardDeliveryLedgerStore.RecordOutcome record(
            MinecraftServer server, QuestRewardDeliveryLedgerEntry entry) {
        QuestRewardDeliveryLedgerStore.RecordOutcome outcome = store(server).record(entry);
        if (outcome == QuestRewardDeliveryLedgerStore.RecordOutcome.CREATED) forceSynchronousFlush(server);
        return outcome;
    }

    /** Applies {@code change} and flushes when the row actually changed. */
    public static Optional<QuestRewardDeliveryLedgerEntry> transition(
            MinecraftServer server, UUID deliveryUuid, UnaryOperator<QuestRewardDeliveryLedgerEntry> change) {
        Optional<QuestRewardDeliveryLedgerEntry> changed = store(server).update(deliveryUuid, change);
        if (changed.isPresent()) forceSynchronousFlush(server);
        return changed;
    }

    /**
     * Applies {@code change} without forcing a flush: for bookkeeping whose loss costs one extra
     * retry, never a duplicate item (attempt counters).
     */
    public static Optional<QuestRewardDeliveryLedgerEntry> note(
            MinecraftServer server, UUID deliveryUuid, UnaryOperator<QuestRewardDeliveryLedgerEntry> change) {
        return store(server).update(deliveryUuid, change);
    }

    public static void forceSynchronousFlush(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }
}

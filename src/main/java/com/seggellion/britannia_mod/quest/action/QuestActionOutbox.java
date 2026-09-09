package com.seggellion.britannia_mod.quest.action;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The durability guarantee for {@link QuestActionOutboxStore}, exactly as
 * {@code QuestRewardDeliveryLedger} provides it for the delivery ledger: every mutating entry
 * point calls the store and then forces a real, synchronous, {@code fsync}'d write through the
 * public {@code DimensionDataStorage#save()} path before returning.
 *
 * <p>Protocol section 2.2 puts one ordering requirement on the caller and this class exists to
 * make it cheap to honour: {@link #enqueue} must have returned before the first HTTP attempt is
 * made. An event that is only {@code setDirty()}d is worth nothing against the failure it exists
 * to survive -- an unclean kill between "the crop was harvested" and "Rails knows".
 *
 * <p>The same honest limits apply: the flush is scoped to every dirty {@code SavedData} in the
 * overworld storage, and a storage device that lies about {@code fsync} is not covered.
 */
public final class QuestActionOutbox {
    private QuestActionOutbox() {}

    public static QuestActionOutboxStore store(MinecraftServer server) {
        return QuestActionOutboxStore.get(server);
    }

    public static List<QuestActionOutboxEntry> all(MinecraftServer server) {
        return store(server).all();
    }

    public static List<QuestActionOutboxEntry> entriesFor(MinecraftServer server, UUID playerUuid) {
        return store(server).entriesFor(playerUuid);
    }

    public static Optional<QuestActionOutboxEntry> find(MinecraftServer server, UUID eventUuid) {
        return store(server).find(eventUuid);
    }

    public static boolean hasOpenEntryFor(MinecraftServer server, UUID playerUuid,
                                          String questStateId, String triggerKey) {
        return store(server).hasOpenEntryFor(playerUuid, questStateId, triggerKey);
    }

    /** Durably records the event. Flushes on {@code CREATED}, before any attempt may be made. */
    public static QuestActionOutboxStore.RecordOutcome enqueue(MinecraftServer server,
                                                               QuestActionOutboxEntry entry) {
        QuestActionOutboxStore.RecordOutcome outcome = store(server).record(entry);
        if (outcome == QuestActionOutboxStore.RecordOutcome.CREATED) forceSynchronousFlush(server);
        return outcome;
    }

    /** Removes a row that reached a terminal result, flushing so a restart cannot resend it. */
    public static boolean remove(MinecraftServer server, UUID eventUuid) {
        boolean removed = store(server).remove(eventUuid);
        if (removed) forceSynchronousFlush(server);
        return removed;
    }

    public static int removeAll(MinecraftServer server) {
        int removed = store(server).removeAll();
        if (removed > 0) forceSynchronousFlush(server);
        return removed;
    }

    /**
     * Applies {@code change} without forcing a flush: attempt counters and next-attempt times,
     * whose loss costs one extra attempt of an event Rails already deduplicates, never a lost one.
     */
    public static Optional<QuestActionOutboxEntry> note(MinecraftServer server, UUID eventUuid,
                                                        UnaryOperator<QuestActionOutboxEntry> change) {
        return store(server).update(eventUuid, change);
    }

    public static void forceSynchronousFlush(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }
}

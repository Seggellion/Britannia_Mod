package com.seggellion.britannia_mod.quest.delivery;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The durable delivery ledger (protocol section 1.8): {@code SavedData}
 * {@value #DATA_NAME} on the overworld storage, modelled on
 * {@link com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceiptStore} -- an explicit
 * {@code SchemaVersion}, per-entry quarantine of a corrupt row (its raw tag preserved and re-saved
 * verbatim), a read-only fallback for a schema from the future, and an overworld anchor so a
 * delivery applied in any dimension is found by the same reconciliation scan.
 *
 * <p>Like that precedent this class performs in-memory mutation and {@code setDirty()} ONLY; the
 * synchronous forced flush lives in {@link QuestRewardDeliveryLedger}.
 *
 * <h2>Retention</h2>
 * An entry is never removed by state transition: an {@code acknowledged} row is what makes a
 * replayed or re-listed delivery recognisable as already granted. The bound is per player, the
 * {@value #MAX_ENTRIES_PER_PLAYER} newest, and pruning only ever takes {@code acknowledged} rows
 * -- an entry whose items are still owed, or whose acknowledgement is still outstanding, is live
 * state and survives the bound (with a warning) rather than being dropped.
 */
public final class QuestRewardDeliveryLedgerStore extends SavedData {
    public static final String DATA_NAME = "britannia_quest_reward_deliveries";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES_PER_PLAYER = 256;
    static final int MAX_COLLECTION_ENTRIES = 65_536;

    private static final String KEY_SCHEMA_VERSION = "SchemaVersion";
    private static final String KEY_ENTRIES = "Entries";
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum RecordOutcome { CREATED, ALREADY_PRESENT, READ_ONLY_SCHEMA }

    /** A row that exists on disk but could not be read; kept verbatim, never silently dropped. */
    public record UnreadableEntry(String reason, CompoundTag rawTag) {
        public UnreadableEntry {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(rawTag, "rawTag");
        }
    }

    private final LinkedHashMap<UUID, QuestRewardDeliveryLedgerEntry> entries = new LinkedHashMap<>();
    private final List<UnreadableEntry> unreadable = new ArrayList<>();
    private final Set<UUID> warnedOverBound = new java.util.HashSet<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;

    public static QuestRewardDeliveryLedgerStore get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static QuestRewardDeliveryLedgerStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(QuestRewardDeliveryLedgerStore::new, QuestRewardDeliveryLedgerStore::load),
            DATA_NAME);
    }

    public static QuestRewardDeliveryLedgerStore load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestRewardDeliveryLedgerStore data = new QuestRewardDeliveryLedgerStore();
        if (!tag.contains(KEY_SCHEMA_VERSION, Tag.TAG_INT)) return data.readOnly(tag, "missing_or_malformed");
        int schema = tag.getInt(KEY_SCHEMA_VERSION);
        if (schema != SCHEMA_VERSION) return data.readOnly(tag, "unsupported schema version " + schema);

        Tag rawEntries = tag.get(KEY_ENTRIES);
        if (!(rawEntries instanceof ListTag list)
            || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return data.readOnly(tag, "malformed_entries_collection");
        }
        if (list.size() > MAX_COLLECTION_ENTRIES) return data.readOnly(tag, "entries_over_limit");
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            try {
                QuestRewardDeliveryLedgerEntry entry = QuestRewardDeliveryLedgerEntry.fromNbt(entryTag);
                if (data.entries.putIfAbsent(entry.deliveryUuid(), entry) != null) {
                    throw new IllegalArgumentException("duplicate DeliveryUuid");
                }
            } catch (RuntimeException exception) {
                data.unreadable.add(new UnreadableEntry("corrupt: " + exception.getMessage(), entryTag.copy()));
                LOGGER.error("Quarantined corrupt quest reward delivery ledger entry at index {}", index, exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readOnlyFutureSchema && futureRoot != null) return futureRoot.copy();
        tag.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        ListTag list = new ListTag();
        entries.values().forEach(entry -> list.add(entry.toNbt()));
        unreadable.forEach(entry -> list.add(entry.rawTag().copy()));
        tag.put(KEY_ENTRIES, list);
        return tag;
    }

    public Optional<QuestRewardDeliveryLedgerEntry> find(UUID deliveryUuid) {
        return Optional.ofNullable(entries.get(deliveryUuid));
    }

    /** Every entry of one player, oldest first. */
    public List<QuestRewardDeliveryLedgerEntry> entriesFor(UUID playerUuid) {
        List<QuestRewardDeliveryLedgerEntry> found = new ArrayList<>();
        for (QuestRewardDeliveryLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) found.add(entry);
        }
        return List.copyOf(found);
    }

    /** The player's entries the reconciler may still have to act on, oldest first. */
    public List<QuestRewardDeliveryLedgerEntry> openEntriesFor(UUID playerUuid) {
        List<QuestRewardDeliveryLedgerEntry> found = new ArrayList<>();
        for (QuestRewardDeliveryLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid) && entry.open()) found.add(entry);
        }
        return List.copyOf(found);
    }

    public List<UnreadableEntry> unreadable() {
        return List.copyOf(unreadable);
    }

    public int size() {
        return entries.size();
    }

    /**
     * Records a new row. Never overwrites: a delivery already in the ledger -- in any state -- is
     * the caller's signal to consult {@link #find} and follow the reconciliation table rather
     * than start again.
     */
    public RecordOutcome record(QuestRewardDeliveryLedgerEntry incoming) {
        Objects.requireNonNull(incoming, "incoming");
        if (readOnlyFutureSchema) return RecordOutcome.READ_ONLY_SCHEMA;
        if (entries.containsKey(incoming.deliveryUuid())) return RecordOutcome.ALREADY_PRESENT;

        pruneAcknowledged(incoming.playerUuid(), MAX_ENTRIES_PER_PLAYER - 1);
        entries.put(incoming.deliveryUuid(), incoming);
        setDirty();
        warnIfOverBound(incoming.playerUuid());
        return RecordOutcome.CREATED;
    }

    /**
     * Replaces one row with the result of {@code change}; empty when the row does not exist, the
     * store is read-only, or the change returned the row unchanged (nothing to flush then).
     */
    public Optional<QuestRewardDeliveryLedgerEntry> update(UUID deliveryUuid,
                                                          UnaryOperator<QuestRewardDeliveryLedgerEntry> change) {
        Objects.requireNonNull(deliveryUuid, "deliveryUuid");
        Objects.requireNonNull(change, "change");
        if (readOnlyFutureSchema) return Optional.empty();
        QuestRewardDeliveryLedgerEntry existing = entries.get(deliveryUuid);
        if (existing == null) return Optional.empty();
        QuestRewardDeliveryLedgerEntry changed = change.apply(existing);
        if (changed == null || !changed.deliveryUuid().equals(deliveryUuid) || changed.equals(existing)) {
            return Optional.empty();
        }
        entries.put(deliveryUuid, changed);
        setDirty();
        return Optional.of(changed);
    }

    /** Test-only: drops a row outright so a test's random delivery never outlives the test. */
    public boolean removeForTesting(UUID deliveryUuid) {
        if (readOnlyFutureSchema) return false;
        boolean removed = entries.remove(deliveryUuid) != null;
        if (removed) setDirty();
        return removed;
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    /**
     * Keeps at most {@code keep} rows for the player by removing the OLDEST {@code acknowledged}
     * rows; live rows are never candidates. Returns how many rows were removed.
     */
    int pruneAcknowledged(UUID playerUuid, int keep) {
        int count = 0;
        for (QuestRewardDeliveryLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) count++;
        }
        int removed = 0;
        if (count <= keep) return 0;
        Iterator<Map.Entry<UUID, QuestRewardDeliveryLedgerEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && count > keep) {
            QuestRewardDeliveryLedgerEntry entry = iterator.next().getValue();
            if (!entry.playerUuid().equals(playerUuid)) continue;
            if (entry.localState() != QuestRewardDeliveryLocalState.ACKNOWLEDGED) continue;
            iterator.remove();
            count--;
            removed++;
        }
        if (removed > 0) setDirty();
        return removed;
    }

    private void warnIfOverBound(UUID playerUuid) {
        int count = 0;
        for (QuestRewardDeliveryLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) count++;
        }
        if (count > MAX_ENTRIES_PER_PLAYER && warnedOverBound.add(playerUuid)) {
            LOGGER.warn("event=quest_delivery_ledger_over_bound player_uuid={} entries={} bound={}",
                playerUuid, count, MAX_ENTRIES_PER_PLAYER);
        }
    }

    private QuestRewardDeliveryLedgerStore readOnly(CompoundTag tag, String reason) {
        readOnlyFutureSchema = true;
        futureRoot = tag.copy();
        entries.clear();
        unreadable.clear();
        if (tag.get(KEY_ENTRIES) instanceof ListTag list && !list.isEmpty()
            && list.getElementType() == Tag.TAG_COMPOUND && list.size() <= MAX_COLLECTION_ENTRIES) {
            for (int index = 0; index < list.size(); index++) {
                unreadable.add(new UnreadableEntry(reason, list.getCompound(index).copy()));
            }
        } else {
            unreadable.add(new UnreadableEntry(reason, tag.copy()));
        }
        LOGGER.error("Quest reward delivery ledger is unsupported ({}); store is read-only", reason);
        return this;
    }
}

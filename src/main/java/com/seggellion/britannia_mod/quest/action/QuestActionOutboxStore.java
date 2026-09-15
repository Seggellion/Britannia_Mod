package com.seggellion.britannia_mod.quest.action;

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
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The durable action-event outbox (protocol section 2.2): {@code SavedData} {@value #DATA_NAME} on
 * the overworld storage, modelled on {@code QuestRewardDeliveryLedgerStore} and through it on
 * {@code BlessedDeliveryReceiptStore} -- an explicit {@code SchemaVersion}, per-entry quarantine of
 * a corrupt row (its raw tag preserved and re-saved verbatim), a read-only fallback for a schema
 * from the future, and an overworld anchor so a farming action performed in any dimension is found
 * by the same sweep.
 *
 * <p>Like those precedents this class performs in-memory mutation and {@code setDirty()} ONLY; the
 * synchronous forced flush lives in {@link QuestActionOutbox}, and section 2.2 requires it to have
 * happened BEFORE the first HTTP attempt.
 *
 * <h2>Retention</h2>
 * Unlike the delivery ledger, a row here is REMOVED on any terminal result: Rails' own
 * {@code (shard_id, event_uuid)} unique row is the duplicate defence, so this store never has to
 * remember a finished event. The bound therefore exists only to survive an outage: at most
 * {@value #MAX_ENTRIES_PER_PLAYER} rows per player, and a new row past the bound drops the OLDEST
 * one and logs {@code event=quest_action_outbox_overflow}.
 */
public final class QuestActionOutboxStore extends SavedData {
    public static final String DATA_NAME = "britannia_quest_action_outbox";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES_PER_PLAYER = 64;
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

    private final LinkedHashMap<UUID, QuestActionOutboxEntry> entries = new LinkedHashMap<>();
    private final List<UnreadableEntry> unreadable = new ArrayList<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;

    public static QuestActionOutboxStore get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static QuestActionOutboxStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(QuestActionOutboxStore::new, QuestActionOutboxStore::load),
            DATA_NAME);
    }

    public static QuestActionOutboxStore load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestActionOutboxStore data = new QuestActionOutboxStore();
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
                QuestActionOutboxEntry entry = QuestActionOutboxEntry.fromNbt(entryTag);
                if (data.entries.putIfAbsent(entry.eventUuid(), entry) != null) {
                    throw new IllegalArgumentException("duplicate EventUuid");
                }
            } catch (RuntimeException exception) {
                data.unreadable.add(new UnreadableEntry("corrupt: " + exception.getMessage(), entryTag.copy()));
                LOGGER.error("Quarantined corrupt quest action outbox entry at index {}", index, exception);
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

    public Optional<QuestActionOutboxEntry> find(UUID eventUuid) {
        return Optional.ofNullable(entries.get(eventUuid));
    }

    /** Every row, oldest first: the sweep the server start and the periodic pass walk. */
    public List<QuestActionOutboxEntry> all() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    /** One player's rows, oldest first. */
    public List<QuestActionOutboxEntry> entriesFor(UUID playerUuid) {
        List<QuestActionOutboxEntry> found = new ArrayList<>();
        for (QuestActionOutboxEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) found.add(entry);
        }
        return List.copyOf(found);
    }

    /**
     * Whether this player already has a row for the same objective. The dispatcher uses it so an
     * outage cannot fill the outbox with sixty-four copies of one harvest -- the same trigger key
     * can only be satisfied once, and Rails would answer every copy but the first {@code duplicate}
     * or {@code irrelevant} anyway.
     */
    public boolean hasOpenEntryFor(UUID playerUuid, String questStateId, String triggerKey) {
        for (QuestActionOutboxEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)
                && entry.questStateId().equals(questStateId)
                && entry.triggerKey().equals(triggerKey)) {
                return true;
            }
        }
        return false;
    }

    public List<UnreadableEntry> unreadable() {
        return List.copyOf(unreadable);
    }

    public int size() {
        return entries.size();
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    /**
     * Records a new row, dropping the player's oldest row first when the bound is already reached
     * (section 2.2: "when exceeded the oldest terminal-less entry is dropped and logged").
     */
    public RecordOutcome record(QuestActionOutboxEntry incoming) {
        Objects.requireNonNull(incoming, "incoming");
        if (readOnlyFutureSchema) return RecordOutcome.READ_ONLY_SCHEMA;
        if (entries.containsKey(incoming.eventUuid())) return RecordOutcome.ALREADY_PRESENT;

        dropOldestOverBound(incoming.playerUuid());
        entries.put(incoming.eventUuid(), incoming);
        setDirty();
        return RecordOutcome.CREATED;
    }

    /** Replaces one row; empty when it does not exist, the store is read-only, or nothing changed. */
    public Optional<QuestActionOutboxEntry> update(UUID eventUuid, UnaryOperator<QuestActionOutboxEntry> change) {
        Objects.requireNonNull(eventUuid, "eventUuid");
        Objects.requireNonNull(change, "change");
        if (readOnlyFutureSchema) return Optional.empty();
        QuestActionOutboxEntry existing = entries.get(eventUuid);
        if (existing == null) return Optional.empty();
        QuestActionOutboxEntry changed = change.apply(existing);
        if (changed == null || !changed.eventUuid().equals(eventUuid) || changed.equals(existing)) {
            return Optional.empty();
        }
        entries.put(eventUuid, changed);
        setDirty();
        return Optional.of(changed);
    }

    /** Removes a row that reached a terminal result. */
    public boolean remove(UUID eventUuid) {
        if (readOnlyFutureSchema) return false;
        boolean removed = entries.remove(eventUuid) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Drops every row, used when Rails is confirmed not to serve the endpoint at all (section 4). */
    public int removeAll() {
        if (readOnlyFutureSchema || entries.isEmpty()) return 0;
        int removed = entries.size();
        entries.clear();
        setDirty();
        return removed;
    }

    private void dropOldestOverBound(UUID playerUuid) {
        int count = 0;
        for (QuestActionOutboxEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) count++;
        }
        Iterator<Map.Entry<UUID, QuestActionOutboxEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && count >= MAX_ENTRIES_PER_PLAYER) {
            QuestActionOutboxEntry entry = iterator.next().getValue();
            if (!entry.playerUuid().equals(playerUuid)) continue;
            iterator.remove();
            count--;
            setDirty();
            LOGGER.warn("event=quest_action_outbox_overflow player_uuid={} event_uuid={} action={} "
                    + "quest_state_id={} trigger_key={} bound={}",
                playerUuid, entry.eventUuid(), entry.event().action().wireName(),
                entry.questStateId(), entry.triggerKey(), MAX_ENTRIES_PER_PLAYER);
        }
    }

    private QuestActionOutboxStore readOnly(CompoundTag tag, String reason) {
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
        LOGGER.error("Quest action outbox is unsupported ({}); store is read-only", reason);
        return this;
    }
}

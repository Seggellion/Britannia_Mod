package com.seggellion.britannia_mod.quest.handin;

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
import java.util.HashSet;
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
 * The durable hand-in ledger: {@code SavedData} {@value #DATA_NAME} on the overworld storage,
 * modelled on {@code QuestRewardDeliveryLedgerStore} and, through it, on
 * {@code BlessedDeliveryReceiptStore} -- an explicit {@code SchemaVersion}, per-entry quarantine of
 * a corrupt row with its raw tag preserved and re-saved verbatim, a read-only fallback for a schema
 * from the future, and an overworld anchor so a hand-in made in any dimension is found by the same
 * reconciliation scan.
 *
 * <p>Like both precedents this class performs in-memory mutation and {@code setDirty()} ONLY. The
 * synchronous forced flush lives in {@link QuestHandinLedger}, because the ordering of those
 * flushes relative to the player-file write is the entire crash-safety argument and it belongs in
 * one place where it can be read.
 *
 * <h2>Retention</h2>
 * The bound is per player and it <b>refuses</b> rather than evicts. Only a settled row is ever
 * pruned; a row that has taken something and not yet reached an ending is live evidence, and a
 * player at the bound with that many live rows is told no rather than quietly losing one. That is
 * the opposite trade from the delivery ledger and for the opposite reason: dropping a delivery row
 * risks granting an item twice, while dropping a hand-in row risks a player having given one up for
 * nothing. A {@link QuestHandinLocalState#STRANDED} row is never pruned at all.
 */
public final class QuestHandinLedgerStore extends SavedData {

    public static final String DATA_NAME = "britannia_quest_item_handins";
    public static final int SCHEMA_VERSION = 1;

    /**
     * A hand-in is a deliberate, player-initiated act at a quest giver, so a player with dozens of
     * unsettled ones is a symptom rather than a workload. The bound is generous enough that no
     * honest play reaches it and small enough that reaching it is visible.
     */
    public static final int MAX_ENTRIES_PER_PLAYER = 64;

    static final int MAX_COLLECTION_ENTRIES = 65_536;

    private static final String KEY_SCHEMA_VERSION = "SchemaVersion";
    private static final String KEY_ENTRIES = "Entries";
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum RecordOutcome {
        CREATED,
        ALREADY_PRESENT,
        READ_ONLY_SCHEMA,
        /** The player already holds the maximum live transactions. No new one is minted. */
        AT_CAPACITY
    }

    /** A row that exists on disk but could not be read; kept verbatim, never silently dropped. */
    public record UnreadableEntry(String reason, CompoundTag rawTag) {
        public UnreadableEntry {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(rawTag, "rawTag");
        }
    }

    private final LinkedHashMap<UUID, QuestHandinLedgerEntry> entries = new LinkedHashMap<>();
    private final List<UnreadableEntry> unreadable = new ArrayList<>();
    private final Set<UUID> warnedAtCapacity = new HashSet<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;

    public static QuestHandinLedgerStore get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static QuestHandinLedgerStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(QuestHandinLedgerStore::new, QuestHandinLedgerStore::load),
                DATA_NAME);
    }

    public static QuestHandinLedgerStore load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestHandinLedgerStore data = new QuestHandinLedgerStore();
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
                QuestHandinLedgerEntry entry = QuestHandinLedgerEntry.fromNbt(entryTag);
                if (data.entries.putIfAbsent(entry.handinUuid(), entry) != null) {
                    throw new IllegalArgumentException("duplicate HandinUuid");
                }
            } catch (RuntimeException exception) {
                data.unreadable.add(new UnreadableEntry("corrupt: " + exception.getMessage(), entryTag.copy()));
                LOGGER.error("Quarantined corrupt quest item hand-in ledger entry at index {}", index, exception);
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

    public Optional<QuestHandinLedgerEntry> find(UUID handinUuid) {
        return Optional.ofNullable(entries.get(handinUuid));
    }

    /** Every entry of one player, oldest first. */
    public List<QuestHandinLedgerEntry> entriesFor(UUID playerUuid) {
        List<QuestHandinLedgerEntry> found = new ArrayList<>();
        for (QuestHandinLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) found.add(entry);
        }
        return List.copyOf(found);
    }

    /** The player's entries the reconciler may still have to act on, oldest first. */
    public List<QuestHandinLedgerEntry> openEntriesFor(UUID playerUuid) {
        List<QuestHandinLedgerEntry> found = new ArrayList<>();
        for (QuestHandinLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid) && entry.open()) found.add(entry);
        }
        return List.copyOf(found);
    }

    /** Every unsettled entry on the server, for the boot-time sweep. */
    public List<QuestHandinLedgerEntry> openEntries() {
        List<QuestHandinLedgerEntry> found = new ArrayList<>();
        for (QuestHandinLedgerEntry entry : entries.values()) {
            if (entry.open()) found.add(entry);
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
     * Records a new transaction. Never overwrites: a hand-in already in the ledger -- in any state
     * -- means the player clicked twice or Rails adopted an existing row, and the caller must
     * consult {@link #find} rather than start again. That is what makes a double-click cost one
     * item rather than two.
     */
    public RecordOutcome record(QuestHandinLedgerEntry incoming) {
        Objects.requireNonNull(incoming, "incoming");
        if (readOnlyFutureSchema) return RecordOutcome.READ_ONLY_SCHEMA;
        if (entries.containsKey(incoming.handinUuid())) return RecordOutcome.ALREADY_PRESENT;

        pruneSettled(incoming.playerUuid(), MAX_ENTRIES_PER_PLAYER - 1);
        if (countFor(incoming.playerUuid()) >= MAX_ENTRIES_PER_PLAYER) {
            // Every row that could be pruned already was, so these are all live. Refusing here is
            // what stops a removal that would have had nowhere durable to be recorded.
            if (warnedAtCapacity.add(incoming.playerUuid())) {
                LOGGER.warn("event=quest_handin_ledger_at_capacity player_uuid={} entries={} bound={}",
                        incoming.playerUuid(), countFor(incoming.playerUuid()), MAX_ENTRIES_PER_PLAYER);
            }
            return RecordOutcome.AT_CAPACITY;
        }
        entries.put(incoming.handinUuid(), incoming);
        setDirty();
        return RecordOutcome.CREATED;
    }

    /**
     * Replaces one row with the result of {@code change}; empty when the row does not exist, the
     * store is read-only, or the change returned the row unchanged (nothing to flush then).
     */
    public Optional<QuestHandinLedgerEntry> update(UUID handinUuid,
                                                   UnaryOperator<QuestHandinLedgerEntry> change) {
        Objects.requireNonNull(handinUuid, "handinUuid");
        Objects.requireNonNull(change, "change");
        if (readOnlyFutureSchema) return Optional.empty();
        QuestHandinLedgerEntry existing = entries.get(handinUuid);
        if (existing == null) return Optional.empty();
        QuestHandinLedgerEntry changed = change.apply(existing);
        if (changed == null || !changed.handinUuid().equals(handinUuid) || changed.equals(existing)) {
            return Optional.empty();
        }
        entries.put(handinUuid, changed);
        setDirty();
        return Optional.of(changed);
    }

    /** Test-only: drops a row outright so a test's random transaction never outlives the test. */
    public boolean removeForTesting(UUID handinUuid) {
        if (readOnlyFutureSchema) return false;
        boolean removed = entries.remove(handinUuid) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Test-only: injects a row the loader could not read, which no live reload can reproduce. */
    public void addUnreadableEntryForTesting(UnreadableEntry entry) {
        unreadable.add(Objects.requireNonNull(entry, "entry"));
        setDirty();
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    public int countFor(UUID playerUuid) {
        int count = 0;
        for (QuestHandinLedgerEntry entry : entries.values()) {
            if (entry.playerUuid().equals(playerUuid)) count++;
        }
        return count;
    }

    /**
     * Keeps at most {@code keep} rows for the player by removing the OLDEST settled rows. A row
     * that removed something and has not reached an ending is never a candidate, and neither is a
     * stranded one: those are the rows an operator needs to still exist.
     */
    int pruneSettled(UUID playerUuid, int keep) {
        int count = countFor(playerUuid);
        if (count <= keep) return 0;
        int removed = 0;
        Iterator<Map.Entry<UUID, QuestHandinLedgerEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext() && count > keep) {
            QuestHandinLedgerEntry entry = iterator.next().getValue();
            if (!entry.playerUuid().equals(playerUuid)) continue;
            if (!entry.localState().prunable()) continue;
            iterator.remove();
            count--;
            removed++;
        }
        if (removed > 0) setDirty();
        return removed;
    }

    private QuestHandinLedgerStore readOnly(CompoundTag tag, String reason) {
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
        LOGGER.error("Quest item hand-in ledger is unsupported ({}); store is read-only", reason);
        return this;
    }
}

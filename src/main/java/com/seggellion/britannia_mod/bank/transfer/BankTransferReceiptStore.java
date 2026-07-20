package com.seggellion.britannia_mod.bank.transfer;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable local receipt store for in-flight bank transfers -- Milestone 8 NeoForge Slice 3.
 * Not yet wired to any live deposit/withdrawal interaction (that is Milestone 9's job); this
 * slice is the standalone durable-storage abstraction and its tests only.
 *
 * Follows {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData}'s own
 * established schema-versioned SavedData conventions (explicit SchemaVersion tag, per-record
 * quarantine of corrupt entries rather than a crash, a read-only fallback for an unsupported
 * future schema that preserves the raw tag verbatim for round-trip safety, a collection-size
 * cap with a one-time warning). It does NOT reuse that class or its data directly -- per the
 * Milestone 8 recon finding, that store is single-resource-scoped to spawn-point registration
 * and only ever calls {@code setDirty()} (flushed at the next autosave/shutdown, not
 * immediately), which is exactly wrong for a receipt that must be durable *before* a risky
 * physical action -- so this is a new, separate SavedData, not an extension of that one.
 *
 * This class itself only performs the in-memory mutation and {@code setDirty()} -- exactly
 * like the precedent it follows. The true synchronous, forced-flush-to-disk guarantee this
 * slice requires lives in {@link BankTransferReceipts}, which calls this store's methods and
 * then forces an immediate save; see that class for the actual durability mechanism and its
 * precise guarantee.
 *
 * <h2>Level/dimension anchor</h2>
 * {@link #get(ServerLevel)} always redirects to {@code level.getServer().overworld()} before
 * touching that level's {@code DimensionDataStorage}, regardless of which {@link ServerLevel}
 * is actually passed in -- deliberately matching the same overworld-anchor convention already
 * established by {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaimData}
 * and {@code ServiceNpcSpawnPendingData} (Milestone 6). Banking, like Service NPC spawn
 * registration, is a single, server-wide concern with no meaningful per-dimension split -- a
 * bank transfer receipt recorded while a player is in the Nether must be found by the exact
 * same startup scan as one recorded in the overworld, not a dimension-scoped copy of it. This
 * is why {@link #get} takes whatever {@code ServerLevel} the caller happens to have on hand
 * (there is no dimension-specific banking concept to preserve) and always resolves to one,
 * single, server-wide store.
 */
public final class BankTransferReceiptStore extends SavedData {
    public enum RecordOutcome {
        CREATED,
        IDEMPOTENT_REPLAY,
        READ_ONLY_SCHEMA
    }

    /**
     * An entry that exists on disk but could not be turned into a {@link BankTransferReceipt}
     * -- either a single corrupt record (schema otherwise supported), or an entry recovered
     * best-effort from a store whose overall schema version is unsupported. Either way, this
     * might represent a real in-flight operation that a startup scan must not silently miss;
     * {@code rawTag} is preserved verbatim so nothing is lost, and {@code reason} says why it
     * could not be read.
     */
    public record UnreadableEntry(String reason, CompoundTag rawTag) {
        public UnreadableEntry {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(rawTag, "rawTag");
        }
    }

    /**
     * The startup-scan result: {@code pending} is every receipt that parsed cleanly (the
     * ordinary "crash after possible insertion" candidates Section A.6 describes), and
     * {@code unreadable} is every entry that exists but could not be parsed at all -- kept as
     * its own distinct category rather than silently absent, because an unreadable entry might
     * just as easily represent a real in-flight operation this scan is specifically meant to
     * catch. A caller that only inspects {@code pending} and ignores a non-empty
     * {@code unreadable} is making the same silent-assumption mistake this program's A.6
     * philosophy rejects everywhere else.
     */
    public record ScanResult(List<BankTransferReceipt> pending, List<UnreadableEntry> unreadable) {
        public boolean isEmpty() {
            return pending.isEmpty() && unreadable.isEmpty();
        }
    }

    public static final String DATA_NAME = "britannia_bank_transfer_receipts";
    public static final int SCHEMA_VERSION = 1;
    static final int MAX_COLLECTION_ENTRIES = 16_384;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkedHashMap<UUID, BankTransferReceipt> receipts = new LinkedHashMap<>();
    private final List<UnreadableEntry> unreadable = new ArrayList<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;
    private boolean warnedLargeReceipts;

    public static BankTransferReceiptStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(BankTransferReceiptStore::new, BankTransferReceiptStore::load),
            DATA_NAME
        );
    }

    public static BankTransferReceiptStore load(CompoundTag tag, HolderLookup.Provider provider) {
        BankTransferReceiptStore data = new BankTransferReceiptStore();
        if (!tag.contains("SchemaVersion", Tag.TAG_INT)) return data.readOnly(tag, "missing_or_malformed");
        int schema = tag.getInt("SchemaVersion");
        if (schema != SCHEMA_VERSION) return data.readOnly(tag, "unsupported schema version " + schema);

        Tag rawReceipts = tag.get("Receipts");
        if (!(rawReceipts instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return data.readOnly(tag, "malformed_receipts_collection");
        }
        if (list.size() > MAX_COLLECTION_ENTRIES) return data.readOnly(tag, "receipts_over_limit");
        for (int index = 0; index < list.size(); index++) {
            CompoundTag receiptTag = list.getCompound(index);
            try {
                BankTransferReceipt receipt = BankTransferReceipt.fromNbt(receiptTag);
                if (data.receipts.putIfAbsent(receipt.operationId(), receipt) != null) {
                    throw new IllegalArgumentException("duplicate OperationId");
                }
            } catch (RuntimeException exception) {
                data.unreadable.add(new UnreadableEntry("corrupt: " + exception.getMessage(), receiptTag.copy()));
                LOGGER.error("Quarantined corrupt bank transfer receipt at index {}", index, exception);
            }
        }
        data.checkWarningThreshold();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readOnlyFutureSchema && futureRoot != null) return futureRoot.copy();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag list = new ListTag();
        receipts.values().forEach(receipt -> list.add(receipt.toNbt()));
        unreadable.forEach(entry -> list.add(entry.rawTag().copy()));
        tag.put("Receipts", list);
        return tag;
    }

    /**
     * Pure in-memory mutation: records the intent to perform a risky physical action, or -- if
     * an identical receipt for this operationId already exists -- treats the call as an
     * idempotent retry rather than a duplicate. A retry carrying genuinely different content
     * for an operationId that already has a receipt is a real programming error (this store is
     * never supposed to see two different receipts for the same operation), not a case this
     * slice needs to resolve gracefully, so it throws.
     */
    public RecordOutcome record(BankTransferReceipt incoming) {
        Objects.requireNonNull(incoming, "incoming");
        if (readOnlyFutureSchema) return RecordOutcome.READ_ONLY_SCHEMA;

        BankTransferReceipt existing = receipts.get(incoming.operationId());
        if (existing != null) {
            if (existing.equals(incoming)) return RecordOutcome.IDEMPOTENT_REPLAY;
            throw new IllegalStateException(
                "conflicting bank transfer receipt already recorded for operation " + incoming.operationId());
        }

        receipts.put(incoming.operationId(), incoming);
        setDirty();
        checkWarningThreshold();
        return RecordOutcome.CREATED;
    }

    /**
     * The operation cleanly completed or was cleanly cancelled before any risk -- removes the
     * receipt entirely (this store is a transient crash-recovery mechanism, not a permanent
     * audit trail; Rails' own {@code BankTransaction} is the permanent record). Returns false,
     * changing nothing, if no receipt exists for this operationId (an idempotent no-op, matching
     * this program's established resolve/cancel/close conventions elsewhere).
     */
    public boolean resolve(UUID operationId) {
        if (readOnlyFutureSchema) return false;
        boolean removed = receipts.remove(operationId) != null;
        if (removed) setDirty();
        return removed;
    }

    public BankTransferReceipt find(UUID operationId) {
        return receipts.get(operationId);
    }

    /**
     * The startup-scan candidate list Section A.6 describes: every cleanly-parsed receipt
     * still present is, by definition, unresolved -- {@link #resolve} removes a receipt the
     * moment it is no longer needed. Also surfaces every entry that exists but could not be
     * parsed (corrupt records, or entries recovered best-effort from an unsupported future
     * schema) as its own distinct {@link UnreadableEntry} category -- never silently absent,
     * because an unreadable entry might just as easily be a real in-flight operation this scan
     * exists specifically to catch.
     */
    public ScanResult scanUnresolved() {
        return new ScanResult(List.copyOf(receipts.values()), List.copyOf(unreadable));
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    /**
     * A schema-version mismatch means this store cannot trust its understanding of the data's
     * *shape*, so it never resumes normal parsing (see {@code fromNbt}) -- but it still makes a
     * best effort to enumerate whatever compound entries exist under "Receipts" so a scan is
     * never silently empty just because the schema moved on. If even that shape is
     * unrecognizable, one single whole-store {@link UnreadableEntry} is recorded instead, so a
     * caller still sees "something here needs attention" rather than nothing at all.
     */
    private BankTransferReceiptStore readOnly(CompoundTag tag, String reason) {
        readOnlyFutureSchema = true;
        futureRoot = tag.copy();
        receipts.clear();
        unreadable.clear();

        if (tag.get("Receipts") instanceof ListTag list && !list.isEmpty()
                && list.getElementType() == Tag.TAG_COMPOUND
                && list.size() <= MAX_COLLECTION_ENTRIES) {
            // The size cap matters here too, not just in the normal-schema load() path: a
            // store that went read-only *because* its Receipts collection was over the limit
            // must not have this best-effort enumeration defeat the exact protection that cap
            // exists for.
            for (int index = 0; index < list.size(); index++) {
                unreadable.add(new UnreadableEntry(reason, list.getCompound(index).copy()));
            }
        } else {
            unreadable.add(new UnreadableEntry(reason, tag.copy()));
        }

        LOGGER.error("Bank transfer receipt store is unsupported ({}); store is read-only", reason);
        return this;
    }

    private void checkWarningThreshold() {
        if (!warnedLargeReceipts && receipts.size() >= MAX_COLLECTION_ENTRIES) {
            warnedLargeReceipts = true;
            LOGGER.warn("Bank transfer receipt store contains {} unresolved receipts", receipts.size());
        }
    }
}

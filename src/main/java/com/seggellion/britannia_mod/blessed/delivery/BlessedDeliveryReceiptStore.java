package com.seggellion.britannia_mod.blessed.delivery;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Durable local delivery-receipt store for blessed-item materialization -- Starfarer M6.
 *
 * <p>Modelled closely on {@link com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore},
 * the proven precedent in this codebase for "persist the intent to perform an irreversible
 * physical act *before* performing it": an explicit {@code SchemaVersion} int tag, per-record
 * quarantine of a corrupt entry (preserving its raw tag verbatim) rather than a crash that
 * loses every other receipt beside it, a read-only fallback for an on-disk schema from the
 * future that still enumerates what it finds instead of reporting an empty store, and a
 * collection size cap with a one-time warning. It is a separate {@link SavedData} with its own
 * distinct file id, not an extension of the banking store -- the two have unrelated lifecycles,
 * unrelated schemas, and unrelated retention rules, and sharing a file would mean a corrupt
 * banking record could take blessed deliveries down with it.
 *
 * <p>Like the precedent, this class performs in-memory mutation and {@code setDirty()} ONLY. The
 * actual synchronous forced-flush-to-disk guarantee -- the entire point of the exercise -- lives
 * in {@link BlessedDeliveryReceipts}; see that class for the mechanism and its precise, honest
 * limits.
 *
 * <h2>Retention differs from the banking precedent, deliberately</h2>
 * {@code BankTransferReceiptStore} <em>removes</em> a receipt on {@code resolve} -- it is a
 * transient crash-recovery aid, and Rails' own {@code BankTransaction} is the permanent record.
 * This store instead <em>transitions</em> a receipt to {@link
 * BlessedDeliveryReceiptStatus#DELIVERED} and keeps it. The reason is the duplicate-prevention
 * requirement: a removed receipt is indistinguishable from one that never existed, so a replayed
 * delivery after removal would look brand new and materialize a second physical item. Retention
 * is what makes {@code IDEMPOTENT_REPLAY} answerable at all. Reclamation of long-settled
 * {@code DELIVERED} receipts is a later milestone's problem, and one that must be solved without
 * reintroducing that hole; the size cap below is the interim guard.
 *
 * <h2>Level/dimension anchor</h2>
 * {@link #get(ServerLevel)} always redirects to {@code level.getServer().overworld()} before
 * touching that level's {@code DimensionDataStorage}, regardless of which {@link ServerLevel} is
 * passed in -- the same overworld-anchor convention the banking store and the Service NPC spawn
 * stores already use. A medallion delivered to a player standing in the Nether must be found by
 * the exact same startup scan as one delivered in the overworld; there is no meaningful
 * per-dimension split in blessed-item entitlement, and a dimension-scoped copy of this store
 * would be a duplicate-item bug generator rather than a feature.
 */
public final class BlessedDeliveryReceiptStore extends SavedData {
    /**
     * The outcome of {@link #record(BlessedDeliveryReceipt)}.
     *
     * <ul>
     *   <li>{@code CREATED} -- a genuinely new instance; the caller may proceed to deliver.</li>
     *   <li>{@code IDEMPOTENT_REPLAY} -- a receipt for this {@code instanceUuid} already exists
     *       and its fingerprint matches (see {@link BlessedDeliveryReceipt#matches}). The stored
     *       receipt is left completely untouched, including its status: a replay arriving after
     *       the item was already delivered must never regress {@link
     *       BlessedDeliveryReceiptStatus#DELIVERED} back to pending. The caller must consult
     *       {@link #find} and deliver only if the stored status is still pending.</li>
     *   <li>{@code FINGERPRINT_MISMATCH} -- a receipt for this {@code instanceUuid} exists but
     *       disagrees on deedId, itemId or ownerUuid. Nothing is written and nothing is
     *       overwritten. This is corruption or a serious bug, and the caller MUST refuse to
     *       deliver: the one thing worse than failing to deliver a commemorative item is
     *       delivering it to the wrong player, or delivering the wrong item, on the strength of
     *       local state that has demonstrably lost integrity.</li>
     *   <li>{@code READ_ONLY_SCHEMA} -- the on-disk store is from a future schema this build
     *       cannot safely mutate. Nothing is written. The caller must refuse to deliver for the
     *       same reason: without a durable receipt there is no crash protection at all.</li>
     * </ul>
     */
    public enum RecordOutcome {
        CREATED,
        IDEMPOTENT_REPLAY,
        FINGERPRINT_MISMATCH,
        READ_ONLY_SCHEMA
    }

    /**
     * The outcome of {@link #markDelivered(UUID, long)}.
     *
     * <ul>
     *   <li>{@code MARKED} -- the receipt transitioned to {@link
     *       BlessedDeliveryReceiptStatus#DELIVERED}.</li>
     *   <li>{@code ALREADY_DELIVERED} -- an idempotent no-op; the original delivery timestamp is
     *       preserved rather than re-stamped.</li>
     *   <li>{@code ALREADY_DESTROYED} -- refused. The receipt has reached the terminal {@link
     *       BlessedDeliveryReceiptStatus#DESTROYED} state, and nothing is written. Added in M7
     *       alongside {@code DESTROYED} itself, because without it this method's
     *       "is it already DELIVERED?" test would silently resurrect a destroyed receipt back
     *       into {@code DELIVERED} -- which is precisely the "a destroyed materialization can
     *       never be re-delivered" invariant, defeated by an unhandled enum value. A caller
     *       seeing this has a real anomaly on its hands: something physically delivered an item
     *       whose materialization this world had already written off.</li>
     *   <li>{@code NOT_FOUND} -- no receipt for this instance; nothing is fabricated.</li>
     *   <li>{@code READ_ONLY_SCHEMA} -- the on-disk store is from a future schema; nothing is
     *       written.</li>
     * </ul>
     */
    public enum MarkDeliveredOutcome {
        MARKED,
        ALREADY_DELIVERED,
        ALREADY_DESTROYED,
        NOT_FOUND,
        READ_ONLY_SCHEMA
    }

    /**
     * The outcome of {@link #markDestroyed(UUID, long)}.
     *
     * <ul>
     *   <li>{@code MARKED} -- the receipt transitioned to {@link
     *       BlessedDeliveryReceiptStatus#DESTROYED}.</li>
     *   <li>{@code ALREADY_DESTROYED} -- an idempotent no-op leaving the ORIGINAL destruction
     *       timestamp intact, exactly as {@link MarkDeliveredOutcome#ALREADY_DELIVERED} does. A
     *       destruction report is retried until Rails acknowledges it, so a repeat call is the
     *       normal case, not an error -- and the first observation is the true one.</li>
     *   <li>{@code NOT_FOUND} -- no receipt exists for this instance. Nothing is fabricated: an
     *       item destroyed under an instance this world never recorded delivering is an anomaly
     *       the caller must see, and inventing a {@code DESTROYED} receipt for it would forge
     *       local evidence about somebody's permanent entitlement.</li>
     *   <li>{@code READ_ONLY_SCHEMA} -- the on-disk store is from a future schema this build
     *       cannot safely mutate; nothing is written.</li>
     * </ul>
     */
    public enum MarkDestroyedOutcome {
        MARKED,
        ALREADY_DESTROYED,
        NOT_FOUND,
        READ_ONLY_SCHEMA
    }

    /**
     * An entry that exists on disk but could not be turned into a {@link BlessedDeliveryReceipt}
     * -- either a single corrupt record (schema otherwise supported), or an entry recovered
     * best-effort from a store whose overall schema version is unsupported. Either way it might
     * represent a real, physically-delivered item, so it is never silently dropped: {@code
     * rawTag} is preserved verbatim (and re-saved verbatim) so nothing is lost, and {@code
     * reason} says why it could not be read.
     */
    public record UnreadableEntry(String reason, CompoundTag rawTag) {
        public UnreadableEntry {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(rawTag, "rawTag");
        }
    }

    /**
     * The startup/reconciliation scan result, in three buckets that each need different handling:
     * <ul>
     *   <li>{@code pendingDelivery} -- the dangerous bucket. Rails authorised a materialization
     *       and this side either never finished it or crashed without recording that it did. The
     *       physical item may or may not exist; establishing which is exactly what
     *       reconciliation is for, and a caller must not assume either way.</li>
     *   <li>{@code delivered} -- the physical item exists. Not a delivery candidate; at most a
     *       candidate for re-sending an acknowledgement Rails may never have received.</li>
     *   <li>{@code destroyed} -- terminal. Not a delivery candidate and never will be; at most a
     *       candidate for re-sending a destruction report Rails may never have received. This
     *       bucket exists as its own category rather than being folded into {@code delivered}
     *       for a blunt reason: before M7 added it, {@code scan} sorted anything that was not
     *       {@code DELIVERED} into {@code pendingDelivery}, so a destroyed receipt would have
     *       been handed to reconciliation as a live delivery candidate -- the exact
     *       duplicate-item outcome this store exists to prevent.</li>
     *   <li>{@code unreadable} -- an entry that exists but could not be parsed. Kept as its own
     *       distinct category rather than silently absent, because an unreadable entry might
     *       just as easily be a real receipt of any of the other three kinds.</li>
     * </ul>
     * A caller that inspects only {@code pendingDelivery} and ignores a non-empty {@code
     * unreadable} is making exactly the silent assumption this store exists to prevent.
     */
    public record ScanResult(
            List<BlessedDeliveryReceipt> pendingDelivery,
            List<BlessedDeliveryReceipt> delivered,
            List<BlessedDeliveryReceipt> destroyed,
            List<UnreadableEntry> unreadable
    ) {
        public boolean isEmpty() {
            return pendingDelivery.isEmpty() && delivered.isEmpty()
                && destroyed.isEmpty() && unreadable.isEmpty();
        }
    }

    /**
     * Deliberately distinct from {@code BankTransferReceiptStore.DATA_NAME}
     * ({@code britannia_bank_transfer_receipts}) -- two separate files, two separate schema
     * version lines, no shared blast radius.
     */
    public static final String DATA_NAME = "britannia_blessed_delivery_receipts";

    /**
     * 1 -- the initial shape (Starfarer M6), and deliberately still 1 after M7 added {@link
     * BlessedDeliveryReceiptStatus#DESTROYED}.
     *
     * <p>Adding an enum constant is not an on-disk schema change here, because {@code Status} is
     * persisted as the enum NAME and never its ordinal: every M6 row still spells {@code
     * PENDING_PHYSICAL_DELIVERY} or {@code DELIVERED}, still parses to the same constant, and
     * still means what it meant. The tag names, tag types and record arity are all untouched.
     *
     * <p>Bumping it would not merely be unnecessary, it would be actively harmful in the one
     * direction that matters. This build treats an unrecognised {@code SchemaVersion} as
     * read-only, refusing every mutation -- so a bump would mean that a world rolled back to an
     * M6 jar could no longer record ANY delivery receipt, turning a downgrade into a total loss
     * of crash protection for every blessed item. Left at 1, an M6 jar reading an M7 file loads
     * normally and quarantines only the individual {@code DESTROYED} rows it cannot name (
     * {@code valueOf} throws, the per-record catch preserves the raw tag verbatim and re-saves
     * it), which is the correct failure: narrow, visible, and lossless.
     */
    public static final int SCHEMA_VERSION = 1;

    static final int MAX_COLLECTION_ENTRIES = 16_384;
    private static final String KEY_SCHEMA_VERSION = "SchemaVersion";
    private static final String KEY_RECEIPTS = "Receipts";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkedHashMap<UUID, BlessedDeliveryReceipt> receipts = new LinkedHashMap<>();
    private final List<UnreadableEntry> unreadable = new ArrayList<>();
    private boolean readOnlyFutureSchema;
    private CompoundTag futureRoot;
    private boolean warnedLargeReceipts;

    public static BlessedDeliveryReceiptStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(BlessedDeliveryReceiptStore::new, BlessedDeliveryReceiptStore::load),
            DATA_NAME
        );
    }

    public static BlessedDeliveryReceiptStore load(CompoundTag tag, HolderLookup.Provider provider) {
        BlessedDeliveryReceiptStore data = new BlessedDeliveryReceiptStore();
        if (!tag.contains(KEY_SCHEMA_VERSION, Tag.TAG_INT)) return data.readOnly(tag, "missing_or_malformed");
        int schema = tag.getInt(KEY_SCHEMA_VERSION);
        if (schema != SCHEMA_VERSION) return data.readOnly(tag, "unsupported schema version " + schema);

        Tag rawReceipts = tag.get(KEY_RECEIPTS);
        if (!(rawReceipts instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return data.readOnly(tag, "malformed_receipts_collection");
        }
        if (list.size() > MAX_COLLECTION_ENTRIES) return data.readOnly(tag, "receipts_over_limit");
        for (int index = 0; index < list.size(); index++) {
            CompoundTag receiptTag = list.getCompound(index);
            try {
                BlessedDeliveryReceipt receipt = BlessedDeliveryReceipt.fromNbt(receiptTag);
                if (data.receipts.putIfAbsent(receipt.instanceUuid(), receipt) != null) {
                    throw new IllegalArgumentException("duplicate InstanceUuid");
                }
            } catch (RuntimeException exception) {
                data.unreadable.add(new UnreadableEntry("corrupt: " + exception.getMessage(), receiptTag.copy()));
                LOGGER.error("Quarantined corrupt blessed delivery receipt at index {}", index, exception);
            }
        }
        data.checkWarningThreshold();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readOnlyFutureSchema && futureRoot != null) return futureRoot.copy();
        tag.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        ListTag list = new ListTag();
        receipts.values().forEach(receipt -> list.add(receipt.toNbt()));
        unreadable.forEach(entry -> list.add(entry.rawTag().copy()));
        tag.put(KEY_RECEIPTS, list);
        return tag;
    }

    /**
     * Pure in-memory mutation: records the intent to materialize a blessed item, or -- if a
     * receipt for this {@code instanceUuid} already exists -- classifies the call as an
     * idempotent replay or a fingerprint mismatch without writing anything. See {@link
     * RecordOutcome} for exactly what each answer obliges the caller to do.
     *
     * <p>Unlike the banking precedent, a conflicting retry does NOT throw. Banking can afford to
     * treat that as an unrecoverable programming error because its receipt is removed the moment
     * it is resolved, so a conflict genuinely cannot arise from ordinary operation. This store
     * retains receipts forever (see the class docs), which makes a stale-file or restored-backup
     * conflict a real, reachable runtime condition rather than a pure bug -- and the correct
     * response to it is a caller that calmly refuses to deliver, not an exception thrown out of
     * the middle of a delivery path where the item may already be half-materialized.
     */
    public RecordOutcome record(BlessedDeliveryReceipt incoming) {
        Objects.requireNonNull(incoming, "incoming");
        if (readOnlyFutureSchema) return RecordOutcome.READ_ONLY_SCHEMA;

        BlessedDeliveryReceipt existing = receipts.get(incoming.instanceUuid());
        if (existing != null) {
            return existing.matches(incoming) ? RecordOutcome.IDEMPOTENT_REPLAY : RecordOutcome.FINGERPRINT_MISMATCH;
        }

        receipts.put(incoming.instanceUuid(), incoming);
        setDirty();
        checkWarningThreshold();
        return RecordOutcome.CREATED;
    }

    /**
     * The physical item now demonstrably exists: transitions an existing receipt to {@link
     * BlessedDeliveryReceiptStatus#DELIVERED}, re-stamping {@code updatedEpochMillis}. Idempotent
     * -- marking an already-delivered receipt is {@link MarkDeliveredOutcome#ALREADY_DELIVERED},
     * a no-op that leaves the original delivery timestamp intact rather than an error or a
     * re-stamp. Never fabricates a receipt: an unknown {@code instanceUuid} is reported as
     * {@link MarkDeliveredOutcome#NOT_FOUND}, because "the item exists but nothing ever recorded
     * the intent to create it" is precisely the anomaly a caller needs to see, not something to
     * paper over by inventing the missing record.
     *
     * <p>A {@link BlessedDeliveryReceiptStatus#DESTROYED} receipt is terminal and is refused with
     * {@link MarkDeliveredOutcome#ALREADY_DESTROYED} rather than transitioned: destruction is the
     * one state this store will not walk back, because walking it back is how a written-off
     * materialization becomes a second physical item.
     */
    public MarkDeliveredOutcome markDelivered(UUID instanceUuid, long nowEpochMillis) {
        Objects.requireNonNull(instanceUuid, "instanceUuid");
        if (readOnlyFutureSchema) return MarkDeliveredOutcome.READ_ONLY_SCHEMA;

        BlessedDeliveryReceipt existing = receipts.get(instanceUuid);
        if (existing == null) return MarkDeliveredOutcome.NOT_FOUND;
        return switch (existing.status()) {
            case DELIVERED -> MarkDeliveredOutcome.ALREADY_DELIVERED;
            case DESTROYED -> MarkDeliveredOutcome.ALREADY_DESTROYED;
            case PENDING_PHYSICAL_DELIVERY -> {
                receipts.put(instanceUuid,
                    existing.withStatus(BlessedDeliveryReceiptStatus.DELIVERED, nowEpochMillis));
                setDirty();
                yield MarkDeliveredOutcome.MARKED;
            }
        };
    }

    /**
     * Convenience overload stamping {@link System#currentTimeMillis()}. The explicit-timestamp
     * overload above is the primary one: {@code updatedEpochMillis} is a real, asserted-on field,
     * and a store that could only ever read a wall clock it did not own would be untestable at
     * exactly the point where its correctness matters.
     */
    public MarkDeliveredOutcome markDelivered(UUID instanceUuid) {
        return markDelivered(instanceUuid, System.currentTimeMillis());
    }

    /**
     * The physical item is positively, terminally gone: transitions an existing receipt to
     * {@link BlessedDeliveryReceiptStatus#DESTROYED}, re-stamping {@code updatedEpochMillis}.
     * Idempotent -- a repeat call is {@link MarkDestroyedOutcome#ALREADY_DESTROYED} and preserves
     * the original destruction timestamp. Never fabricates a receipt; see {@link
     * MarkDestroyedOutcome} for what each answer obliges the caller to do.
     *
     * <h2>Both non-terminal states are valid predecessors, deliberately</h2>
     * {@link BlessedDeliveryReceiptStatus#DELIVERED} to {@code DESTROYED} is the ordinary path:
     * the item existed, and now it demonstrably does not.
     *
     * <p>{@link BlessedDeliveryReceiptStatus#PENDING_PHYSICAL_DELIVERY} to {@code DESTROYED} is
     * ALSO allowed, and that is a considered choice rather than an oversight. A pending receipt
     * means "the item may or may not have landed" -- a crash mid-materialization leaves exactly
     * that ambiguity -- and a positively-observed destruction resolves the ambiguity in the only
     * direction it can be resolved: the item did land, and it is now gone. Refusing the
     * transition would leave that receipt sitting in {@code pendingDelivery} forever, where it is
     * a standing invitation to a reconciliation pass to "finish" a delivery for an item that has
     * already been destroyed -- manufacturing a second physical medallion out of a bookkeeping
     * refusal. The pending bucket is the dangerous bucket precisely because it is a delivery
     * candidate list; letting a confirmed terminal outcome empty it is safer than preserving a
     * tidy state machine.
     *
     * <p>What protects against a wrongly-written {@code DESTROYED} is therefore not this method
     * but the caller's evidence bar, which {@link BlessedDeliveryReceiptStatus#DESTROYED}'s own
     * docs state: a positively-observed destruction, never a failed search. This store cannot
     * check that bar and does not pretend to.
     */
    public MarkDestroyedOutcome markDestroyed(UUID instanceUuid, long nowEpochMillis) {
        Objects.requireNonNull(instanceUuid, "instanceUuid");
        if (readOnlyFutureSchema) return MarkDestroyedOutcome.READ_ONLY_SCHEMA;

        BlessedDeliveryReceipt existing = receipts.get(instanceUuid);
        if (existing == null) return MarkDestroyedOutcome.NOT_FOUND;
        if (existing.status() == BlessedDeliveryReceiptStatus.DESTROYED) {
            return MarkDestroyedOutcome.ALREADY_DESTROYED;
        }

        receipts.put(instanceUuid, existing.withStatus(BlessedDeliveryReceiptStatus.DESTROYED, nowEpochMillis));
        setDirty();
        return MarkDestroyedOutcome.MARKED;
    }

    /** Convenience overload stamping {@link System#currentTimeMillis()}. */
    public MarkDestroyedOutcome markDestroyed(UUID instanceUuid) {
        return markDestroyed(instanceUuid, System.currentTimeMillis());
    }

    public Optional<BlessedDeliveryReceipt> find(UUID instanceUuid) {
        return Optional.ofNullable(receipts.get(instanceUuid));
    }

    /**
     * The startup/reconciliation scan, split into pending-delivery, delivered and destroyed
     * buckets (see {@link ScanResult} for why each needs different handling), plus every entry
     * that exists but could not be parsed at all as its own distinct {@link UnreadableEntry}
     * category.
     *
     * <p>The sort below is an exhaustive switch over the status, not an
     * {@code if DELIVERED else pending} test, and that is load-bearing: the {@code else} form
     * silently classifies every future status as a live delivery candidate, which is how adding
     * {@link BlessedDeliveryReceiptStatus#DESTROYED} would otherwise have handed reconciliation a
     * destroyed materialization to re-deliver. An exhaustive switch over an enum with no default
     * makes the next such addition a compile error instead.
     */
    public ScanResult scan() {
        List<BlessedDeliveryReceipt> pendingDelivery = new ArrayList<>();
        List<BlessedDeliveryReceipt> delivered = new ArrayList<>();
        List<BlessedDeliveryReceipt> destroyed = new ArrayList<>();
        for (BlessedDeliveryReceipt receipt : receipts.values()) {
            switch (receipt.status()) {
                case PENDING_PHYSICAL_DELIVERY -> pendingDelivery.add(receipt);
                case DELIVERED -> delivered.add(receipt);
                case DESTROYED -> destroyed.add(receipt);
            }
        }
        return new ScanResult(
            List.copyOf(pendingDelivery),
            List.copyOf(delivered),
            List.copyOf(destroyed),
            List.copyOf(unreadable));
    }

    public boolean isReadOnlyFutureSchema() {
        return readOnlyFutureSchema;
    }

    /**
     * Test-only seam mirroring the banking precedent's own: forces an {@link UnreadableEntry}
     * into this live instance without going through {@link #load}. A real unreadable entry can
     * otherwise only be produced by loading genuinely corrupt NBT from disk, and {@code
     * get(ServerLevel)} caches the first-loaded instance for the rest of a server session, so a
     * GameTest cannot force a fresh reload mid-batch.
     */
    public void addUnreadableEntryForTesting(UnreadableEntry entry) {
        unreadable.add(Objects.requireNonNull(entry, "entry"));
    }

    /**
     * A schema-version mismatch means this store cannot trust its understanding of the data's
     * shape, so it never resumes normal parsing -- but it still makes a best effort to enumerate
     * whatever compound entries exist under "Receipts", so a scan is never silently empty just
     * because the schema moved on. If even that shape is unrecognizable, one single whole-store
     * {@link UnreadableEntry} is recorded instead, so a caller still sees "something here needs
     * attention" rather than nothing at all.
     */
    private BlessedDeliveryReceiptStore readOnly(CompoundTag tag, String reason) {
        readOnlyFutureSchema = true;
        futureRoot = tag.copy();
        receipts.clear();
        unreadable.clear();

        if (tag.get(KEY_RECEIPTS) instanceof ListTag list && !list.isEmpty()
                && list.getElementType() == Tag.TAG_COMPOUND
                && list.size() <= MAX_COLLECTION_ENTRIES) {
            // The size cap matters here too, not just on the normal-schema load() path: a store
            // that went read-only *because* its Receipts collection was over the limit must not
            // have this best-effort enumeration defeat the exact protection that cap exists for.
            for (int index = 0; index < list.size(); index++) {
                unreadable.add(new UnreadableEntry(reason, list.getCompound(index).copy()));
            }
        } else {
            unreadable.add(new UnreadableEntry(reason, tag.copy()));
        }

        LOGGER.error("Blessed delivery receipt store is unsupported ({}); store is read-only", reason);
        return this;
    }

    private void checkWarningThreshold() {
        if (!warnedLargeReceipts && receipts.size() >= MAX_COLLECTION_ENTRIES) {
            warnedLargeReceipts = true;
            LOGGER.warn("Blessed delivery receipt store contains {} receipts", receipts.size());
        }
    }
}

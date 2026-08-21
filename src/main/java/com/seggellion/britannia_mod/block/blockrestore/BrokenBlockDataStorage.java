package com.seggellion.britannia_mod.blockrestore;

import com.seggellion.britannia_mod.resource.Resources;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every pending restoration in one dimension, indexed by the chunk it lives in.
 *
 * <h2>Why the index exists</h2>
 * The old layout was one flat map of every debt in the dimension, and the scheduler walked all of
 * it on every server tick — twenty times a second, forever, including debts in chunks nobody had
 * loaded for months. With ten thousand outstanding debts that is two hundred thousand map visits
 * per second to discover that none of them are ready.
 *
 * <p>Grouping by {@link ChunkPos} is what makes the event-driven scheduler possible: a chunk load
 * can ask for exactly that chunk's debts, and nothing else is touched. {@link #debtsIn} is that
 * question, and it is the only access the scheduler uses.
 *
 * <p>{@link #getBrokenBlocks()} still returns a flat view, because diagnostics and tests want one
 * and there is no reason to make them reconstruct it. It is O(all debts) and deliberately not used
 * by anything on a tick path.
 *
 * <h2>Schema 2 and the legacy migration</h2>
 * A file with no {@code schema} field is the pre-milestone-4 flat layout. It is read, regrouped by
 * chunk, and given the due time it already implicitly had — see {@link #migrateLegacyDueTime}. No
 * debt is dropped, no chunk is loaded to do it, and saving afterwards writes only schema 2.
 */
public class BrokenBlockDataStorage extends SavedData {

    private static final String DATA_NAME = "broken_blocks";
    /** 1 was the flat layout; it had no marker, so its absence is how it is recognised. */
    public static final int SCHEMA = 2;

    private final Map<ChunkPos, Map<BlockPos, BrokenBlockData>> byChunk = new LinkedHashMap<>();
    private int total;
    private boolean migratedFromLegacy;

    /**
     * The level this store belongs to, when it was obtained through {@link #get}.
     *
     * <p>Held so that {@link #add} can tell the scheduler about a new debt. Any caller that records
     * a debt must make the scheduler aware of it, or a debt added to an already-loaded chunk would
     * wait for that chunk to cycle before anything looked at it. Putting that here rather than in
     * one caller means every caller gets it, including tests.
     *
     * <p>Null for a store constructed directly, which is how the unit tests drive the scheduler
     * explicitly without one.
     */
    private transient ServerLevel owner;

    public BrokenBlockDataStorage() {
        super();
    }

    public BrokenBlockDataStorage(CompoundTag tag, HolderLookup.Provider provider) {
        super();
        if (tag.contains("schema") && tag.getInt("schema") == SCHEMA) {
            readSchemaTwo(tag);
        } else {
            readLegacyFlat(tag);
            migratedFromLegacy = true;
            // Nothing is written here: SavedData persists when something marks it dirty, and the
            // migration itself is a change worth keeping even if no debt is touched afterwards.
            setDirty();
        }
    }

    private void readSchemaTwo(CompoundTag tag) {
        ListTag chunks = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (Tag chunkTag : chunks) {
            CompoundTag chunk = (CompoundTag) chunkTag;
            ChunkPos pos = new ChunkPos(chunk.getInt("cx"), chunk.getInt("cz"));
            for (Tag entry : chunk.getList("entries", Tag.TAG_COMPOUND)) {
                insert(pos, BrokenBlockData.fromNbt((CompoundTag) entry));
            }
        }
    }

    /**
     * The pre-milestone-4 layout: one flat {@code blocks} list, no due times, no ownership.
     *
     * <p>Every entry survives. What it cannot supply — which deposit owned the cell — is left
     * genuinely absent rather than guessed at, so diagnostics can say "legacy, unowned" instead of
     * asserting a provenance that was never recorded.
     */
    private void readLegacyFlat(CompoundTag tag) {
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (Tag item : list) {
            BrokenBlockData data = BrokenBlockData.fromNbt((CompoundTag) item);
            insert(new ChunkPos(data.pos), data.withDueAt(migrateLegacyDueTime(data)));
        }
    }

    /**
     * The due moment a legacy record already had.
     *
     * <p>Not the historical six-hour constant. Milestone 2 made the delay resolve from the
     * resource definition at the moment the scheduler looked at a record, so a pending silica bed
     * in a pre-milestone-4 file was <em>already</em> owed back after 24 hours rather than six —
     * that was live behaviour, not something this migration introduces. Computing the same answer
     * once, here, keeps every existing debt due at exactly the moment it was due before.
     *
     * <p>A block that is no longer a managed resource falls back to the historical constant, which
     * is what the scheduler did for it too.
     */
    private static long migrateLegacyDueTime(BrokenBlockData data) {
        long delay = Resources.regenerationMillis(data.originalState)
                .orElse(BrokenBlockData.DEFAULT_RESTORE_DELAY);
        return data.brokenTime + delay;
    }

    public static BrokenBlockDataStorage get(ServerLevel level) {
        BrokenBlockDataStorage storage = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                BrokenBlockDataStorage::new,
                BrokenBlockDataStorage::new
            ),
            DATA_NAME
        );
        storage.owner = level;
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("schema", SCHEMA);
        ListTag chunks = new ListTag();
        for (Map.Entry<ChunkPos, Map<BlockPos, BrokenBlockData>> entry : byChunk.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag chunk = new CompoundTag();
            chunk.putInt("cx", entry.getKey().x);
            chunk.putInt("cz", entry.getKey().z);
            ListTag entries = new ListTag();
            for (BrokenBlockData data : entry.getValue().values()) {
                entries.add(data.toNbt());
            }
            chunk.put("entries", entries);
            chunks.add(chunk);
        }
        tag.put("chunks", chunks);
        return tag;
    }

    /* ------------------------------------------------------------------ */
    /*  Indexed access -- the only thing the scheduler uses                */
    /* ------------------------------------------------------------------ */

    /** This chunk's debts, and nothing else's. Empty when the chunk has none. */
    public List<BrokenBlockData> debtsIn(ChunkPos chunk) {
        Map<BlockPos, BrokenBlockData> entries = byChunk.get(chunk);
        return entries == null || entries.isEmpty()
                ? List.of()
                : new ArrayList<>(entries.values());
    }

    /** Whether this chunk has any debts at all, without building a list. */
    public boolean hasDebtsIn(ChunkPos chunk) {
        Map<BlockPos, BrokenBlockData> entries = byChunk.get(chunk);
        return entries != null && !entries.isEmpty();
    }

    public BrokenBlockData debtAt(BlockPos pos) {
        Map<BlockPos, BrokenBlockData> entries = byChunk.get(new ChunkPos(pos));
        return entries == null ? null : entries.get(pos);
    }

    /** Chunks holding at least one debt. Diagnostics and migration tests. */
    public List<ChunkPos> chunksWithDebts() {
        List<ChunkPos> chunks = new ArrayList<>();
        byChunk.forEach((chunk, entries) -> {
            if (!entries.isEmpty()) chunks.add(chunk);
        });
        return chunks;
    }

    /** Total outstanding debts. Maintained, so it costs nothing to ask. */
    public int totalCount() {
        return total;
    }

    /** Whether this dimension's file was read from the pre-milestone-4 layout. */
    public boolean migratedFromLegacy() {
        return migratedFromLegacy;
    }

    /* ------------------------------------------------------------------ */
    /*  Flat view -- diagnostics and tests only, never a tick path         */
    /* ------------------------------------------------------------------ */

    /**
     * Every debt in the dimension, flattened.
     *
     * <p>O(all debts), which is exactly the cost the scheduler used to pay every tick. Kept because
     * commands and tests genuinely want the whole picture; use {@link #debtsIn} anywhere that runs
     * more than occasionally.
     */
    public Map<BlockPos, BrokenBlockData> getBrokenBlocks() {
        Map<BlockPos, BrokenBlockData> flat = new LinkedHashMap<>(Math.max(16, total));
        for (Map<BlockPos, BrokenBlockData> entries : byChunk.values()) {
            flat.putAll(entries);
        }
        return Collections.unmodifiableMap(flat);
    }

    /* ------------------------------------------------------------------ */
    /*  Mutation                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Record a debt, and make sure something will look at it.
     *
     * <p>The second half matters as much as the first. A debt added to a chunk that is already
     * loaded would otherwise sit unwatched until that chunk happened to unload and load again,
     * because the scheduler only reads a chunk's debts when it activates.
     */
    public void add(BrokenBlockData data) {
        ChunkPos chunk = new ChunkPos(data.pos);
        insert(chunk, data);
        setDirty();
        // Only tell the scheduler about a debt whose chunk is genuinely loaded. Watching an
        // unloaded chunk would be a lie -- the scheduler treats "active" as "this chunk is here,
        // its cells can be looked at" -- and it would quietly resurrect the very behaviour this
        // milestone removed, where debts in chunks nobody had loaded still cost something.
        if (owner != null && owner.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
            RestorationScheduler.of(owner).offer(this, data);
        }
    }

    public void remove(BlockPos pos) {
        Map<BlockPos, BrokenBlockData> entries = byChunk.get(new ChunkPos(pos));
        if (entries == null) return;
        if (entries.remove(pos) != null) {
            total--;
            if (entries.isEmpty()) {
                byChunk.remove(new ChunkPos(pos));
            }
            setDirty();
        }
    }

    /** Replace a debt in place, as the backoff does. Position must be unchanged. */
    /**
     * Replace a debt in place, as the backoff does. Position must be unchanged.
     *
     * <p>Deliberately does not notify the scheduler: the only caller is the scheduler itself, which
     * queues the replacement entry as part of backing off. Notifying here would queue it twice.
     */
    public void replace(BrokenBlockData data) {
        Map<BlockPos, BrokenBlockData> entries = byChunk.get(new ChunkPos(data.pos));
        if (entries == null || !entries.containsKey(data.pos)) return;
        entries.put(data.pos, data);
        setDirty();
    }

    /**
     * Forget every debt in this dimension.
     *
     * <p>An explicit operation rather than {@code getBrokenBlocks().clear()}, which is what
     * {@code /brokenblocks destroy_all} used to do: the flat view is now a copy, so clearing it
     * would have quietly cleared nothing. Any queued scheduler entry for a forgotten debt is
     * discarded when it surfaces, because the debt is no longer there to be found.
     */
    public void clearAll() {
        byChunk.clear();
        total = 0;
        setDirty();
    }

    private void insert(ChunkPos chunk, BrokenBlockData data) {
        Map<BlockPos, BrokenBlockData> entries =
                byChunk.computeIfAbsent(chunk, key -> new HashMap<>());
        if (entries.put(data.pos, data) == null) {
            total++;
        }
    }
}

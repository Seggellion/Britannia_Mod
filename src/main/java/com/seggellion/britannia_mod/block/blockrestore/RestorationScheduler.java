package com.seggellion.britannia_mod.blockrestore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which restoration is next, per dimension.
 *
 * <h2>What this replaces</h2>
 * A loop that walked every debt in every dimension on every server pre-tick — twenty times a
 * second, whether or not anything was due, whether or not the chunk was loaded, forever. Its cost
 * grew with the number of debts the world had ever accumulated.
 *
 * <p>Nothing here scales with that number. Debts live in {@link BrokenBlockDataStorage}, indexed by
 * chunk, and only become <em>active</em> when their chunk loads. The active set is a due-time
 * priority queue, so a pass looks at the head and stops as soon as the head is not ready. Ten
 * thousand debts in unloaded chunks cost exactly nothing per tick, and that is measured rather
 * than asserted — see {@link #debtsInspected()}.
 *
 * <pre>
 *   chunk load    -> read that chunk's debts, push the ones worth watching
 *   every cadence -> drain the queue head while it is ready, up to a budget
 *   blocked cell  -> back off, re-queue, leave the economic due time alone
 *   chunk unload  -> deactivate that chunk; the debts stay on disk
 * </pre>
 *
 * <h2>Stale entries</h2>
 * Unloading does not walk the queue; it drops the chunk from {@link #active}. An entry whose chunk
 * is no longer active is discarded when it surfaces. That stays bounded because an entry is only
 * ever queued by a chunk load, so the queue can hold at most one entry per debt per load, and a
 * discarded entry is O(1) to reject.
 */
public final class RestorationScheduler {

    /**
     * How often a pass runs, in ticks. One second.
     *
     * <p>These are six-hour and twenty-four-hour economic timers; sub-second precision on when a
     * vein returns is worth nothing, and the old twenty-passes-a-second was the entire problem.
     */
    public static final int CADENCE_TICKS = 20;

    /** Restorations one pass may perform. A mass chunk-load drains over several passes instead. */
    public static final int WORK_BUDGET = 64;

    /** First backoff after a blocked cell, doubling to {@link #MAX_BACKOFF_MILLIS}. */
    public static final long BASE_BACKOFF_MILLIS = 30_000L;
    /** Ceiling on the backoff. A permanently flooded cell is checked twice an hour, not constantly. */
    public static final long MAX_BACKOFF_MILLIS = 30L * 60L * 1000L;

    private static final Map<ServerLevel, RestorationScheduler> BY_LEVEL = new ConcurrentHashMap<>();

    /** One queued look at one debt. Compared by when it may next be tried. */
    private record Entry(long at, BlockPos pos, ChunkPos chunk) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return Long.compare(at, other.at);
        }
    }

    private final PriorityQueue<Entry> queue = new PriorityQueue<>();
    private final Set<ChunkPos> active = new HashSet<>();

    /** Instrumentation: how many stored debts this scheduler has actually looked at. */
    private long debtsInspected;
    /** Instrumentation: how many chunk activations have happened. */
    private long chunkActivations;

    private RestorationScheduler() {
    }

    public static RestorationScheduler of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, ignored -> new RestorationScheduler());
    }

    /** Drops every scheduler. Server stop, and test isolation. */
    public static void clearAll() {
        BY_LEVEL.clear();
    }

    /* ------------------------------------------------------------------ */
    /*  Chunk lifecycle                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * A chunk became available: queue its debts, and only its debts.
     *
     * <p>Costs the number of debts in this one chunk. Loading a chunk in a world with debts in ten
     * thousand other chunks does not touch any of them.
     */
    public void activate(BrokenBlockDataStorage storage, ChunkPos chunk) {
        if (!active.add(chunk)) {
            return;
        }
        chunkActivations++;
        if (!storage.hasDebtsIn(chunk)) {
            return;
        }
        for (BrokenBlockData debt : storage.debtsIn(chunk)) {
            debtsInspected++;
            queue.add(new Entry(debt.effectiveTime(), debt.pos, chunk));
        }
    }

    /**
     * A chunk went away: stop watching it.
     *
     * <p>The debts are untouched on disk and remain owed. Nothing walks the queue here — an entry
     * for a deactivated chunk is dropped when it surfaces, which keeps unload O(1).
     */
    public void deactivate(ChunkPos chunk) {
        active.remove(chunk);
    }

    /** Whether this chunk's debts are currently being watched. */
    public boolean isActive(ChunkPos chunk) {
        return active.contains(chunk);
    }

    /**
     * A debt was just recorded: start watching it now rather than waiting for a chunk reload.
     *
     * <p>The cell was mined, so its chunk is certainly loaded — but the scheduler may never have
     * seen a load event for it, because the chunk could have been loaded before this handler
     * registered. In that case the chunk is activated properly, which also picks up any other
     * debts it was already carrying, rather than queuing this one debt and leaving its neighbours
     * invisible until the chunk happens to cycle.
     */
    public void offer(BrokenBlockDataStorage storage, BrokenBlockData debt) {
        ChunkPos chunk = new ChunkPos(debt.pos);
        if (active.contains(chunk)) {
            queue.add(new Entry(debt.effectiveTime(), debt.pos, chunk));
        } else {
            activate(storage, chunk);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  The pass                                                           */
    /* ------------------------------------------------------------------ */

    /** What one pass did, so callers can report it and tests can assert on it. */
    public record PassResult(int restored, int blocked, int discarded) {
        public boolean didAnything() {
            return restored > 0 || blocked > 0;
        }
    }

    /**
     * Restore what is ready, up to the budget.
     *
     * <p>Stops at the first entry that is not yet ready, because the queue is ordered by time —
     * so a pass with nothing to do costs one comparison, not a walk.
     */
    public PassResult runPass(
            BrokenBlockDataStorage storage, long now, RestorationAttempt attempt) {
        int restored = 0;
        int blocked = 0;
        int discarded = 0;

        while (restored + blocked < WORK_BUDGET) {
            Entry head = queue.peek();
            if (head == null || head.at() > now) {
                break;
            }
            queue.poll();

            if (!active.contains(head.chunk())) {
                discarded++;
                continue;
            }
            BrokenBlockData debt = storage.debtAt(head.pos());
            if (debt == null) {
                discarded++;
                continue;
            }
            debtsInspected++;
            if (debt.effectiveTime() > now) {
                // Backed off since this entry was queued; the newer entry is already in the queue.
                discarded++;
                continue;
            }

            if (attempt.tryRestore(debt)) {
                storage.remove(debt.pos);
                restored++;
            } else {
                BrokenBlockData backedOff = debt.blockedUntil(now + backoffFor(debt.retryCount));
                storage.replace(backedOff);
                queue.add(new Entry(backedOff.effectiveTime(), backedOff.pos, head.chunk()));
                blocked++;
            }
        }
        return new PassResult(restored, blocked, discarded);
    }

    /** Doubling backoff from 30 seconds, capped at half an hour. */
    public static long backoffFor(int previousRetries) {
        long backoff = BASE_BACKOFF_MILLIS << Math.min(previousRetries, 6);
        return Math.min(backoff, MAX_BACKOFF_MILLIS);
    }

    /**
     * What the scheduler asks of the world.
     *
     * <p>The scheduler itself never touches a level. It owns ordering, budget and backoff; whether
     * a particular cell may actually take its block back is {@code BlockRestoreHandler}'s question,
     * and it closes over the level to answer it. Keeping the level out of here is also what lets
     * the scheduler's ordering, budget and backoff be tested exhaustively without a world.
     */
    @FunctionalInterface
    public interface RestorationAttempt {
        /** True when the cell was restored; false when it was blocked and should back off. */
        boolean tryRestore(BrokenBlockData debt);
    }

    /* ------------------------------------------------------------------ */
    /*  Diagnostics and instrumentation                                    */
    /* ------------------------------------------------------------------ */

    /** Debts currently queued for watching. Not the number owed — that is the storage's total. */
    public int queuedCount() {
        return queue.size();
    }

    public int activeChunkCount() {
        return active.size();
    }

    /**
     * How many stored debts this scheduler has read, ever.
     *
     * <p>The number that proves the complexity claim. It rises when a chunk activates and when a
     * queued entry comes up for its turn; it does <b>not</b> rise on a pass that finds nothing
     * ready, however many debts the dimension is carrying. A scale test asserts on it directly
     * rather than on wall-clock time.
     */
    public long debtsInspected() {
        return debtsInspected;
    }

    public long chunkActivations() {
        return chunkActivations;
    }

    /** The next moment anything queued could happen, or empty when nothing is queued. */
    public java.util.OptionalLong nextDueAt() {
        Entry head = queue.peek();
        return head == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(head.at());
    }

    /** Test seam: forget queued work without touching what is owed on disk. */
    public void resetForTesting() {
        queue.clear();
        active.clear();
        debtsInspected = 0;
        chunkActivations = 0;
    }

    /** Every active chunk, for diagnostics. */
    public List<ChunkPos> activeChunks() {
        return new ArrayList<>(active);
    }
}

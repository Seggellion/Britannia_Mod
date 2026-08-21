package com.seggellion.britannia_mod.blockrestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 4: the scheduler's ordering, budget, activation and backoff.
 *
 * <p>None of this needs a world. The scheduler owns <em>when</em> a restoration is attempted; the
 * handler owns <em>whether</em> the cell will take it. Keeping the level out of the scheduler is
 * what lets the complexity claim be proved by counters rather than by a stopwatch.
 */
class RestorationSchedulerTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long HOUR = 60L * 60L * 1000L;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** {@code RestorationScheduler} is only constructed through a level, so tests reach for one. */
    private static RestorationScheduler newScheduler() throws Exception {
        Constructor<RestorationScheduler> constructor =
                RestorationScheduler.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static BrokenBlockData debt(BlockPos pos, long dueAt) {
        return new BrokenBlockData(pos, Blocks.STONE.defaultBlockState(), dueAt - 6 * HOUR,
                UUID.nameUUIDFromBytes("miner".getBytes()), dueAt, 0L, "britannia_mod:stone", 0L, 0);
    }

    /** Every cell in its own chunk, so "one chunk's debts" is unambiguous. */
    private static BlockPos posInChunk(int chunkX, int chunkZ) {
        return new BlockPos(chunkX * 16 + 4, 60, chunkZ * 16 + 4);
    }

    /* ------------------------------------------------------------------ */
    /*  Activation                                                         */
    /* ------------------------------------------------------------------ */

    /** A chunk load reads that chunk's debts and no others. */
    @Test
    void activatingAChunkReadsOnlyThatChunksDebts() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        for (int i = 0; i < 50; i++) {
            storage.add(debt(posInChunk(i, 0), NOW));
        }
        RestorationScheduler scheduler = newScheduler();

        scheduler.activate(storage, new ChunkPos(7, 0));

        assertEquals(1, scheduler.queuedCount(), "only the loaded chunk's debt may be queued");
        assertEquals(1, scheduler.debtsInspected(),
                "activating one chunk must read one debt, not fifty");
        assertTrue(scheduler.isActive(new ChunkPos(7, 0)));
        assertFalse(scheduler.isActive(new ChunkPos(8, 0)));
    }

    /** Activating a chunk with no debts costs a lookup and nothing more. */
    @Test
    void activatingAnEmptyChunkReadsNothing() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        storage.add(debt(posInChunk(0, 0), NOW));
        RestorationScheduler scheduler = newScheduler();

        scheduler.activate(storage, new ChunkPos(99, 99));

        assertEquals(0, scheduler.queuedCount());
        assertEquals(0, scheduler.debtsInspected());
    }

    /** Activation is idempotent: a chunk loaded twice does not queue its debts twice. */
    @Test
    void reactivatingAnAlreadyActiveChunkDoesNothing() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        storage.add(debt(posInChunk(0, 0), NOW));
        RestorationScheduler scheduler = newScheduler();

        scheduler.activate(storage, new ChunkPos(0, 0));
        scheduler.activate(storage, new ChunkPos(0, 0));

        assertEquals(1, scheduler.queuedCount());
        assertEquals(1, scheduler.chunkActivations());
    }

    /* ------------------------------------------------------------------ */
    /*  Unload                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Unloading stops the watching but keeps the debt.
     *
     * <p>The queue is not walked on unload — that would make unload cost the queue's size. The stale
     * entry is discarded when it surfaces, which is what {@code discarded} counts.
     */
    @Test
    void unloadingAChunkStopsItsWorkWithoutLosingTheDebt() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos pos = posInChunk(0, 0);
        storage.add(debt(pos, NOW - HOUR));
        RestorationScheduler scheduler = newScheduler();
        scheduler.activate(storage, new ChunkPos(0, 0));

        scheduler.deactivate(new ChunkPos(0, 0));
        RestorationScheduler.PassResult result =
                scheduler.runPass(storage, NOW, restored -> true);

        assertEquals(0, result.restored(), "a deactivated chunk must not be restored into");
        assertEquals(1, result.discarded(), "its stale queue entry is dropped when it surfaces");
        assertNotNull(storage.debtAt(pos), "the debt itself is still owed");
        assertEquals(1, storage.totalCount());
    }

    /** And when it loads again, its debts come back — overdue ones immediately. */
    @Test
    void anOverdueDebtCatchesUpWhenItsChunkLoadsAgain() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos pos = posInChunk(3, 3);
        storage.add(debt(pos, NOW - 10 * HOUR));
        RestorationScheduler scheduler = newScheduler();

        // Never loaded: nothing happens, however overdue it is.
        assertEquals(0, scheduler.runPass(storage, NOW, d -> true).restored());

        scheduler.activate(storage, new ChunkPos(3, 3));
        RestorationScheduler.PassResult result = scheduler.runPass(storage, NOW, d -> true);

        assertEquals(1, result.restored(), "an overdue debt must be eligible as soon as it is watched");
        assertEquals(0, storage.totalCount(), "a restored debt is settled");
    }

    /* ------------------------------------------------------------------ */
    /*  Ordering, due time and budget                                      */
    /* ------------------------------------------------------------------ */

    /** A debt that is not due yet is not touched, however long it has been watched. */
    @Test
    void aDebtThatIsNotDueIsNotRestored() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        storage.add(debt(posInChunk(0, 0), NOW + HOUR));
        RestorationScheduler scheduler = newScheduler();
        scheduler.activate(storage, new ChunkPos(0, 0));

        assertEquals(0, scheduler.runPass(storage, NOW, d -> true).restored());
        assertEquals(1, storage.totalCount());
        assertEquals(1, scheduler.runPass(storage, NOW + 2 * HOUR, d -> true).restored());
    }

    /**
     * A pass with nothing ready costs one comparison.
     *
     * <p>The single most important property here, and the reason the queue is ordered by time: the
     * old scheduler asked every debt in the world whether it was ready, twenty times a second.
     */
    @Test
    void aPassWithNothingReadyInspectsNothing() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        for (int i = 0; i < 500; i++) {
            storage.add(debt(posInChunk(i, 0), NOW + HOUR));
        }
        RestorationScheduler scheduler = newScheduler();
        for (int i = 0; i < 500; i++) {
            scheduler.activate(storage, new ChunkPos(i, 0));
        }
        long afterActivation = scheduler.debtsInspected();

        for (int pass = 0; pass < 100; pass++) {
            scheduler.runPass(storage, NOW, d -> true);
        }

        assertEquals(afterActivation, scheduler.debtsInspected(),
                "a hundred passes over five hundred watched-but-not-due debts must read none of them");
    }

    /** Restorations happen oldest-due first. */
    @Test
    void theEarliestDueDebtIsRestoredFirst() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos late = posInChunk(0, 0);
        BlockPos early = posInChunk(1, 0);
        storage.add(debt(late, NOW - HOUR));
        storage.add(debt(early, NOW - 5 * HOUR));
        RestorationScheduler scheduler = newScheduler();
        scheduler.activate(storage, new ChunkPos(0, 0));
        scheduler.activate(storage, new ChunkPos(1, 0));

        List<BlockPos> order = new ArrayList<>();
        scheduler.runPass(storage, NOW, d -> {
            order.add(d.pos);
            return true;
        });
        assertEquals(List.of(early, late), order);
    }

    /** A mass of overdue work drains over several bounded passes rather than one spike. */
    @Test
    void overdueWorkDrainsOverBoundedPasses() throws Exception {
        int debts = RestorationScheduler.WORK_BUDGET * 3 + 7;
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        RestorationScheduler scheduler = newScheduler();
        for (int i = 0; i < debts; i++) {
            storage.add(debt(posInChunk(i, 0), NOW - HOUR));
            scheduler.activate(storage, new ChunkPos(i, 0));
        }

        int passes = 0;
        int restored = 0;
        while (restored < debts && passes < 100) {
            RestorationScheduler.PassResult result = scheduler.runPass(storage, NOW, d -> true);
            assertTrue(result.restored() <= RestorationScheduler.WORK_BUDGET,
                    "a pass must never exceed its budget, did " + result.restored());
            restored += result.restored();
            passes++;
        }
        assertEquals(debts, restored);
        assertEquals(4, passes, "3 full budgets plus the remainder");
        assertEquals(0, storage.totalCount());
    }

    /* ------------------------------------------------------------------ */
    /*  Blocked debts and backoff                                          */
    /* ------------------------------------------------------------------ */

    /** A blocked cell is kept, backed off, and not attempted again immediately. */
    @Test
    void aBlockedDebtBacksOffInsteadOfHotLooping() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos pos = posInChunk(0, 0);
        storage.add(debt(pos, NOW - HOUR));
        RestorationScheduler scheduler = newScheduler();
        scheduler.activate(storage, new ChunkPos(0, 0));

        int[] attempts = {0};
        RestorationScheduler.PassResult first =
                scheduler.runPass(storage, NOW, d -> { attempts[0]++; return false; });

        assertEquals(0, first.restored());
        assertEquals(1, first.blocked());
        assertEquals(1, attempts[0]);

        // A hundred more passes at the same instant must not touch it again.
        for (int i = 0; i < 100; i++) {
            scheduler.runPass(storage, NOW, d -> { attempts[0]++; return false; });
        }
        assertEquals(1, attempts[0], "a blocked debt must not be retried on every pass");

        BrokenBlockData backedOff = storage.debtAt(pos);
        assertNotNull(backedOff, "a blocked debt is never dropped");
        assertEquals(1, backedOff.retryCount);
        assertEquals(NOW + RestorationScheduler.BASE_BACKOFF_MILLIS, backedOff.retryAt);
    }

    /**
     * Being blocked does not make a deposit economically younger.
     *
     * <p>The due moment is what the player is owed; the retry time is only when it is worth looking
     * again. Moving the first because of the second would quietly extend every flooded deposit's
     * timer for as long as the puddle lasted.
     */
    @Test
    void backingOffNeverMovesTheEconomicDueTime() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos pos = posInChunk(0, 0);
        long due = NOW - HOUR;
        storage.add(debt(pos, due));
        RestorationScheduler scheduler = newScheduler();
        scheduler.activate(storage, new ChunkPos(0, 0));

        long at = NOW;
        for (int i = 0; i < 5; i++) {
            scheduler.runPass(storage, at, d -> false);
            at = storage.debtAt(pos).retryAt;
        }
        BrokenBlockData debt = storage.debtAt(pos);
        assertEquals(due, debt.dueAt, "the due moment must not have moved");
        assertEquals(5, debt.retryCount);
    }

    /** The backoff doubles and then stops doubling. */
    @Test
    void backoffDoublesAndIsCapped() {
        assertEquals(RestorationScheduler.BASE_BACKOFF_MILLIS, RestorationScheduler.backoffFor(0));
        assertEquals(RestorationScheduler.BASE_BACKOFF_MILLIS * 2, RestorationScheduler.backoffFor(1));
        assertEquals(RestorationScheduler.BASE_BACKOFF_MILLIS * 4, RestorationScheduler.backoffFor(2));
        for (int retries = 6; retries < 1000; retries++) {
            assertEquals(RestorationScheduler.MAX_BACKOFF_MILLIS,
                    RestorationScheduler.backoffFor(retries),
                    "a permanently blocked cell must settle at the cap, not grow forever");
        }
    }

    /** Once the blocker is gone, the backed-off debt restores on its next turn. */
    @Test
    void aBlockedDebtRestoresOnceTheBlockerIsGone() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos pos = posInChunk(0, 0);
        storage.add(debt(pos, NOW - HOUR));
        RestorationScheduler scheduler = newScheduler();
        scheduler.activate(storage, new ChunkPos(0, 0));

        scheduler.runPass(storage, NOW, d -> false);
        long retryAt = storage.debtAt(pos).retryAt;

        assertEquals(1, scheduler.runPass(storage, retryAt, d -> true).restored());
        assertEquals(0, storage.totalCount());
    }

    /** Many permanently blocked debts do not dominate a pass: each costs one look per backoff. */
    @Test
    void manyPermanentlyBlockedDebtsDoNotDominateTheScheduler() throws Exception {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        RestorationScheduler scheduler = newScheduler();
        for (int i = 0; i < 200; i++) {
            storage.add(debt(posInChunk(i, 0), NOW - HOUR));
            scheduler.activate(storage, new ChunkPos(i, 0));
        }

        int[] attempts = {0};
        // Drain them all into backoff.
        for (int pass = 0; pass < 10; pass++) {
            scheduler.runPass(storage, NOW, d -> { attempts[0]++; return false; });
        }
        assertEquals(200, attempts[0], "each blocked debt is attempted once before backing off");

        // Now a hundred passes at the same instant cost nothing at all.
        for (int pass = 0; pass < 100; pass++) {
            scheduler.runPass(storage, NOW, d -> { attempts[0]++; return false; });
        }
        assertEquals(200, attempts[0], "backed-off debts must not be re-attempted every pass");
        assertEquals(200, storage.totalCount(), "and none of them is dropped");
    }

    /* ------------------------------------------------------------------ */
    /*  Scale                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Ten thousand debts, almost all in unloaded chunks, cost the scheduler nothing.
     *
     * <p>The complexity claim, proved by counters rather than by a stopwatch. The old scheduler
     * would have visited all ten thousand on every one of these passes — two hundred thousand map
     * visits a second — to discover that none of them were reachable.
     */
    @Test
    void tenThousandUnloadedDebtsAreNeverInspected() throws Exception {
        final int total = 10_000;
        final int loaded = 25;

        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        for (int i = 0; i < total; i++) {
            storage.add(debt(posInChunk(i, i % 97), NOW - HOUR));
        }
        assertEquals(total, storage.totalCount());

        RestorationScheduler scheduler = newScheduler();

        // Not one chunk loaded: a thousand passes must read nothing.
        for (int pass = 0; pass < 1_000; pass++) {
            RestorationScheduler.PassResult result = scheduler.runPass(storage, NOW, d -> true);
            assertEquals(0, result.restored());
        }
        assertEquals(0, scheduler.debtsInspected(),
                "a thousand passes over ten thousand unloaded debts must inspect none of them");
        assertEquals(total, storage.totalCount(), "and none of them is lost");

        // Now load a bounded handful. Only their debts become active.
        Set<BlockPos> expected = new HashSet<>();
        for (int i = 0; i < loaded; i++) {
            BlockPos pos = posInChunk(i, i % 97);
            expected.add(pos);
            scheduler.activate(storage, new ChunkPos(pos));
        }
        assertEquals(loaded, scheduler.debtsInspected(),
                "loading " + loaded + " chunks must read exactly " + loaded + " debts");
        assertEquals(loaded, scheduler.queuedCount());

        Set<BlockPos> restored = new HashSet<>();
        scheduler.runPass(storage, NOW, d -> {
            restored.add(d.pos);
            return true;
        });
        assertEquals(expected, restored, "only the loaded chunks' debts may be restored");
        assertEquals(total - loaded, storage.totalCount(),
                "the other " + (total - loaded) + " remain owed, untouched and unread");
    }
}

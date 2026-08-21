package com.seggellion.britannia_mod.blockrestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.deposit.DepositInstance;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 4: the pre-milestone-4 restoration file must survive being read.
 *
 * <p>These build the legacy layout by hand rather than depending on a checked-in binary, so the
 * fixture is readable, and so it is obvious exactly which fields the old format did and did not
 * carry. The old format is: a flat {@code blocks} list, no {@code schema}, no due time, no owner.
 */
class RestorationStorageMigrationTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final long HOUR = 60L * 60L * 1000L;

    /** A pre-milestone-4 file, exactly as the old {@code save} would have written it. */
    private static CompoundTag legacyFile(List<BlockPos> positions, Block block, long brokenTime) {
        CompoundTag root = new CompoundTag();
        ListTag blocks = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            entry.put("blockState", NbtUtils.writeBlockState(block.defaultBlockState()));
            entry.putLong("brokenTime", brokenTime);
            entry.putUUID("playerUUID", UUID.nameUUIDFromBytes("miner".getBytes()));
            blocks.add(entry);
        }
        root.put("blocks", blocks);
        // Deliberately no "schema": its absence is how the legacy layout is recognised.
        return root;
    }

    /* ------------------------------------------------------------------ */
    /*  Migration                                                          */
    /* ------------------------------------------------------------------ */

    @Test
    void everyLegacyDebtSurvivesAndKeepsItsBlockAndTimestamp() {
        long broken = 1_700_000_000_000L;
        List<BlockPos> positions = List.of(
                new BlockPos(4, 60, 4), new BlockPos(20, 61, 4), new BlockPos(-33, 12, 77));

        BrokenBlockDataStorage storage = new BrokenBlockDataStorage(
                legacyFile(positions, Blocks.STONE, broken), null);

        assertTrue(storage.migratedFromLegacy(), "a file with no schema must be recognised as legacy");
        assertEquals(3, storage.totalCount(), "no legacy debt may be dropped");
        for (BlockPos pos : positions) {
            BrokenBlockData debt = storage.debtAt(pos);
            assertNotNull(debt, "lost the debt at " + pos.toShortString());
            assertTrue(debt.originalState.is(Blocks.STONE), "the original block must survive");
            assertEquals(broken, debt.brokenTime, "the original timestamp must survive");
        }
    }

    /**
     * A migrated debt is due exactly when it was already due.
     *
     * <p>Not the historical six-hour constant. Milestone 2 made the delay resolve from the resource
     * definition every time the old scheduler looked at a record, so a pending silica bed in a
     * legacy file was <em>already</em> owed at 24 hours. The migration computes the same answer once
     * rather than changing anybody's timer.
     */
    @Test
    void migratedDueTimesMatchWhatTheOldSchedulerWouldHaveComputed() {
        long broken = 1_700_000_000_000L;

        BrokenBlockDataStorage stone = new BrokenBlockDataStorage(
                legacyFile(List.of(new BlockPos(1, 1, 1)), Blocks.STONE, broken), null);
        assertEquals(broken + 6 * HOUR, stone.debtAt(new BlockPos(1, 1, 1)).dueAt,
                "the ore ladder and the stone family keep six hours");

        BrokenBlockDataStorage dirt = new BrokenBlockDataStorage(
                legacyFile(List.of(new BlockPos(1, 1, 1)), Blocks.DIRT, broken), null);
        assertEquals(broken + 6 * HOUR, dirt.debtAt(new BlockPos(1, 1, 1)).dueAt,
                "a block the catalogue no longer governs falls back to the historical constant");
    }

    /** Migrated debts land in the chunk they belong to, which is what the scheduler indexes on. */
    @Test
    void migratedDebtsAreGroupedUnderTheCorrectChunk() {
        List<BlockPos> positions = List.of(
                new BlockPos(4, 60, 4),      // chunk 0,0
                new BlockPos(20, 61, 4),     // chunk 1,0
                new BlockPos(-33, 12, 77));  // chunk -3,4

        BrokenBlockDataStorage storage = new BrokenBlockDataStorage(
                legacyFile(positions, Blocks.STONE, 1L), null);

        assertEquals(1, storage.debtsIn(new ChunkPos(0, 0)).size());
        assertEquals(1, storage.debtsIn(new ChunkPos(1, 0)).size());
        assertEquals(1, storage.debtsIn(new ChunkPos(-3, 4)).size());
        assertTrue(storage.debtsIn(new ChunkPos(9, 9)).isEmpty());

        Set<ChunkPos> chunks = new HashSet<>(storage.chunksWithDebts());
        assertEquals(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(-3, 4)), chunks);
    }

    /**
     * The legacy format carried no deposit identity, so migrated debts have none.
     *
     * <p>Deliberately left absent rather than filled in. A derived id would be a guess written into
     * durable storage, and diagnostics could no longer tell a genuinely adopted deposit from an
     * invented one. Milestone 8's retrofit is what adopts these properly.
     */
    @Test
    void migratedDebtsAreUnownedRatherThanGivenAnInventedDeposit() {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage(
                legacyFile(List.of(new BlockPos(1, 1, 1)), Blocks.STONE, 1L), null);
        BrokenBlockData debt = storage.debtAt(new BlockPos(1, 1, 1));

        assertFalse(debt.owned(), "a legacy debt must not claim an owner it never had");
        assertEquals(DepositInstance.NO_INSTANCE, debt.instanceId);
        assertEquals("", debt.resourceId);
    }

    /* ------------------------------------------------------------------ */
    /*  Schema 2 round trip                                                */
    /* ------------------------------------------------------------------ */

    /** Saving a migrated file writes only the new layout. */
    @Test
    void savingAfterMigrationWritesSchemaTwoAndNotTheOldList() {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage(
                legacyFile(List.of(new BlockPos(4, 60, 4), new BlockPos(20, 61, 4)), Blocks.STONE, 1L), null);

        CompoundTag saved = storage.save(new CompoundTag(), null);

        assertEquals(BrokenBlockDataStorage.SCHEMA, saved.getInt("schema"));
        assertTrue(saved.contains("chunks"), "schema 2 groups by chunk");
        assertFalse(saved.contains("blocks"), "the flat legacy list must not be written back");
        assertEquals(2, saved.getList("chunks", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
    }

    /** And reading that back gives exactly the same debts, without a second migration. */
    @Test
    void schemaTwoRoundTripsAndIsNotTreatedAsLegacy() {
        BrokenBlockDataStorage first = new BrokenBlockDataStorage();
        BlockPos pos = new BlockPos(4, 60, 4);
        first.add(new BrokenBlockData(pos, Blocks.STONE.defaultBlockState(), 1_000L,
                UUID.nameUUIDFromBytes("miner".getBytes()), 5_000L, 0xABCDL, "britannia_mod:silver",
                7_000L, 3));

        BrokenBlockDataStorage reloaded =
                new BrokenBlockDataStorage(first.save(new CompoundTag(), null), null);

        assertFalse(reloaded.migratedFromLegacy(), "schema 2 must not be re-migrated");
        BrokenBlockData debt = reloaded.debtAt(pos);
        assertNotNull(debt);
        assertEquals(1_000L, debt.brokenTime);
        assertEquals(5_000L, debt.dueAt);
        assertEquals(0xABCDL, debt.instanceId);
        assertEquals("britannia_mod:silver", debt.resourceId);
        assertEquals(7_000L, debt.retryAt);
        assertEquals(3, debt.retryCount);
        assertTrue(debt.owned());
    }

    /** An empty file is not a broken one. */
    @Test
    void anEmptyLegacyFileMigratesToAnEmptySchemaTwoFile() {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage(legacyFile(List.of(), Blocks.STONE, 1L), null);
        assertEquals(0, storage.totalCount());
        CompoundTag saved = storage.save(new CompoundTag(), null);
        assertEquals(BrokenBlockDataStorage.SCHEMA, saved.getInt("schema"));
    }

    /* ------------------------------------------------------------------ */
    /*  Indexed access                                                     */
    /* ------------------------------------------------------------------ */

    /** Removing a debt clears its chunk when it was the last one, so the index stays tight. */
    @Test
    void removingTheLastDebtInAChunkDropsTheChunk() {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        BlockPos pos = new BlockPos(4, 60, 4);
        storage.add(new BrokenBlockData(pos, Blocks.STONE.defaultBlockState(), 1L, UUID.randomUUID()));

        assertTrue(storage.hasDebtsIn(new ChunkPos(0, 0)));
        storage.remove(pos);
        assertFalse(storage.hasDebtsIn(new ChunkPos(0, 0)));
        assertEquals(0, storage.totalCount());
        assertTrue(storage.chunksWithDebts().isEmpty());
    }

    /** The flat view is still available for diagnostics, and agrees with the index. */
    @Test
    void theFlatViewAgreesWithTheChunkIndex() {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        for (int i = 0; i < 40; i++) {
            storage.add(new BrokenBlockData(new BlockPos(i * 7, 60, i * 5),
                    Blocks.STONE.defaultBlockState(), 1L, UUID.randomUUID()));
        }
        assertEquals(40, storage.totalCount());
        assertEquals(40, storage.getBrokenBlocks().size());

        int viaChunks = 0;
        for (ChunkPos chunk : storage.chunksWithDebts()) {
            viaChunks += storage.debtsIn(chunk).size();
        }
        assertEquals(40, viaChunks, "the index must account for every debt the flat view shows");
    }
}

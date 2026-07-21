package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferOperationType;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipt;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import com.seggellion.britannia_mod.bank.transfer.BankTransferReceipts;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Milestone 8 NeoForge Slice 3: proves the durable pending-transfer receipt store's real,
 * synchronous, forced-flush-to-disk guarantee against an actual live {@link ServerLevel} and
 * real filesystem -- not just an in-memory NBT round-trip (that part is already covered by
 * {@code BankTransferReceiptStoreTest}, a plain JUnit test with no live server at all).
 *
 * "Simulating a crash" here means: force-write a receipt through the real synchronous-flush
 * path, then reconstruct a brand-new {@link BankTransferReceiptStore} by reading the raw bytes
 * directly off disk with a fresh {@code NbtIo} read -- deliberately bypassing the live,
 * still-cached in-memory instance sitting in this same server's {@code DimensionDataStorage}.
 * This is the closest proxy achievable inside a single GameTest process (which cannot itself
 * kill and restart the JVM mid-test): it proves the bytes that reached disk before any
 * "crash point" are independently correct and reloadable from cold, with no reliance on
 * anything still live in memory -- which is the actual substance of a "survives a restart"
 * claim. It does NOT and cannot prove the OS/hardware-level fsync contract itself (that a real
 * power-loss event would not lose the data) -- that rests on the documented behavior of
 * {@code FileChannel#force(true)}, not on anything a single-process test can observe; see
 * {@link BankTransferReceipts} for the precise, non-aspirational statement of what is and is
 * not guaranteed.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankTransferReceiptGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private BankTransferReceiptGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void recordForcesAnImmediateSynchronousDiskWriteNotDeferredToAutosave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID operationId = UUID.randomUUID();
        byte[] payload = {1, 2, 3, 4, 5};

        BankTransferReceiptStore.RecordOutcome outcome = BankTransferReceipts.record(
            level, operationId, BankTransferOperationType.DEPOSIT, payload, null, 1_000L
        );
        check(outcome == BankTransferReceiptStore.RecordOutcome.CREATED, "record() did not report CREATED");

        // No autosave tick has run and no shutdown has happened -- if this is on disk already,
        // it can only be because record() itself forced the write synchronously.
        BankTransferReceiptStore freshFromDisk = readFreshFromDisk(level);
        BankTransferReceipt onDisk = freshFromDisk.find(operationId);
        check(onDisk != null, "receipt was not on disk immediately after record() returned");
        check(java.util.Arrays.equals(payload, onDisk.itemPayload()), "on-disk receipt payload did not match what was recorded");

        BankTransferReceipts.resolve(level, operationId);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aReceiptSurvivesASimulatedCrashAndIsFoundByAFreshStartupScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID operationId = UUID.randomUUID();
        byte[] payload = {9, 8, 7};

        BankTransferReceipts.record(level, operationId, BankTransferOperationType.WITHDRAWAL, payload, null, 2_000L);
        // Deliberately never call resolve() -- this is the "crash after possible insertion,
        // before any resolve call" scenario Section A.6 describes.

        // Scoped to this test's own operationId, not the store's total unresolved count -- the
        // GameTest runner executes many tests concurrently against this same single,
        // server-wide store (confirmed directly: this assertion failed nondeterministically
        // from cross-test contamination once enough other concurrent receipt-writing tests
        // existed in the same batch, not from a real defect).
        BankTransferReceiptStore freshFromDisk = readFreshFromDisk(level);
        BankTransferReceipt survived = freshFromDisk.scanUnresolved().pending().stream()
            .filter(receipt -> receipt.operationId().equals(operationId))
            .findFirst()
            .orElse(null);
        check(survived != null, "expected this operation's receipt to be found unresolved after the simulated crash");
        check(survived.operationType() == BankTransferOperationType.WITHDRAWAL,
            "the recovered receipt's operation type did not survive the simulated crash");

        BankTransferReceipts.resolve(level, operationId);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCleanlyResolvedReceiptIsExcludedFromAFreshStartupScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID operationId = UUID.randomUUID();

        BankTransferReceipts.record(level, operationId, BankTransferOperationType.DEPOSIT, new byte[]{1}, null, 3_000L);
        boolean resolved = BankTransferReceipts.resolve(level, operationId);
        check(resolved, "resolve() did not report success for a receipt that was just recorded");

        // Scoped to this operationId specifically -- see the comment in
        // aReceiptSurvivesASimulatedCrashAndIsFoundByAFreshStartupScan for why an unscoped
        // check against this shared, server-wide store is unsafe under concurrent GameTest
        // batching.
        BankTransferReceiptStore freshFromDisk = readFreshFromDisk(level);
        check(freshFromDisk.find(operationId) == null,
            "a cleanly resolved receipt was still present in a fresh reload from disk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCorruptReceiptOnDiskIsSurfacedAsUnreadableNotSilentlyDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // What a real in-flight operation's receipt would look like if something corrupted it
        // after the fact -- an identity, but none of the fields BankTransferReceipt#fromNbt
        // requires. This must never be silently invisible to a startup scan: it might be
        // exactly the "crash after possible insertion" case Section A.6 describes.
        CompoundTag corruptReceipt = new CompoundTag();
        corruptReceipt.putUUID("OperationId", UUID.randomUUID());
        writeRawStoreFileForTest(level, BankTransferReceiptStore.SCHEMA_VERSION, List.of(corruptReceipt));

        BankTransferReceiptStore freshFromDisk = readFreshFromDisk(level);
        BankTransferReceiptStore.ScanResult scan = freshFromDisk.scanUnresolved();
        check(scan.pending().isEmpty(), "a corrupt entry must never be silently treated as a normal pending receipt");
        check(scan.unreadable().size() == 1, "the corrupt receipt was not surfaced as unreadable at all");
        check(!scan.isEmpty(), "a scan with an unreadable entry must not report itself as empty");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnsupportedFutureSchemaVersionEntryIsSurfacedAsUnreadableNotSilentlyDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // A receipt that would parse perfectly fine under today's schema -- the file on disk
        // is simply stamped with a schema version this build does not recognize yet.
        CompoundTag futureReceipt = new CompoundTag();
        futureReceipt.putUUID("OperationId", UUID.randomUUID());
        futureReceipt.putString("OperationType", "DEPOSIT");
        futureReceipt.putByteArray("ItemPayload", new byte[]{1, 2, 3});
        futureReceipt.putString("Status", "PENDING_LOCAL_ACTION");
        futureReceipt.putLong("CreatedAtEpochMillis", 5_000L);
        writeRawStoreFileForTest(level, 99, List.of(futureReceipt));

        BankTransferReceiptStore freshFromDisk = readFreshFromDisk(level);
        check(freshFromDisk.isReadOnlyFutureSchema(), "expected the store to recognize the unsupported schema version");
        BankTransferReceiptStore.ScanResult scan = freshFromDisk.scanUnresolved();
        check(scan.pending().isEmpty(), "a future-schema entry must never be silently treated as a normal pending receipt");
        check(scan.unreadable().size() == 1, "the future-schema entry was not surfaced as unreadable at all");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theStoreIsAnchoredToTheOverworldRegardlessOfWhichDimensionCallsIt(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        check(nether != null, "expected the Nether dimension to be available in the GameTest server");
        check(nether != overworld, "test is meaningless if the 'different dimension' level is actually the overworld");

        UUID operationId = UUID.randomUUID();
        BankTransferReceipts.record(nether, operationId, BankTransferOperationType.DEPOSIT, new byte[]{7}, null, 6_000L);

        BankTransferReceiptStore fromOverworld = BankTransferReceiptStore.get(overworld);
        BankTransferReceiptStore fromNether = BankTransferReceiptStore.get(nether);
        check(fromOverworld == fromNether, "get() returned two different store instances for the same server");
        check(fromOverworld.find(operationId) != null,
            "a receipt recorded via the Nether level was not visible through the overworld-anchored store");

        BankTransferReceipts.resolve(overworld, operationId);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aDepositAndAWithdrawalBothInFlightSurviveASimulatedCrashIndependently(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID depositId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();

        BankTransferReceipts.record(level, depositId, BankTransferOperationType.DEPOSIT, new byte[]{1, 1}, null, 4_000L);
        BankTransferReceipts.record(level, withdrawalId, BankTransferOperationType.WITHDRAWAL, new byte[]{2, 2}, null, 4_100L);
        BankTransferReceipts.resolve(level, depositId);
        // withdrawalId is deliberately left unresolved -- the simulated crash point.

        // Scoped to each of this test's own two operationIds -- see the comment in
        // aReceiptSurvivesASimulatedCrashAndIsFoundByAFreshStartupScan for why an unscoped
        // check against this shared, server-wide store is unsafe under concurrent GameTest
        // batching.
        BankTransferReceiptStore freshFromDisk = readFreshFromDisk(level);
        check(freshFromDisk.find(depositId) == null, "the resolved deposit leaked into the fresh startup scan");
        check(freshFromDisk.find(withdrawalId) != null, "the withdrawal did not survive the simulated crash");

        BankTransferReceipts.resolve(level, withdrawalId);
        helper.succeed();
    }

    /**
     * Writes a hand-crafted store file directly to the same path {@link #readFreshFromDisk}
     * reads from, bypassing the store entirely -- used to construct on-disk states (a corrupt
     * record, an unsupported schema version) the store's own API would never itself produce.
     */
    private static void writeRawStoreFileForTest(ServerLevel level, int schemaVersion, List<CompoundTag> receiptTags) {
        CompoundTag content = new CompoundTag();
        content.putInt("SchemaVersion", schemaVersion);
        ListTag list = new ListTag();
        receiptTags.forEach(list::add);
        content.put("Receipts", list);

        CompoundTag outer = new CompoundTag();
        outer.put("data", content);

        File dataFile = level.getServer().getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve(BankTransferReceiptStore.DATA_NAME + ".dat")
            .toFile();
        File parent = dataFile.getParentFile();
        if (parent != null) parent.mkdirs();
        try (var output = new FileOutputStream(dataFile)) {
            NbtIo.writeCompressed(outer, output);
        } catch (IOException exception) {
            throw new IllegalStateException("failed writing test bank transfer receipt store file", exception);
        }
    }

    /**
     * Deliberately does not go through {@link BankTransferReceiptStore#get}, which would just
     * hand back the same live, in-memory-cached instance this server already holds. Reads the
     * exact file {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData}'s
     * own {@code get(ServerLevel)} convention resolves to (the overworld's data storage), using
     * the same path {@link net.minecraft.world.level.storage.DimensionDataStorage} itself
     * writes to ({@code getWorldPath(LevelResource.ROOT)/data/<name>.dat}), and reconstructs a
     * brand-new store purely from those bytes -- proof the data does not depend on anything
     * still resident in this process's memory.
     */
    private static BankTransferReceiptStore readFreshFromDisk(ServerLevel level) {
        File dataFile = level.getServer().getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve(BankTransferReceiptStore.DATA_NAME + ".dat")
            .toFile();
        check(dataFile.isFile(), "expected a bank transfer receipt store file on disk at " + dataFile);

        CompoundTag outer;
        try (InputStream input = new FileInputStream(dataFile)) {
            outer = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new IllegalStateException("failed reading bank transfer receipt store file directly", exception);
        }
        return BankTransferReceiptStore.load(outer.getCompound("data"), level.registryAccess());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

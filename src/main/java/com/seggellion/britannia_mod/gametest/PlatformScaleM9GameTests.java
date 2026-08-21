package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.RestorationScheduler;
import com.seggellion.britannia_mod.commands.OreVeinDiagnosticsCommand;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Milestone 9: what the platform costs when it is full.
 *
 * <h2>Reading the numbers</h2>
 * Everything printed with an {@code M9SCALE} prefix goes into the milestone report. The assertions
 * beside them are the ones that would actually indicate a defect — a scheduler pass that walks
 * every debt, a ledger lookup that degrades with size, a materialisation that writes without a
 * budget — rather than pinned figures that move when a cadence is retuned.
 *
 * <h2>Why the debts have no blocks behind them</h2>
 * The scale tests register tens of thousands of restoration debts at coordinates no chunk is loaded
 * for. That is the point: the property being proved is that unloaded debt costs nothing per pass,
 * and giving each one a real block would both defeat the test and force chunks to load.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class PlatformScaleM9GameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    /** Far enough from any test structure that these coordinates are certainly not loaded. */
    private static final int FAR_AWAY = 6_000_000;

    private PlatformScaleM9GameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition silver() {
        return ResourceCatalog.instance().byId("britannia_mod:silver").orElseThrow();
    }

    private static void report(String line) {
        System.out.println("M9SCALE " + line);
    }

    /**
     * A scheduler of this test's own, not the level's.
     *
     * <p>{@code RestorationScheduler.of(level)} returns the instance the running server is using,
     * and loading a hundred thousand synthetic debts into it would leave that state behind for
     * every test that runs afterwards. The unit tests reach for the private constructor for the
     * same reason; this follows them.
     */
    private static RestorationScheduler isolatedScheduler() {
        try {
            var constructor = RestorationScheduler.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("could not build an isolated scheduler: " + failure);
        }
    }

    /** Serialized size of a SavedData payload, which is what a save actually writes. */
    private static int nbtBytes(CompoundTag tag) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
            return bytes.size();
        } catch (Exception failure) {
            throw new GameTestAssertException("could not serialise: " + failure);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Ledger at scale                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * A ledger holding thousands of deposits stays cheap to register into, look up and serialise.
     *
     * <p>Deliberately built against a fresh {@code DepositLedger} rather than the level's own, so
     * the measurement is of the ledger and not of whatever other tests have left behind.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void theLedgerHoldsThousandsOfDepositsCheaply(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        DepositLedger ledger = new DepositLedger();
        String dimension = level.dimension().location().toString();

        final int deposits = 4_000;
        List<Long> ids = new ArrayList<>(deposits);

        long registerStart = System.nanoTime();
        for (int i = 0; i < deposits; i++) {
            BlockPos origin = new BlockPos(FAR_AWAY + i * 64, -40, FAR_AWAY + (i % 97) * 64);
            long id = DepositIdentity.rails("scale-shard", dimension, silver().id(),
                    origin.getX(), origin.getY(), origin.getZ(), 4, ShapeRotation.XZ, "scale");
            PlannedDeposit plan = PlacementPlanner.plan(silver(), dimension, origin, 4,
                    ShapeRotation.XZ, DepositIdentity.plannerSeed(id));
            DepositLedger.Registration registration = ledger.register(
                    DepositRegistrar.describe(plan, id, DepositSource.RAILS, "rails|scale|" + i));
            check(registration.outcome() == DepositLedger.Outcome.REGISTERED,
                    "deposit " + i + " answered " + registration.outcome());
            ids.add(id);
        }
        double registerMs = (System.nanoTime() - registerStart) / 1_000_000.0;

        long lookupStart = System.nanoTime();
        for (long id : ids) {
            check(ledger.byId(id).isPresent(), "a registered deposit could not be looked up");
        }
        double lookupMs = (System.nanoTime() - lookupStart) / 1_000_000.0;

        long saveStart = System.nanoTime();
        CompoundTag saved = ledger.save(new CompoundTag(), registries);
        double saveMs = (System.nanoTime() - saveStart) / 1_000_000.0;
        int bytes = nbtBytes(saved);

        long loadStart = System.nanoTime();
        DepositLedger reloaded = new DepositLedger(saved, registries);
        double loadMs = (System.nanoTime() - loadStart) / 1_000_000.0;

        report(String.format(Locale.ROOT,
                "ledger: %d deposits  register=%.0fms  %d lookups=%.1fms  save=%.0fms  load=%.0fms"
                        + "  nbt=%d bytes (%.0f bytes/deposit)",
                deposits, registerMs, ids.size(), lookupMs, saveMs, loadMs,
                bytes, bytes / (double) deposits));

        check(reloaded.size() == deposits,
                "reload produced " + reloaded.size() + " of " + deposits + " deposits");
        for (long id : ids) {
            check(reloaded.byId(id).isPresent(), "a deposit did not survive the round trip");
        }
        // Re-registering everything must be recognised, not duplicated.
        int duplicates = 0;
        for (long id : ids) {
            DepositInstance existing = reloaded.byId(id).orElseThrow();
            if (reloaded.register(existing).outcome() == DepositLedger.Outcome.ALREADY_REGISTERED) {
                duplicates++;
            }
        }
        check(duplicates == deposits, "only " + duplicates + " re-registrations were recognised");
        check(reloaded.size() == deposits, "re-registration grew the ledger");
        report("ledger: " + duplicates + " re-registrations all answered ALREADY_REGISTERED");

        long statsStart = System.nanoTime();
        OreVeinDiagnosticsCommand.stats(level.getServer().createCommandSourceStack());
        report(String.format(Locale.ROOT, "diagnostics: /orevein stats over the live ledger = %.1fms",
                (System.nanoTime() - statsStart) / 1_000_000.0));
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Restoration at scale                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Ten thousand unloaded debts cost nothing per scheduler pass.
     *
     * <p>Milestone 4's central claim, re-proved at milestone 9 with the storage and scheduler as
     * they now stand. The measurement that matters is {@code debtsInspected}: if it grows with the
     * number of debts rather than with the number of <em>due, loaded</em> debts, the scheduler is
     * walking the world every cadence.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void tenThousandUnloadedDebtsCostNothingPerPass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        RestorationScheduler scheduler = isolatedScheduler();

        final int debts = 12_000;
        long now = System.currentTimeMillis();
        long buildStart = System.nanoTime();
        for (int i = 0; i < debts; i++) {
            BlockPos pos = new BlockPos(FAR_AWAY + (i % 512) * 16, -50, FAR_AWAY + (i / 512) * 16);
            storage.add(new BrokenBlockData(pos,
                    BlockRegistry.SILVER_ORE.get().defaultBlockState(),
                    now - 1_000L, UUID.randomUUID(),
                    now - 1_000L, 0x5CA1EL + i, silver().id(), 0L, 0));
        }
        double buildMs = (System.nanoTime() - buildStart) / 1_000_000.0;

        check(storage.totalCount() == debts,
                "storage holds " + storage.totalCount() + " of " + debts);
        check(scheduler.queuedCount() == 0,
                "the scheduler queued " + scheduler.queuedCount() + " debts without a chunk loading");
        check(scheduler.activeChunkCount() == 0, "chunks are active without being loaded");

        long inspectedBefore = scheduler.debtsInspected();
        long passStart = System.nanoTime();
        for (int pass = 0; pass < 1_000; pass++) {
            RestorationScheduler.PassResult result =
                    scheduler.runPass(storage, System.currentTimeMillis(), (data) -> true);
            check(!result.didAnything(), "a pass restored something with no chunk loaded");
        }
        double passMs = (System.nanoTime() - passStart) / 1_000_000.0;
        long inspected = scheduler.debtsInspected() - inspectedBefore;

        CompoundTag saved = storage.save(new CompoundTag(), level.registryAccess());
        int bytes = nbtBytes(saved);
        long loadStart = System.nanoTime();
        BrokenBlockDataStorage reloaded = new BrokenBlockDataStorage(saved, level.registryAccess());
        double loadMs = (System.nanoTime() - loadStart) / 1_000_000.0;

        report(String.format(Locale.ROOT,
                "restoration: %d unloaded debts  build=%.0fms  1000 passes=%.1fms"
                        + "  debts inspected=%d  queue=%d  active chunks=%d",
                debts, buildMs, passMs, inspected, scheduler.queuedCount(),
                scheduler.activeChunkCount()));
        report(String.format(Locale.ROOT,
                "restoration: nbt=%d bytes (%.0f bytes/debt)  reload=%.0fms  chunks with debts=%d",
                bytes, bytes / (double) debts, loadMs, reloaded.chunksWithDebts().size()));

        check(inspected == 0,
                "the scheduler inspected " + inspected + " debts over 1000 passes with nothing"
                        + " loaded; the cost is proportional to stored debt, not to due work");
        check(reloaded.totalCount() == debts, "debts did not survive the save round trip");
        helper.succeed();
    }

    /**
     * A region loading with a large overdue backlog drains progressively, not in one spike.
     *
     * <p>The failure this guards is the one that would actually hurt a live server: a player walks
     * into an area that has been unloaded for a week, and every owed cell is restored in a single
     * tick.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aMassOverdueBacklogDrainsInBoundedPasses(GameTestHelper helper) {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        RestorationScheduler scheduler = isolatedScheduler();

        final int overdue = 5_000;
        final int chunks = 40;
        long now = System.currentTimeMillis();
        for (int i = 0; i < overdue; i++) {
            int chunkIndex = i % chunks;
            BlockPos pos = new BlockPos(
                    FAR_AWAY + chunkIndex * 16 + (i / chunks) % 16, -50,
                    FAR_AWAY + (i / (chunks * 16)) % 16);
            storage.add(new BrokenBlockData(pos,
                    BlockRegistry.SILVER_ORE.get().defaultBlockState(),
                    now - 90_000_000L, UUID.randomUUID(),
                    now - 90_000_000L, 0xBAC10L + i, silver().id(), 0L, 0));
        }

        for (int chunkIndex = 0; chunkIndex < chunks; chunkIndex++) {
            scheduler.activate(storage, new ChunkPos(
                    (FAR_AWAY + chunkIndex * 16) >> 4, FAR_AWAY >> 4));
        }
        int queuedAfterLoad = scheduler.queuedCount();

        int passes = 0;
        int restoredTotal = 0;
        int largestPass = 0;
        while (scheduler.queuedCount() > 0 && passes < 10_000) {
            RestorationScheduler.PassResult result =
                    scheduler.runPass(storage, System.currentTimeMillis(), (data) -> true);
            passes++;
            restoredTotal += result.restored();
            largestPass = Math.max(largestPass, result.restored() + result.blocked());
            if (!result.didAnything()) {
                break;
            }
        }

        report(String.format(Locale.ROOT,
                "overdue backlog: %d debts across %d chunks  queued on load=%d"
                        + "  passes to drain=%d  restored=%d  largest single pass=%d (budget %d)",
                overdue, chunks, queuedAfterLoad, passes, restoredTotal, largestPass,
                RestorationScheduler.WORK_BUDGET));

        check(largestPass <= RestorationScheduler.WORK_BUDGET,
                "one pass did " + largestPass + " units of work against a budget of "
                        + RestorationScheduler.WORK_BUDGET + "; the backlog drained in a spike");
        check(passes > 1, "the whole backlog drained in a single pass");
        helper.succeed();
    }

    /** A blocked debt backs off, and the backoff grows rather than retrying every pass. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void blockedDebtsBackOffInsteadOfSpinning(GameTestHelper helper) {
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        RestorationScheduler scheduler = isolatedScheduler();
        long now = System.currentTimeMillis();

        BlockPos pos = new BlockPos(FAR_AWAY + 7, -50, FAR_AWAY + 7);
        storage.add(new BrokenBlockData(pos,
                BlockRegistry.SILVER_ORE.get().defaultBlockState(),
                now - 60_000L, UUID.randomUUID(),
                now - 60_000L, 0xB10CL, silver().id(), 0L, 0));
        scheduler.activate(storage, new ChunkPos(pos));

        RestorationScheduler.PassResult first =
                scheduler.runPass(storage, System.currentTimeMillis(), (data) -> false);
        check(first.blocked() == 1, "the blocked debt was not reported blocked");

        RestorationScheduler.PassResult second =
                scheduler.runPass(storage, System.currentTimeMillis(), (data) -> false);
        check(second.blocked() == 0,
                "a blocked debt was retried immediately instead of backing off");

        long first_backoff = RestorationScheduler.backoffFor(0);
        long later_backoff = RestorationScheduler.backoffFor(5);
        report(String.format(Locale.ROOT,
                "backoff: first retry in %ds, sixth in %ds, capped at %ds",
                first_backoff / 1000, later_backoff / 1000,
                RestorationScheduler.MAX_BACKOFF_MILLIS / 1000));
        check(later_backoff > first_backoff, "the backoff does not grow");
        check(later_backoff <= RestorationScheduler.MAX_BACKOFF_MILLIS, "the backoff is uncapped");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Materialisation accounting                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Every rejection reason is counted, and the budget stops an unbounded write burst.
     *
     * <p>The counts matter beyond diagnostics: the ledger records them as a deposit's materialised
     * and blocked totals, so a miscount becomes a permanently wrong belief about the world.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void materialisationAccountsForEveryCandidateAndRespectsItsBudget(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(NODE);
        String dimension = level.dimension().location().toString();

        // One cell of each kind the policy has an opinion about.
        List<BlockPos> candidates = new ArrayList<>();
        record Cell(net.minecraft.world.level.block.Block block, boolean placeable) {
        }
        List<Cell> cells = List.of(
                new Cell(Blocks.STONE, true),
                new Cell(Blocks.DEEPSLATE, true),
                new Cell(Blocks.WATER, false),
                new Cell(Blocks.LAVA, false),
                new Cell(Blocks.CHEST, false),
                new Cell(Blocks.BEDROCK, false),
                new Cell(Blocks.DIRT, false),
                new Cell(BlockRegistry.CLAY_DEPOSIT.get(), false));
        for (int i = 0; i < cells.size(); i++) {
            BlockPos pos = base.east(i);
            level.setBlock(pos, cells.get(i).block().defaultBlockState(), 2);
            candidates.add(pos);
        }

        long id = DepositIdentity.rails("m9", dimension, silver().id(),
                base.getX(), base.getY(), base.getZ(), 4, ShapeRotation.XZ, "m9");
        PlannedDeposit deposit = PlacementPlanner.plan(silver(), dimension, base, 4,
                ShapeRotation.XZ, DepositIdentity.plannerSeed(id));

        MaterializationService.Result first =
                MaterializationService.materialize(level, deposit, candidates, 64);

        int expectedPlaced = (int) cells.stream().filter(Cell::placeable).count();
        check(first.placed() == expectedPlaced,
                "placed " + first.placed() + ", expected " + expectedPlaced);
        check(first.placed() + first.totalRejected() == candidates.size(),
                "placed + rejected = " + (first.placed() + first.totalRejected())
                        + ", but " + candidates.size() + " candidates were offered");

        StringBuilder breakdown = new StringBuilder();
        for (MaterializationService.Rejection reason : MaterializationService.Rejection.values()) {
            breakdown.append(reason).append('=').append(first.rejected(reason)).append(' ');
        }
        report("materialisation: " + candidates.size() + " candidates, " + first.placed()
                + " written, " + first.totalRejected() + " rejected -> " + breakdown.toString().trim());

        // Re-running is a no-op that is counted as already present, not as a rewrite.
        MaterializationService.Result second =
                MaterializationService.materialize(level, deposit, candidates, 64);
        check(second.placed() == 0, "a repeat pass wrote " + second.placed() + " cells");
        check(second.rejected(MaterializationService.Rejection.ALREADY_PRESENT) == expectedPlaced,
                "a repeat pass did not recognise its own work as already present");
        report("materialisation: repeat pass wrote 0 and counted " + expectedPlaced
                + " already-present cells");

        // A budget of one stops after one write and reports the remainder.
        BlockPos budgetBase = base.above(3);
        List<BlockPos> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            BlockPos pos = budgetBase.east(i);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            many.add(pos);
        }
        MaterializationService.Result bounded =
                MaterializationService.materialize(level, deposit, many, 1);
        check(bounded.placed() == 1, "a budget of one wrote " + bounded.placed() + " cells");
        check(bounded.truncated(), "a budget-limited pass did not report itself truncated");
        report("materialisation: budget=1 over 8 candidates wrote 1, remaining="
                + bounded.remaining() + ", truncated=" + bounded.truncated());
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Persistence and restart                                            */
    /* ------------------------------------------------------------------ */

    /**
     * A deposit and its debts survive a save/load boundary unchanged.
     *
     * <p>A GameTest cannot restart the server, so the boundary is exercised where it actually
     * lives: {@code SavedData} serialisation. Everything a restart would do to this state — write
     * it to NBT and read it back into fresh objects — is done here, including the offline-time case,
     * which is simply a debt whose due moment has passed while nothing was running.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void depositsAndDebtsSurviveASaveAndLoadBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        String dimension = level.dimension().location().toString();
        BlockPos origin = new BlockPos(FAR_AWAY + 128, -44, FAR_AWAY + 128);

        DepositLedger ledger = new DepositLedger();
        long id = DepositIdentity.rails("restart", dimension, silver().id(),
                origin.getX(), origin.getY(), origin.getZ(), 6, ShapeRotation.XZ, "restart");
        PlannedDeposit deposit = PlacementPlanner.plan(silver(), dimension, origin, 6,
                ShapeRotation.XZ, DepositIdentity.plannerSeed(id));
        DepositInstance before = DepositRegistrar.describe(
                deposit, id, DepositSource.RAILS, "rails|restart|row");
        ledger.register(before);

        DepositLedger afterRestart =
                new DepositLedger(ledger.save(new CompoundTag(), registries), registries);
        DepositInstance after = afterRestart.byId(id).orElseThrow();

        check(after.instanceId() == before.instanceId(), "the deposit id changed across a restart");
        check(after.seed() == before.seed(), "the planner seed changed across a restart");
        check(after.radius() == before.radius(), "the radius changed across a restart");
        check(after.origin().equals(before.origin()), "the origin changed across a restart");
        check(after.plannedCells() == before.plannedCells(), "the geometry changed across a restart");
        check(after.source() == before.source(), "the source changed across a restart");

        // Re-deriving from the same immutable Rails row gives the same id: idempotent import.
        long rederived = DepositIdentity.rails("restart", dimension, silver().id(),
                origin.getX(), origin.getY(), origin.getZ(), 6, ShapeRotation.XZ, "restart");
        check(rederived == id, "the same Rails row derived a different id after restart");
        check(afterRestart.register(after).outcome() == DepositLedger.Outcome.ALREADY_REGISTERED,
                "re-importing the same row after a restart created a duplicate");

        // A changed immutable input is a different deposit, by design.
        long moved = DepositIdentity.rails("restart", dimension, silver().id(),
                origin.getX() + 1, origin.getY(), origin.getZ(), 6, ShapeRotation.XZ, "restart");
        check(moved != id, "moving a curated row kept its identity, which the fallback cannot do");

        // A revision change is reported, never silently regenerated.
        DepositInstance revised = new DepositInstance(
                after.instanceId(), after.resourceId(), after.definitionRevision() + 1,
                after.source(), after.sourceIdentity(), after.origin(), after.seed(),
                after.radius(), after.rotation(), after.boundsMin(), after.boundsMax(),
                after.plannedCells(), after.materializedCells(), after.blockedCells(),
                after.materializationVersion());
        DepositLedger.Registration mismatch = afterRestart.register(revised);
        check(mismatch.outcome() == DepositLedger.Outcome.REVISION_MISMATCH,
                "a revision change answered " + mismatch.outcome());
        check(!mismatch.mayMaterialize(), "a revision mismatch would have been materialised over");

        // A debt keeps its own due moment, including one that came due while nothing was running.
        BrokenBlockDataStorage storage = new BrokenBlockDataStorage();
        long now = System.currentTimeMillis();
        long futureDue = now + 3_600_000L;
        long offlineDue = now - 3_600_000L;
        storage.add(new BrokenBlockData(origin,
                BlockRegistry.SILVER_ORE.get().defaultBlockState(), now, UUID.randomUUID(),
                futureDue, id, silver().id(), 0L, 0));
        storage.add(new BrokenBlockData(origin.above(),
                BlockRegistry.SILVER_ORE.get().defaultBlockState(), now - 7_200_000L,
                UUID.randomUUID(), offlineDue, id, silver().id(), 0L, 0));

        BrokenBlockDataStorage reloaded = new BrokenBlockDataStorage(
                storage.save(new CompoundTag(), registries), registries);

        check(reloaded.debtAt(origin).dueAt == futureDue,
                "a pending debt's due moment did not survive the restart");
        check(reloaded.debtAt(origin.above()).dueAt == offlineDue,
                "an overdue debt's due moment did not survive the restart");
        check(reloaded.debtAt(origin).instanceId == id,
                "a debt lost the deposit it belongs to");

        // The overdue one becomes eligible only when its chunk is loaded, which is the offline rule.
        RestorationScheduler scheduler = isolatedScheduler();
        check(scheduler.queuedCount() == 0, "a debt was queued before its chunk loaded");
        scheduler.activate(reloaded, new ChunkPos(origin));
        check(scheduler.queuedCount() == 2,
                "loading the chunk queued " + scheduler.queuedCount() + " of its 2 debts");

        RestorationScheduler.PassResult pass =
                scheduler.runPass(reloaded, System.currentTimeMillis(), (data) -> true);
        check(pass.restored() == 1,
                "the pass restored " + pass.restored() + "; only the overdue debt was eligible");
        report("persistence: deposit identity, seed, geometry, revision guard, debt due times and"
                + " offline eligibility all survived a save/load boundary");
        helper.succeed();
    }
}

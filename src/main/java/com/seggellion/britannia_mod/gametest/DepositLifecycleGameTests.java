package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.blockrestore.RestorationScheduler;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * OreVein milestone 4, in a world: deposits are remembered, and debts know what they belong to.
 *
 * <p>The unit tests cover identity derivation, ledger semantics, migration and scheduler behaviour
 * without a world, because none of those need one. These cover the parts that only exist when a
 * level does: a real extraction recording a real debt, a registered deposit claiming it, and the
 * scheduler refusing to reach into a chunk nobody has loaded.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class DepositLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos CELL = new BlockPos(1, 1, 1);
    private static final long HOUR = 60L * 60L * 1000L;

    private DepositLifecycleGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition resource(String path) {
        return ResourceCatalog.instance().byPath(path).orElseThrow();
    }

    /* ------------------------------------------------------------------ */
    /*  Extraction records what the debt is                                */
    /* ------------------------------------------------------------------ */

    /**
     * A worked cell records its resource and the moment it is owed back.
     *
     * <p>The due time is resolved once, here, from the resource's own policy — rather than being
     * recomputed on every visit by a loop that visited every debt in the world twenty times a
     * second.
     */
    @GameTest(template = TEMPLATE)
    public static void extractionRecordsTheResourceAndItsOwnDueTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(CELL);
        long before = System.currentTimeMillis();

        helper.setBlock(CELL, BlockRegistry.SILVER_ORE.get());
        BrokenBlockTracker.recordBrokenBlock(level, absolute,
                BlockRegistry.SILVER_ORE.get().defaultBlockState(), UUID.randomUUID());

        BrokenBlockData debt = BrokenBlockDataStorage.get(level).debtAt(absolute);
        check(debt != null, "extraction must record a debt");
        check("britannia_mod:silver".equals(debt.resourceId),
                "the debt must know its resource, got '" + debt.resourceId + "'");
        long expected = before + 6 * HOUR;
        check(Math.abs(debt.dueAt - expected) < 5_000L,
                "silver is owed back in six hours; due was " + (debt.dueAt - before) + "ms out");

        BrokenBlockTracker.removeBlock(level, absolute);
        helper.succeed();
    }

    /** Silica's approved 24 hours reaches the debt, not the historical six. */
    @GameTest(template = TEMPLATE)
    public static void silicaRecordsTwentyFourHoursAndOthersKeepSix(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(CELL);
        long before = System.currentTimeMillis();

        BrokenBlockTracker.recordBrokenBlock(level, absolute,
                BlockRegistry.SILICA_SAND_DEPOSIT.get().defaultBlockState(), UUID.randomUUID());
        BrokenBlockData silica = BrokenBlockDataStorage.get(level).debtAt(absolute);
        check(Math.abs(silica.dueAt - (before + 24 * HOUR)) < 5_000L,
                "silica must be owed back in 24 hours, was " + (silica.dueAt - before));
        BrokenBlockTracker.removeBlock(level, absolute);

        BrokenBlockTracker.recordBrokenBlock(level, absolute,
                BlockRegistry.CLAY_DEPOSIT.get().defaultBlockState(), UUID.randomUUID());
        BrokenBlockData clay = BrokenBlockDataStorage.get(level).debtAt(absolute);
        check(Math.abs(clay.dueAt - (before + 6 * HOUR)) < 5_000L,
                "clay must keep six hours, was " + (clay.dueAt - before));
        BrokenBlockTracker.removeBlock(level, absolute);
        helper.succeed();
    }

    /**
     * A cell inside a registered deposit records that deposit as its owner.
     *
     * <p>Which is the question milestone 4 exists to be able to answer: given a hole in the ground,
     * which deposit is missing a piece.
     */
    @GameTest(template = TEMPLATE)
    public static void aDebtInsideARegisteredDepositRecordsItsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        ResourceDefinition silver = resource("silver");

        long instanceId = DepositIdentity.rails("shard", level.dimension().location().toString(),
                silver.id(), origin.getX(), origin.getY(), origin.getZ(), 4, ShapeRotation.ZW, "");
        PlannedDeposit deposit = PlacementPlanner.plan(silver, level.dimension().location().toString(),
                origin, 4, ShapeRotation.ZW, DepositIdentity.plannerSeed(instanceId));
        DepositInstance instance = DepositRegistrar.describe(
                deposit, instanceId, DepositSource.RAILS, "v1|rails|fixture");

        DepositLedger ledger = DepositLedger.get(level);
        check(ledger.register(instance).mayMaterialize(), "a fresh deposit must register");

        BrokenBlockTracker.recordBrokenBlock(level, origin,
                BlockRegistry.SILVER_ORE.get().defaultBlockState(), UUID.randomUUID());

        BrokenBlockData debt = BrokenBlockDataStorage.get(level).debtAt(origin);
        check(debt.owned(), "a cell inside a registered deposit must record its owner");
        check(debt.instanceId == instanceId,
                "expected deposit " + Long.toHexString(instanceId)
                        + ", got " + Long.toHexString(debt.instanceId));
        check(DepositRegistrar.depletedCells(level, instance) == 1,
                "the deposit must be able to count its own worked-out cells");

        BrokenBlockTracker.removeBlock(level, origin);
        helper.succeed();
    }

    /**
     * A cell with no registered deposit is recorded as unowned rather than given an invented one.
     *
     * <p>The common case for an existing world: every resource block placed before this milestone
     * has no ledger entry. Deriving a plausible-looking id for it would put false provenance into
     * durable storage.
     */
    @GameTest(template = TEMPLATE)
    public static void aDebtWithNoRegisteredDepositIsRecordedAsUnowned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(new BlockPos(0, 3, 0));

        BrokenBlockTracker.recordBrokenBlock(level, absolute,
                BlockRegistry.VERITE_ORE.get().defaultBlockState(), UUID.randomUUID());

        BrokenBlockData debt = BrokenBlockDataStorage.get(level).debtAt(absolute);
        check(!debt.owned(), "an unclaimed cell must not invent an owner");
        check(debt.instanceId == DepositInstance.NO_INSTANCE, "unowned means zero");
        check("britannia_mod:verite".equals(debt.resourceId),
                "the resource is still known even when the deposit is not");

        BrokenBlockTracker.removeBlock(level, absolute);
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Registration and resumption, end to end                            */
    /* ------------------------------------------------------------------ */

    /**
     * A deposit registers, materialises in bounded slices, and resumes without duplicating.
     *
     * <p>The whole milestone-3-plus-4 loop: deterministic geometry, a budget that stops part-way,
     * an identity that survives, and a second run that finishes the job rather than starting a
     * second deposit.
     */
    @GameTest(template = TEMPLATE)
    public static void aDepositRegistersMaterializesInSlicesAndResumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        String dimension = level.dimension().location().toString();
        ResourceDefinition silver = resource("silver");

        long instanceId = DepositIdentity.rails("shard", dimension, silver.id(),
                origin.getX(), origin.getY(), origin.getZ(), 3, ShapeRotation.ZW, "resume-test");
        PlannedDeposit deposit = PlacementPlanner.plan(silver, dimension, origin, 3,
                ShapeRotation.ZW, DepositIdentity.plannerSeed(instanceId));
        check(deposit.count() >= 3, "this test needs a deposit of at least three cells");

        for (BlockPos pos : deposit.positions()) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }

        DepositLedger ledger = DepositLedger.get(level);
        DepositInstance instance =
                DepositRegistrar.describe(deposit, instanceId, DepositSource.RAILS, "v1|rails|resume");

        check(ledger.register(instance).outcome() == DepositLedger.Outcome.REGISTERED,
                "first registration must be new");

        // A budget-limited pass writes part of it and records nothing, because it has not looked
        // at the whole plan.
        MaterializationService.Result partial =
                MaterializationService.materialize(level, deposit, deposit.positions(), 2);
        DepositRegistrar.recordCompletePass(level, instanceId, partial, deposit.count());
        check(partial.placed() == 2, "the budget must cap the first run");
        check(!ledger.byId(instanceId).orElseThrow().progressKnown(),
                "a truncated pass must not pretend to know how much is blocked");

        // The same deposit again: resumes, does not duplicate, does not reroll.
        DepositLedger.Registration second = ledger.register(instance);
        check(second.outcome() == DepositLedger.Outcome.ALREADY_REGISTERED,
                "the same deposit must be recognised, got " + second.outcome());
        check(ledger.size() >= 1, "no second instance may be created");

        MaterializationService.Result complete = MaterializationService.materialize(level, deposit);
        DepositRegistrar.recordCompletePass(level, instanceId, complete, deposit.count());

        DepositInstance stored = ledger.byId(instanceId).orElseThrow();
        check(stored.progressKnown(), "a complete pass must establish the counts");
        check(stored.materializedCells() + stored.blockedCells() == stored.plannedCells(),
                "a complete pass partitions the plan: " + stored.materializedCells() + " + "
                        + stored.blockedCells() + " != " + stored.plannedCells());
        check(stored.fullyMaterialized(), "every cell had a host, so it should be finished");

        for (BlockPos pos : deposit.positions()) {
            check(level.getBlockState(pos).is(BlockRegistry.SILVER_ORE.get()),
                    "resuming must complete the deposit, missing " + pos.toShortString());
        }
        helper.succeed();
    }

    /** A different deposit wearing the same id is refused, and the original is untouched. */
    @GameTest(template = TEMPLATE)
    public static void anIdentityCollisionIsRefusedInAWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        String dimension = level.dimension().location().toString();
        long instanceId = 0x0DDC0111DEL;

        DepositLedger ledger = DepositLedger.get(level);
        PlannedDeposit silver = PlacementPlanner.plan(resource("silver"), dimension, origin, 3,
                ShapeRotation.ZW, 1L);
        PlannedDeposit tin = PlacementPlanner.plan(resource("tin"), dimension, origin, 3,
                ShapeRotation.XZ, 1L);

        ledger.register(DepositRegistrar.describe(silver, instanceId, DepositSource.RAILS, "a"));
        DepositLedger.Registration clash =
                ledger.register(DepositRegistrar.describe(tin, instanceId, DepositSource.RAILS, "b"));

        check(clash.outcome() == DepositLedger.Outcome.CONFLICT, "two deposits, one id, must clash");
        check(!clash.mayMaterialize(), "a collision must never be written");
        check("britannia_mod:silver".equals(ledger.byId(instanceId).orElseThrow().resourceId()),
                "the deposit already there must be untouched");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The scheduler never reaches into an unloaded chunk                 */
    /* ------------------------------------------------------------------ */

    /**
     * A debt in a chunk nobody has loaded is never touched, and the chunk is never loaded for it.
     *
     * <p>The invariant milestone 0 found the old scheduler already respected and milestone 4 had to
     * keep while changing everything around it: an unloaded chunk stays unloaded, and its debt waits.
     */
    @GameTest(template = TEMPLATE)
    public static void anUnloadedChunkIsNeverLoadedToRestoreIntoIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RestorationScheduler scheduler = RestorationScheduler.of(level);

        // Far enough away that nothing in this test world is keeping it loaded.
        BlockPos faraway = new BlockPos(4_000_000, 64, 4_000_000);
        ChunkPos farChunk = new ChunkPos(faraway);
        check(level.getChunkSource().getChunkNow(farChunk.x, farChunk.z) == null,
                "precondition: the far chunk must not already be loaded");

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        storage.add(new BrokenBlockData(faraway, Blocks.STONE.defaultBlockState(),
                0L, UUID.randomUUID(), 1L, DepositInstance.NO_INSTANCE, "britannia_mod:stone", 0L, 0));

        long inspectedBefore = scheduler.debtsInspected();
        for (int pass = 0; pass < 50; pass++) {
            scheduler.runPass(storage, System.currentTimeMillis(), debt -> true);
        }

        check(scheduler.debtsInspected() == inspectedBefore,
                "an unloaded chunk's debt must not be inspected");
        check(level.getChunkSource().getChunkNow(farChunk.x, farChunk.z) == null,
                "restoration must never force a chunk to load");
        check(storage.debtAt(faraway) != null, "and the debt is still owed");

        storage.remove(faraway);
        helper.succeed();
    }

    /**
     * Loading a chunk activates its debts and nothing else's.
     *
     * <p>The test chunk is already active in a running world — the server loaded it — so it is
     * deactivated first. That is not test scaffolding around a limitation; it is the unload/load
     * cycle the invariant is actually about.
     */
    @GameTest(template = TEMPLATE)
    public static void activatingAChunkInAWorldPicksUpOnlyItsOwnDebts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(CELL);
        BlockPos faraway = new BlockPos(4_100_000, 64, 4_100_000);
        ChunkPos hereChunk = new ChunkPos(here);
        ChunkPos farChunk = new ChunkPos(faraway);

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        storage.add(new BrokenBlockData(here, Blocks.STONE.defaultBlockState(),
                0L, UUID.randomUUID(), 1L, DepositInstance.NO_INSTANCE, "britannia_mod:stone", 0L, 0));
        storage.add(new BrokenBlockData(faraway, Blocks.STONE.defaultBlockState(),
                0L, UUID.randomUUID(), 1L, DepositInstance.NO_INSTANCE, "britannia_mod:stone", 0L, 0));

        RestorationScheduler scheduler = RestorationScheduler.of(level);
        scheduler.deactivate(hereChunk);
        check(!scheduler.isActive(hereChunk), "precondition: the chunk is not being watched");

        long before = scheduler.debtsInspected();
        scheduler.activate(storage, hereChunk);

        long read = scheduler.debtsInspected() - before;
        check(read == 1, "activating one chunk must read exactly its own debts, read " + read);
        check(scheduler.isActive(hereChunk), "and it is now watched");
        check(!scheduler.isActive(farChunk),
                "the far chunk must not have become active, and its debt must stay unread");

        storage.remove(here);
        storage.remove(faraway);
        helper.succeed();
    }
}

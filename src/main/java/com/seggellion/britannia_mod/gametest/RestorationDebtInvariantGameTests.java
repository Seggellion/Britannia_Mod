package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.RestorationScheduler;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A restoration debt is economic material owed to the world, and may only be discharged by paying
 * it.
 *
 * <h2>The defect this exists for</h2>
 * The scheduler removes a debt when, and only when, the restoration attempt reports success. The
 * attempt used to call {@code setBlockAndUpdate}, throw the result away, and return an
 * unconditional {@code true} — so any write the level refused consumed the debt anyway. The cell
 * stayed empty, the debt was gone, and nothing anywhere recorded that a managed resource had been
 * deleted. There is no second record to reconcile against: the debt <em>is</em> the record.
 *
 * <p>That is unrecoverable in a way most bugs are not, so the invariant is asserted generically
 * rather than only through the resources that happened to expose it. These tests use plain stone
 * and a synthetic debt: nothing here depends on iron, gold, or any particular shape, and a future
 * restoration defect in any resource is caught by the same assertions.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class RestorationDebtInvariantGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos NODE = new BlockPos(1, 1, 1);

    private RestorationDebtInvariantGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static BrokenBlockData debtFor(BlockPos pos, BlockState original, long dueAt) {
        return new BrokenBlockData(pos, original, dueAt, UUID.randomUUID(), dueAt,
                DepositInstance.NO_INSTANCE, "britannia_mod:stone", 0L, 0);
    }

    /**
     * A cell that cannot take its block back keeps its debt.
     *
     * <p>The blocked case the platform already handled correctly, asserted here so the fix to the
     * silent case cannot regress it into "always succeed".
     */
    @GameTest(template = TEMPLATE)
    public static void aBlockedCellKeepsItsDebt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE);
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);

        // Water blocks restoration: bringing the node back would delete the fluid.
        level.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
        BrokenBlockData debt = debtFor(cell, Blocks.STONE.defaultBlockState(), 1L);
        storage.add(debt);

        check(!BlockRestoreHandler.restore(level, debt),
                "a flooded cell reported a successful restoration");
        check(storage.getBrokenBlocks().containsKey(cell),
                "a refused restoration consumed the debt");
        check(!level.getBlockState(cell).is(Blocks.STONE),
                "a refused restoration wrote the block anyway");

        storage.remove(cell);
        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }

    /**
     * A debt with nothing sensible to restore keeps its debt rather than quietly closing.
     *
     * <p>An air target is the shape a corrupted or half-migrated record takes. Reporting success
     * for it would mean "paid" while paying nothing, which is exactly the failure mode being
     * closed; keeping it means the cell shows up in diagnostics as still owed.
     */
    @GameTest(template = TEMPLATE)
    public static void aDebtWithNoRestorableTargetIsKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE).above();
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);

        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        BrokenBlockData debt = debtFor(cell, Blocks.AIR.defaultBlockState(), 1L);
        storage.add(debt);

        check(!BlockRestoreHandler.restore(level, debt),
                "a debt whose target is air reported a successful restoration");
        check(storage.getBrokenBlocks().containsKey(cell),
                "a debt with no restorable target was consumed");

        storage.remove(cell);
        helper.succeed();
    }

    /**
     * A cell that can take its block back is restored, and only then is the debt discharged.
     *
     * <p>The other half of the invariant: the fix must not make restoration conservative to the
     * point of never paying. Uses a managed block so the assertion covers the real transition.
     */
    @GameTest(template = TEMPLATE)
    public static void aClearCellIsRestoredAndOnlyThenDischarged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE).above(2);
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);

        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        BlockState target = BlockRegistry.COAL_ORE.get().defaultBlockState();
        BrokenBlockData debt = debtFor(cell, target, 1L);
        storage.add(debt);

        check(BlockRestoreHandler.restore(level, debt),
                "a clear cell refused a restoration it should have accepted");
        check(level.getBlockState(cell).is(target.getBlock()),
                "restoration reported success but left " + level.getBlockState(cell).getBlock());

        storage.remove(cell);
        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }

    /**
     * A debt survives a save and reload, and the reloaded debt still restores.
     *
     * <p>The restart half of the lifecycle. A GameTest cannot restart the server, but the debt's
     * durability is a property of its serialisation, so this round-trips the storage through the
     * exact {@code SavedData} path the server uses at shutdown and startup and then pays the
     * reloaded debt. Uses a Vertical resource's block, since Vertical was one of the two shapes
     * whose restoration was reported broken.
     */
    @GameTest(template = TEMPLATE)
    public static void aDebtSurvivesSaveAndReloadAndStillRestores(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE).above(4);
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);

        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        BlockState target = net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState();
        BrokenBlockData debt = debtFor(cell, target, 1L);
        storage.add(debt);

        // Through the real SavedData serialisation and back.
        net.minecraft.nbt.CompoundTag saved = storage.save(new net.minecraft.nbt.CompoundTag(),
                level.registryAccess());
        BrokenBlockDataStorage reloaded =
                new BrokenBlockDataStorage(saved, level.registryAccess());

        BrokenBlockData survivor = reloaded.getBrokenBlocks().get(cell);
        check(survivor != null, "the debt did not survive a save and reload");
        check(survivor.originalState.is(target.getBlock()),
                "the reloaded debt restores " + survivor.originalState.getBlock()
                        + " rather than " + target.getBlock());
        check(survivor.dueAt == debt.dueAt, "the reloaded debt lost its due moment");
        check(survivor.resourceId.equals(debt.resourceId), "the reloaded debt lost its resource");

        check(BlockRestoreHandler.restore(level, survivor),
                "the reloaded debt could not be paid");
        check(level.getBlockState(cell).is(target.getBlock()),
                "the reloaded debt reported success but left "
                        + level.getBlockState(cell).getBlock());

        storage.remove(cell);
        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }

    /**
     * The scheduler retains and backs off a refused debt rather than dropping it.
     *
     * <p>Asserted against the scheduler directly with a deliberately failing attempt, so the rule
     * holds for any future restoration implementation and not only for the current one.
     */
    @GameTest(template = TEMPLATE)
    public static void theSchedulerNeverDropsARefusedDebt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(NODE).above(3);
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        RestorationScheduler scheduler = RestorationScheduler.of(level);

        BrokenBlockData debt = debtFor(cell, Blocks.STONE.defaultBlockState(), 1L);
        storage.add(debt);

        RestorationScheduler.PassResult result =
                scheduler.runPass(storage, System.currentTimeMillis(), attempt -> false);

        check(storage.getBrokenBlocks().containsKey(cell),
                "the scheduler dropped a debt whose restoration was refused");
        BrokenBlockData after = storage.getBrokenBlocks().get(cell);
        check(after.retryAt > 0L,
                "a refused debt was not backed off, so it would be retried in a hot loop");
        check(after.dueAt == debt.dueAt,
                "a refused debt had its due moment moved; being blocked does not make a deposit"
                        + " economically younger");
        System.out.println("M11INV refused pass: restored=" + result.restored()
                + " blocked=" + result.blocked() + " retryAt set=" + (after.retryAt > 0L));

        storage.remove(cell);
        helper.succeed();
    }
}

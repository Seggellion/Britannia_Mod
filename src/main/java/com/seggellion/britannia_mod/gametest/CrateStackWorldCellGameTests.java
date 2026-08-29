package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How a column claims and releases the world cells it stands in.
 *
 * <h2>What is being protected</h2>
 *
 * <p>The crates are the authority and the cells follow them. Every test here is really one question:
 * can the world and the column disagree in a way that costs an inventory? Growth that hits a ceiling
 * must refuse before it changes anything; shrinking must release cells without any teardown mistaking
 * that for the column being destroyed; and reconciliation must never overwrite a block that is not
 * its own, however much it would like the space.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackWorldCellGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CrateStackWorldCellGameTests() {
    }

    /** Two small crates come to 14.30 voxels, which is one cell and no continuation at all. */
    @GameTest(template = TEMPLATE)
    public static void aColumnInsideOneCellClaimsNoContinuation(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);

        check(stack.crateCount() == 2, "the column should hold two crates");
        check(stack.requiredCellCount() == 1, "14.30 voxels is one cell");
        check(helper.getLevel().getBlockState(root).getValue(CrateStackBlock.PART) == 0,
                "the root must be part zero");
        check(!helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "a one-cell column claimed a cell above itself");
        helper.succeed();
    }

    /** Three come to 21.45, which crosses into a second cell that must exist but own nothing. */
    @GameTest(template = TEMPLATE)
    public static void aColumnCrossingABoundaryClaimsAContinuationWithNoBlockEntity(
            GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);
        grow(helper, root, CrateVariant.SMALL);

        check(stack.requiredCellCount() == 2, "21.45 voxels needs two cells");
        BlockState above = helper.getLevel().getBlockState(root.above());
        check(above.is(BlockRegistry.CRATE_STACK.get()), "the continuation cell was not claimed");
        check(above.getValue(CrateStackBlock.PART) == 1, "the continuation must be part one");
        check(helper.getLevel().getBlockEntity(root.above()) == null,
                "a continuation cell must never own a block entity");
        check(CrateStackBlock.rootOf(root.above(), above).equals(root),
                "the continuation cell cannot find its root");
        helper.succeed();
    }

    /** Eight small crates reach the cap exactly: four cells, and nothing above them. */
    @GameTest(template = TEMPLATE)
    public static void aColumnAtTheCapClaimsFourCellsAndNoFifth(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        for (int index = 1; index < 8; index++) {
            grow(helper, root, CrateVariant.SMALL);
        }

        check(stack.crateCount() == 8, "eight small crates should fit");
        check(stack.requiredCellCount() == CrateStackLayout.MAX_CELLS, "and fill four cells");
        for (int cell = 1; cell < CrateStackLayout.MAX_CELLS; cell++) {
            check(helper.getLevel().getBlockState(root.above(cell))
                            .is(BlockRegistry.CRATE_STACK.get()),
                    "cell " + cell + " was not claimed");
        }
        check(!helper.getLevel().getBlockState(root.above(CrateStackLayout.MAX_CELLS))
                        .is(BlockRegistry.CRATE_STACK.get()),
                "the column claimed a fifth cell");

        CrateStackColumnSync.Growth refused =
                CrateStackColumnSync.appendCrate(helper.getLevel(), root, CrateVariant.SMALL,
                        Direction.NORTH);
        check(!refused.succeeded(), "a ninth crate must not fit");
        check(refused.refusal() == CrateStackColumnSync.GrowthRefusal.AT_HEIGHT_CAP,
                "refused for the wrong reason: " + refused.refusal());
        helper.succeed();
    }

    /**
     * A ceiling has to be discovered before the column changes, not after.
     *
     * <p>The alternative is appending the crate, finding the obstruction, and unwinding — which means
     * an inventory momentarily exists in a state nothing else expects.
     */
    @GameTest(template = TEMPLATE)
    public static void growthBlockedByAForeignBlockChangesNothingAtAll(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);
        stack.containerFor(0).setItem(0, new ItemStack(Items.DIAMOND, 5));

        // Obsidian exactly where a third crate would push the column.
        BlockPos ceiling = root.above();
        helper.getLevel().setBlock(ceiling, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);

        int crates = stack.crateCount();
        int nextId = stack.nextCrateId();
        int height = stack.totalHeightHundredths();

        CrateStackColumnSync.Growth refused = CrateStackColumnSync.appendCrate(
                helper.getLevel(), root, CrateVariant.SMALL, Direction.NORTH);

        check(!refused.succeeded(), "growth into obsidian must be refused");
        check(refused.refusal() == CrateStackColumnSync.GrowthRefusal.OBSTRUCTED,
                "refused for the wrong reason: " + refused.refusal());
        check(stack.crateCount() == crates, "a refused growth changed the crate count");
        check(stack.nextCrateId() == nextId, "a refused growth consumed an id");
        check(stack.totalHeightHundredths() == height, "a refused growth changed the height");
        check(stack.containerFor(0).getItem(0).getCount() == 5, "a refused growth disturbed contents");
        check(helper.getLevel().getBlockState(ceiling).is(Blocks.OBSIDIAN),
                "a refused growth overwrote the block in its way");
        helper.succeed();
    }

    /** Releasing a cell is not destruction, and nothing may fall out of the column when it happens. */
    @GameTest(template = TEMPLATE)
    public static void shrinkingReleasesCellsWithoutDroppingAnything(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);
        grow(helper, root, CrateVariant.SMALL);
        stack.containerFor(0).setItem(0, new ItemStack(Items.DIAMOND, 7));
        check(helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "the column should have claimed a second cell");

        stack.removeCrate(stack.topCrate().id());
        CrateStackColumnSync.reconcile(helper.getLevel(), root, stack);

        check(stack.requiredCellCount() == 1, "two small crates need one cell");
        check(!helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "the stale continuation cell was not released");
        check(helper.getLevel().getBlockState(root).is(BlockRegistry.CRATE_STACK.get()),
                "shrinking removed the root");
        check(stack.containerFor(0).getItem(0).getCount() == 7,
                "shrinking disturbed a crate's contents");
        check(dropsAround(helper, root) == 0, "shrinking dropped something");
        helper.succeed();
    }

    /** A column that comes back from disk with a cell missing rebuilds it from its own crates. */
    @GameTest(template = TEMPLATE)
    public static void aMissingContinuationIsRestoredFromTheColumn(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);
        grow(helper, root, CrateVariant.SMALL);

        // As if the second cell had never been written, or had been lost to an interrupted mutation.
        helper.getLevel().removeBlock(root.above(), false);
        check(!helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "the cell should have been cleared for this test");

        CrateStackColumnSync.reconcile(helper.getLevel(), root, stack);

        check(helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "reconciliation did not restore the missing cell");
        check(stack.crateCount() == 3, "reconciliation must not touch the crates");
        helper.succeed();
    }

    /** And one that comes back with a cell too many gives it up. */
    @GameTest(template = TEMPLATE)
    public static void aStaleContinuationIsReleased(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);

        // A cell left behind by a column that used to be taller.
        helper.getLevel().setBlock(
                root.above(),
                BlockRegistry.CRATE_STACK.get().defaultBlockState()
                        .setValue(CrateStackBlock.PART, 1),
                Block.UPDATE_ALL);

        CrateStackColumnSync.reconcile(helper.getLevel(), root, stack);

        check(!helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "a cell the column does not need was kept");
        check(stack.crateCount() == 2, "reconciliation must not touch the crates");
        helper.succeed();
    }

    /**
     * When the world and the column cannot both be satisfied, the column keeps its crates.
     *
     * <p>A foreign block standing where a cell belongs is left exactly where it is. The column simply
     * occupies less world than it would like — which is recoverable, whereas deleting a crate to make
     * the geometry tidy is not.
     */
    @GameTest(template = TEMPLATE)
    public static void reconciliationNeverOverwritesAForeignBlock(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.SMALL);
        grow(helper, root, CrateVariant.SMALL);
        stack.containerFor(0).setItem(0, new ItemStack(Items.EMERALD, 3));

        helper.getLevel().removeBlock(root.above(), false);
        helper.getLevel().setBlock(root.above(), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);

        CrateStackColumnSync.reconcile(helper.getLevel(), root, stack);

        check(helper.getLevel().getBlockState(root.above()).is(Blocks.OBSIDIAN),
                "reconciliation overwrote a block that was not its own");
        check(stack.crateCount() == 3, "reconciliation deleted crates to tidy the geometry");
        check(stack.containerFor(0).getItem(0).getCount() == 3, "contents were disturbed");
        check(dropsAround(helper, root) == 0, "reconciliation dropped something");
        helper.succeed();
    }

    /** Saved and reloaded, a column rebuilds exactly the cells its crates call for. */
    @GameTest(template = TEMPLATE)
    public static void aReloadedColumnClaimsTheCellsItsCratesRequire(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        grow(helper, root, CrateVariant.MEDIUM);
        grow(helper, root, CrateVariant.SMALL);
        int cellsBefore = stack.requiredCellCount();

        CompoundTag saved = stack.saveWithoutMetadata(helper.getLevel().registryAccess());
        stack.loadWithComponents(saved, helper.getLevel().registryAccess());
        CrateStackColumnSync.reconcile(helper.getLevel(), root, stack);

        check(stack.requiredCellCount() == cellsBefore, "the reloaded column changed height");
        for (int cell = 1; cell < cellsBefore; cell++) {
            check(helper.getLevel().getBlockState(root.above(cell))
                            .is(BlockRegistry.CRATE_STACK.get()),
                    "cell " + cell + " is missing after reload");
            check(helper.getLevel().getBlockEntity(root.above(cell)) == null,
                    "a reloaded continuation cell gained a block entity");
        }
        helper.succeed();
    }

    /* ─── helpers ────────────────────────────────────────────── */

    /** A promoted column of one crate, standing on stone, ready to grow. */
    private static BlockPos column(GameTestHelper helper, CrateVariant first) {
        BlockPos floor = new BlockPos(2, 0, 2);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos root = helper.absolutePos(floor.above());
        helper.getLevel().setBlock(
                root,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);

        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), root);
        check(promoted.succeeded(), "could not start a column: " + promoted.refusal());
        if (first != CrateVariant.SMALL) {
            CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
            stack.removeCrate(promoted.promoted().orElseThrow().crateId());
            stack.appendCrate(first, Direction.NORTH);
        }
        return root;
    }

    private static CrateStackBlockEntity stackAt(GameTestHelper helper, BlockPos root) {
        if (helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity stack) {
            return stack;
        }
        throw new GameTestAssertException("no crate column at " + root);
    }

    private static void grow(GameTestHelper helper, BlockPos root, CrateVariant variant) {
        CrateStackColumnSync.Growth growth =
                CrateStackColumnSync.appendCrate(helper.getLevel(), root, variant, Direction.NORTH);
        check(growth.succeeded(), "could not grow the column: " + growth.refusal());
    }

    private static int dropsAround(GameTestHelper helper, BlockPos root) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(6.0D))
                .size();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

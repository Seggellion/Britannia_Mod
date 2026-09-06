package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackAutomation;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hoppers and comparators against a compact column.
 *
 * <h2>The thing that had to be proven first</h2>
 *
 * <p>Only a column's root carries a block entity. A hopper beside a continuation cell finds nothing
 * if it looks for one - so the block answers instead, through {@code WorldlyContainerHolder}, which
 * {@code HopperBlockEntity.getBlockContainer} consults before any block entity and hands the position
 * to. That is what makes automation reach a column at all, and the continuation test below is the one
 * that would catch it breaking.
 *
 * <p>Which crate a hopper talks to is decided by the face it is on, because a column is several crates
 * a player sees separately and exposing all of them at once would make automation unpredictable the
 * moment the column repacked.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackAutomationGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "crate_automation";

    private CrateStackAutomationGameTests() {
    }

    /* ─── the lookup itself ──────────────────────────────────── */

    /**
     * A continuation cell answers for its column, though it holds no block entity of its own.
     *
     * <p>This is the load-bearing one. Everything else here assumes a hopper can find a column from
     * any of its cells; without this it can only find it from the root, and a tall column is
     * unreachable from the side.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aContinuationCellAnswersForItsColumn(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        check(stack.requiredCellCount() >= 2, "this fixture needs a continuation cell");

        BlockPos continuation = root.above();
        BlockState state = helper.getLevel().getBlockState(continuation);
        check(state.getBlock() instanceof CrateStackBlock, "no continuation cell at " + continuation);
        check(helper.getLevel().getBlockEntity(continuation) == null,
                "the continuation cell has a block entity, so this proves nothing");

        Container found = HopperBlockEntity.getContainerAt(helper.getLevel(), continuation);
        check(found != null,
                "a hopper beside a continuation cell finds no container, so a tall column cannot be "
                        + "automated from the side");
        check(found instanceof CrateStackAutomation, "the container found was " + found);
        helper.succeed();
    }

    /* ─── which crate a face means ───────────────────────────── */

    /** The top face reaches the top crate, and the bottom face the bottom one. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void verticalFacesReachTheEndsOfTheColumn(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.MEDIUM);
        CrateStackBlockEntity stack = stackAt(helper, root);
        CrateStackAutomation view = view(helper, root);

        check(view.crateIndexFor(Direction.UP) == 1,
                "the top face reached crate " + view.crateIndexFor(Direction.UP) + ", not the top one");
        check(view.crateIndexFor(Direction.DOWN) == 0,
                "the bottom face reached crate " + view.crateIndexFor(Direction.DOWN)
                        + ", not the bottom one");

        // And the slots offered are that crate's, not the whole column's.
        check(view.getSlotsForFace(Direction.DOWN).length
                        == stack.crates().get(0).variant().slotCount(),
                "the bottom face was offered the wrong number of slots");
        check(view.getSlotsForFace(Direction.UP).length
                        == stack.crates().get(1).variant().slotCount(),
                "the top face was offered the wrong number of slots");
        helper.succeed();
    }

    /**
     * A side hopper reaches whichever crate is mostly in front of it.
     *
     * <p>Two small crates share the root cell: the first fills 7.15 voxels of it and the second 7.15
     * more, so the cell is shared almost equally and the rule has to be stated rather than guessed.
     * The lower crate wins a draw, and here it is not even a draw - both are wholly inside, and the
     * first one listed is lower.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aSideHopperReachesTheCrateMostlyInFrontOfIt(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackAutomation atRoot = view(helper, root);

        check(atRoot.crateIndexFor(Direction.NORTH) == 0,
                "the root cell offered crate " + atRoot.crateIndexFor(Direction.NORTH)
                        + "; both small crates are inside it and the lower one wins");

        // A taller column puts a different crate in the cell above.
        BlockPos tall = column(helper, new BlockPos(4, 1, 4),
                CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL);
        // Cell one spans 16.00 to 32.00 voxels. The third small crate reaches 14.30 to 21.45 and so
        // has 5.45 voxels inside it; the fourth reaches 21.45 to 28.60 and has all 7.15. The fourth
        // wins, and would go on winning until something below it was removed.
        CrateStackAutomation upper = view(helper, tall.above());
        check(upper.crateIndexFor(Direction.NORTH) == 3,
                "the cell above offered crate " + upper.crateIndexFor(Direction.NORTH)
                        + "; the fourth crate has the most of itself in it");
        helper.succeed();
    }

    /* ─── real hoppers ───────────────────────────────────────── */

    /** An item poured in from above lands in the top crate and nowhere else. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void aHopperAboveFillsTheTopCrate(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        int bottom = stack.crates().get(0).id();
        int top = stack.crates().get(1).id();

        BlockPos hopper = root.above(stack.requiredCellCount());
        helper.getLevel().setBlock(hopper,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        check(helper.getLevel().getBlockEntity(hopper) instanceof HopperBlockEntity feeder
                        && trySet(feeder, new ItemStack(Items.DIAMOND, 1)),
                "the feeding hopper could not be loaded");

        helper.runAfterDelay(60L, () -> {
            check(stack.containerFor(top).getItem(0).is(Items.DIAMOND),
                    "the item did not reach the top crate");
            for (int slot = 0; slot < stack.containerFor(bottom).getContainerSize(); slot++) {
                check(stack.containerFor(bottom).getItem(slot).isEmpty(),
                        "the item reached the bottom crate as well");
            }
            helper.succeed();
        });
    }

    /** A hopper underneath drains the bottom crate and leaves the top one alone. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void aHopperBelowDrainsTheBottomCrate(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        int bottom = stack.crates().get(0).id();
        int top = stack.crates().get(1).id();
        stack.containerFor(bottom).setItem(0, new ItemStack(Items.DIAMOND, 2));
        stack.containerFor(top).setItem(0, new ItemStack(Items.EMERALD, 2));

        BlockPos hopper = root.below();
        helper.getLevel().setBlock(hopper,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);

        helper.runAfterDelay(60L, () -> {
            check(helper.getLevel().getBlockEntity(hopper) instanceof HopperBlockEntity drain
                            && holds(drain, Items.DIAMOND),
                    "the hopper below did not draw from the bottom crate");
            check(stack.containerFor(top).getItem(0).is(Items.EMERALD)
                            && stack.containerFor(top).getItem(0).getCount() == 2,
                    "the hopper below took from the top crate as well");
            helper.succeed();
        });
    }

    /* ─── comparators ────────────────────────────────────────── */

    /** A comparator reads the whole column, from any of its cells. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aComparatorReadsTheWholeColumn(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);

        int empty = signal(helper, root);
        check(empty == 0, "an empty column reported " + empty);

        for (LogicalFill fill : List.of(new LogicalFill(0, 9), new LogicalFill(1, 9))) {
            int crateId = stack.crates().get(fill.crate()).id();
            for (int slot = 0; slot < fill.slots(); slot++) {
                stack.containerFor(crateId).setItem(slot, new ItemStack(Items.DIAMOND, 64));
            }
        }

        int filled = signal(helper, root);
        check(filled > empty,
                "filling two of three crates did not change the signal (" + filled + ")");

        // Every cell of the column reports the same column.
        for (int cell = 0; cell < stack.requiredCellCount(); cell++) {
            int here = signal(helper, root.above(cell));
            check(here == filled,
                    "cell " + cell + " reported " + here + " rather than the column's " + filled);
        }
        helper.succeed();
    }


    /* --- a column that changes under the hopper --------------- */

    /**
     * Repacking a column never moves anybody's items into another crate.
     *
     * <p>A hopper addresses a crate by where it sits, and removing a crate low down slides everything
     * above it into new positions. Identity has to survive that: the crate that held the diamonds
     * still holds them afterwards, whatever cell it has moved into.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aRepackNeverMovesItemsBetweenCrates(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = stackAt(helper, root);
        int bottom = stack.crates().get(0).id();
        int middle = stack.crates().get(1).id();
        int top = stack.crates().get(2).id();
        stack.containerFor(bottom).setItem(0, new ItemStack(Items.APPLE, 1));
        stack.containerFor(middle).setItem(0, new ItemStack(Items.DIAMOND, 2));
        stack.containerFor(top).setItem(0, new ItemStack(Items.EMERALD, 3));

        // Taking the lowest crate slides the other two down a whole crate's height.
        stack.removeCrate(bottom);
        CrateStackColumnSync.notifyClients(helper.getLevel(), root,
                CrateStackColumnSync.reconcile(helper.getLevel(), root));

        check(stack.containerFor(middle).getItem(0).is(Items.DIAMOND),
                "the repack moved the middle crate's diamonds somewhere else");
        check(stack.containerFor(top).getItem(0).is(Items.EMERALD),
                "the repack moved the top crate's emeralds somewhere else");

        // A view taken after the repack writes into the crate that is actually there now.
        CrateStackAutomation after = view(helper, root);
        check(after.crateIndexFor(Direction.NORTH) == 0,
                "the root cell offered crate " + after.crateIndexFor(Direction.NORTH)
                        + " after the repack; the crate that slid down to the floor is first");
        int[] slots = after.getSlotsForFace(Direction.NORTH);
        after.setItem(slots[1], new ItemStack(Items.GOLD_INGOT, 1));
        check(stack.containerFor(middle).getItem(1).is(Items.GOLD_INGOT),
                "a post-repack insert landed outside the crate the hopper was offered");
        check(stack.containerFor(top).getItem(1).isEmpty(),
                "a post-repack insert reached a crate the hopper was never offered");
        helper.succeed();
    }

    /**
     * A view held across the removal of its own column does nothing rather than something wrong.
     *
     * <p>A hopper is handed a container and uses it moments later, which leaves a window where the
     * column can be broken or carried away in between. Writing into what is left must not bring the
     * column back from the dead.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aViewOutlivesTheColumnItAddressed(GameTestHelper helper) {
        BlockPos root = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackAutomation stale = view(helper, root);

        helper.getLevel().removeBlock(root, false);
        check(!helper.getLevel().getBlockState(root).is(BlockRegistry.CRATE_STACK.get()),
                "the fixture needs the column gone");

        // Anything the hopper does now must be inert, not fatal.
        stale.getContainerSize();
        stale.isEmpty();
        stale.setItem(0, new ItemStack(Items.DIAMOND, 1));

        check(!helper.getLevel().getBlockState(root).is(BlockRegistry.CRATE_STACK.get()),
                "a stale hopper view put the removed column back into the world");
        helper.succeed();
    }

    private record LogicalFill(int crate, int slots) {
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private static BlockPos column(GameTestHelper helper, CrateVariant... variants) {
        return column(helper, new BlockPos(2, 1, 2), variants);
    }

    private static BlockPos column(GameTestHelper helper, BlockPos floor, CrateVariant... variants) {
        helper.setBlock(floor, Blocks.STONE);
        BlockPos root = helper.absolutePos(floor.above());
        helper.getLevel().setBlock(root,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(CrateBlock.FACING, Direction.NORTH),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), root);
        check(promoted.succeeded(), "could not build a column: " + promoted.refusal());
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        // The promotion made the first crate; the fixture asks for the rest.
        check(variants[0] == CrateVariant.SMALL, "the first crate of a fixture is always small");
        for (int index = 1; index < variants.length; index++) {
            check(CrateStackColumnSync.appendCrate(
                            helper.getLevel(), root, variants[index], Direction.NORTH).succeeded(),
                    "could not grow the column");
        }
        return root;
    }

    private static CrateStackBlockEntity stackAt(GameTestHelper helper, BlockPos root) {
        check(helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity,
                "no column at " + root);
        return (CrateStackBlockEntity) helper.getLevel().getBlockEntity(root);
    }

    /** The automation view a hopper touching this cell would be handed. */
    private static CrateStackAutomation view(GameTestHelper helper, BlockPos cell) {
        BlockState state = helper.getLevel().getBlockState(cell);
        check(state.getBlock() instanceof WorldlyContainerHolder,
                "the block at " + cell + " does not answer for hoppers");
        WorldlyContainer container = ((WorldlyContainerHolder) state.getBlock())
                .getContainer(state, helper.getLevel(), cell);
        check(container instanceof CrateStackAutomation, "got " + container + " at " + cell);
        return (CrateStackAutomation) container;
    }

    private static int signal(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(pos);
        return state.getAnalogOutputSignal(helper.getLevel(), pos);
    }

    private static boolean trySet(HopperBlockEntity hopper, ItemStack item) {
        hopper.setItem(0, item);
        return !hopper.getItem(0).isEmpty();
    }

    private static boolean holds(HopperBlockEntity hopper, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
            if (hopper.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

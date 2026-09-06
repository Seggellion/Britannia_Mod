package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Worlds that existed before columns did.
 *
 * <h2>Nothing is migrated</h2>
 *
 * <p>Every crate already in the world stays exactly the crate it was. There is no bulk conversion
 * pass, no load-time rewrite and no repair sweep: a crate becomes part of a column only when a player
 * deliberately stacks something on it, and until then it keeps its own block, its own block entity
 * and its own contents.
 *
 * <p>That includes the pairs of crates old worlds are full of - one placed on top of another, back
 * when that left the upper one visibly floating. Those are two ordinary crates, and they are left as
 * two ordinary crates.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackLegacyWorldGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "crate_legacy_world";

    private CrateStackLegacyWorldGameTests() {
    }

    /* --- crates that were already there ---------------------- */

    /** A crate standing on its own is still a crate, and still holds what it held. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void aLegacyCrateIsNeverMigratedOnItsOwn(GameTestHelper helper) {
        BlockPos crate = legacyCrate(helper, new BlockPos(2, 1, 2), CrateVariant.SMALL);
        contents(helper, crate).setItem(0, new ItemStack(Items.DIAMOND, 7));

        // Block updates around it are what a migration pass would most plausibly ride in on.
        helper.getLevel().setBlock(crate.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().removeBlock(crate.east(), false);

        helper.runAfterDelay(20L, () -> {
            check(helper.getLevel().getBlockState(crate).is(BlockRegistry.SMALL_CRATE.get()),
                    "a crate that was already in the world became "
                            + helper.getLevel().getBlockState(crate).getBlock());
            check(contents(helper, crate).getItem(0).getCount() == 7,
                    "the crate's contents were disturbed");
            helper.succeed();
        });
    }

    /** The old floating pair is two crates, and stays two crates. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void anOldFloatingPairIsLeftExactlyAsItWas(GameTestHelper helper) {
        BlockPos lower = legacyCrate(helper, new BlockPos(2, 1, 2), CrateVariant.SMALL);
        BlockPos upper = legacyCrate(helper, new BlockPos(2, 2, 2), CrateVariant.SMALL);
        contents(helper, lower).setItem(0, new ItemStack(Items.APPLE, 3));
        contents(helper, upper).setItem(0, new ItemStack(Items.EMERALD, 5));

        helper.runAfterDelay(20L, () -> {
            check(helper.getLevel().getBlockState(lower).is(BlockRegistry.SMALL_CRATE.get()),
                    "the lower crate of an old pair was converted");
            check(helper.getLevel().getBlockState(upper).is(BlockRegistry.SMALL_CRATE.get()),
                    "the upper crate of an old pair was converted");
            check(contents(helper, lower).getItem(0).is(Items.APPLE),
                    "the lower crate's contents moved");
            check(contents(helper, upper).getItem(0).is(Items.EMERALD),
                    "the upper crate's contents moved");
            helper.succeed();
        });
    }

    /** Joining a column is opt-in: it happens when somebody stacks on the crate, and not before. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aLegacyCrateStillPromotesWhenSomethingIsStackedOnIt(GameTestHelper helper) {
        BlockPos crate = legacyCrate(helper, new BlockPos(2, 1, 2), CrateVariant.SMALL);
        contents(helper, crate).setItem(0, new ItemStack(Items.DIAMOND, 7));

        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), crate);

        check(promoted.succeeded(),
                "an existing crate refused to join a column: " + promoted.refusal());
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        int carried = promoted.promoted().orElseThrow().crateId();
        check(stack.crateCount() == 1, "promotion invented crates that were not there");
        check(stack.containerFor(carried).getItem(0).getCount() == 7,
                "the crate lost its contents on the way into a column");
        helper.succeed();
    }

    /* --- the same column, over and over ----------------------- */

    /**
     * Building a column, filling it and emptying it leaves nothing behind, however often it is done.
     *
     * <p>Run three times over two shapes, because the failure this guards against is cumulative: a
     * cell released one crate short, or a block entity that outlives its block, shows up as the
     * second cycle refusing to build rather than as anything wrong with the first.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theBuildFillEmptyCycleRepeats(GameTestHelper helper) {
        CrateVariant[][] shapes = {
            {CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL},
            {CrateVariant.SMALL, CrateVariant.MEDIUM, CrateVariant.SMALL},
        };
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos root = helper.absolutePos(floor.above());

        for (int cycle = 1; cycle <= 3; cycle++) {
            for (CrateVariant[] shape : shapes) {
                String where = "cycle " + cycle + " of " + describe(shape);
                CrateStackBlockEntity stack = build(helper, root, shape, where);

                for (int index = 0; index < shape.length; index++) {
                    stack.containerFor(stack.crates().get(index).id())
                            .setItem(0, new ItemStack(Items.DIAMOND, index + 1));
                }
                check(stack.crateCount() == shape.length, where + ": the column did not fill");

                while (stack.crateCount() > 0) {
                    stack.removeCrate(stack.crates().get(stack.crateCount() - 1).id());
                }
                CrateStackColumnSync.notifyClients(helper.getLevel(), root,
                        CrateStackColumnSync.reconcile(helper.getLevel(), root));
                CrateStackBlock.removeColumn(helper.getLevel(), root);

                for (int cell = 0; cell < 4; cell++) {
                    check(helper.getLevel().getBlockState(root.above(cell)).isAir(),
                            where + ": cell " + cell + " still holds "
                                    + helper.getLevel().getBlockState(root.above(cell)).getBlock());
                }
                check(helper.getLevel().getBlockEntity(root) == null,
                        where + ": a block entity outlived its column");
            }
        }
        helper.succeed();
    }

    /* --- fixtures -------------------------------------------- */

    /** A crate placed the way a world from before columns holds one: on its own, as a plain block. */
    private static BlockPos legacyCrate(GameTestHelper helper, BlockPos at, CrateVariant variant) {
        BlockPos pos = helper.absolutePos(at);
        helper.getLevel().setBlock(pos,
                block(variant).defaultBlockState().setValue(CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        check(helper.getLevel().getBlockEntity(pos) instanceof CrateBlockEntity,
                "the legacy crate at " + pos + " has no inventory");
        return pos;
    }

    private static CrateBlockEntity contents(GameTestHelper helper, BlockPos pos) {
        check(helper.getLevel().getBlockEntity(pos) instanceof CrateBlockEntity,
                "no crate inventory at " + pos);
        return (CrateBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static CrateStackBlockEntity build(
            GameTestHelper helper, BlockPos root, CrateVariant[] shape, String where) {

        helper.getLevel().setBlock(root,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), root);
        check(promoted.succeeded(), where + ": could not start a column: " + promoted.refusal());
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        for (int index = 1; index < shape.length; index++) {
            check(CrateStackColumnSync.appendCrate(
                            helper.getLevel(), root, shape[index], Direction.NORTH).succeeded(),
                    where + ": could not grow the column to crate " + index);
        }
        return stack;
    }

    private static Block block(CrateVariant variant) {
        return switch (variant) {
            case SMALL -> BlockRegistry.SMALL_CRATE.get();
            case MEDIUM -> BlockRegistry.MEDIUM_CRATE.get();
        };
    }

    private static String describe(CrateVariant[] shape) {
        StringBuilder text = new StringBuilder();
        for (CrateVariant variant : shape) {
            text.append(text.isEmpty() ? "" : "+").append(variant.serializedName());
        }
        return text.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

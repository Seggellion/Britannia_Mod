package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Building a stacked pair of large crates, taking it away, and building it again.
 *
 * <h2>The defect these exist for</h2>
 *
 * <p>A crate standing on another is drawn by the crate underneath it, because that is the cell its art
 * physically occupies. Remove the lower crate and the upper one was still entirely there - eight
 * cells, its own block entity, its fifty-four slots - with nothing left in the world that knew how to
 * draw it. It became solid, selectable, unbreakable and invisible: outlines hanging in empty air, and
 * a position nothing else could be built in.
 *
 * <p>So a crate that loses its foundation now settles onto its own cell floor and goes back to being
 * an ordinary large crate. These hold that for both the way it is supposed to happen and the way a
 * world that already contains one recovers.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateLargeStackLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The lid of a large crate, where the crate above it rests. */
    private static final int LID = 1900;

    private CrateLargeStackLifecycleGameTests() {
    }

    /* ─── the cycle that was reported broken ─────────────────── */

    /**
     * Stack, clear, rebuild - three times over, in the same place.
     *
     * <p>Three rather than one because the complaint was not that it failed, but that it stopped
     * working after the first time: anything left behind accumulates, and one round would not show it.
     */
    @GameTest(template = TEMPLATE)
    public static void theStackClearRebuildCycleRepeats(GameTestHelper helper) {
        ServerPlayer player = placer(helper);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = floor.above();

        for (int round = 1; round <= 3; round++) {
            placeLarge(helper, player, floor);
            check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                    "round " + round + ": the lower crate would not go down again");
            stackLarge(helper, player, anchor);

            BlockPos upper = CrateFoundation.columnRootFor(anchor);
            check(helper.getLevel().getBlockEntity(upper) instanceof CrateBlockEntity resting
                            && resting.hasFoundation(),
                    "round " + round + ": nothing could be stacked on the replacement crate");

            clearVolume(helper, anchor, 4);
            check(noCrateCellsAround(helper, anchor),
                    "round " + round + ": clearing the pair left crate cells behind");
        }
        finish(helper, player);
    }

    /* ─── losing the crate underneath ────────────────────────── */

    /**
     * Taking the lower crate away leaves the upper one visible and ordinary.
     *
     * <p>This is the exact sequence that produced the floating outlines: the lower crate goes and the
     * upper one has nothing left to draw it.
     */
    @GameTest(template = TEMPLATE)
    public static void aCrateWhoseFoundationGoesBecomesAnOrdinaryCrate(GameTestHelper helper) {
        ServerPlayer player = placer(helper);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = floor.above();

        placeLarge(helper, player, floor);
        stackLarge(helper, player, anchor);
        BlockPos upperAnchor = CrateFoundation.columnRootFor(anchor);
        CrateBlockEntity upper = crateAt(helper, upperAnchor);
        upper.setItem(0, new ItemStack(Items.DIAMOND, 3));
        check(upper.hasFoundation(), "the fixture never stacked");

        // Only the lower crate, which is what a tester would remove.
        clearVolume(helper, anchor, 2);

        CrateBlockEntity after = crateAt(helper, upperAnchor);
        check(!after.hasFoundation(),
                "the upper crate is still standing on a crate that no longer exists, so nothing "
                        + "draws it: it is solid, invisible and cannot be removed");
        check(after.originHundredths() == 0,
                "the upper crate settled to " + after.originHundredths() + " rather than its floor");
        check(after.getItem(0).is(Items.DIAMOND), "settling cost the crate its contents");
        check(!helper.getLevel().getBlockState(upperAnchor)
                        .getShape(helper.getLevel(), upperAnchor).isEmpty(),
                "the settled crate has no shape to click on");
        finish(helper, player);
    }

    /**
     * A crate already orphaned in a saved world repairs itself.
     *
     * <p>Worlds built with the earlier code contain crates that are standing on nothing and cannot say
     * so. They are put right the first time anything happens next to them, which is what stops the
     * only remedy being a new world.
     */
    @GameTest(template = TEMPLATE)
    public static void anAlreadyOrphanedCrateRepairsItself(GameTestHelper helper) {
        ServerPlayer player = placer(helper);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = floor.above();

        // A large crate standing on the ground, then told it is resting on something that is not
        // there - which is precisely the state the old code left behind.
        placeLarge(helper, player, floor);
        CrateBlockEntity orphan = crateAt(helper, anchor);
        orphan.setItem(0, new ItemStack(Items.EMERALD, 2));
        orphan.setOriginHundredths(LID - 2 * 1600);
        check(orphan.hasFoundation(), "the fixture did not reproduce the orphaned state");

        // Anything at all happening beside it.
        helper.getLevel().setBlock(anchor.offset(-1, 0, 0), Blocks.STONE.defaultBlockState(), 3);

        CrateBlockEntity repaired = crateAt(helper, anchor);
        check(!repaired.hasFoundation(),
                "an orphaned crate stayed invisible after the world changed around it");
        check(repaired.getItem(0).is(Items.EMERALD), "repairing the crate cost it its contents");
        check(helper.getLevel().getBlockState(anchor.offset(-1, 0, 0)).is(Blocks.STONE),
                "the repair disturbed a block that had nothing to do with it");
        finish(helper, player);
    }

    /** An ordinary large crate standing on the ground is left completely alone. */
    @GameTest(template = TEMPLATE)
    public static void anOrdinaryLargeCrateIsNotTouched(GameTestHelper helper) {
        ServerPlayer player = placer(helper);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = floor.above();

        placeLarge(helper, player, floor);
        CrateBlockEntity crate = crateAt(helper, anchor);
        crate.setItem(0, new ItemStack(Items.EMERALD, 4));

        helper.getLevel().setBlock(anchor.offset(-1, 0, 0), Blocks.STONE.defaultBlockState(), 3);

        CrateBlockEntity after = crateAt(helper, anchor);
        check(!after.hasFoundation(), "an ordinary crate was given a foundation offset");
        check(after.getItem(0).is(Items.EMERALD), "an ordinary crate lost its contents");
        int cells = 0;
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    if (helper.getLevel().getBlockState(anchor.offset(x, y, z))
                            .getBlock() instanceof CrateBlock) {
                        cells++;
                    }
                }
            }
        }
        check(cells == 8, "an ordinary large crate lost cells; found " + cells + " of 8");
        finish(helper, player);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private static void placeLarge(GameTestHelper helper, ServerPlayer player, BlockPos floor) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(large(), 8));
        Vec3 at = new Vec3(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, new BlockHitResult(at, Direction.UP, floor, false));
    }

    private static void stackLarge(GameTestHelper helper, ServerPlayer player, BlockPos anchor) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(large(), 8));
        double y = anchor.getY() + LID / 1600.0D;
        Vec3 at = new Vec3(anchor.getX() + 0.5D, y, anchor.getZ() + 0.5D);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(at, Direction.UP, anchor.above(), false));
    }

    private static void clearVolume(GameTestHelper helper, BlockPos anchor, int levels) {
        for (int y = 0; y < levels; y++) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    helper.getLevel().setBlock(anchor.offset(x, y, z),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean noCrateCellsAround(GameTestHelper helper, BlockPos anchor) {
        for (int y = -1; y <= 5; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    if (helper.getLevel().getBlockState(anchor.offset(x, y, z))
                            .getBlock() instanceof CrateBlock) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static CrateBlockEntity crateAt(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(pos);
        check(state.getBlock() instanceof CrateBlock,
                "no crate at " + pos + "; found " + state.getBlock());
        check(helper.getLevel().getBlockEntity(pos) instanceof CrateBlockEntity,
                "the crate at " + pos + " has no inventory");
        return (CrateBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ServerPlayer placer(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        player.absMoveTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setXRot(0.0F);
        player.setOldPosAndRot();
        return player;
    }

    private static void finish(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static Item large() {
        return ItemRegistry.LARGE_CRATE_ITEM.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
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
 * Picking stacked large crates up with Grabby Hands.
 *
 * <h2>The two things that went wrong</h2>
 *
 * <p>A crate standing on another is physically inside the lower crate's cells, so a ray that meets its
 * body lands on the lower crate's block. Grabby named a crate from that position alone, which meant
 * aiming at the upper crate and grabbing the lower one.
 *
 * <p>And a crate put there by stacking never carried Grabby's provenance stamp. Grabby stamps what it
 * places, and it steps aside for stacking - so the crate read as scenery nobody had put there, and
 * Grabby refused to pick it up at all.
 *
 * <p>Both are driven here through {@code GrabbyPickupTransaction}, the transaction gameplay actually
 * runs, rather than by handing a helper a target and asserting on the result.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateLargeStackGrabbyGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The lid of a large crate, where the crate above it rests. */
    private static final int LID = 1900;

    private CrateLargeStackGrabbyGameTests() {
    }

    /* ─── which crate the player meant ───────────────────────── */

    /**
     * Aiming at the upper crate takes the upper crate.
     *
     * <p>The aim is deliberately at 25 voxels - the middle of the upper crate's body, which is inside
     * the lower crate's cell. Before this was fixed that click resolved to the crate underneath and
     * took the wrong one.
     */
    @GameTest(template = TEMPLATE)
    public static void grabbingTheUpperCrateTakesTheUpperCrate(GameTestHelper helper) {
        Pair pair = stackedPair(helper);
        pair.lower().setItem(0, new ItemStack(Items.APPLE, 4));
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        GrabbyPickupResult result = grabAt(helper, pair, 2500);

        check(result.outcome() == GrabbyPickupOutcome.SUCCESS,
                "Grabby refused the upper crate: " + result.outcome());
        check(result.root().equals(pair.upperAnchor()),
                "Grabby took the crate at " + result.root() + " when the player was pointing at the "
                        + "one at " + pair.upperAnchor());
        check(helper.getLevel().getBlockEntity(pair.anchor()) instanceof CrateBlockEntity lower
                        && lower.getItem(0).is(Items.APPLE),
                "the lower crate did not survive with its own contents");
        check(!(helper.getLevel().getBlockState(pair.upperAnchor()).getBlock() instanceof CrateBlock),
                "the upper crate is still standing there");
        finish(helper, pair);
    }

    /** Aiming at the lower crate's own body still takes the lower crate. */
    @GameTest(template = TEMPLATE)
    public static void grabbingTheLowerCrateTakesTheLowerCrate(GameTestHelper helper) {
        Pair pair = stackedPair(helper);
        pair.lower().setItem(0, new ItemStack(Items.APPLE, 4));
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        // Ten voxels up: the lower crate's own body, well below its lid.
        GrabbyPickupResult result = grabAt(helper, pair, 1000);

        check(result.outcome() == GrabbyPickupOutcome.SUCCESS,
                "Grabby refused the lower crate: " + result.outcome());
        check(result.root().equals(pair.anchor()),
                "Grabby took the crate at " + result.root() + " rather than the lower one");

        // And the crate that was standing on it is now an ordinary crate, not an invisible one.
        CrateBlockEntity survivor = crateAt(helper, pair.upperAnchor());
        check(!survivor.hasFoundation(),
                "the surviving crate is still standing on a crate that has gone");
        check(survivor.getItem(0).is(Items.DIAMOND), "the survivor lost its contents");
        finish(helper, pair);
    }

    /**
     * The crate left behind can be grabbed immediately afterwards.
     *
     * <p>This is the second half of the live complaint, and the half a state assertion would miss: the
     * survivor settled correctly and still could not be picked up, because nothing had ever recorded
     * that a player put it there.
     */
    @GameTest(template = TEMPLATE)
    public static void theSurvivingCrateCanBeGrabbedImmediately(GameTestHelper helper) {
        Pair pair = stackedPair(helper);
        pair.upper().setItem(0, new ItemStack(Items.DIAMOND, 3));

        grabAt(helper, pair, 1000);
        CrateBlockEntity survivor = crateAt(helper, pair.upperAnchor());
        check(!survivor.hasFoundation(), "the survivor never settled, so this proves nothing");

        GrabbyPickupResult second = GrabbyPickupTransaction.execute(
                GrabbyWorld.of(helper.getLevel()), GrabbyActor.of(pair.player()),
                pair.upperAnchor());

        check(second.outcome() == GrabbyPickupOutcome.SUCCESS,
                "the crate left behind cannot be picked up: " + second.outcome());
        check(!(helper.getLevel().getBlockState(pair.upperAnchor()).getBlock() instanceof CrateBlock),
                "the crate left behind was not removed");
        finish(helper, pair);
    }

    /** A stacked crate carries the same provenance as one Grabby placed itself. */
    @GameTest(template = TEMPLATE)
    public static void aStackedCrateIsRecordedAsPlayerPlaced(GameTestHelper helper) {
        Pair pair = stackedPair(helper);

        check(pair.upper().grabbyState().grabbyManaged(),
                "a crate the player stacked reads as scenery nobody placed, so Grabby will not "
                        + "touch it");
        finish(helper, pair);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Pair(
            GameTestHelper helper, ServerPlayer player, BlockPos anchor, BlockPos upperAnchor,
            CrateBlockEntity lower, CrateBlockEntity upper) {
    }

    /** A large crate with another standing on its lid, both placed the way a player places them. */
    private static Pair stackedPair(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        player.absMoveTo(floor.getX() + 2.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.setYRot(90.0F);
        player.setYHeadRot(90.0F);
        player.setXRot(0.0F);
        player.setOldPosAndRot();

        place(helper, player, floor, floor.getY() + 1.0D, floor);
        BlockPos anchor = floor.above();
        place(helper, player, anchor, anchor.getY() + LID / 1600.0D, anchor.above());
        BlockPos upperAnchor = CrateFoundation.columnRootFor(anchor);

        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "the lower crate was not placed");
        check(helper.getLevel().getBlockEntity(upperAnchor) instanceof CrateBlockEntity resting
                        && resting.hasFoundation(),
                "the upper crate is not standing on the lower one");
        return new Pair(helper, player, anchor, upperAnchor,
                (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor),
                (CrateBlockEntity) helper.getLevel().getBlockEntity(upperAnchor));
    }

    private static void place(
            GameTestHelper helper, ServerPlayer player, BlockPos at, double y, BlockPos clicked) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(large(), 8));
        Vec3 hit = new Vec3(at.getX() + 0.5D, y, at.getZ() + 0.5D);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, clicked, false));
    }

    /**
     * Grabs whatever is at a height on the stack, the way the interaction handler names it.
     *
     * <p>The handler decides which crate a hit belongs to and hands that to the transaction, so this
     * follows the same two steps rather than reaching past the first one.
     */
    private static GrabbyPickupResult grabAt(GameTestHelper helper, Pair pair, int heightHundredths) {
        BlockPos cell = pair.anchor().above(heightHundredths / 1600);
        Vec3 hit = new Vec3(pair.anchor().getX() + 0.5D,
                pair.anchor().getY() + heightHundredths / 1600.0D,
                pair.anchor().getZ() + 0.5D);
        BlockState state = helper.getLevel().getBlockState(cell);
        BlockPos aimedAt = CrateFoundation.crateRootAt(helper.getLevel(), cell, state, hit);
        return GrabbyPickupTransaction.execute(
                GrabbyWorld.of(helper.getLevel()), GrabbyActor.of(pair.player()), aimedAt);
    }

    private static CrateBlockEntity crateAt(GameTestHelper helper, BlockPos pos) {
        check(helper.getLevel().getBlockState(pos).getBlock() instanceof CrateBlock,
                "no crate at " + pos + "; found " + helper.getLevel().getBlockState(pos).getBlock());
        check(helper.getLevel().getBlockEntity(pos) instanceof CrateBlockEntity,
                "the crate at " + pos + " has no inventory");
        return (CrateBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void finish(GameTestHelper helper, Pair pair) {
        helper.getLevel().getServer().getPlayerList().remove(pair.player());
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

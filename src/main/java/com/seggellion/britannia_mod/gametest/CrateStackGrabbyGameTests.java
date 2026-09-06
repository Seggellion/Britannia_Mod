package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateStackBreakTargets;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupOutcome;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupResult;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPickupTransaction;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Carrying one crate out of a compact column.
 *
 * <h2>One visible crate is one object</h2>
 *
 * <p>Three crates a player can point at individually share one block entity and one position. Grabby
 * has always taken whatever was at a position, which here would be all three - so the crate the player
 * meant is named, by the same resolver the menus and breaking use, and only that crate travels.
 *
 * <p>Transport, not destruction: nothing drops, and the contents are in exactly one place at every
 * moment.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackGrabbyGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "crate_grabby_logical";

    private CrateStackGrabbyGameTests() {
    }

    /* ─── one crate at a time ────────────────────────────────── */

    /** Grabbing the middle crate takes the middle crate, and the rest settle. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void grabbingOneCrateLeavesTheRestOfTheColumn(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = fixture.stack();
        int bottom = stack.crates().get(0).id();
        int middle = stack.crates().get(1).id();
        int top = stack.crates().get(2).id();
        stack.containerFor(bottom).setItem(0, new ItemStack(Items.APPLE, 4));
        stack.containerFor(middle).setItem(0, new ItemStack(Items.DIAMOND, 3));
        stack.containerFor(top).setItem(0, new ItemStack(Items.EMERALD, 2));

        GrabbyPickupResult result = grab(helper, fixture, middle);

        check(result.outcome() == GrabbyPickupOutcome.SUCCESS,
                "Grabby refused the middle crate: " + result.outcome());
        check(stack.crateCount() == 2,
                "the whole column went, not one crate: " + stack.crateCount() + " left");
        check(stack.crateById(middle) == null, "the aimed crate is still there");
        check(stack.crateById(bottom) != null && stack.crateById(top) != null,
                "the wrong crate was taken");
        check(stack.containerFor(bottom).getItem(0).is(Items.APPLE),
                "the crate below lost its contents");
        check(stack.containerFor(top).getItem(0).is(Items.EMERALD),
                "the crate above lost its contents");
        finish(helper, fixture);
    }

    /** The carried item holds that crate's contents, and only that crate's. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theCarriedCrateHoldsItsOwnContents(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.MEDIUM);
        CrateStackBlockEntity stack = fixture.stack();
        int small = stack.crates().get(0).id();
        int medium = stack.crates().get(1).id();
        stack.containerFor(small).setItem(0, new ItemStack(Items.APPLE, 4));
        stack.containerFor(medium).setItem(0, new ItemStack(Items.DIAMOND, 3));

        GrabbyPickupResult result = grab(helper, fixture, medium);
        check(result.outcome() == GrabbyPickupOutcome.SUCCESS, "refused: " + result.outcome());

        ItemStack carried = result.portable();
        check(carried.is(CrateVariant.MEDIUM.item()),
                "the carried item is " + carried.getItem() + ", not the crate that was taken");
        CustomData data = carried.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
        check(!data.isEmpty(), "the carried crate is empty of its own contents");
        check(data.copyTag().contains("Items"), "the carried crate does not carry its items");
        check(stack.containerFor(small).getItem(0).is(Items.APPLE),
                "the crate left behind lost its contents");
        finish(helper, fixture);
    }

    /**
     * A crate carried away and put back is the same crate.
     *
     * <p>The round trip is where a carried inventory is most likely to be quietly lost: the item is
     * built by one path and read by another, and only placing it back proves the two agree.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aCarriedCrateGoesBackWithItsContents(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = fixture.stack();
        int top = stack.crates().get(1).id();
        stack.containerFor(top).setItem(0, new ItemStack(Items.DIAMOND, 3));

        GrabbyPickupResult taken = grab(helper, fixture, top);
        check(taken.outcome() == GrabbyPickupOutcome.SUCCESS, "refused: " + taken.outcome());
        check(stack.crateCount() == 1, "the column should be one crate shorter");

        // Put it back through the ordinary placement path, which is what a player does with it.
        com.seggellion.britannia_mod.crate.CrateStackPlacement.Result replaced =
                com.seggellion.britannia_mod.crate.CrateStackPlacement.place(
                        helper.getLevel(), fixture.root(), fixture.player(),
                        taken.portable(), Direction.NORTH);

        check(replaced.succeeded(), "the carried crate would not go back: " + replaced.refusal());
        check(stack.crateCount() == 2, "the column did not grow again");
        int restored = stack.crates().get(1).id();
        ItemStack back = stack.containerFor(restored).getItem(0);
        check(back.is(Items.DIAMOND) && back.getCount() == 3,
                "the crate came back holding " + back + " rather than its three diamonds");
        finish(helper, fixture);
    }

    /* ─── refusals are per crate ─────────────────────────────── */

    /** An open crate refuses, and the ones beside it do not. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void anOpenCrateDoesNotFreezeTheWholeColumn(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = fixture.stack();
        int bottom = stack.crates().get(0).id();
        int top = stack.crates().get(1).id();
        stack.crateById(bottom).incrementOpeners();

        check(grab(helper, fixture, bottom).outcome() == GrabbyPickupOutcome.IN_USE,
                "a crate somebody is reading was carried away anyway");
        check(grab(helper, fixture, top).outcome() == GrabbyPickupOutcome.SUCCESS,
                "one open crate stopped the rest of the column being moved");
        finish(helper, fixture);
    }

    /** A crate holding a full container still refuses, exactly as an ordinary crate does. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aCrateHoldingAContainerRefuses(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = fixture.stack();
        int top = stack.crates().get(1).id();

        ItemStack nested = new ItemStack(CrateVariant.SMALL.item());
        nested.set(DataComponents.BLOCK_ENTITY_DATA,
                CustomData.of(new net.minecraft.nbt.CompoundTag() {{
                    putString("id", "britannia_mod:crate");
                }}));
        stack.containerFor(top).setItem(0, nested);

        check(grab(helper, fixture, top).outcome() == GrabbyPickupOutcome.NESTED_CONTAINER,
                "a crate carrying a full container was allowed inside another one");
        finish(helper, fixture);
    }

    /** The second player to reach the same crate finds it gone rather than getting a copy. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void twoPlayersCannotCarryTheSameCrate(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = fixture.stack();
        int top = stack.crates().get(1).id();
        stack.containerFor(top).setItem(0, new ItemStack(Items.DIAMOND, 3));

        check(grab(helper, fixture, top).outcome() == GrabbyPickupOutcome.SUCCESS,
                "the first attempt should have taken it");
        GrabbyPickupResult second = grab(helper, fixture, top);

        check(second.outcome() != GrabbyPickupOutcome.SUCCESS,
                "the same crate was handed out twice");
        check(stack.crateCount() == 1, "the column lost a second crate to a duplicate pickup");
        finish(helper, fixture);
    }


    /**
     * A crate that has just been carried away cannot also be broken.
     *
     * <p>Both gestures name a crate and then act on it a moment later, so a player who aims, grabs and
     * swings has a target captured for a crate that is no longer in the column. Spending it has to do
     * nothing at all rather than fall through onto whichever crate now occupies that place.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aCarriedCrateCannotThenBeBroken(GameTestHelper helper) {
        Fixture fixture = column(helper, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL);
        CrateStackBlockEntity stack = fixture.stack();
        int bottom = stack.crates().get(0).id();
        int middle = stack.crates().get(1).id();
        int top = stack.crates().get(2).id();

        // Aimed at the top crate, then carried off before the swing lands.
        CrateStackBreakTargets.capture(fixture.player(), fixture.root(), top, fixture.root());
        check(grab(helper, fixture, top).outcome() == GrabbyPickupOutcome.SUCCESS,
                "the aimed crate could not be carried");
        check(stack.crateCount() == 2, "carrying it away should leave two crates");

        fixture.player().gameMode.destroyBlock(fixture.root());

        check(stack.crateCount() == 2,
                "a target spent after its crate was carried away destroyed something else");
        check(stack.crateById(bottom) != null && stack.crateById(middle) != null,
                "the stale target fell through onto a surviving crate");
        finish(helper, fixture);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Fixture(
            GameTestHelper helper, ServerPlayer player, BlockPos root, CrateStackBlockEntity stack) {
    }

    private static Fixture column(GameTestHelper helper, CrateVariant... variants) {
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos root = helper.absolutePos(floor.above());
        helper.getLevel().setBlock(root,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), root);
        check(promoted.succeeded(), "could not build a column: " + promoted.refusal());
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        for (int index = 1; index < variants.length; index++) {
            check(CrateStackColumnSync.appendCrate(
                            helper.getLevel(), root, variants[index], Direction.NORTH).succeeded(),
                    "could not grow the column");
        }

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.onUpdateAbilities();
        player.absMoveTo(root.getX() + 1.5D, root.getY(), root.getZ() + 0.5D);
        player.setOldPosAndRot();

        // Promotion is a domain call, so it carries no player: stamp the column as something a
        // player put down, which is what building one by hand does.
        stack.setGrabbyState(
                GrabbyInstanceState.playerPlaced(player.getUUID(), helper.getLevel().getGameTime()));
        return new Fixture(helper, player, root, stack);
    }

    /** Carries away one named crate, through the transaction gameplay runs. */
    private static GrabbyPickupResult grab(GameTestHelper helper, Fixture fixture, int crateId) {
        return GrabbyPickupTransaction.execute(
                GrabbyWorld.of(helper.getLevel()), GrabbyActor.of(fixture.player()),
                fixture.root(), OptionalInt.of(crateId));
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

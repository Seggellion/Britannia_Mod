package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackBreakTargets;
import com.seggellion.britannia_mod.crate.CrateStackBreakTransaction;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breaking one crate out of a column.
 *
 * <h2>What these can and cannot prove</h2>
 *
 * <p>Everything server-side: which crate is removed, what survives, what drops, how many world cells
 * remain, and how two players racing each other resolve. What they cannot prove is what the player
 * <em>sees</em> — a client predicts the block it broke is gone, and whether that prediction is
 * repaired is decided on the client. That needs a real client and is called out as such rather than
 * claimed here.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackBreakGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CrateStackBreakGameTests() {
    }

    /* ─── which crate goes ───────────────────────────────────── */

    /** Breaking the top crate leaves the two below it exactly as they were. */
    @GameTest(template = TEMPLATE)
    public static void breakingTheTopCrateLeavesTheOthersUntouched(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int top = fixture.ids().get(2);

        breakCrate(helper, fixture, top);

        check(fixture.stack().crateCount() == 2, "breaking the top crate removed more than one");
        check(fixture.stack().crateById(top) == null, "the targeted crate survived");
        check(fixture.stack().crateById(fixture.ids().get(0)) != null, "the bottom crate was lost");
        check(fixture.stack().crateById(fixture.ids().get(1)) != null, "the middle crate was lost");
        checkContents(fixture, 0, Items.APPLE, 1);
        checkContents(fixture, 1, Items.DIAMOND, 2);
        checkDrops(helper, fixture.root(), Items.STICK, 3, ItemRegistry.SMALL_CRATE_ITEM.get(), 0);
        finish(helper, fixture);
    }

    /** Breaking the middle crate settles the top one onto the bottom, keeping every identity. */
    @GameTest(template = TEMPLATE)
    public static void breakingTheMiddleCrateSettlesTheOneAboveIt(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int bottom = fixture.ids().get(0);
        int middle = fixture.ids().get(1);
        int top = fixture.ids().get(2);
        check(fixture.stack().placementOf(top).baseHundredths() == 1430, "fixture is not as expected");

        breakCrate(helper, fixture, middle);

        check(fixture.stack().crateCount() == 2, "breaking the middle crate removed more than one");
        check(fixture.stack().crateById(middle) == null, "the targeted crate survived");
        check(fixture.stack().placementOf(bottom).baseHundredths() == 0, "the bottom crate moved");
        check(fixture.stack().placementOf(top).baseHundredths() == 715,
                "the top crate did not settle onto the bottom one");
        check(fixture.stack().topCrate().id() == top, "the surviving top crate lost its identity");
        checkContents(fixture, 0, Items.APPLE, 1);
        checkContents(fixture, 2, Items.STICK, 3);
        checkDrops(helper, fixture.root(), Items.DIAMOND, 2, ItemRegistry.SMALL_CRATE_ITEM.get(), 0);
        finish(helper, fixture);
    }

    /** Breaking the bottom crate drops the other two to the floor without disturbing them. */
    @GameTest(template = TEMPLATE)
    public static void breakingTheBottomCrateRepacksTheRestToTheFloor(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int bottom = fixture.ids().get(0);
        int middle = fixture.ids().get(1);
        int top = fixture.ids().get(2);

        breakCrate(helper, fixture, bottom);

        check(fixture.stack().crateCount() == 2, "breaking the bottom crate removed more than one");
        check(helper.getLevel().getBlockState(fixture.root()).is(BlockRegistry.CRATE_STACK.get()),
                "breaking the bottom crate destroyed the column");
        check(fixture.stack().placementOf(middle).baseHundredths() == 0,
                "the middle crate did not settle to the floor");
        check(fixture.stack().placementOf(top).baseHundredths() == 715, "the top crate did not settle");
        checkContents(fixture, 1, Items.DIAMOND, 2);
        checkContents(fixture, 2, Items.STICK, 3);
        checkDrops(helper, fixture.root(), Items.APPLE, 1, ItemRegistry.SMALL_CRATE_ITEM.get(), 0);
        finish(helper, fixture);
    }

    /* ─── cells ──────────────────────────────────────────────── */

    /** Three small crates need two cells; two need one, so the upper cell has to go. */
    @GameTest(template = TEMPLATE)
    public static void breakingACrateThatShrinksTheColumnReleasesItsUpperCell(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        check(fixture.stack().requiredCellCount() == 2, "fixture should occupy two cells");
        check(helper.getLevel().getBlockState(fixture.root().above())
                        .is(BlockRegistry.CRATE_STACK.get()),
                "fixture should have a continuation cell");

        breakCrate(helper, fixture, fixture.ids().get(2));

        check(fixture.stack().requiredCellCount() == 1, "two small crates need one cell");
        check(!helper.getLevel().getBlockState(fixture.root().above())
                        .is(BlockRegistry.CRATE_STACK.get()),
                "the continuation cell was not released");
        check(helper.getLevel().getBlockState(fixture.root()).is(BlockRegistry.CRATE_STACK.get()),
                "the root was released instead");
        finish(helper, fixture);
    }

    /** A crate can be broken by aiming through the continuation cell its geometry lives in. */
    @GameTest(template = TEMPLATE)
    public static void aCrateIsBrokenIdenticallyThroughAContinuationCell(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int top = fixture.ids().get(2);

        // The clicked cell is the continuation, not the root; identity still comes from the id.
        breakCrateThrough(helper, fixture, top, fixture.root().above());

        check(fixture.stack().crateCount() == 2, "breaking through a continuation cell removed nothing");
        check(fixture.stack().crateById(top) == null, "the wrong crate was removed");
        finish(helper, fixture);
    }

    /* ─── the last crate ─────────────────────────────────────── */

    /** The final crate takes the column with it, and never leaves a raw stack item behind. */
    @GameTest(template = TEMPLATE)
    public static void breakingTheFinalCrateRemovesTheColumnEntirely(GameTestHelper helper) {
        Fixture fixture = twoSmall(helper);
        breakCrate(helper, fixture, fixture.ids().get(1));
        check(helper.getLevel().getBlockState(fixture.root()).is(BlockRegistry.CRATE_STACK.get()),
                "a one-crate column must remain a column");
        check(fixture.stack().crateCount() == 1, "the column should be down to one crate");

        breakCrate(helper, fixture, fixture.ids().get(0));

        check(helper.getLevel().getBlockState(fixture.root()).isAir(),
                "the last crate did not take the column with it");
        int stacks = 0;
        for (ItemEntity entity : dropsAround(helper, fixture.root())) {
            if (entity.getItem().getItem() == BlockRegistry.CRATE_STACK.get().asItem()
                    && !entity.getItem().isEmpty()) {
                stacks++;
            }
        }
        check(stacks == 0, "the internal column block dropped as an item");
        check(countDropped(helper, fixture.root(), ItemRegistry.SMALL_CRATE_ITEM.get()) == 0,
                "a creative break must not hand the crate back");
        finish(helper, fixture);
    }

    /* ─── drops ──────────────────────────────────────────────── */

    /** Contents and components survive the trip to the floor exactly as a standalone crate's would. */
    @GameTest(template = TEMPLATE)
    public static void aBrokenCrateDropsItsContentsWithComponentsIntact(GameTestHelper helper) {
        Fixture fixture = twoSmall(helper);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bane of Crates"));
        fixture.stack().containerFor(fixture.ids().get(1)).setItem(1, named);

        breakCrate(helper, fixture, fixture.ids().get(1));

        boolean found = false;
        for (ItemEntity entity : dropsAround(helper, fixture.root())) {
            if (entity.getItem().is(Items.DIAMOND_SWORD)) {
                found = Component.literal("Bane of Crates")
                        .equals(entity.getItem().get(DataComponents.CUSTOM_NAME));
            }
        }
        check(found, "the broken crate's contents lost their components on the way out");
        finish(helper, fixture);
    }

    /** Creative keeps the contents behaviour of a standalone crate and withholds the crate item. */
    @GameTest(template = TEMPLATE)
    public static void creativeDropsContentsButNotTheCrateItself(GameTestHelper helper) {
        Fixture fixture = twoSmall(helper);
        fixture.player().setGameMode(GameType.CREATIVE);
        fixture.player().getAbilities().instabuild = true;
        fixture.player().onUpdateAbilities();

        breakCrate(helper, fixture, fixture.ids().get(1));

        check(countDropped(helper, fixture.root(), Items.DIAMOND) == 2,
                "creative should still spill a broken crate's contents, as a standalone crate does");
        check(countDropped(helper, fixture.root(), ItemRegistry.SMALL_CRATE_ITEM.get()) == 0,
                "creative should not hand back the crate itself");
        finish(helper, fixture);
    }

    /**
     * A survival break hands the crate back; a creative one does not.
     *
     * <p>Driven through the transaction rather than a swing, because the mod's own world rules stop
     * a survival player breaking scenery outside a house long before the block is reached. What is
     * under test is the drop rule itself, which is the one thing the two modes genuinely differ on -
     * and it mirrors {@code DecorativeMultiblockBlock.dismantle}, which spills a crate's contents
     * either way and pops the crate only for a player without infinite materials.
     */
    @GameTest(template = TEMPLATE)
    public static void aSurvivalBreakHandsBackTheCrateAndACreativeOneDoesNot(GameTestHelper helper) {
        Fixture fixture = twoSmall(helper);
        ServerPlayer survivor = helper.makeMockServerPlayerInLevel();
        survivor.setGameMode(GameType.SURVIVAL);
        survivor.getAbilities().instabuild = false;
        survivor.onUpdateAbilities();

        CrateStackBreakTransaction.breakCrate(
                helper.getLevel(), fixture.root(), fixture.ids().get(1), survivor);

        check(countDropped(helper, fixture.root(), Items.DIAMOND) == 2,
                "a survival break should spill the crate's contents");
        check(countDropped(helper, fixture.root(), ItemRegistry.SMALL_CRATE_ITEM.get()) == 1,
                "a survival break should hand back exactly one crate");

        CrateStackBreakTransaction.breakCrate(
                helper.getLevel(), fixture.root(), fixture.ids().get(0), fixture.player());

        check(countDropped(helper, fixture.root(), Items.APPLE) == 1,
                "a creative break should still spill the crate's contents");
        check(countDropped(helper, fixture.root(), ItemRegistry.SMALL_CRATE_ITEM.get()) == 1,
                "a creative break must not add a second crate item");
        helper.getLevel().getServer().getPlayerList().remove(survivor);
        finish(helper, fixture);
    }

    /* ─── races ──────────────────────────────────────────────── */

    /**
     * Two players aiming at the same crate: one removes it, the other finds it gone and does nothing.
     *
     * <p>The second must not fall back to whichever crate moved into that height — that would destroy
     * a crate nobody aimed at, and drop its contents twice over.
     */
    @GameTest(template = TEMPLATE)
    public static void twoPlayersBreakingTheSameCrateRemoveItOnce(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int middle = fixture.ids().get(1);
        ServerPlayer second = builder(helper);

        CrateStackBreakTargets.capture(fixture.player(), fixture.root(), middle, fixture.root());
        CrateStackBreakTargets.capture(second, fixture.root(), middle, fixture.root());

        destroy(helper, fixture.player(), fixture.root());
        destroy(helper, second, fixture.root());

        check(fixture.stack().crateCount() == 2, "the same crate was removed twice");
        check(fixture.stack().crateById(fixture.ids().get(0)) != null, "a bystander crate was removed");
        check(fixture.stack().crateById(fixture.ids().get(2)) != null, "a bystander crate was removed");
        check(countDropped(helper, fixture.root(), ItemRegistry.SMALL_CRATE_ITEM.get()) == 0,
                "a creative break must not hand the crate back");
        check(countDropped(helper, fixture.root(), Items.DIAMOND) == 2,
                "the crate's contents dropped more than once");
        helper.getLevel().getServer().getPlayerList().remove(second);
        finish(helper, fixture);
    }

    /**
     * The reason identities exist: a target captured before the column moved still means that crate.
     *
     * <p>One player starts on the top crate, another removes the middle one, the top crate slides down
     * a whole crate's height, and the first player's swing completes. It must still destroy the crate
     * they aimed at rather than the one now standing where it used to be.
     */
    @GameTest(template = TEMPLATE)
    public static void aCapturedTargetSurvivesTheColumnRepackingBeneathIt(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int middle = fixture.ids().get(1);
        int top = fixture.ids().get(2);
        ServerPlayer second = builder(helper);

        CrateStackBreakTargets.capture(fixture.player(), fixture.root(), top, fixture.root());

        // Somebody else pulls the crate out from under them.
        CrateStackBreakTargets.capture(second, fixture.root(), middle, fixture.root());
        destroy(helper, second, fixture.root());
        check(fixture.stack().placementOf(top).baseHundredths() == 715, "the top crate should have moved");

        destroy(helper, fixture.player(), fixture.root());

        check(fixture.stack().crateById(top) == null, "the captured crate was not the one destroyed");
        check(fixture.stack().crateById(fixture.ids().get(0)) != null,
                "the bottom crate was destroyed instead of the captured one");
        check(fixture.stack().crateCount() == 1, "more than the two targeted crates were removed");
        helper.getLevel().getServer().getPlayerList().remove(second);
        finish(helper, fixture);
    }

    /* ─── refusals ───────────────────────────────────────────── */

    /** A swing with no captured target destroys nothing and leaves the column standing. */
    @GameTest(template = TEMPLATE)
    public static void aBreakWithNoCapturedTargetChangesNothing(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        CrateStackBreakTargets.clear(fixture.player());

        destroy(helper, fixture.player(), fixture.root());

        check(fixture.stack().crateCount() == 3, "an uncaptured break removed a crate");
        check(helper.getLevel().getBlockState(fixture.root()).is(BlockRegistry.CRATE_STACK.get()),
                "an uncaptured break destroyed the column");
        check(dropsAround(helper, fixture.root()).isEmpty(), "an uncaptured break dropped something");
        finish(helper, fixture);
    }

    /** A target captured on one column may never be spent on another. */
    @GameTest(template = TEMPLATE)
    public static void aTargetFromAnotherColumnIsNeverApplied(GameTestHelper helper) {
        Fixture first = threeSmall(helper);
        Fixture second = twoSmallAt(helper, new BlockPos(5, 1, 2));

        CrateStackBreakTargets.capture(
                first.player(), first.root(), first.ids().get(1), first.root());
        destroy(helper, first.player(), second.root());

        check(second.stack().crateCount() == 2, "a stale target was spent on a different column");
        check(first.stack().crateCount() == 3, "the other column lost a crate too");
        helper.getLevel().getServer().getPlayerList().remove(second.player());
        finish(helper, first);
    }

    /**
     * A held mouse button must not eat the column.
     *
     * <p>The client keeps swinging after a crate breaks and immediately starts on whatever it now
     * aims at, which is the next crate down. A brief per-player, per-column guard refuses to capture
     * a new target straight after a completed break, so the second swing has nothing to destroy.
     */
    @GameTest(template = TEMPLATE)
    public static void aHeldButtonCannotCascadeThroughTheColumn(GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        breakCrate(helper, fixture, fixture.ids().get(2));
        check(fixture.stack().crateCount() == 2, "the first crate should have broken");

        // What a held button produces: another start on the same column, immediately.
        check(CrateStackBreakTargets.withinCascadeGuard(fixture.player(), fixture.root()),
                "a break completed a moment ago should be inside the guard");
        destroy(helper, fixture.player(), fixture.root());

        check(fixture.stack().crateCount() == 2,
                "a held button destroyed a second crate without a fresh target");
        finish(helper, fixture);
    }

    /* ─── menus ──────────────────────────────────────────────── */

    /** A menu open on one crate survives a neighbour being broken and dies with its own crate. */
    @GameTest(template = TEMPLATE)
    public static void anOpenMenuSurvivesANeighbourBreakAndClosesWithItsOwnCrate(
            GameTestHelper helper) {
        Fixture fixture = threeSmall(helper);
        int top = fixture.ids().get(2);
        var view = fixture.stack().containerFor(top);
        check(view.stillValid(fixture.player()), "a view of a live crate should be usable");

        breakCrate(helper, fixture, fixture.ids().get(1));
        check(view.stillValid(fixture.player()),
                "breaking a crate below must not invalidate the one above");
        check(view.getItem(0).is(Items.STICK), "the view stopped addressing its own crate");

        breakCrate(helper, fixture, top);
        check(!view.stillValid(fixture.player()), "a view must not outlive its crate");
        finish(helper, fixture);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Fixture(
            ServerPlayer player, BlockPos root, CrateStackBlockEntity stack, List<Integer> ids) {
    }

    /** Three small crates holding an apple, a diamond and a stick, bottom upwards. */
    private static Fixture threeSmall(GameTestHelper helper) {
        Fixture fixture = column(helper, new BlockPos(2, 1, 2), 3);
        fixture.stack().containerFor(fixture.ids().get(0)).setItem(0, new ItemStack(Items.APPLE, 1));
        fixture.stack().containerFor(fixture.ids().get(1)).setItem(0, new ItemStack(Items.DIAMOND, 2));
        fixture.stack().containerFor(fixture.ids().get(2)).setItem(0, new ItemStack(Items.STICK, 3));
        return fixture;
    }

    private static Fixture twoSmall(GameTestHelper helper) {
        return twoSmallAt(helper, new BlockPos(2, 1, 2));
    }

    private static Fixture twoSmallAt(GameTestHelper helper, BlockPos floor) {
        Fixture fixture = column(helper, floor, 2);
        fixture.stack().containerFor(fixture.ids().get(0)).setItem(0, new ItemStack(Items.APPLE, 1));
        fixture.stack().containerFor(fixture.ids().get(1)).setItem(0, new ItemStack(Items.DIAMOND, 2));
        return fixture;
    }

    private static Fixture column(GameTestHelper helper, BlockPos floor, int crates) {
        helper.setBlock(floor, Blocks.STONE);
        BlockPos root = helper.absolutePos(floor.above());
        helper.getLevel().setBlock(
                root,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);

        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(helper.getLevel(), root);
        check(promoted.succeeded(), "could not build a column: " + promoted.refusal());
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        for (int index = 1; index < crates; index++) {
            check(CrateStackColumnSync.appendCrate(
                            helper.getLevel(), root, CrateVariant.SMALL, Direction.NORTH).succeeded(),
                    "could not grow the column");
        }
        return new Fixture(builder(helper), root, stack,
                stack.crates().stream().map(crate -> crate.id()).toList());
    }

    /* ─── driving a break ────────────────────────────────────── */

    /** Captures a target and completes the destruction, as a swing on that crate would. */
    private static void breakCrate(GameTestHelper helper, Fixture fixture, int crateId) {
        breakCrateThrough(helper, fixture, crateId, fixture.root());
    }

    private static void breakCrateThrough(
            GameTestHelper helper, Fixture fixture, int crateId, BlockPos clickedCell) {
        // Cleared for this player only. These tests run beside one another in one server,
        // and wiping every target would reach into another structure that is mid-swing.
        CrateStackBreakTargets.clear(fixture.player());
        CrateStackBreakTargets.capture(fixture.player(), fixture.root(), crateId, clickedCell);
        destroy(helper, fixture.player(), clickedCell);
    }

    /** The server's own destruction entry point, so the block's real break path runs. */
    private static void destroy(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        player.gameMode.destroyBlock(pos);
    }

    /* ─── assertions ─────────────────────────────────────────── */

    private static void checkContents(Fixture fixture, int index, Item item, int count) {
        int crateId = fixture.ids().get(index);
        check(fixture.stack().crateById(crateId) != null, "crate " + crateId + " was lost");
        ItemStack held = fixture.stack().containerFor(crateId).getItem(0);
        check(held.is(item) && held.getCount() == count,
                "crate " + crateId + " no longer holds what it held");
    }

    private static void checkDrops(
            GameTestHelper helper, BlockPos root, Item content, int contentCount,
            Item crateItem, int crateCount) {
        check(countDropped(helper, root, content) == contentCount,
                "the broken crate's contents did not drop exactly once");
        check(countDropped(helper, root, crateItem) == crateCount,
                "the broken crate did not drop exactly one crate item");
    }

    private static int countDropped(GameTestHelper helper, BlockPos root, Item item) {
        int found = 0;
        for (ItemEntity entity : dropsAround(helper, root)) {
            if (entity.getItem().is(item)) {
                found += entity.getItem().getCount();
            }
        }
        return found;
    }

    private static List<ItemEntity> dropsAround(GameTestHelper helper, BlockPos root) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(6.0D));
    }

    /**
     * A player who is actually allowed to break a crate here.
     *
     * <p>Creative, and that is a finding rather than a convenience. This mod already refuses a
     * bare-handed survival break of anything standing outside a house, and its two-handed axe -
     * the tool that would satisfy that rule - is claimed by the wood handler for every block it
     * touches. So the only way a survival player reaches a column's own destruction is through
     * paths that belong to other systems. Creative is exempt from both, which makes it the mode
     * that exercises this block's break path rather than somebody else's.
     *
     * <p>That the survival gates stop the break before it starts is itself the proof that this
     * milestone does not bypass them; the drop rules that differ between the modes are tested
     * against the transaction directly.
     */
    private static ServerPlayer builder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        BlockPos stand = helper.absolutePos(new BlockPos(4, 2, 2));
        player.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        return player;
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        // The UUID overload, so a departing mock player leaves no completion behind either.
        CrateStackBreakTargets.clear(fixture.player().getUUID());
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

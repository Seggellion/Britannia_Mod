package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
 * Compact crates resting on a large crate's lid.
 *
 * <h2>What a foundation is</h2>
 *
 * <p>A large crate is nineteen voxels tall across two world cells, so its lid sits three voxels into
 * the upper one. A column may take neither cell, so it roots two above the anchor and starts at a
 * negative origin — its lowest crates are physically inside the cell the large crate owns, and the
 * large crate draws them, collides with them and answers for the clicks that land on them.
 *
 * <p>Nothing is promoted and nothing is merged. The large crate keeps its own block entity and its own
 * fifty-four slots throughout, which is what most of these check.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackFoundationGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The large crate's lid, in the layout's own units, above its anchor cell's floor. */
    private static final int LID = 1900;

    /** Which is this far below the first cell the column may own. */
    private static final int ORIGIN =
            LID - CrateFoundation.ROOT_CELL_ABOVE_ANCHOR * CrateStackLayout.CELL_HUNDREDTHS;

    private CrateStackFoundationGameTests() {
    }

    /* ─── the geometry this all rests on ─────────────────────── */

    /** The measured lid height, held to the value every other test here assumes. */
    @GameTest(template = TEMPLATE)
    public static void theLargeCrateLidIsWhereWeThinkItIs(GameTestHelper helper) {
        CrateBlock large = BlockRegistry.LARGE_CRATE.get();

        check(CrateFoundation.topHundredths(large) == LID,
                "the large crate's lid moved to " + CrateFoundation.topHundredths(large)
                        + "; every foundation height here is derived from it");
        check(CrateFoundation.originFor(large) == ORIGIN,
                "a column founded on it would start at " + CrateFoundation.originFor(large));
        helper.succeed();
    }

    /* ─── starting a column ──────────────────────────────────── */

    /** A small crate clicked onto the lid starts a column resting on it. */
    @GameTest(template = TEMPLATE)
    public static void aSmallCrateStartsAColumnOnTheLid(GameTestHelper helper) {
        foundsColumn(helper, small(), CrateVariant.SMALL);
    }

    /** The same for a medium crate. */
    @GameTest(template = TEMPLATE)
    public static void aMediumCrateStartsAColumnOnTheLid(GameTestHelper helper) {
        foundsColumn(helper, medium(), CrateVariant.MEDIUM);
    }

    private static void foundsColumn(GameTestHelper helper, Item held, CrateVariant expected) {
        Fixture fixture = largeCrate(helper, Direction.NORTH);
        fixture.large().setItem(0, new ItemStack(Items.EMERALD, 5));

        // Creative restores the stack count after every use, so consumption can only be seen from
        // survival - and placing a crate is allowed there.
        fixture.player().setGameMode(GameType.SURVIVAL);
        ItemStack hand = new ItemStack(held, 8);
        click(fixture, hand, lidHit(fixture));

        CrateStackBlockEntity column = column(helper, fixture);
        check(column.crateCount() == 1, "the lid click did not start a column");
        check(column.crates().get(0).variant() == expected, "the wrong crate was placed");
        check(column.originHundredths() == ORIGIN,
                "the column starts at " + column.originHundredths() + " rather than on the lid");
        check(column.layout().placements().get(0).baseHundredths() == ORIGIN,
                "the bottom crate is not resting on the lid");
        check(hand.getCount() == 7, "the placement consumed " + (8 - hand.getCount()) + " crates");

        // The large crate is a foundation, not a member: it keeps its own block entity and contents.
        check(helper.getLevel().getBlockEntity(fixture.anchor()) instanceof CrateBlockEntity,
                "the large crate lost its block entity");
        check(fixture.large().getItem(0).is(Items.EMERALD),
                "the large crate's own contents were disturbed");
        check(fixture.large().getContainerSize() == 54,
                "the large crate stopped being a fifty-four slot crate");

        // Nothing was placed on the grid above it, which is the defect this replaces.
        check(!(helper.getLevel().getBlockState(fixture.anchor().above(2))
                        .getBlock() instanceof CrateBlock),
                "an ordinary crate block was placed above the large crate");
        finish(helper, fixture);
    }

    /** A founded column keeps growing by ordinary appends, still resting on the lid. */
    @GameTest(template = TEMPLATE)
    public static void aFoundedColumnKeepsGrowing(GameTestHelper helper) {
        Fixture fixture = largeCrate(helper, Direction.NORTH);

        click(fixture, new ItemStack(small(), 8), lidHit(fixture));
        CrateStackBlockEntity column = column(helper, fixture);
        click(fixture, new ItemStack(medium(), 8), columnTopHit(fixture, column));
        click(fixture, new ItemStack(small(), 8), columnTopHit(fixture, column));

        check(column.crateCount() == 3, "the column stopped accepting crates at "
                + column.crateCount());
        check(column.originHundredths() == ORIGIN,
                "the column forgot its foundation when it grew");
        check(column.layout().placements().get(0).baseHundredths() == ORIGIN,
                "the bottom crate left the lid");
        finish(helper, fixture);
    }

    /* ─── one canonical anchor ───────────────────────────────── */

    /**
     * Every cell of the large crate answers for the same column.
     *
     * <p>The alternative — resolving from whichever cell the player happened to click — would put the
     * column in a different place depending on which quadrant of the lid was hit.
     */
    @GameTest(template = TEMPLATE)
    public static void everyCellOfTheFoundationResolvesTheSameColumn(GameTestHelper helper) {
        Fixture fixture = largeCrate(helper, Direction.NORTH);
        click(fixture, new ItemStack(small(), 8), lidHit(fixture));
        BlockPos expected = CrateFoundation.columnRootFor(fixture.anchor());

        Set<BlockPos> roots = new HashSet<>();
        int cells = 0;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos cell = fixture.anchor().offset(x, y, z);
                    BlockState state = helper.getLevel().getBlockState(cell);
                    if (!(state.getBlock() instanceof CrateBlock)) {
                        continue;
                    }
                    cells++;
                    CrateFoundation.columnOn(helper.getLevel(), cell, state)
                            .ifPresent(founded -> roots.add(founded.root()));
                }
            }
        }
        check(cells == 8, "the large crate is not standing on all eight of its cells");
        check(roots.size() == 1 && roots.contains(expected),
                "cells of one large crate disagreed about where its column is: " + roots);
        finish(helper, fixture);
    }

    /** A column takes its orientation from the crate it stands on, whichever way that faces. */
    @GameTest(template = TEMPLATE)
    public static void aFoundedColumnInheritsEveryFacing(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Fixture fixture = largeCrate(helper, facing);
            click(fixture, new ItemStack(small(), 8), lidHit(fixture));

            CrateStackBlockEntity column = column(helper, fixture);
            check(column.crates().get(0).facing() == fixture.facing(),
                    "a column on a " + fixture.facing() + " crate faced "
                            + column.crates().get(0).facing());
            clear(helper, fixture);
        }
        helper.succeed();
    }

    /* ─── refusals ───────────────────────────────────────────── */

    /** With the column's cell taken, nothing happens at all. */
    @GameTest(template = TEMPLATE)
    public static void anObstructedFoundationRefusesWithoutTouchingAnything(GameTestHelper helper) {
        Fixture fixture = largeCrate(helper, Direction.NORTH);
        fixture.large().setItem(0, new ItemStack(Items.EMERALD, 5));
        BlockPos blocked = CrateFoundation.columnRootFor(fixture.anchor());
        helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);

        ItemStack hand = new ItemStack(small(), 8);
        click(fixture, hand, lidHit(fixture));

        check(helper.getLevel().getBlockState(blocked).is(Blocks.STONE),
                "the obstruction was overwritten");
        check(hand.getCount() == 8, "a refused placement still consumed a crate");
        check(fixture.large().getItem(0).is(Items.EMERALD),
                "a refused placement disturbed the large crate");
        check(helper.getLevel().getBlockEntity(fixture.anchor()) instanceof CrateBlockEntity,
                "a refused placement damaged the large crate");
        finish(helper, fixture);
    }

    /* ─── losing the foundation ──────────────────────────────── */

    /**
     * Breaking the large crate leaves the column standing, contents and all.
     *
     * <p>Those crates hold a player's items, so the column outlives whatever it was resting on. It
     * settles onto its own cell floor, which is the only origin at which every crate still has a block
     * to draw it.
     */
    @GameTest(template = TEMPLATE)
    public static void breakingTheFoundationLeavesTheColumnStanding(GameTestHelper helper) {
        Fixture fixture = largeCrate(helper, Direction.NORTH);
        click(fixture, new ItemStack(small(), 8), lidHit(fixture));
        CrateStackBlockEntity column = column(helper, fixture);
        click(fixture, new ItemStack(small(), 8), columnTopHit(fixture, column));

        int bottom = column.crates().get(0).id();
        int top = column.crates().get(1).id();
        column.containerFor(bottom).setItem(0, new ItemStack(Items.APPLE, 3));
        column.containerFor(top).setItem(0, new ItemStack(Items.DIAMOND, 2));

        fixture.player().gameMode.destroyBlock(fixture.anchor());

        check(!(helper.getLevel().getBlockState(fixture.anchor()).getBlock() instanceof CrateBlock),
                "the large crate survived being broken");
        CrateStackBlockEntity after = column(helper, fixture);
        check(after.crateCount() == 2, "the column lost crates with its foundation");
        check(after.crateById(bottom) != null && after.crateById(top) != null,
                "the crates were renumbered when the foundation went");
        check(after.containerFor(bottom).getItem(0).is(Items.APPLE),
                "the bottom crate's contents were lost");
        check(after.containerFor(top).getItem(0).is(Items.DIAMOND),
                "the top crate's contents were lost");
        check(after.originHundredths() == 0,
                "with nothing to rest on the column should settle onto its own floor, not stay at "
                        + after.originHundredths());
        finish(helper, fixture);
    }

    /* ─── fixtures ───────────────────────────────────────────── */

    private record Fixture(
            GameTestHelper helper, ServerPlayer player, BlockPos anchor, Direction facing,
            CrateBlockEntity large) {
    }

    /** A large crate standing on stone, with a creative player beside it. */
    private static Fixture largeCrate(GameTestHelper helper, Direction facing) {
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
        player.absMoveTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        // The item takes its orientation from where the player is looking.
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        player.setXRot(0.0F);
        player.setOldPosAndRot();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(large(), 4));
        Vec3 at = new Vec3(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(at, Direction.UP, floor, false));

        BlockPos anchor = floor.above();
        BlockState placed = helper.getLevel().getBlockState(anchor);
        check(placed.getBlock() instanceof CrateBlock && CrateFoundation.isFoundation(placed),
                "the large crate was not placed at " + anchor);
        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "the large crate has no inventory");
        return new Fixture(helper, player, anchor, placed.getValue(CrateBlock.FACING),
                (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor));
    }

    /** A click on the very top of the lid, straight above the anchor. */
    private static BlockHitResult lidHit(Fixture fixture) {
        BlockPos anchor = fixture.anchor();
        double y = anchor.getY() + LID / (double) CrateStackLayout.CELL_HUNDREDTHS;
        Vec3 at = new Vec3(anchor.getX() + 0.5D, y, anchor.getZ() + 0.5D);
        return new BlockHitResult(at, Direction.UP, anchor.above(), false);
    }

    /** A click on the top of whatever the column has grown to. */
    private static BlockHitResult columnTopHit(Fixture fixture, CrateStackBlockEntity column) {
        BlockPos root = CrateFoundation.columnRootFor(fixture.anchor());
        double y = root.getY()
                + column.totalHeightHundredths() / (double) CrateStackLayout.CELL_HUNDREDTHS;
        int cell = Math.floorDiv(column.totalHeightHundredths() - 1,
                CrateStackLayout.CELL_HUNDREDTHS);
        Vec3 at = new Vec3(root.getX() + 0.5D, y, root.getZ() + 0.5D);
        return new BlockHitResult(at, Direction.UP, root.above(cell), false);
    }

    private static void click(Fixture fixture, ItemStack hand, BlockHitResult hit) {
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, hand);
        fixture.player().gameMode.useItemOn(fixture.player(), fixture.helper().getLevel(),
                fixture.player().getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static CrateStackBlockEntity column(GameTestHelper helper, Fixture fixture) {
        BlockPos root = CrateFoundation.columnRootFor(fixture.anchor());
        BlockState state = helper.getLevel().getBlockState(root);
        check(state.getBlock() instanceof CrateStackBlock,
                "no column stands at " + root + "; found " + state.getBlock());
        Optional<CrateStackBlockEntity> found =
                helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity stack
                        ? Optional.of(stack)
                        : Optional.empty();
        check(found.isPresent(), "the column at " + root + " has no block entity");
        return found.get();
    }

    private static void clear(GameTestHelper helper, Fixture fixture) {
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        for (int y = 0; y <= 5; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    helper.getLevel().removeBlock(fixture.anchor().offset(x, y, z), false);
                }
            }
        }
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        helper.getLevel().getServer().getPlayerList().remove(fixture.player());
        helper.succeed();
    }

    private static Item small() {
        return ItemRegistry.SMALL_CRATE_ITEM.get();
    }

    private static Item medium() {
        return ItemRegistry.MEDIUM_CRATE_ITEM.get();
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

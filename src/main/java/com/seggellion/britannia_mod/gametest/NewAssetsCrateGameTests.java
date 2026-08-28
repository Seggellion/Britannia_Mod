package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server persistence, menu, and anti-duplication checks for crate containers. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsCrateGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsCrateGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void largeCrateUsesOneMenuInventoryAndDropsItOnceFromAChildBreak(GameTestHelper helper) {
        CrateBlock block = BlockRegistry.LARGE_CRATE.get();
        Direction facing = Direction.NORTH;
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 3, 3));
        place(helper, block, anchor, facing);

        check(helper.getLevel().getBlockEntity(anchor) instanceof CrateBlockEntity,
                "large crate root did not create its inventory");
        CrateBlockEntity crate = (CrateBlockEntity) helper.getLevel().getBlockEntity(anchor);
        long blockEntities = block.cells().stream()
                .filter(cell -> helper.getLevel().getBlockEntity(block.worldPosition(anchor, facing, cell)) != null)
                .count();
        check(blockEntities == 1, "large crate created more than one authoritative inventory");
        crate.setItem(0, new ItemStack(Items.DIAMOND, 7));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.openMenu(crate);
        check(player.containerMenu instanceof ChestMenu, "large crate did not open a server chest menu");
        player.closeContainer();

        DecorativeMultiblockBlock.Cell child = block.cells().getLast();
        BlockPos childPos = block.worldPosition(anchor, facing, child);
        var childState = helper.getLevel().getBlockState(childPos);
        block.onDestroyedByPlayer(
                childState, helper.getLevel(), childPos, player, true, childState.getFluidState());

        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            check(!helper.getLevel().getBlockState(block.worldPosition(anchor, facing, cell)).is(block),
                    "child break left an orphaned large-crate cell");
        }

        AABB dropsArea = new AABB(anchor).inflate(4.0D);
        int diamonds = 0;
        int crateItems = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropsArea)) {
            ItemStack stack = entity.getItem();
            if (stack.is(Items.DIAMOND)) {
                diamonds += stack.getCount();
            } else if (stack.is(ItemRegistry.LARGE_CRATE_ITEM.get())) {
                crateItems += stack.getCount();
            }
        }
        check(diamonds == 7, "large crate contents were lost or duplicated");
        check(crateItems == 1, "large crate structure did not drop exactly one block item");
        helper.succeed();
    }

    /* ─── stacking ───────────────────────────────────────────── */

    /*
     * These tests drive ServerPlayerGameMode.useItemOn, which is the real server entry point, and
     * that matters more here than it usually would.
     *
     * A crate is enrolled in Grabby Hands, and GrabbyInteractionHandler subscribes to
     * PlayerInteractEvent.RightClickBlock at HIGHEST priority - an event NeoForge fires as the first
     * statement of useItemOn, before the block's own interaction and before the item's. For any
     * enrolled block in hand, Grabby resolves the placement itself and consumes the click, so the
     * production path for a crate held over a crate runs through GrabbyPlacementTransaction and
     * reaches DecorativeMultiblockItem.useOn from there. Calling the block or the item directly would
     * test a path no player takes.
     *
     * Two consequences shape what is asserted below. Grabby range-checks the destination server-side,
     * so the player has to be standing near the crate rather than wherever a mock player lands. And
     * an item Grabby does not recognise - a stick here - is the way to reach the crate's own menu,
     * because anything enrolled is claimed by Grabby before the menu is ever considered.
     */

    /**
     * The milestone in one test: an ordinary right-click, no sneaking, and the crate below stays shut.
     *
     * <p>Both halves matter. A fix that placed the crate but also opened the one underneath would be
     * just as wrong as the defect it replaced.
     */
    @GameTest(template = TEMPLATE)
    public static void plainRightClickStacksACrateAndLeavesTheLowerCrateShut(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        ServerPlayer player = builder(helper, floor);
        helper.setBlock(floor, Blocks.STONE);

        BlockPos lower = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        check(helper.getLevel().getBlockState(lower).is(BlockRegistry.SMALL_CRATE.get()),
                "a crate could no longer be placed on ordinary ground");

        BlockPos upper = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        check(helper.getLevel().getBlockState(upper).is(BlockRegistry.SMALL_CRATE.get()),
                "a plain right-click on a crate top did not stack a crate");
        check(!(player.containerMenu instanceof ChestMenu),
                "stacking a crate opened the crate underneath it");
        check(helper.getLevel().getBlockEntity(lower) instanceof CrateBlockEntity
                        && helper.getLevel().getBlockEntity(upper) instanceof CrateBlockEntity,
                "a stacked crate did not get its own inventory");

        disconnect(helper, player);
        helper.succeed();
    }

    /**
     * Opening a crate must survive the fix.
     *
     * <p>A stick is deliberate: it is not a block, not an axe, and not in
     * {@code grabby_placeable_items}, so Grabby ignores it entirely and the click reaches
     * {@code CrateBlock.useItemOn} - the one production route that still exercises the block's own
     * choice between placing and opening.
     */
    @GameTest(template = TEMPLATE)
    public static void aCrateStillOpensForAnItemGrabbyDoesNotClaim(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        ServerPlayer player = builder(helper, floor);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos crate = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        check(helper.getLevel().getBlockState(crate).is(BlockRegistry.SMALL_CRATE.get()),
                "the crate under test was never placed");

        clickFace(helper, player, new ItemStack(Items.STICK), floor.above(), Direction.UP);

        check(player.containerMenu instanceof ChestMenu,
                "a crate no longer opens when the held item is not a crate");
        check(helper.getLevel().getBlockState(crate.above()).isAir(),
                "a stick placed something above the crate");
        player.closeContainer();

        disconnect(helper, player);
        helper.succeed();
    }

    /** Crates differ in height, and none of those heights may decide whether a stack is allowed. */
    @GameTest(template = TEMPLATE)
    public static void crateVariantsStackOnOneAnother(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        ServerPlayer player = builder(helper, floor);
        helper.setBlock(floor, Blocks.STONE);

        placeByHand(helper, player, ItemRegistry.MEDIUM_CRATE_ITEM.get(), floor);
        BlockPos small = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        check(helper.getLevel().getBlockState(small).is(BlockRegistry.SMALL_CRATE.get()),
                "a small crate would not stack on a medium crate");

        BlockPos medium = placeByHand(
                helper, player, ItemRegistry.MEDIUM_CRATE_ITEM.get(), floor.above(2));
        check(helper.getLevel().getBlockState(medium).is(BlockRegistry.MEDIUM_CRATE.get()),
                "a medium crate would not stack on a small crate");

        disconnect(helper, player);
        helper.succeed();
    }

    /** A stack reads as one object only if the crates agree about which way they face. */
    @GameTest(template = TEMPLATE)
    public static void aStackedCrateAdoptsTheSupportingCratesFacing(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        ServerPlayer player = builder(helper, floor);
        helper.setBlock(floor, Blocks.STONE);

        // West is deliberately not the facing this player's own placement would produce.
        CrateBlock block = BlockRegistry.SMALL_CRATE.get();
        place(helper, block, helper.absolutePos(floor.above()), Direction.WEST);

        BlockPos upper = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        BlockState stacked = helper.getLevel().getBlockState(upper);
        check(stacked.is(block), "the crate did not stack at all");
        check(stacked.getValue(CrateBlock.FACING) == Direction.WEST,
                "a stacked crate ignored the facing of the crate it sits on");

        disconnect(helper, player);
        helper.succeed();
    }

    /**
     * Two crates in a stack are two containers, and removing one must not reach into the other.
     *
     * <p>This is the property the whole milestone is subordinate to: a stacking fix that merged or
     * spilled an inventory would be worse than no stacking at all.
     */
    @GameTest(template = TEMPLATE)
    public static void stackedCratesKeepSeparateInventoriesWhenOneIsBroken(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        ServerPlayer player = builder(helper, floor);
        helper.setBlock(floor, Blocks.STONE);

        BlockPos lower = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        BlockPos upper = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        CrateBlockEntity below = (CrateBlockEntity) helper.getLevel().getBlockEntity(lower);
        CrateBlockEntity above = (CrateBlockEntity) helper.getLevel().getBlockEntity(upper);
        check(below != null && above != null && below != above,
                "the two crates did not end up with one inventory each");
        below.setItem(0, new ItemStack(Items.DIAMOND, 3));
        above.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));

        check(below.getItem(0).is(Items.DIAMOND) && below.getItem(0).getCount() == 3,
                "the lower crate did not keep its own contents");
        check(above.getItem(0).is(Items.GOLD_INGOT) && above.getItem(0).getCount() == 5,
                "the upper crate did not keep its own contents");

        BlockState upperState = helper.getLevel().getBlockState(upper);
        BlockRegistry.SMALL_CRATE.get().onDestroyedByPlayer(
                upperState, helper.getLevel(), upper, player, true, upperState.getFluidState());

        check(helper.getLevel().getBlockState(upper).isAir(), "breaking the upper crate left it behind");
        check(helper.getLevel().getBlockState(lower).is(BlockRegistry.SMALL_CRATE.get()),
                "breaking the upper crate destroyed the lower one");
        CrateBlockEntity survivor = (CrateBlockEntity) helper.getLevel().getBlockEntity(lower);
        check(survivor != null && survivor.getItem(0).is(Items.DIAMOND)
                        && survivor.getItem(0).getCount() == 3,
                "breaking the upper crate disturbed the lower crate's contents");

        int gold = 0;
        int diamonds = 0;
        for (ItemEntity entity : helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(lower).inflate(4.0D))) {
            if (entity.getItem().is(Items.GOLD_INGOT)) {
                gold += entity.getItem().getCount();
            } else if (entity.getItem().is(Items.DIAMOND)) {
                diamonds += entity.getItem().getCount();
            }
        }
        check(gold == 5, "the upper crate's contents were lost or duplicated");
        check(diamonds == 0, "breaking the upper crate spilled the lower crate's contents");

        disconnect(helper, player);
        helper.succeed();
    }

    /**
     * The support exception has to stay narrow. A crate top is valid ground; a bottom slab, which is
     * just as partial, still is not.
     */
    @GameTest(template = TEMPLATE)
    public static void onlyACrateTopEscapesTheSturdySupportRule(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 1, 2);
        BlockPos slab = new BlockPos(3, 1, 2);
        ServerPlayer player = builder(helper, stone);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(slab, Blocks.SMOOTH_STONE_SLAB);

        BlockPos onStone = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), stone);
        check(helper.getLevel().getBlockState(onStone).is(BlockRegistry.SMALL_CRATE.get()),
                "an ordinary sturdy block stopped being valid support");

        BlockPos onSlab = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), slab);
        check(helper.getLevel().getBlockState(onSlab).isAir(),
                "the support rule was weakened for every partial block, not just crates");

        disconnect(helper, player);
        helper.succeed();
    }

    /**
     * Grabby Hands reaches placement by calling the item directly, so this is the same call it makes
     * once its own reach and policy checks have passed.
     */
    @GameTest(template = TEMPLATE)
    public static void theGrabbyPlacementCallAlsoStacksOntoACrate(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        ServerPlayer player = builder(helper, floor);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos lower = placeByHand(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        check(helper.getLevel().getBlockState(lower).is(BlockRegistry.SMALL_CRATE.get()),
                "the crate under test was never placed");

        player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get()));
        ItemRegistry.SMALL_CRATE_ITEM.get().useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, upwardHit(helper, floor.above())));

        check(helper.getLevel().getBlockState(lower.above()).is(BlockRegistry.SMALL_CRATE.get()),
                "the call Grabby Hands makes could not stack a crate");
        check(!(player.containerMenu instanceof ChestMenu),
                "the Grabby placement call opened the crate below");

        disconnect(helper, player);
        helper.succeed();
    }

    /* ─── helpers ────────────────────────────────────────────── */

    /**
     * Places through the real server entry point, so every handler that sits in front of placement -
     * Grabby Hands included - runs exactly as it does for a player who is not sneaking.
     *
     * @return the position the crate should have landed on
     */
    private static BlockPos placeByHand(
            GameTestHelper helper, ServerPlayer player, Item item, BlockPos supportRelative) {
        clickFace(helper, player, new ItemStack(item), supportRelative, Direction.UP);
        return helper.absolutePos(supportRelative).above();
    }

    private static void clickFace(
            GameTestHelper helper, ServerPlayer player, ItemStack held, BlockPos relative, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolute = helper.absolutePos(relative);
        player.gameMode.useItemOn(
                player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(absolute).relative(face, 0.5D), face, absolute, false));
    }

    private static BlockHitResult upwardHit(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockHitResult(
                Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5D), Direction.UP, absolute, false);
    }

    /**
     * A player who can build, standing next to the column under test.
     *
     * <p>Both halves are load-bearing. Grabby Hands range-checks the destination on the server, so a
     * player left wherever a mock join drops them has every placement refused as out of reach. And
     * {@code SurvivalZoneHandler} parks ordinary players in Adventure and lends {@code mayBuild} only
     * inside their own house; build rights are a housing question with its own suite, so they are
     * granted outright here to keep this file about crates.
     */
    private static ServerPlayer builder(GameTestHelper helper, BlockPos columnRelative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayBuild = true;
        player.onUpdateAbilities();
        // Beside the column, never in it: a player standing in the destination blocks their own
        // placement through the block's own unobstructed check.
        BlockPos stand = helper.absolutePos(columnRelative.offset(2, 1, 0));
        player.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        return player;
    }

    /**
     * Sends the player home again.
     *
     * <p>A joined player keeps the chunks around them loaded and stays in the per-tick player loop
     * for the rest of the run, which pre-existing suites in this project are sensitive to.
     */
    private static void disconnect(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void place(GameTestHelper helper, CrateBlock block, BlockPos anchor, Direction facing) {
        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell), block.stateFor(facing, cell), flags);
            }
            return null;
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

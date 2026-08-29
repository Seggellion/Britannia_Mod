package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Compact stacking and per-crate menus, through the path a player actually uses.
 *
 * <p>Everything here drives {@code ServerPlayerGameMode.useItemOn}, so Grabby Hands claims the click
 * first exactly as it does in play and the arbitration between "stack this" and "open this" is being
 * tested rather than assumed. Calling the item or the block directly would prove nothing about the
 * interaction a player receives.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackInteractionGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CrateStackInteractionGameTests() {
    }

    /* ─── promotion by hand ──────────────────────────────────── */

    /** The headline: two crates placed by hand become one compact column, not two floating blocks. */
    @GameTest(template = TEMPLATE)
    public static void stackingACrateOnACratePromotesItIntoOneCompactColumn(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);

        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        BlockPos root = helper.absolutePos(floor.above());
        check(helper.getLevel().getBlockState(root).is(BlockRegistry.SMALL_CRATE.get()),
                "the first crate should be an ordinary crate");

        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());

        check(helper.getLevel().getBlockState(root).is(BlockRegistry.CRATE_STACK.get()),
                "the crate was not promoted into a column");
        CrateStackBlockEntity stack = column(helper, root);
        check(stack.crateCount() == 2, "the column should hold two crates");
        check(stack.requiredCellCount() == 1, "two small crates fit in one cell");
        check(stack.totalHeightHundredths() == 1430, "they should pack to 14.30 voxels");
        check(!helper.getLevel().getBlockState(root.above()).is(BlockRegistry.SMALL_CRATE.get()),
                "a crate was left floating above the column");
        disconnect(helper, player);
        helper.succeed();
    }

    /** Promotion must carry the standing crate's contents, components and all. */
    @GameTest(template = TEMPLATE)
    public static void promotionByHandKeepsTheStandingCratesContents(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);

        BlockPos root = helper.absolutePos(floor.above());
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bane of Crates"));
        ((com.seggellion.britannia_mod.block.entity.CrateBlockEntity)
                helper.getLevel().getBlockEntity(root)).setItem(0, named);

        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());

        CrateStackBlockEntity stack = column(helper, root);
        int bottom = stack.bottomCrate().id();
        check(stack.containerFor(bottom).getItem(0).is(Items.DIAMOND_SWORD),
                "the standing crate's contents did not survive promotion");
        check(Component.literal("Bane of Crates").equals(
                        stack.containerFor(bottom).getItem(0).get(DataComponents.CUSTOM_NAME)),
                "item components did not survive promotion");
        check(stack.containerFor(stack.topCrate().id()).isEmpty(),
                "the newly placed crate should be empty and separate");
        check(dropsAround(helper, root) == 0, "promotion by hand dropped something");
        disconnect(helper, player);
        helper.succeed();
    }

    /** Mixed variants pack against one another exactly as the layout says. */
    @GameTest(template = TEMPLATE)
    public static void mixedVariantsStackCompactly(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);

        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        placeOnTop(helper, player, ItemRegistry.MEDIUM_CRATE_ITEM.get(), floor.above());

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        check(stack.crateCount() == 2, "a medium crate did not stack onto a small one");
        check(stack.topCrate().variant() == CrateVariant.MEDIUM, "the wrong variant was appended");
        check(stack.totalHeightHundredths() == 1866, "small plus medium should pack to 18.66");
        check(stack.requiredCellCount() == 2, "18.66 voxels needs two cells");
        check(helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "the continuation cell was not claimed");
        disconnect(helper, player);
        helper.succeed();
    }

    /** A third crate grows the column into a second cell, through the ordinary placement path. */
    @GameTest(template = TEMPLATE)
    public static void appendingToAColumnGrowsItIntoANewCell(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        check(stack.requiredCellCount() == 1, "two small crates need one cell");

        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());

        check(stack.crateCount() == 3, "the third crate did not join the column");
        check(stack.requiredCellCount() == 2, "three small crates need two cells");
        check(helper.getLevel().getBlockState(root.above()).is(BlockRegistry.CRATE_STACK.get()),
                "the new continuation cell was not claimed");
        check(helper.getLevel().getBlockEntity(root.above()) == null,
                "the continuation cell must not own a block entity");
        disconnect(helper, player);
        helper.succeed();
    }

    /* ─── menus ──────────────────────────────────────────────── */

    /**
     * Every visible crate opens its own inventory.
     *
     * <p>The point of the whole architecture: one block entity, several crates, and a player who can
     * tell them apart only by where they aim.
     */
    @GameTest(template = TEMPLATE)
    public static void eachVisibleCrateOpensItsOwnInventory(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        placeOnTop(helper, player, ItemRegistry.MEDIUM_CRATE_ITEM.get(), floor.above());

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        int lower = stack.bottomCrate().id();
        int upper = stack.topCrate().id();
        stack.containerFor(lower).setItem(0, new ItemStack(Items.APPLE, 4));
        stack.containerFor(upper).setItem(0, new ItemStack(Items.DIAMOND, 6));

        // Aimed low: the small crate at the bottom of the column.
        openAt(helper, player, root, 3.0D);
        check(player.containerMenu instanceof ChestMenu lowerMenu
                        && lowerMenu.getContainer().getContainerSize() == 9,
                "aiming at the lower crate did not open a nine-slot crate");
        check(((ChestMenu) player.containerMenu).getContainer().getItem(0).is(Items.APPLE),
                "the lower crate showed the wrong contents");
        player.closeContainer();

        // Aimed high: the medium crate above it, which is a different size and different inventory.
        openAt(helper, player, root, 12.0D);
        check(player.containerMenu instanceof ChestMenu upperMenu
                        && upperMenu.getContainer().getContainerSize() == 27,
                "aiming at the upper crate did not open a twenty-seven-slot crate");
        check(((ChestMenu) player.containerMenu).getContainer().getItem(0).is(Items.DIAMOND),
                "the upper crate showed the wrong contents");
        player.closeContainer();

        disconnect(helper, player);
        helper.succeed();
    }

    /** A crate whose geometry lives in a continuation cell is still opened by clicking that cell. */
    @GameTest(template = TEMPLATE)
    public static void aCrateInAContinuationCellOpensFromThatCell(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        for (int index = 0; index < 2; index++) {
            placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        }

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        int top = stack.topCrate().id();
        stack.containerFor(top).setItem(0, new ItemStack(Items.EMERALD, 2));
        check(stack.requiredCellCount() == 2, "this fixture needs a continuation cell");

        // 18.00 voxels up: inside the third crate, and inside the cell above the root.
        BlockPos continuation = root.above();
        BlockHitResult hit = new BlockHitResult(
                new Vec3(root.getX() + 0.5D, root.getY() + 18.0D / 16.0D, root.getZ() + 0.5D),
                Direction.NORTH, continuation, false);
        check(CrateStackTargetResolver.resolve(helper.getLevel(), hit)
                        .map(CrateStackTargetResolver.Target::crateId)
                        .orElse(-1) == top,
                "a click on the continuation cell did not resolve the top crate");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY,
                InteractionHand.MAIN_HAND, hit);
        check(player.containerMenu instanceof ChestMenu menu
                        && menu.getContainer().getItem(0).is(Items.EMERALD),
                "clicking a continuation cell opened the wrong crate");
        player.closeContainer();

        check(helper.getLevel().getBlockEntity(continuation) == null,
                "a continuation cell must never own a container of its own");
        disconnect(helper, player);
        helper.succeed();
    }

    /** Holding a crate and clicking a column's side opens it, exactly as M1.5 established for crates. */
    @GameTest(template = TEMPLATE)
    public static void aSideClickWithACrateInHandOpensTheColumn(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        int crates = stack.crateCount();

        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get()));
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(root.getX() + 0.5D, root.getY() + 3.0D / 16.0D, root.getZ()),
                        Direction.NORTH, root, false));

        check(player.containerMenu instanceof ChestMenu,
                "a side click with a crate in hand did not open the column");
        check(stack.crateCount() == crates, "a side click appended a crate");
        player.closeContainer();
        disconnect(helper, player);
        helper.succeed();
    }

    /* ─── failure atomicity ──────────────────────────────────── */

    /** A ceiling refuses the placement, and refusing costs nothing — including the held crate. */
    @GameTest(template = TEMPLATE)
    public static void anObstructedAppendChangesNothingAndKeepsTheHeldCrate(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        stack.containerFor(stack.bottomCrate().id()).setItem(0, new ItemStack(Items.DIAMOND, 3));
        helper.getLevel().setBlock(root.above(), Blocks.OBSIDIAN.defaultBlockState(),
                Block.UPDATE_ALL);

        int crates = stack.crateCount();
        int nextId = stack.nextCrateId();
        int height = stack.totalHeightHundredths();
        ItemStack held = new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, topHit(helper, root, stack));

        check(stack.crateCount() == crates, "an obstructed append changed the crate count");
        check(stack.nextCrateId() == nextId, "an obstructed append consumed an id");
        check(stack.totalHeightHundredths() == height, "an obstructed append changed the height");
        check(stack.containerFor(stack.bottomCrate().id()).getItem(0).getCount() == 3,
                "an obstructed append disturbed the contents");
        check(player.getMainHandItem().getCount() == 3, "an obstructed append consumed the held crate");
        check(helper.getLevel().getBlockState(root.above()).is(Blocks.OBSIDIAN),
                "an obstructed append overwrote the block in its way");
        disconnect(helper, player);
        helper.succeed();
    }

    /** At the cap, the column refuses and the player keeps their crate. */
    @GameTest(template = TEMPLATE)
    public static void anAppendAtTheCapKeepsTheHeldCrate(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 0, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        for (int index = 0; index < 7; index++) {
            placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        }
        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        check(stack.crateCount() == 8, "eight small crates should have stacked");

        ItemStack held = new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        int nextId = stack.nextCrateId();

        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, topHit(helper, root, stack));

        check(stack.crateCount() == 8, "a ninth crate joined a full column");
        check(stack.nextCrateId() == nextId, "a refused append consumed an id");
        check(player.getMainHandItem().getCount() == 2, "a refused append consumed the held crate");
        disconnect(helper, player);
        helper.succeed();
    }

    /** Survival consumes exactly one crate; creative consumes none. */
    @GameTest(template = TEMPLATE)
    public static void aSuccessfulAppendConsumesExactlyOneCrateInSurvivalAndNoneInCreative(
            GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);

        ItemStack held = new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get(), 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, upwardHit(helper, floor.above()));
        check(player.getMainHandItem().getCount() == 4, "survival should consume exactly one crate");

        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        int crates = stack.crateCount();

        player.getAbilities().instabuild = true;
        player.onUpdateAbilities();
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, topHit(helper, root, stack));

        check(stack.crateCount() == crates + 1, "creative placement did not add a crate");
        check(player.getMainHandItem().getCount() == 4, "creative placement consumed a crate");
        disconnect(helper, player);
        helper.succeed();
    }

    /* ─── the temporary break guard ──────────────────────────── */

    /**
     * A column cannot be broken until the destruction milestone lands.
     *
     * <p>Players can now build these in ordinary play, and taking one apart correctly is genuinely
     * hard — several inventories in one block entity, cells that must shrink in step, and a client
     * that predicts removal. Refusing is visibly wrong and completely safe, which is the right way
     * round for something holding other people's belongings.
     */
    @GameTest(template = TEMPLATE)
    public static void aCompactColumnCannotBeBrokenYet(GameTestHelper helper) {
        ServerPlayer player = builder(helper);
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor);
        for (int index = 0; index < 2; index++) {
            placeOnTop(helper, player, ItemRegistry.SMALL_CRATE_ITEM.get(), floor.above());
        }
        BlockPos root = helper.absolutePos(floor.above());
        CrateStackBlockEntity stack = column(helper, root);
        stack.containerFor(stack.bottomCrate().id()).setItem(0, new ItemStack(Items.DIAMOND, 9));
        BlockPos continuation = root.above();
        check(helper.getLevel().getBlockState(continuation).is(BlockRegistry.CRATE_STACK.get()),
                "this fixture needs a continuation cell");

        player.gameMode.destroyBlock(root);
        player.gameMode.destroyBlock(continuation);

        check(helper.getLevel().getBlockState(root).is(BlockRegistry.CRATE_STACK.get()),
                "the column's root was destroyed before the break milestone");
        check(helper.getLevel().getBlockState(continuation).is(BlockRegistry.CRATE_STACK.get()),
                "a continuation cell was destroyed before the break milestone");
        check(column(helper, root).crateCount() == 3, "breaking changed the column");
        check(column(helper, root).containerFor(stack.bottomCrate().id()).getItem(0).getCount() == 9,
                "breaking disturbed a crate's contents");
        check(dropsAround(helper, root) == 0, "a refused break dropped something");
        disconnect(helper, player);
        helper.succeed();
    }

    /* ─── helpers ────────────────────────────────────────────── */

    /**
     * Places through the real server entry point, so Grabby arbitration runs as it does in play.
     *
     * <p>Aims at the column's lid once one exists. A column taller than a cell keeps its lid in an
     * upper cell, so the top face of the root block is no longer the surface a player would be
     * pointing at — and aiming there is a click inside the column, not a stacking gesture.
     */
    private static void placeOnTop(
            GameTestHelper helper, ServerPlayer player, Item item, BlockPos supportRelative) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 16));
        BlockPos absolute = helper.absolutePos(supportRelative);
        BlockHitResult hit =
                helper.getLevel().getBlockEntity(absolute) instanceof CrateStackBlockEntity column
                        ? topHit(helper, absolute, column)
                        : upwardHit(helper, supportRelative);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, hit);
    }

    /** A hit on the top face of whatever occupies a relative position. */
    private static BlockHitResult upwardHit(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        double top = topSurfaceOf(helper, absolute);
        return new BlockHitResult(
                new Vec3(absolute.getX() + 0.5D, top, absolute.getZ() + 0.5D),
                Direction.UP, absolute, false);
    }

    /** A hit exactly on a column's lid, which is the only surface that accepts another crate. */
    private static BlockHitResult topHit(
            GameTestHelper helper, BlockPos root, CrateStackBlockEntity stack) {
        double lid = root.getY() + stack.totalHeightHundredths() / 1600.0D;
        int cell = Math.max(0, stack.requiredCellCount() - 1);
        return new BlockHitResult(
                new Vec3(root.getX() + 0.5D, lid, root.getZ() + 0.5D),
                Direction.UP, root.above(cell), false);
    }

    /** Where a block's own geometry ends, so a hit lands on it rather than inside it. */
    private static double topSurfaceOf(GameTestHelper helper, BlockPos absolute) {
        var shape = helper.getLevel().getBlockState(absolute)
                .getShape(helper.getLevel(), absolute);
        return absolute.getY() + (shape.isEmpty() ? 1.0D : shape.max(Direction.Axis.Y));
    }

    private static void openAt(
            GameTestHelper helper, ServerPlayer player, BlockPos root, double voxelsUp) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(root.getX() + 0.5D, root.getY() + voxelsUp / 16.0D, root.getZ()),
                        Direction.NORTH, root, false));
    }

    private static CrateStackBlockEntity column(GameTestHelper helper, BlockPos root) {
        if (helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity stack) {
            return stack;
        }
        throw new GameTestAssertException("no crate column at " + root);
    }

    private static int dropsAround(GameTestHelper helper, BlockPos root) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(6.0D))
                .size();
    }

    /** A player who can build, standing beside the column and within Grabby's reach of it. */
    private static ServerPlayer builder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();
        BlockPos stand = helper.absolutePos(new BlockPos(4, 2, 2));
        player.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        return player;
    }

    private static void disconnect(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

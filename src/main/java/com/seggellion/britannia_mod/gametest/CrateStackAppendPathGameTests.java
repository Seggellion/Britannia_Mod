package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Stacking crates the way a player does it: one right-click at a time, on the lid.
 *
 * <h2>What this is for</h2>
 *
 * <p>Every other crate test builds its column by calling the domain directly, which proves the domain
 * and proves nothing about the click. A player reported placing a crate and not seeing it, and the
 * first thing that has to be established is whether the crate exists at all — an append that silently
 * did not happen and a crate that is not drawn look identical from the other side of the screen.
 *
 * <p>So these drive {@code ServerPlayerGameMode.useItemOn}, the same entry point a
 * {@code ServerboundUseItemOnPacket} reaches, and count the column after every click. The count is the
 * whole assertion: click <i>n</i> must leave <i>n</i> crates.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrateStackAppendPathGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private CrateStackAppendPathGameTests() {
    }

    /* ─── one variant at a time ──────────────────────────────── */

    /** Four small crates, four clicks, four crates. */
    @GameTest(template = TEMPLATE)
    public static void fourSmallClicksLeaveFourSmallCrates(GameTestHelper helper) {
        clickSequence(helper, false, small(), small(), small(), small());
    }

    /** Three medium crates: the second already needs a continuation cell. */
    @GameTest(template = TEMPLATE)
    public static void threeMediumClicksLeaveThreeMediumCrates(GameTestHelper helper) {
        clickSequence(helper, false, medium(), medium(), medium());
    }

    /* ─── mixed ──────────────────────────────────────────────── */

    /** A medium on a small. */
    @GameTest(template = TEMPLATE)
    public static void aMediumClickedOntoASmallAppends(GameTestHelper helper) {
        clickSequence(helper, false, small(), medium());
    }

    /** A small on a medium. */
    @GameTest(template = TEMPLATE)
    public static void aSmallClickedOntoAMediumAppends(GameTestHelper helper) {
        clickSequence(helper, false, medium(), small());
    }

    /** Small, medium, small — the mixed column the milestone asks for. */
    @GameTest(template = TEMPLATE)
    public static void aSmallMediumSmallColumnBuildsByClicking(GameTestHelper helper) {
        clickSequence(helper, false, small(), medium(), small());
    }

    /* ─── sneaking ───────────────────────────────────────────── */

    /** Sneaking must not change how many crates a click adds. */
    @GameTest(template = TEMPLATE)
    public static void sneakingSmallClicksAppendTheSameWay(GameTestHelper helper) {
        clickSequence(helper, true, small(), small(), small());
    }

    /** The same for medium, which crosses a cell on its second crate. */
    @GameTest(template = TEMPLATE)
    public static void sneakingMediumClicksAppendTheSameWay(GameTestHelper helper) {
        clickSequence(helper, true, medium(), medium());
    }

    /* ─── large crates ───────────────────────────────────────── */

    /**
     * A large crate will not perch on a large one.
     *
     * <p>A compact crate on a large one is a foundation stack and is covered by
     * {@code CrateStackFoundationGameTests}; these are the combinations that stay refused, because
     * the large crate's two-block envelope has no compact representation.
     */
    @GameTest(template = TEMPLATE)
    public static void aLargeCrateWillNotPerchOnALargeCrate(GameTestHelper helper) {
        refusedOnTopOf(helper, large(), large());
    }

    /** A large crate will not perch on a compact column either. */
    @GameTest(template = TEMPLATE)
    public static void aLargeCrateWillNotPerchOnACompactColumn(GameTestHelper helper) {
        refusedOnTopOf(helper, small(), large());
    }

    /** A large crate will not perch on a lone small crate. */
    @GameTest(template = TEMPLATE)
    public static void aLargeCrateWillNotPerchOnASmallCrate(GameTestHelper helper) {
        refusedOnTopOf(helper, null, large(), small());
    }

    /**
     * Builds a base out of {@code base} crates, then proves {@code held} cannot be put on top of it.
     *
     * <p>The refusal is checked three ways, because a placement that only half happens is worse than
     * one that happens: the crates in the world must be exactly the ones that were there before, the
     * held stack must not have shrunk, and the base must still be standing.
     */
    private static void refusedOnTopOf(GameTestHelper helper, Item base, Item held) {
        refusedOnTopOf(helper, base, held, base);
    }

    private static void refusedOnTopOf(
            GameTestHelper helper, Item second, Item held, Item first) {
        BlockPos floor = layFloor(helper);
        BlockPos root = helper.absolutePos(floor.above());
        ServerPlayer player = placer(helper, root, false);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(first, 8));
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, floorHit(helper.absolutePos(floor)));
        if (second != null && second != first) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(second, 8));
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND, lidHit(helper, root));
        }

        Set<BlockPos> before = crateBlocks(helper, root);
        check(!before.isEmpty(), "the base crate was never placed, so nothing was proved");

        player.setGameMode(GameType.SURVIVAL);
        ItemStack hand = new ItemStack(held, 8);
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, topFaceOf(helper, before));

        check(crateBlocks(helper, root).equals(before),
                "stacking onto an unsupported crate changed the world; was " + before
                        + " and is now " + crateBlocks(helper, root));
        check(hand.getCount() == 8, "a refused placement still consumed a crate");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Every crate-family block standing in the column's neighbourhood. */
    private static Set<BlockPos> crateBlocks(GameTestHelper helper, BlockPos root) {
        Set<BlockPos> found = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 6; y++) {
                    BlockPos pos = root.offset(x, y, z);
                    BlockState state = helper.getLevel().getBlockState(pos);
                    if (state.getBlock() instanceof CrateBlock
                            || state.getBlock() instanceof CrateStackBlock) {
                        found.add(pos);
                    }
                }
            }
        }
        return found;
    }

    /** A click on the upward face of the highest crate present, at the height its art reaches. */
    private static BlockHitResult topFaceOf(GameTestHelper helper, Set<BlockPos> crates) {
        BlockPos highest = crates.stream()
                .max((a, b) -> Integer.compare(a.getY(), b.getY()))
                .orElseThrow();
        double top = highest.getY()
                + helper.getLevel().getBlockState(highest).getShape(helper.getLevel(), highest)
                        .max(Direction.Axis.Y);
        Vec3 at = new Vec3(highest.getX() + 0.5D, top, highest.getZ() + 0.5D);
        return new BlockHitResult(at, Direction.UP, highest, false);
    }

    private static Item large() {
        return ItemRegistry.LARGE_CRATE_ITEM.get();
    }

    /**
     * Ground wide enough for any crate in the family, and the spot to click on it.
     *
     * <p>A large crate is two blocks square, so a single block of floor leaves half of it standing
     * over air and the placement is refused for a reason that has nothing to do with what is being
     * tested.
     */
    private static BlockPos layFloor(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        return new BlockPos(2, 1, 2);
    }

    /* ─── driving clicks ─────────────────────────────────────── */

    /**
     * Places the first crate on the floor and every later one on the lid, counting as it goes.
     *
     * <p>The report is built up across the whole sequence and only read at the end, so a failure says
     * what the column did on every click rather than only the one that broke.
     */
    private static void clickSequence(GameTestHelper helper, boolean sneaking, Item... crates) {
        BlockPos floor = layFloor(helper);
        BlockPos root = helper.absolutePos(floor.above());
        ServerPlayer player = placer(helper, root, sneaking);

        List<String> progress = new ArrayList<>();
        for (int index = 0; index < crates.length; index++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(crates[index], 8));
            BlockHitResult hit = index == 0
                    ? floorHit(helper.absolutePos(floor))
                    : lidHit(helper, root);
            player.gameMode.useItemOn(
                    player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            progress.add((index + 1) + "->" + logicalCrateCount(helper, root));
        }

        int expected = crates.length;
        int actual = logicalCrateCount(helper, root);
        check(actual == expected,
                "clicking " + expected + " crates onto one another left " + actual
                        + "; count after each click was " + String.join(", ", progress));
        check(drawnCrateCount(helper, root) == expected,
                "the column holds " + expected + " crates but hands the renderer "
                        + drawnCrateCount(helper, root));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * How many crates the cells actually hand the renderer, counted across the whole column.
     *
     * <p>The slice is the renderer's only input, so a crate missing from it is a crate that cannot be
     * drawn however many times the section is rebuilt. Counting it here is the closest a headless test
     * gets to counting what is on screen.
     */
    private static int drawnCrateCount(GameTestHelper helper, BlockPos root) {
        if (!(helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity stack)) {
            return helper.getLevel().getBlockState(root).getBlock() instanceof CrateBlock ? 1 : 0;
        }
        Set<Integer> drawn = new HashSet<>();
        for (int cell = 0; cell < stack.requiredCellCount(); cell++) {
            stack.sliceFor(cell).entries().forEach(entry -> drawn.add(entry.crateId()));
        }
        return drawn.size();
    }

    /**
     * How many crates the column holds, however it is currently represented.
     *
     * <p>A lone crate that has never been stacked on is still one crate to the player, so it counts as
     * one; that is the only way the first click and the rest can be compared on the same scale.
     */
    private static int logicalCrateCount(GameTestHelper helper, BlockPos root) {
        if (helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity stack) {
            return stack.crateCount();
        }
        return helper.getLevel().getBlockState(root).getBlock() instanceof CrateBlock ? 1 : 0;
    }

    /** A click on the top face of the floor, which is how the first crate gets placed. */
    private static BlockHitResult floorHit(BlockPos floor) {
        Vec3 at = new Vec3(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
        return new BlockHitResult(at, Direction.UP, floor, false);
    }

    /**
     * A click on the exposed lid of whatever stands at the root, wherever that lid has risen to.
     *
     * <p>The height comes from the column itself, so this follows a stack up through its continuation
     * cells exactly as a player's aim would.
     */
    private static BlockHitResult lidHit(GameTestHelper helper, BlockPos root) {
        int height = heightHundredths(helper, root);
        // The lid belongs to the cell the crate below it is in, not the one its surface touches.
        int cell = Math.max(0, (height - 1) / CrateStackLayout.CELL_HUNDREDTHS);
        BlockPos clicked = root.above(cell);
        Vec3 at = new Vec3(
                root.getX() + 0.5D,
                root.getY() + height / (double) CrateStackLayout.CELL_HUNDREDTHS,
                root.getZ() + 0.5D);
        return new BlockHitResult(at, Direction.UP, clicked, false);
    }

    /** How tall the thing at the root currently is, in hundredths of a voxel. */
    private static int heightHundredths(GameTestHelper helper, BlockPos root) {
        if (helper.getLevel().getBlockEntity(root) instanceof CrateStackBlockEntity stack) {
            return stack.totalHeightHundredths();
        }
        BlockState state = helper.getLevel().getBlockState(root);
        check(state.getBlock() instanceof CrateBlock, "nothing was placed to click on at " + root);
        return (int) Math.round(
                state.getShape(helper.getLevel(), root).max(Direction.Axis.Y)
                        * 16 * CrateStackLayout.HUNDREDTHS_PER_VOXEL);
    }

    /** A player standing beside the column, close enough for every reach check on the way in. */
    private static ServerPlayer placer(GameTestHelper helper, BlockPos root, boolean sneaking) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().mayBuild = true;
        player.onUpdateAbilities();
        player.absMoveTo(root.getX() + 1.5D, root.getY(), root.getZ() + 0.5D);
        player.setYRot(90.0F);
        player.setXRot(0.0F);
        player.setYHeadRot(90.0F);
        player.setOldPosAndRot();
        player.setShiftKeyDown(sneaking);
        return player;
    }

    private static Item small() {
        return ItemRegistry.SMALL_CRATE_ITEM.get();
    }

    private static Item medium() {
        return ItemRegistry.MEDIUM_CRATE_ITEM.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

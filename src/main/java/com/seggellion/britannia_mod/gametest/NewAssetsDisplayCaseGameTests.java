package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock.ConnectionForm;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server topology and lifecycle checks for Milestone 9 display cases. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsDisplayCaseGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsDisplayCaseGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void displayCasesConnectWithoutCreatingInventories(GameTestHelper helper) {
        DisplayCaseBlock block = BlockRegistry.DISPLAY_CASE.get();
        BlockPos west = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos middle = helper.absolutePos(new BlockPos(3, 3, 2));
        BlockPos east = helper.absolutePos(new BlockPos(4, 3, 2));

        place(helper, block, west);
        check(form(helper, block, west) == ConnectionForm.INDEPENDENT,
                "a lone display case was not independent");
        var independentLowerBounds = helper.getLevel().getBlockState(west)
                .getCollisionShape(helper.getLevel(), west).bounds();
        var independentUpperBounds = helper.getLevel().getBlockState(west.above())
                .getCollisionShape(helper.getLevel(), west.above()).bounds();
        check(independentLowerBounds.minX == 1.0D / 16.0D
                        && independentLowerBounds.maxX == 15.0D / 16.0D,
                "independent collision did not follow the inset owner base");
        check(independentUpperBounds.maxY == 6.0D / 16.0D,
                "upper collision did not stop at the requested 22-voxel global model height");
        var independentUpperState = helper.getLevel().getBlockState(west.above());
        var independentUpperOutline = independentUpperState
                .getShape(helper.getLevel(), west.above()).bounds();
        var independentUpperSupport = independentUpperState
                .getBlockSupportShape(helper.getLevel(), west.above()).bounds();
        check(independentUpperOutline.maxY == 1.0D,
                "upper outline did not expose a normal top placement target at Y=16");
        check(independentUpperSupport.minY == 15.0D / 16.0D
                        && independentUpperSupport.maxY == 1.0D
                        && independentUpperSupport.minX == 0.0D
                        && independentUpperSupport.maxX == 1.0D
                        && independentUpperSupport.minZ == 0.0D
                        && independentUpperSupport.maxZ == 1.0D,
                "upper logical support was not the authored one-voxel top plate");
        check(independentUpperState.isFaceSturdy(helper.getLevel(), west.above(), Direction.UP),
                "display-case top did not report a sturdy upper face");
        check(!independentUpperState.isFaceSturdy(helper.getLevel(), west.above(), Direction.NORTH),
                "logical top support accidentally made a cage side sturdy");
        place(helper, block, middle);
        check(form(helper, block, west) == ConnectionForm.END
                        && form(helper, block, middle) == ConnectionForm.END,
                "two display cases did not become matching end pieces");
        place(helper, block, east);
        check(form(helper, block, west) == ConnectionForm.END
                        && form(helper, block, middle) == ConnectionForm.MIDDLE
                        && form(helper, block, east) == ConnectionForm.END,
                "three display cases did not form end/middle/end");
        check(helper.getLevel().getBlockState(middle).getValue(DisplayCaseBlock.WEST)
                        && helper.getLevel().getBlockState(middle).getValue(DisplayCaseBlock.EAST),
                "middle display case did not open both shared faces");
        check(helper.getLevel().getBlockState(middle.above()).getValue(DisplayCaseBlock.WEST)
                        && helper.getLevel().getBlockState(middle.above()).getValue(DisplayCaseBlock.EAST),
                "upper rendering cell did not mirror the root's open shared faces");

        BlockPos corner = helper.absolutePos(new BlockPos(7, 3, 5));
        BlockPos cornerEast = corner.east();
        BlockPos cornerSouth = corner.south();
        place(helper, block, corner);
        place(helper, block, cornerEast);
        place(helper, block, cornerSouth);
        check(form(helper, block, corner) == ConnectionForm.CORNER,
                "adjacent display-case neighbours did not form a corner");
        check(helper.getLevel().getBlockState(corner).getValue(DisplayCaseBlock.EAST)
                        && helper.getLevel().getBlockState(corner).getValue(DisplayCaseBlock.SOUTH),
                "corner display case did not open the two shared faces");

        for (BlockPos anchor : List.of(west, middle, east, corner, cornerEast, cornerSouth)) {
            check(helper.getLevel().getBlockEntity(anchor) == null
                            && helper.getLevel().getBlockEntity(anchor.above()) == null,
                    "decorative display case created a storage/display block entity");
        }

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos middleTop = middle.above();
        var topState = helper.getLevel().getBlockState(middleTop);
        block.onDestroyedByPlayer(
                topState, helper.getLevel(), middleTop, player, true, topState.getFluidState());

        check(!helper.getLevel().getBlockState(middle).is(block)
                        && !helper.getLevel().getBlockState(middleTop).is(block),
                "breaking the upper cell left an orphaned display case");
        check(form(helper, block, west) == ConnectionForm.INDEPENDENT
                        && form(helper, block, east) == ConnectionForm.INDEPENDENT,
                "neighbour display cases did not disconnect after teardown");
        int drops = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, new AABB(middle).inflate(2.0D)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemRegistry.DISPLAY_CASE_ITEM.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        check(drops == 1, "display-case teardown did not drop exactly one item");

        place(helper, block, middle);
        check(form(helper, block, west) == ConnectionForm.END
                        && form(helper, block, middle) == ConnectionForm.MIDDLE
                        && form(helper, block, east) == ConnectionForm.END,
                "restoring the middle did not reconnect the straight run");
        BlockPos turn = east.south();
        place(helper, block, turn);
        check(form(helper, block, east) == ConnectionForm.CORNER,
                "extending the restored run by 90 degrees did not produce a corner");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void displayCaseTopUsesOrdinaryPlacementWithoutAddingCollision(GameTestHelper helper) {
        DisplayCaseBlock block = BlockRegistry.DISPLAY_CASE.get();
        BlockPos run = helper.absolutePos(new BlockPos(2, 3, 2));
        for (int x = 0; x < 3; x++) {
            place(helper, block, run.offset(x, 0, 0));
        }

        ServerPlayer survival = helper.makeMockServerPlayerInLevel();
        survival.setGameMode(GameType.SURVIVAL);
        survival.setPos(run.getX() + 5.5D, run.getY(), run.getZ() + 3.5D);
        for (int x = 0; x < 3; x++) {
            BlockPos root = run.offset(x, 0, 0);
            BlockHitResult hit = centerTopHit(helper, survival, root);
            check(hit.getBlockPos().equals(root.above()) && hit.getDirection() == Direction.UP,
                    "center ray did not target the display case's upper face: expected="
                            + root.above() + "/UP actual=" + hit.getBlockPos() + "/"
                            + hit.getDirection() + " at " + hit.getLocation());

            ItemStack stone = new ItemStack(Items.STONE);
            InteractionResult result = use(survival, stone, hit);
            check(result.consumesAction(), "vanilla full-block placement was refused: " + result);
            check(helper.getLevel().getBlockState(root.above(2)).is(Blocks.STONE),
                    "vanilla full block did not land above display case " + x);
            check(stone.isEmpty(), "survival placement did not consume one vanilla block");

            var physicalCollision = helper.getLevel().getBlockState(root.above())
                    .getCollisionShape(helper.getLevel(), root.above());
            check(!physicalCollision.isEmpty()
                            && physicalCollision.bounds().maxY == 6.0D / 16.0D,
                    "logical support leaked into upper physical collision");
        }

        BlockPos reusedRoot = run;
        helper.getLevel().removeBlock(reusedRoot.above(2), false);
        ItemStack smallCrate = new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get());
        InteractionResult customResult = use(
                survival, smallCrate, centerTopHit(helper, survival, reusedRoot));
        check(customResult.consumesAction(),
                "representative UltimaCraft block placement was refused: " + customResult);
        check(helper.getLevel().getBlockState(reusedRoot.above(2)).is(BlockRegistry.SMALL_CRATE.get()),
                "representative UltimaCraft block did not land above the display case");
        check(smallCrate.isEmpty(), "survival custom-block placement did not consume its item");
        helper.getLevel().removeBlock(reusedRoot.above(2), false);
        check(helper.getLevel().getBlockState(reusedRoot).is(block)
                        && helper.getLevel().getBlockState(reusedRoot.above()).is(block),
                "removing custom merchandise dismantled its display case");

        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.setGameMode(GameType.CREATIVE);
        creative.setPos(reusedRoot.getX() + 5.5D, reusedRoot.getY(), reusedRoot.getZ() + 3.5D);
        ItemStack creativeStone = new ItemStack(Items.STONE);
        InteractionResult creativeResult = use(
                creative, creativeStone, centerTopHit(helper, creative, reusedRoot));
        check(creativeResult.consumesAction()
                        && helper.getLevel().getBlockState(reusedRoot.above(2)).is(Blocks.STONE),
                "creative placement above a display case failed");
        check(creativeStone.getCount() == 1,
                "creative placement unexpectedly consumed the held block");

        helper.getLevel().removeBlock(reusedRoot.above(2), false);
        check(helper.getLevel().getBlockState(reusedRoot).is(block)
                        && helper.getLevel().getBlockState(reusedRoot.above()).is(block),
                "removing merchandise dismantled its display case");

        ServerPlayer adventure = helper.makeMockServerPlayerInLevel();
        adventure.setGameMode(GameType.ADVENTURE);
        adventure.setPos(reusedRoot.getX() + 5.5D, reusedRoot.getY(), reusedRoot.getZ() + 3.5D);
        ItemStack deniedStone = new ItemStack(Items.STONE);
        use(adventure, deniedStone, centerTopHit(helper, adventure, reusedRoot));
        check(helper.getLevel().getBlockState(reusedRoot.above(2)).isAir(),
                "display-case support bypassed Adventure mode placement authorization");

        ItemStack supportedStone = new ItemStack(Items.STONE);
        InteractionResult supportedResult = use(
                survival, supportedStone, centerTopHit(helper, survival, reusedRoot));
        check(supportedResult.consumesAction(),
                "could not prepare supported block for display-case teardown regression");
        BlockState rootState = helper.getLevel().getBlockState(reusedRoot);
        block.onDestroyedByPlayer(rootState, helper.getLevel(), reusedRoot, survival, true,
                rootState.getFluidState());
        check(!helper.getLevel().getBlockState(reusedRoot).is(block)
                        && !helper.getLevel().getBlockState(reusedRoot.above()).is(block),
                "breaking the display case left one of its cells behind");
        check(helper.getLevel().getBlockState(reusedRoot.above(2)).is(Blocks.STONE),
                "breaking the display case unnaturally destroyed the full block above it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void displayCaseGridsRemoveInteriorPartitions(GameTestHelper helper) {
        DisplayCaseBlock block = BlockRegistry.DISPLAY_CASE.get();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));

        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 3; x++) {
                place(helper, block, origin.offset(x, 0, z));
            }
        }

        BlockPos sixCaseInteriorEdge = origin.offset(1, 0, 0);
        check(form(helper, block, sixCaseInteriorEdge) == ConnectionForm.JUNCTION,
                "six-case grid edge did not classify as an open tee");
        var teeShape = helper.getLevel().getBlockState(sixCaseInteriorEdge.above())
                .getCollisionShape(helper.getLevel(), sixCaseInteriorEdge.above());
        check(!teeShape.isEmpty() && teeShape.bounds().maxZ == 1.0D / 16.0D,
                "six-case grid retained an interior partition instead of only its outside wall: "
                        + (teeShape.isEmpty() ? "empty" : teeShape.bounds()));

        for (int x = 0; x < 3; x++) {
            place(helper, block, origin.offset(x, 0, 2));
        }

        BlockPos nineCaseCenter = origin.offset(1, 0, 1);
        check(form(helper, block, nineCaseCenter) == ConnectionForm.JUNCTION,
                "nine-case center did not classify as a four-way interior");
        BlockState centerRoot = helper.getLevel().getBlockState(nineCaseCenter);
        BlockState centerUpper = helper.getLevel().getBlockState(nineCaseCenter.above());
        check(centerUpper.getValue(DisplayCaseBlock.NORTH) == centerRoot.getValue(DisplayCaseBlock.NORTH)
                        && centerUpper.getValue(DisplayCaseBlock.EAST) == centerRoot.getValue(DisplayCaseBlock.EAST)
                        && centerUpper.getValue(DisplayCaseBlock.SOUTH) == centerRoot.getValue(DisplayCaseBlock.SOUTH)
                        && centerUpper.getValue(DisplayCaseBlock.WEST) == centerRoot.getValue(DisplayCaseBlock.WEST),
                "nine-case upper renderer did not mirror all four root connections");
        check(helper.getLevel().getBlockState(nineCaseCenter.above())
                        .getCollisionShape(helper.getLevel(), nineCaseCenter.above()).isEmpty(),
                "nine-case center retained invisible upper partition collision");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void displayCaseCornersCoverAllRotationsWithoutChangingFacing(GameTestHelper helper) {
        DisplayCaseBlock block = BlockRegistry.DISPLAY_CASE.get();
        BlockPos[] centers = {
            helper.absolutePos(new BlockPos(2, 3, 2)),
            helper.absolutePos(new BlockPos(6, 3, 2)),
            helper.absolutePos(new BlockPos(2, 3, 6)),
            helper.absolutePos(new BlockPos(6, 3, 6))
        };
        Direction[][] neighbours = {
            {Direction.SOUTH, Direction.WEST},
            {Direction.NORTH, Direction.WEST},
            {Direction.NORTH, Direction.EAST},
            {Direction.EAST, Direction.SOUTH}
        };
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

        for (int index = 0; index < centers.length; index++) {
            BlockPos center = centers[index];
            place(helper, block, center, facings[index]);
            place(helper, block, center.relative(neighbours[index][0]), facings[index]);
            place(helper, block, center.relative(neighbours[index][1]), facings[index]);
            BlockState state = helper.getLevel().getBlockState(center);
            check(block.connectionForm(state) == ConnectionForm.CORNER,
                    "corner rotation " + index + " did not classify as a corner");
            check(state.getValue(DecorativeMultiblockBlock.FACING) == facings[index],
                    "corner connection changed the player's selected facing");
        }
        helper.succeed();
    }

    private static void place(GameTestHelper helper, DisplayCaseBlock block, BlockPos anchor) {
        place(helper, block, anchor, Direction.NORTH);
    }

    private static void place(
            GameTestHelper helper, DisplayCaseBlock block, BlockPos anchor, Direction facing) {
        block.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell), block.stateFor(facing, cell), flags);
            }
            return null;
        });
        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            helper.getLevel().updateNeighborsAt(block.worldPosition(anchor, facing, cell), block);
        }
    }

    private static ConnectionForm form(GameTestHelper helper, DisplayCaseBlock block, BlockPos anchor) {
        var state = helper.getLevel().getBlockState(anchor);
        check(state.is(block) && block.isRoot(state), "expected a display-case root");
        return block.connectionForm(state);
    }

    private static BlockHitResult centerTopHit(
            GameTestHelper helper, ServerPlayer player, BlockPos root) {
        // Start inside the destination cell. The shared empty template may contain fixture blocks
        // farther overhead, which are irrelevant to the client ray reaching the case.
        Vec3 start = Vec3.atCenterOf(root.above(2)).add(0.0D, 0.49D, 0.0D);
        Vec3 end = Vec3.atCenterOf(root);
        return helper.getLevel().clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static InteractionResult use(
            ServerPlayer player, ItemStack stack, BlockHitResult hit) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

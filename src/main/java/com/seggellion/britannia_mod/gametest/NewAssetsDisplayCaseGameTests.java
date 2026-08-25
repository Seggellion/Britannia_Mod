package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock.ConnectionForm;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
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
    public static void displayCasesConnectWithRootOwnedMerchandiseHosts(GameTestHelper helper) {
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
        check(independentUpperOutline.maxY == 6.0D / 16.0D,
                "upper outline escaped the authored frame height");
        check(independentUpperSupport.maxY == 6.0D / 16.0D,
                "upper helper retained the obsolete Y=16 placement support plate");
        check(!independentUpperState.isFaceSturdy(helper.getLevel(), west.above(), Direction.UP),
                "structural helper still advertised a vanilla block-placement surface");
        check(!independentUpperState.isFaceSturdy(helper.getLevel(), west.above(), Direction.NORTH),
                "structural helper unexpectedly made a cage side sturdy");
        check(helper.getLevel().getBlockState(west).getRenderShape() == RenderShape.MODEL
                        && independentUpperState.getRenderShape() == RenderShape.MODEL,
                "base or upper casing lost its normal baked-model render path");
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
                "baked upper casing did not mirror the root's open shared faces");

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
            check(helper.getLevel().getBlockEntity(anchor) instanceof DisplayCaseBlockEntity
                            && helper.getLevel().getBlockEntity(anchor.above()) == null,
                    "display case did not create exactly one root-owned merchandise host");
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
    public static void displayCaseStoresRootAnchoredMerchandiseWithoutWorldBlocks(GameTestHelper helper) {
        DisplayCaseBlock block = BlockRegistry.DISPLAY_CASE.get();
        BlockPos run = helper.absolutePos(new BlockPos(2, 3, 2));
        for (int x = 0; x < 3; x++) {
            place(helper, block, run.offset(x, 0, 0));
        }

        ServerPlayer survival = helper.makeMockServerPlayerInLevel();
        survival.setGameMode(GameType.SURVIVAL);
        survival.setPos(run.getX() + 5.5D, run.getY(), run.getZ() + 3.5D);

        ItemStack namedStone = new ItemStack(Items.STONE);
        namedStone.set(DataComponents.CUSTOM_NAME, Component.literal("Root anchored stone"));
        ItemStack[] offered = {
            namedStone,
            new ItemStack(ItemRegistry.SMALL_CRATE_ITEM.get()),
            new ItemStack(Items.APPLE)
        };
        ItemStack[] expected = {
            offered[0].copyWithCount(1),
            offered[1].copyWithCount(1),
            offered[2].copyWithCount(1)
        };

        for (int x = 0; x < 3; x++) {
            BlockPos root = run.offset(x, 0, 0);
            BlockHitResult hit = centerTopHit(helper, survival, root);
            check(hit.getBlockPos().equals(root.above()) && hit.getDirection() == Direction.UP,
                    "center ray did not target the helper's root-routed interaction surface: expected="
                            + root.above() + "/UP actual=" + hit.getBlockPos() + "/"
                            + hit.getDirection() + " at " + hit.getLocation());

            ItemInteractionResult result = use(survival, offered[x], hit);
            check(result.consumesAction(), "merchandise interaction was refused: " + result);
            check(offered[x].isEmpty(), "survival display did not consume exactly one item");
            check(helper.getLevel().getBlockEntity(root) instanceof DisplayCaseBlockEntity display
                            && ItemStack.isSameItemSameComponents(display.displayedItem(), expected[x])
                            && display.displayedItem().getCount() == 1,
                    "merchandise was not stored independently at root " + x);
            check(helper.getLevel().getBlockEntity(root.above()) == null,
                    "upper helper incorrectly acquired merchandise state");
            check(helper.getLevel().getBlockState(root).getRenderShape() == RenderShape.MODEL
                            && helper.getLevel().getBlockState(root.above()).getRenderShape()
                                    == RenderShape.MODEL,
                    "merchandise storage displaced the base or baked upper casing model");
            check(helper.getLevel().getBlockState(root.above()).is(block)
                            && helper.getLevel().getBlockState(root.above(2)).isAir(),
                    "merchandise was placed as a real world block above root " + x);

            var physicalCollision = helper.getLevel().getBlockState(root.above())
                    .getCollisionShape(helper.getLevel(), root.above());
            if (!physicalCollision.isEmpty()) {
                check(physicalCollision.bounds().maxY == 6.0D / 16.0D,
                        "merchandise changed the authored upper collision height");
            }
        }

        DisplayCaseBlockEntity persisted = (DisplayCaseBlockEntity) helper.getLevel()
                .getBlockEntity(run);
        var disk = persisted.saveWithoutMetadata(helper.getLevel().registryAccess());
        DisplayCaseBlockEntity diskCopy = new DisplayCaseBlockEntity(
                run, helper.getLevel().getBlockState(run));
        diskCopy.loadWithComponents(disk, helper.getLevel().registryAccess());
        check(ItemStack.isSameItemSameComponents(
                        persisted.displayedItem(), diskCopy.displayedItem()),
                "displayed stack lost components across persistence round trip");
        DisplayCaseBlockEntity clientCopy = new DisplayCaseBlockEntity(
                run, helper.getLevel().getBlockState(run));
        clientCopy.handleUpdateTag(
                persisted.getUpdateTag(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        check(ItemStack.isSameItemSameComponents(
                        persisted.displayedItem(), clientCopy.displayedItem()),
                "client update tag did not carry the authoritative merchandise stack");

        BlockPos normalItemRoot = run.offset(2, 0, 0);
        survival.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult retrieved = helper.getLevel().getBlockState(normalItemRoot.above())
                .useWithoutItem(
                        helper.getLevel(), survival,
                        centerTopHit(helper, survival, normalItemRoot));
        check(retrieved.consumesAction()
                        && playerHas(survival, Items.APPLE)
                        && ((DisplayCaseBlockEntity) helper.getLevel().getBlockEntity(normalItemRoot))
                                .displayedItem().isEmpty(),
                "empty-hand retrieval did not transfer normal-item merchandise exactly once");

        ServerPlayer adventure = helper.makeMockServerPlayerInLevel();
        adventure.setGameMode(GameType.ADVENTURE);
        adventure.setPos(normalItemRoot.getX() + 3.5D, normalItemRoot.getY(), normalItemRoot.getZ() + 3.5D);
        ItemStack deniedStone = new ItemStack(Items.STONE);
        use(adventure, deniedStone, centerTopHit(helper, adventure, normalItemRoot));
        check(deniedStone.getCount() == 1
                        && !((DisplayCaseBlockEntity) helper.getLevel().getBlockEntity(normalItemRoot))
                                .hasDisplayedItem(),
                "display interaction bypassed Adventure placement authorization");

        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.setGameMode(GameType.CREATIVE);
        creative.setPos(normalItemRoot.getX() + 3.5D, normalItemRoot.getY(), normalItemRoot.getZ() + 3.5D);
        ItemStack creativeStone = new ItemStack(Items.STONE);
        ItemInteractionResult creativeResult = use(
                creative, creativeStone, centerTopHit(helper, creative, normalItemRoot));
        check(creativeResult.consumesAction()
                        && ((DisplayCaseBlockEntity) helper.getLevel().getBlockEntity(normalItemRoot))
                                .displayedItem().is(Items.STONE),
                "creative merchandise display failed");
        check(creativeStone.getCount() == 1,
                "creative display unexpectedly consumed the held block");

        BlockState rootState = helper.getLevel().getBlockState(run);
        block.onDestroyedByPlayer(rootState, helper.getLevel(), run, survival, true,
                rootState.getFluidState());
        check(!helper.getLevel().getBlockState(run).is(block)
                        && !helper.getLevel().getBlockState(run.above()).is(block),
                "breaking the display case left one of its cells behind");
        check(helper.getLevel().getBlockState(run.above(2)).isAir(),
                "displayed block unexpectedly materialized during teardown");
        int stoneDrops = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, new AABB(run).inflate(2.0D)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(Items.STONE)
                        && stack.has(DataComponents.CUSTOM_NAME))
                .mapToInt(ItemStack::getCount)
                .sum();
        check(stoneDrops == 1,
                "destroying the root did not drop its component-bearing merchandise exactly once");
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
                        && centerUpper.getValue(DisplayCaseBlock.WEST) == centerRoot.getValue(DisplayCaseBlock.WEST)
                        && centerUpper.getRenderShape() == RenderShape.MODEL,
                "real upper casing state lost mirrored topology or its baked-model render path");
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
            BlockState upper = helper.getLevel().getBlockState(center.above());
            check(upper.getValue(DecorativeMultiblockBlock.FACING) == facings[index]
                            && upper.getValue(DisplayCaseBlock.NORTH) == state.getValue(DisplayCaseBlock.NORTH)
                            && upper.getValue(DisplayCaseBlock.EAST) == state.getValue(DisplayCaseBlock.EAST)
                            && upper.getValue(DisplayCaseBlock.SOUTH) == state.getValue(DisplayCaseBlock.SOUTH)
                            && upper.getValue(DisplayCaseBlock.WEST) == state.getValue(DisplayCaseBlock.WEST),
                    "upper casing lost facing or topology for corner rotation " + index);
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

    private static ItemInteractionResult use(
            ServerPlayer player, ItemStack stack, BlockHitResult hit) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player.level().getBlockState(hit.getBlockPos()).useItemOn(
                stack, player.level(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static boolean playerHas(ServerPlayer player, Item item) {
        return player.getInventory().contains(new ItemStack(item));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

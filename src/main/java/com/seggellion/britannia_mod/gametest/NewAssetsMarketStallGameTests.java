package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.item.DecorativeMultiblockItem;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live server coverage for transactional placement, orientation, obstruction, and single drops. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsMarketStallGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsMarketStallGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void everyColourAndFacingPlacesAtomicallyAndDropsOnce(GameTestHelper helper) {
        List<DecorativeMultiblockBlock> blocks = List.of(
                BlockRegistry.MARKET_STALL_RED.get(), BlockRegistry.MARKET_STALL_BLUE.get(),
                BlockRegistry.MARKET_STALL_GREEN.get(), BlockRegistry.MARKET_STALL_PURPLE.get());
        List<DecorativeMultiblockItem> items = List.of(
                (DecorativeMultiblockItem) ItemRegistry.MARKET_STALL_RED_ITEM.get(),
                (DecorativeMultiblockItem) ItemRegistry.MARKET_STALL_BLUE_ITEM.get(),
                (DecorativeMultiblockItem) ItemRegistry.MARKET_STALL_GREEN_ITEM.get(),
                (DecorativeMultiblockItem) ItemRegistry.MARKET_STALL_PURPLE_ITEM.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        BlockPos minimum = helper.absolutePos(new BlockPos(2, 3, 2));
        player.setPos(minimum.getX() + 0.5D, minimum.getY(), minimum.getZ() + 0.5D);

        for (int color = 0; color < blocks.size(); color++) {
            DecorativeMultiblockBlock block = blocks.get(color);
            DecorativeMultiblockItem item = items.get(color);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos anchor = prepareSupports(helper, block, minimum, facing);
                player.setYRot(facing.getOpposite().toYRot());
                ItemStack stack = new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);

                InteractionResult result = item.useOn(context(player, minimum.below()));
                check(result.consumesAction(), "ordinary item placement failed for " + color + "/" + facing);
                check(stack.isEmpty(), "successful survival placement did not consume exactly one item");
                check(block.cells().size() == 9, "stall did not expose a 3x1x3 nine-cell footprint");
                for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                    BlockPos position = block.worldPosition(anchor, facing, cell);
                    var state = helper.getLevel().getBlockState(position);
                    check(state.is(block), "placement omitted cell " + cell + " for " + facing);
                    check(block.anchorPosition(position, state).equals(anchor),
                            "part did not resolve the root for " + facing);
                }

                List<DecorativeMultiblockBlock.Cell> breakTargets = List.of(
                        cell(block, 0, 0), cell(block, -1, 0),
                        cell(block, 1, 1), cell(block, 0, 2));
                DecorativeMultiblockBlock.Cell breakTarget =
                        breakTargets.get((color * 4 + facing.get2DDataValue()) % breakTargets.size());
                BlockPos breakPosition = block.worldPosition(anchor, facing, breakTarget);
                var breakState = helper.getLevel().getBlockState(breakPosition);
                block.onDestroyedByPlayer(
                        breakState, helper.getLevel(), breakPosition, player,
                        true, breakState.getFluidState());
                for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
                    check(!helper.getLevel().getBlockState(block.worldPosition(anchor, facing, cell)).is(block),
                            "breaking an upper child left an orphan at " + cell);
                }
                List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, new AABB(anchor).inflate(4.0D),
                        entity -> entity.getItem().is(item));
                check(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
                        "teardown did not create exactly one matching stall item for "
                                + color + "/" + facing + "; found=" + drops.size());
                drops.forEach(ItemEntity::discard);
                clearSupports(helper, block, anchor, facing);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockedUpperCellAndUnevenSupportRejectWithoutPartialPlacement(GameTestHelper helper) {
        DecorativeMultiblockBlock block = BlockRegistry.MARKET_STALL_RED.get();
        DecorativeMultiblockItem item = (DecorativeMultiblockItem) ItemRegistry.MARKET_STALL_RED_ITEM.get();
        Direction facing = Direction.NORTH;
        BlockPos minimum = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos anchor = prepareSupports(helper, block, minimum, facing);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.setPos(minimum.getX() + 0.5D, minimum.getY(), minimum.getZ() + 0.5D);
        player.setYRot(Direction.SOUTH.toYRot());

        List<DecorativeMultiblockBlock.Cell> obstructionTargets = List.of(
                cell(block, -1, 0), cell(block, 0, 0), cell(block, 1, 2));
        for (DecorativeMultiblockBlock.Cell obstruction : obstructionTargets) {
            BlockPos blocked = block.worldPosition(anchor, facing, obstruction);
            helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack blockedStack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, blockedStack);
            check(!item.useOn(context(player, minimum.below())).consumesAction(),
                    "placement succeeded through occupied cell " + obstruction);
            check(blockedStack.getCount() == 1 && helper.getLevel().getBlockState(blocked).is(Blocks.STONE),
                    "blocked placement changed the item or obstruction at " + obstruction);
            helper.getLevel().setBlock(blocked, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            assertNoStallCells(helper, block, anchor, facing);
        }

        DecorativeMultiblockBlock.Cell unsupported = block.cells().stream()
                .filter(cell -> cell.x() == -1 && cell.y() == 0).findFirst().orElseThrow();
        helper.getLevel().setBlock(
                block.worldPosition(anchor, facing, unsupported).below(),
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack unevenStack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, unevenStack);
        check(!item.useOn(context(player, minimum.below())).consumesAction(),
                "placement succeeded on uneven/unsupported terrain");
        check(unevenStack.getCount() == 1, "failed uneven placement consumed the stall item");
        assertNoStallCells(helper, block, anchor, facing);
        helper.succeed();
    }

    private static BlockPos prepareSupports(
            GameTestHelper helper, DecorativeMultiblockBlock block, BlockPos minimum, Direction facing) {
        BlockPos anchor = block.anchorForMinimumPosition(minimum, facing);
        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            BlockPos position = block.worldPosition(anchor, facing, cell);
            helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (cell.y() == 0) {
                helper.getLevel().setBlock(position.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return anchor;
    }

    private static void clearSupports(
            GameTestHelper helper, DecorativeMultiblockBlock block, BlockPos anchor, Direction facing) {
        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            if (cell.y() == 0) {
                helper.getLevel().setBlock(
                        block.worldPosition(anchor, facing, cell).below(),
                        Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static UseOnContext context(ServerPlayer player, BlockPos support) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
    }

    private static void assertNoStallCells(
            GameTestHelper helper, DecorativeMultiblockBlock block, BlockPos anchor, Direction facing) {
        for (DecorativeMultiblockBlock.Cell cell : block.cells()) {
            check(!helper.getLevel().getBlockState(block.worldPosition(anchor, facing, cell)).is(block),
                    "failed placement left a partial stall cell at " + cell);
        }
    }

    private static DecorativeMultiblockBlock.Cell cell(
            DecorativeMultiblockBlock block, int x, int y) {
        return block.cells().stream()
                .filter(candidate -> candidate.x() == x && candidate.y() == y && candidate.z() == 0)
                .findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

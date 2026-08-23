package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.LoomBlock;
import com.seggellion.britannia_mod.block.SpinningWheelBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server transaction checks for the Milestone 8 spinning wheel and loom. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsTextileGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsTextileGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void spinningAndWeavingAreExactAndRejectSpidersSilk(GameTestHelper helper) {
        BlockPos wheelPos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(wheelPos, BlockRegistry.SPINNING_WHEEL.get().defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        // Mock players join at the shared world spawn, where every concurrently batched test's
        // players and drops pile up; the yarn count below scans a bubble around this player, so
        // both players must stand inside this test's own structure.
        BlockPos standAt = helper.absolutePos(new BlockPos(2, 2, 3));
        player.setPos(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);

        process(helper, player, wheelPos, new ItemStack(Items.WHITE_WOOL, 2),
                ItemRegistry.BALL_OF_YARN.get(), 1, 1);
        process(helper, player, wheelPos, new ItemStack(ItemRegistry.COTTON.get()),
                ItemRegistry.SPOOL_OF_THREAD.get(), 0, 1);
        process(helper, player, wheelPos, new ItemStack(ItemRegistry.FLAX.get()),
                ItemRegistry.SPOOL_OF_THREAD.get(), 0, 1);
        check(helper.getLevel().getBlockState(wheelPos).getValue(SpinningWheelBlock.ACTIVE),
                "successful spinning did not activate the synchronized visual state");

        player.getInventory().clearContent();
        helper.getLevel().setBlock(
                wheelPos,
                helper.getLevel().getBlockState(wheelPos).setValue(SpinningWheelBlock.ACTIVE, false),
                Block.UPDATE_ALL);
        ItemStack spidersSilk = new ItemStack(ItemRegistry.SPIDERS_SILK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, spidersSilk);
        ItemInteractionResult silkResult = interact(helper, player, wheelPos, spidersSilk);
        check(!silkResult.consumesAction(), "spiders' silk was incorrectly accepted as textile silk");
        check(spidersSilk.getCount() == 1, "rejected spiders' silk was consumed");
        check(count(player, ItemRegistry.SPOOL_OF_THREAD.get()) == 0,
                "rejected spiders' silk produced thread");
        check(!helper.getLevel().getBlockState(wheelPos).getValue(SpinningWheelBlock.ACTIVE),
                "rejected input activated the spinning-wheel visual state");

        LoomBlock loom = BlockRegistry.LOOM.get();
        BlockPos loomAnchor = helper.absolutePos(new BlockPos(5, 3, 5));
        placeLoom(helper, loom, loomAnchor);
        DecorativeMultiblockBlock.Cell child = loom.cells().getLast();
        BlockPos loomChild = loom.worldPosition(loomAnchor, Direction.NORTH, child);

        process(helper, player, loomChild, new ItemStack(ItemRegistry.BALL_OF_YARN.get(), 5),
                ItemRegistry.FOLDED_CLOTH_ITEM.get(), 0, 1);
        process(helper, player, loomChild, new ItemStack(ItemRegistry.SPOOL_OF_THREAD.get(), 5),
                ItemRegistry.FOLDED_CLOTH_ITEM.get(), 0, 1);

        player.getInventory().clearContent();
        ItemStack insufficient = new ItemStack(ItemRegistry.BALL_OF_YARN.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, insufficient);
        ItemInteractionResult insufficientResult = interact(helper, player, loomChild, insufficient);
        check(insufficientResult == ItemInteractionResult.FAIL, "loom did not reject fewer than five inputs");
        check(insufficient.getCount() == 4, "failed loom exchange consumed input");
        check(count(player, ItemRegistry.FOLDED_CLOTH_ITEM.get()) == 0,
                "failed loom exchange produced folded cloth");

        player.getInventory().clearContent();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        ItemStack fullInventoryWool = new ItemStack(Items.WHITE_WOOL, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, fullInventoryWool);
        check(interact(helper, player, wheelPos, fullInventoryWool).consumesAction(),
                "full-inventory spinning exchange was rejected");
        check(fullInventoryWool.getCount() == 1, "full-inventory exchange debited the wrong input count");
        int droppedYarn = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, player.getBoundingBox().inflate(2.0D)).stream()
                .filter(entity -> entity.getItem().is(ItemRegistry.BALL_OF_YARN.get()))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
        check(droppedYarn == 1, "full-inventory exchange did not drop exactly one output");
        check(helper.getLevel().getBlockState(wheelPos).getValue(SpinningWheelBlock.ACTIVE),
                "successful full-inventory spinning did not activate the visual state");

        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        second.setGameMode(GameType.SURVIVAL);
        second.setPos(standAt.getX() + 1.5, standAt.getY(), standAt.getZ() + 0.5);
        ItemStack firstThread = new ItemStack(ItemRegistry.SPOOL_OF_THREAD.get(), 5);
        ItemStack secondThread = new ItemStack(ItemRegistry.SPOOL_OF_THREAD.get(), 5);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, firstThread);
        second.setItemInHand(InteractionHand.MAIN_HAND, secondThread);
        check(interact(helper, player, loomChild, firstThread).consumesAction(),
                "first concurrent loom user was rejected");
        check(interact(helper, second, loomChild, secondThread).consumesAction(),
                "second concurrent loom user was rejected");
        check(count(player, ItemRegistry.FOLDED_CLOTH_ITEM.get()) == 1
                        && count(second, ItemRegistry.FOLDED_CLOTH_ITEM.get()) == 1,
                "two-player loom use lost or duplicated an output");
        helper.runAfterDelay(SpinningWheelBlock.ACTIVE_TICKS + 1, () -> {
            check(!helper.getLevel().getBlockState(wheelPos).getValue(SpinningWheelBlock.ACTIVE),
                    "spinning-wheel visual state did not return to idle after processing");
            helper.succeed();
        });
    }

    private static void process(
            GameTestHelper helper,
            ServerPlayer player,
            BlockPos pos,
            ItemStack input,
            Item output,
            int remainingInput,
            int expectedOutput) {
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, input);
        ItemInteractionResult result = interact(helper, player, pos, input);
        check(result.consumesAction(), "supported textile exchange did not consume the action");
        check(input.getCount() == remainingInput, "textile exchange consumed the wrong input count");
        check(count(player, output) == expectedOutput, "textile exchange produced the wrong output count");
    }

    private static ItemInteractionResult interact(
            GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack input) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return helper.getLevel().getBlockState(pos).useItemOn(
                input, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static void placeLoom(GameTestHelper helper, LoomBlock loom, BlockPos anchor) {
        loom.duringMutation(() -> {
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
            for (DecorativeMultiblockBlock.Cell cell : loom.cells()) {
                helper.getLevel().setBlock(
                        loom.worldPosition(anchor, Direction.NORTH, cell),
                        loom.stateFor(Direction.NORTH, cell), flags);
            }
            return null;
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

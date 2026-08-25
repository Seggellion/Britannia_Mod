package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18MilestoneFourGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    private Patch18MilestoneFourGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void exactDryChainConsumesOnePairPerStepOnTheServer(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper, "patch18-bowl-chain", GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get()));

        InteractionResultHolder<ItemStack> dirtResult = use(player, InteractionHand.MAIN_HAND);
        check(dirtResult.getResult().consumesAction(), "valid dirt preparation did not consume the action");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()),
                "empty bowl did not become bowl_of_dirt");
        check(player.getOffhandItem().isEmpty(), "dirt ingredient was not consumed exactly once");

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DUNG.get()));
        InteractionResultHolder<ItemStack> fertileResult = use(player, InteractionHand.MAIN_HAND);
        check(fertileResult.getResult().consumesAction(), "valid dung preparation did not consume the action");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()),
                "bowl_of_dirt did not become bowl_of_fertile_dirt");
        check(player.getOffhandItem().isEmpty(), "dung ingredient was not consumed exactly once");
        check(player.getInventory().countItem(ItemRegistry.FERTILIZED_DIRT.get()) == 0,
                "M4 enabled the later final fertile-dirt transformation");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void vanillaLegacyWrongAndReversedInputsPassWithoutMutation(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper, "patch18-bowl-invalid", GameType.SURVIVAL);

        assertInvalidPair(player, Items.BOWL, ItemRegistry.DIRT.get(), "vanilla bowl");
        assertInvalidPair(player, ItemRegistry.EMPTY_PEWTER_BOWL.get(), ItemRegistry.DIRT.get(),
                "legacy pewter bowl");
        assertInvalidPair(player, ItemRegistry.EMPTY_BOWL.get(), Items.DIRT, "vanilla dirt item");
        assertInvalidPair(player, ItemRegistry.BOWL_OF_DIRT.get(), ItemRegistry.DIRT.get(),
                "wrong second-step ingredient");

        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.DIRT.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        ItemStack beforeMain = player.getMainHandItem().copy();
        ItemStack beforeOffhand = player.getOffhandItem().copy();
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "reversed main-hand ingredient consumed the action");
        check(!use(player, InteractionHand.OFF_HAND).getResult().consumesAction(),
                "offhand bowl callback consumed the reversed action");
        check(ItemStack.matches(beforeMain, player.getMainHandItem())
                        && ItemStack.matches(beforeOffhand, player.getOffhandItem()),
                "reversed hands mutated an input");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void repeatedPacketsProduceOnlyOneOutputPerPaidPair(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper, "patch18-bowl-repeat", GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get(), 2));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get(), 2));

        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "first packet failed");
        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "second packet failed");
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "unpaid third packet was accepted");
        check(player.getInventory().countItem(ItemRegistry.BOWL_OF_DIRT.get()) == 2,
                "two paid pairs did not produce exactly two bowls of dirt");
        check(player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get()) == 0,
                "repeated packets left an unconsumed empty bowl");
        check(player.getInventory().countItem(ItemRegistry.DIRT.get()) == 0,
                "repeated packets left an unconsumed dirt ingredient");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void fullSurvivalAndCreativeInventoriesDropOneOutputWithoutDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(helper.absolutePos(STAND)).inflate(4.0D);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        ServerPlayer survival = preparedFullPlayer(helper, "patch18-bowl-full-survival", GameType.SURVIVAL);
        check(use(survival, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "full Survival inventory rejected a paid pair");
        assertFullInventoryResult(level, survival, area, 1);
        disconnect(survival);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        ServerPlayer creative = preparedFullPlayer(helper, "patch18-bowl-full-creative", GameType.CREATIVE);
        check(use(creative, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "full Creative inventory rejected a paid pair");
        assertFullInventoryResult(level, creative, area, 1);
        disconnect(creative);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void twoPlayersPrepareAgainstIndependentInventories(GameTestHelper helper) {
        ServerPlayer first = preparedPlayer(helper, "patch18-bowl-first", GameType.SURVIVAL);
        ServerPlayer second = preparedPlayer(helper, "patch18-bowl-second", GameType.SURVIVAL);
        preparePair(first, ItemRegistry.EMPTY_BOWL.get(), ItemRegistry.DIRT.get());
        preparePair(second, ItemRegistry.EMPTY_BOWL.get(), ItemRegistry.DIRT.get());

        check(use(first, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "first player's server transaction failed");
        check(use(second, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "second player's server transaction failed");
        check(first.getInventory().countItem(ItemRegistry.BOWL_OF_DIRT.get()) == 1,
                "first player did not receive exactly one private output");
        check(second.getInventory().countItem(ItemRegistry.BOWL_OF_DIRT.get()) == 1,
                "second player did not receive exactly one private output");
        disconnect(first);
        disconnect(second);
        helper.succeed();
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        player.setGameMode(mode);
        player.getInventory().clearContent();
        BlockPos stand = helper.absolutePos(STAND);
        player.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        return player;
    }

    private static ServerPlayer preparedFullPlayer(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = preparedPlayer(helper, name, mode);
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get(), 2));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get(), 2));
        return player;
    }

    private static void assertFullInventoryResult(
            ServerLevel level,
            ServerPlayer player,
            AABB area,
            int expectedDropped
    ) {
        check(player.getMainHandItem().is(ItemRegistry.EMPTY_BOWL.get())
                        && player.getMainHandItem().getCount() == 1,
                "full inventory did not debit one main-hand bowl");
        check(player.getOffhandItem().is(ItemRegistry.DIRT.get())
                        && player.getOffhandItem().getCount() == 1,
                "full inventory did not debit one offhand dirt");
        check(player.getInventory().countItem(ItemRegistry.BOWL_OF_DIRT.get()) == 0,
                "full inventory unexpectedly inserted the output");
        int dropped = level.getEntitiesOfClass(ItemEntity.class, area).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemRegistry.BOWL_OF_DIRT.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        check(dropped == expectedDropped,
                "full inventory dropped " + dropped + " bowl outputs instead of " + expectedDropped);
    }

    private static void assertInvalidPair(ServerPlayer player, Item main, Item offhand, String label) {
        player.getInventory().clearContent();
        preparePair(player, main, offhand);
        ItemStack beforeMain = player.getMainHandItem().copy();
        ItemStack beforeOffhand = player.getOffhandItem().copy();
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                label + " was incorrectly accepted");
        check(ItemStack.matches(beforeMain, player.getMainHandItem())
                        && ItemStack.matches(beforeOffhand, player.getOffhandItem()),
                label + " mutated an input");
    }

    private static void preparePair(ServerPlayer player, Item main, Item offhand) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(offhand));
    }

    private static InteractionResultHolder<ItemStack> use(ServerPlayer player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem().use(player.level(), player, hand);
    }

    private static void disconnect(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

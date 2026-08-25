package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class FertileDirtFinalMixGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 5);
    private static final BlockPos SOURCE = new BlockPos(2, 2, 2);

    private FertileDirtFinalMixGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onePairCreatesCanonicalFertilizedDirtAndReturnsTwoCustomBowls(
            GameTestHelper helper
    ) {
        ServerPlayer player = preparedPlayer(helper, "patch18-final-one", GameType.SURVIVAL);
        prepareFinalPair(player, 1);

        InteractionResultHolder<ItemStack> result = use(player, InteractionHand.MAIN_HAND);

        check(result.getResult().consumesAction(), "valid final mix did not consume the action");
        check(player.getMainHandItem().is(ItemRegistry.FERTILIZED_DIRT.get())
                        && player.getMainHandItem().getCount() == 1,
                "final mix did not place the canonical fertilized_dirt in the main hand");
        check(player.getOffhandItem().is(ItemRegistry.EMPTY_BOWL.get())
                        && player.getOffhandItem().getCount() == 2,
                "final mix did not conserve both custom bowls in the offhand");
        check(player.getInventory().countItem(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()) == 0,
                "final mix left a fertile-dirt bowl input");
        check(player.getInventory().countItem(ItemRegistry.BOWL_OF_WATER.get()) == 0,
                "final mix left a water-bowl input");
        check(player.getInventory().countItem(Items.BOWL) == 0,
                "final mix returned a vanilla bowl");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void repeatedPacketsProduceOneExactOutputSetPerPaidPair(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper, "patch18-final-repeat", GameType.SURVIVAL);
        prepareFinalPair(player, 2);

        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "first paid final mix failed");
        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "second paid final mix failed");
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "unpaid third final mix was accepted");
        check(player.getInventory().countItem(ItemRegistry.FERTILIZED_DIRT.get()) == 2,
                "two paid pairs did not produce exactly two canonical outputs");
        check(player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get()) == 4,
                "two paid pairs did not return exactly four custom bowls");
        check(player.getInventory().countItem(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()) == 0
                        && player.getInventory().countItem(ItemRegistry.BOWL_OF_WATER.get()) == 0,
                "repeated mixing left a paid input behind");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void invalidOffhandAndReversedHandsPassWithoutMutation(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper, "patch18-final-invalid", GameType.SURVIVAL);

        assertInvalidPair(player, ItemRegistry.BOWL_OF_FERTILE_DIRT.get(), Items.WATER_BUCKET,
                "vanilla water bucket");
        assertInvalidPair(player, ItemRegistry.BOWL_OF_FERTILE_DIRT.get(),
                ItemRegistry.EMPTY_PEWTER_BOWL.get(), "legacy pewter bowl");
        assertInvalidPair(player, ItemRegistry.BOWL_OF_FERTILE_DIRT.get(),
                ItemRegistry.EMPTY_BOWL.get(), "unfilled custom bowl");

        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ItemRegistry.BOWL_OF_WATER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()));
        ItemStack beforeMain = player.getMainHandItem().copy();
        ItemStack beforeOffhand = player.getOffhandItem().copy();
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "reversed main-hand water bowl was accepted");
        check(!use(player, InteractionHand.OFF_HAND).getResult().consumesAction(),
                "offhand fertile bowl callback accepted reversed hands");
        check(ItemStack.matches(beforeMain, player.getMainHandItem())
                        && ItemStack.matches(beforeOffhand, player.getOffhandItem()),
                "reversed hands mutated an input");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void nearFullInventoryUsesOneSlotAndTheFreedOffhandWithoutDropping(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = preparedPlayer(helper, "patch18-final-near-full", GameType.SURVIVAL);
        for (int slot = 0; slot < 35; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ItemRegistry.BOWL_OF_FERTILE_DIRT.get(), 2));
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ItemRegistry.BOWL_OF_WATER.get()));
        AABB area = new AABB(player.position(), player.position()).inflate(4.0D);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "near-full final mix failed");

        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_FERTILE_DIRT.get())
                        && player.getMainHandItem().getCount() == 1,
                "near-full mix did not debit one main-hand input");
        check(player.getOffhandItem().is(ItemRegistry.EMPTY_BOWL.get())
                        && player.getOffhandItem().getCount() == 2,
                "near-full mix did not return both bowls to the freed offhand");
        check(player.getInventory().countItem(ItemRegistry.FERTILIZED_DIRT.get()) == 1,
                "near-full mix did not insert the canonical output into the free slot");
        check(countDropped(level, area, ItemRegistry.FERTILIZED_DIRT.get()) == 0
                        && countDropped(level, area, ItemRegistry.EMPTY_BOWL.get()) == 0,
                "near-full mix dropped an output despite sufficient capacity");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void fullSurvivalAndCreativeInventoriesDropBothExactOutputs(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(helper.absolutePos(STAND)).inflate(5.0D);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        ServerPlayer survival = preparedFullPlayer(
                helper, "patch18-final-full-survival", GameType.SURVIVAL);
        check(use(survival, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "full Survival final mix failed");
        assertFullResult(level, survival, area);
        disconnect(survival);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        ServerPlayer creative = preparedFullPlayer(
                helper, "patch18-final-full-creative", GameType.CREATIVE);
        check(use(creative, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "full Creative final mix failed");
        assertFullResult(level, creative, area);
        disconnect(creative);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void twoPlayersMixAgainstIndependentInventories(GameTestHelper helper) {
        ServerPlayer first = preparedPlayer(helper, "patch18-final-first", GameType.SURVIVAL);
        ServerPlayer second = preparedPlayer(helper, "patch18-final-second", GameType.SURVIVAL);
        prepareFinalPair(first, 1);
        prepareFinalPair(second, 1);

        check(use(first, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "first player's final mix failed");
        check(use(second, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "second player's final mix failed");
        assertSinglePlayerResult(first, "first");
        assertSinglePlayerResult(second, "second");
        disconnect(first);
        disconnect(second);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void playerCanCompleteTheEntireBowlPreparationLoop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(SOURCE);
        level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = preparedPlayer(helper, "patch18-final-end-to-end", GameType.ADVENTURE);

        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ItemRegistry.DIRT.get()));
        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "end-to-end dirt bowl step failed");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()),
                "end-to-end dirt bowl step produced the wrong item");

        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ItemRegistry.DUNG.get()));
        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "end-to-end dung mix step failed");
        ItemStack fertileBowl = player.getMainHandItem().copy();
        check(fertileBowl.is(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()),
                "end-to-end dung mix produced the wrong item");

        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(source));
        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "end-to-end water bowl step failed");
        ItemStack waterBowl = player.getMainHandItem().copy();
        check(waterBowl.is(ItemRegistry.BOWL_OF_WATER.get()),
                "end-to-end water step produced the wrong item");
        check(level.getFluidState(source).is(FluidTags.WATER)
                        && level.getFluidState(source).isSource(),
                "end-to-end water step consumed the source");

        player.setItemInHand(InteractionHand.MAIN_HAND, fertileBowl);
        player.setItemInHand(InteractionHand.OFF_HAND, waterBowl);
        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "end-to-end final mix failed");
        assertSinglePlayerResult(player, "end-to-end");
        disconnect(player);
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

    private static ServerPlayer preparedFullPlayer(
            GameTestHelper helper,
            String name,
            GameType mode
    ) {
        ServerPlayer player = preparedPlayer(helper, name, mode);
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        prepareFinalPair(player, 2);
        return player;
    }

    private static void prepareFinalPair(ServerPlayer player, int count) {
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ItemRegistry.BOWL_OF_FERTILE_DIRT.get(), count));
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ItemRegistry.BOWL_OF_WATER.get(), count));
    }

    private static void assertInvalidPair(ServerPlayer player, Item main, Item offhand, String label) {
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(offhand));
        ItemStack beforeMain = player.getMainHandItem().copy();
        ItemStack beforeOffhand = player.getOffhandItem().copy();
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                label + " was incorrectly accepted");
        check(ItemStack.matches(beforeMain, player.getMainHandItem())
                        && ItemStack.matches(beforeOffhand, player.getOffhandItem()),
                label + " mutated an input");
    }

    private static void assertSinglePlayerResult(ServerPlayer player, String label) {
        check(player.getInventory().countItem(ItemRegistry.FERTILIZED_DIRT.get()) == 1,
                label + " player did not receive exactly one canonical output");
        check(player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get()) == 2,
                label + " player did not receive exactly two returned custom bowls");
        check(player.getInventory().countItem(Items.BOWL) == 0,
                label + " player received a vanilla bowl");
    }

    private static void assertFullResult(
            ServerLevel level,
            ServerPlayer player,
            AABB area
    ) {
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_FERTILE_DIRT.get())
                        && player.getMainHandItem().getCount() == 1,
                "full inventory did not debit one main-hand fertile bowl");
        check(player.getOffhandItem().is(ItemRegistry.BOWL_OF_WATER.get())
                        && player.getOffhandItem().getCount() == 1,
                "full inventory did not debit one offhand water bowl");
        check(player.getInventory().countItem(ItemRegistry.FERTILIZED_DIRT.get()) == 0
                        && player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get()) == 0,
                "full inventory unexpectedly inserted an output");
        check(countDropped(level, area, ItemRegistry.FERTILIZED_DIRT.get()) == 1,
                "full inventory did not drop exactly one canonical output");
        check(countDropped(level, area, ItemRegistry.EMPTY_BOWL.get()) == 2,
                "full inventory did not drop exactly two returned bowls");
        check(countDropped(level, area, Items.BOWL) == 0,
                "full inventory dropped a vanilla bowl");
    }

    private static int countDropped(ServerLevel level, AABB area, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, area).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
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

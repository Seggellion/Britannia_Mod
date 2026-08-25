package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.WaterWellBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18MilestoneFiveGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos SOURCE = new BlockPos(2, 2, 2);
    private static final BlockPos STAND = new BlockPos(2, 2, 5);

    private Patch18MilestoneFiveGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void exactSourceWaterWinsOverDryDirtAndRemainsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(SOURCE);
        level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        BlockState before = level.getBlockState(source);
        ServerPlayer player = preparedPlayer(helper, "patch18-water-source", GameType.ADVENTURE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get()));
        aimAt(player, source);

        InteractionResultHolder<ItemStack> result = use(player, InteractionHand.MAIN_HAND);

        check(result.getResult().consumesAction(), "source-water bowl fill did not consume the action");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_WATER.get()),
                "source water did not create bowl_of_water");
        check(player.getOffhandItem().is(ItemRegistry.DIRT.get())
                        && player.getOffhandItem().getCount() == 1,
                "source targeting fell through to the dry dirt recipe");
        check(level.getBlockState(source).equals(before), "source water block state changed");
        check(level.getFluidState(source).is(FluidTags.WATER)
                        && level.getFluidState(source).isSource(),
                "source water was consumed or downgraded");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void flowingAndNonWaterTargetsDoNotFillAnyBowl(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(SOURCE);
        ServerPlayer player = preparedPlayer(helper, "patch18-water-invalid", GameType.SURVIVAL);

        level.setBlock(target,
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1),
                Block.UPDATE_ALL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        aimAt(player, target);
        ItemStack flowingBefore = player.getMainHandItem().copy();
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "flowing water was accepted as a source");
        check(ItemStack.matches(flowingBefore, player.getMainHandItem()),
                "flowing water mutated the bowl");

        level.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        aimAt(player, target);
        ItemStack stoneBefore = player.getMainHandItem().copy();
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "non-water target was accepted");
        check(ItemStack.matches(stoneBefore, player.getMainHandItem()),
                "non-water target mutated the bowl");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
        level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        aimAt(player, target);
        check(!use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "vanilla bowl was accepted by the custom water path");
        check(player.getMainHandItem().is(Items.BOWL), "vanilla bowl was mutated");
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void adventureCanFillAtWellRootAndChildWithoutMutatingWell(GameTestHelper helper) {
        WaterWellBlock well = BlockRegistry.WATER_WELL.get();
        Direction facing = Direction.NORTH;
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        placeWell(helper.getLevel(), well, anchor, facing);
        ServerPlayer player = preparedPlayer(helper, "patch18-water-well", GameType.ADVENTURE);

        DecorativeMultiblockBlock.Cell root = well.cells().stream()
                .filter(cell -> well.isRoot(well.stateFor(facing, cell)))
                .findFirst()
                .orElseThrow();
        DecorativeMultiblockBlock.Cell child = well.cells().stream()
                .filter(cell -> !well.isRoot(well.stateFor(facing, cell)))
                .findFirst()
                .orElseThrow();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        ItemInteractionResult rootResult = clickWell(
                helper.getLevel(), well.worldPosition(anchor, facing, root), player,
                InteractionHand.MAIN_HAND);
        check(rootResult.consumesAction(), "well root did not accept the custom empty bowl");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_WATER.get()),
                "well root did not fill the bowl");
        assertWellUnchanged(helper.getLevel(), well, anchor, facing);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        ItemInteractionResult childResult = clickWell(
                helper.getLevel(), well.worldPosition(anchor, facing, child), player,
                InteractionHand.OFF_HAND);
        check(childResult.consumesAction(), "well child did not accept an offhand custom bowl");
        check(player.getOffhandItem().is(ItemRegistry.BOWL_OF_WATER.get()),
                "well child did not fill the offhand bowl");
        assertWellUnchanged(helper.getLevel(), well, anchor, facing);
        disconnect(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void fullCreativeInventoryConsumesOneBowlAndDropsOneOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(SOURCE);
        level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        BlockState before = level.getBlockState(source);
        ServerPlayer player = preparedPlayer(helper, "patch18-water-creative", GameType.CREATIVE);
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get(), 2));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get(), 2));
        aimAt(player, source);
        AABB area = new AABB(player.position(), player.position()).inflate(4.0D);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        check(use(player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "full Creative water fill did not consume the action");

        check(player.getMainHandItem().is(ItemRegistry.EMPTY_BOWL.get())
                        && player.getMainHandItem().getCount() == 1,
                "Creative did not debit exactly one empty bowl");
        check(player.getOffhandItem().is(ItemRegistry.DIRT.get())
                        && player.getOffhandItem().getCount() == 2,
                "Creative source targeting consumed the dry offhand input");
        check(player.getInventory().countItem(ItemRegistry.BOWL_OF_WATER.get()) == 0,
                "full Creative inventory unexpectedly inserted the output");
        int dropped = level.getEntitiesOfClass(ItemEntity.class, area).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemRegistry.BOWL_OF_WATER.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        check(dropped == 1, "full Creative inventory dropped " + dropped + " outputs instead of one");
        check(level.getBlockState(source).equals(before), "Creative fill changed source water");
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

    private static void aimAt(ServerPlayer player, BlockPos target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
    }

    private static InteractionResultHolder<ItemStack> use(ServerPlayer player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem().use(player.level(), player, hand);
    }

    private static void placeWell(
            ServerLevel level,
            WaterWellBlock well,
            BlockPos anchor,
            Direction facing
    ) {
        well.duringMutation(() -> {
            for (DecorativeMultiblockBlock.Cell cell : well.cells()) {
                level.setBlock(
                        well.worldPosition(anchor, facing, cell),
                        well.stateFor(facing, cell),
                        Block.UPDATE_ALL);
            }
            return null;
        });
    }

    private static ItemInteractionResult clickWell(
            ServerLevel level,
            BlockPos position,
            ServerPlayer player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(position), Direction.UP, position, false);
        return level.getBlockState(position).useItemOn(stack, level, player, hand, hit);
    }

    private static void assertWellUnchanged(
            ServerLevel level,
            WaterWellBlock well,
            BlockPos anchor,
            Direction facing
    ) {
        for (DecorativeMultiblockBlock.Cell cell : well.cells()) {
            BlockPos position = well.worldPosition(anchor, facing, cell);
            check(level.getBlockState(position).equals(well.stateFor(facing, cell)),
                    "well cell changed at " + position);
        }
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

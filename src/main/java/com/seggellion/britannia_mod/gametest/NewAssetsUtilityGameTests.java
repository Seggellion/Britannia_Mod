package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.item.AdventureLadderItem;
import com.seggellion.britannia_mod.item.AdventureScarecrowItem;
import com.seggellion.britannia_mod.block.HedgeBushBlock;
import com.seggellion.britannia_mod.item.PitcherItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.WaterSourceInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server behavior checks for the Milestone 5 well and Adventure ladder. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsUtilityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsUtilityGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void waterSourceFillsWateringCanPitcherAndBucket(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));

        ItemStack wateringCan = new ItemStack(ItemRegistry.WATERING_CAN.get());
        WateringCanItem.setWaterCharges(wateringCan, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, wateringCan);
        WaterSourceInteraction.fillFromSource(
                helper.getLevel(), source, player, InteractionHand.MAIN_HAND, wateringCan);
        check(WateringCanItem.getWaterCharges(wateringCan) == WateringCanItem.MAX_WATER_CHARGES,
                "well did not fully refill the watering can");

        ItemStack pitcher = new ItemStack(ItemRegistry.PITCHER_EMPTY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pitcher);
        WaterSourceInteraction.fillFromSource(
                helper.getLevel(), source, player, InteractionHand.MAIN_HAND, pitcher);
        check(((PitcherItem) pitcher.getItem()).isFilled(pitcher),
                "well did not mark the registered empty pitcher as water-filled");

        ItemStack bucket = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        WaterSourceInteraction.fillFromSource(
                helper.getLevel(), source, player, InteractionHand.MAIN_HAND, bucket);
        check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "well did not convert an empty vanilla bucket into a water bucket");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void adventurePlayerPlacesClimbsAndBreaksAtomicLadder(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.ADVENTURE);
        player.setPos(support.getX() + 0.5D, support.getY() + 1.0D, support.getZ() + 2.5D);
        ItemStack ladderStack = new ItemStack(ItemRegistry.LADDER_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ladderStack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0D, 0.5D, 0.0D),
                net.minecraft.core.Direction.UP, support, false);
        InteractionResult placementResult = ((AdventureLadderItem) ladderStack.getItem()).useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        check(placementResult.consumesAction(),
                "Adventure ladder item rejected upward placement: " + placementResult);

        DecorativeMultiblockBlock ladder = BlockRegistry.LADDER.get();
        BlockPos anchor = support.above();
        for (int y = 0; y < 3; y++) {
            var state = helper.getLevel().getBlockState(anchor.above(y));
            check(state.is(ladder), "Adventure placement did not create all three ladder cells");
            check(state.is(BlockTags.CLIMBABLE), "ladder cell is not in the vanilla climbable tag");
        }

        BlockPos top = anchor.above(2);
        var topState = helper.getLevel().getBlockState(top);
        check(topState.getCollisionShape(helper.getLevel(), top).toAabbs().stream()
                        .anyMatch(box -> box.maxY == 1.0D && box.getXsize() >= 0.75D),
                "ladder top does not provide a standing platform at the third-block height");
        ladder.onDestroyedByPlayer(
                topState, helper.getLevel(), top, player, true, topState.getFluidState());
        for (int y = 0; y < 3; y++) {
            check(!helper.getLevel().getBlockState(anchor.above(y)).is(ladder),
                    "breaking the ladder top left an orphaned cell");
        }

        int ladderDrops = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(anchor).inflate(3.0D))) {
            if (entity.getItem().is(ItemRegistry.LADDER_ITEM.get())) {
                ladderDrops += entity.getItem().getCount();
            }
        }
        check(ladderDrops == 1, "ladder did not drop exactly one item after whole teardown");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void hedgeStacksAndAdventureScarecrowUsesCommunityFarm(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);

        BlockPos hedgeSoil = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(hedgeSoil, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack hedgeStack = new ItemStack(ItemRegistry.HEDGE_BUSH_ITEM.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, hedgeStack);
        BlockPos clicked = hedgeSoil;
        for (int index = 0; index < 3; index++) {
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(clicked).add(0.0D, 0.5D, 0.0D),
                    net.minecraft.core.Direction.UP, clicked, false);
            InteractionResult result = ((BlockItem) hedgeStack.getItem()).useOn(
                    new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            check(result.consumesAction(), "hedge segment " + index + " failed to stack");
            clicked = clicked.above();
        }
        BlockPos hedgeBottom = hedgeSoil.above();
        check(helper.getLevel().getBlockState(hedgeBottom).getValue(HedgeBushBlock.SEGMENT)
                        == HedgeBushBlock.BOTTOM,
                "hedge bottom segment did not resolve");
        check(helper.getLevel().getBlockState(hedgeBottom.above()).getValue(HedgeBushBlock.SEGMENT)
                        == HedgeBushBlock.MIDDLE,
                "hedge middle segment did not resolve");
        check(helper.getLevel().getBlockState(hedgeBottom.above(2)).getValue(HedgeBushBlock.SEGMENT)
                        == HedgeBushBlock.TOP,
                "hedge top segment did not resolve");

        player.setGameMode(GameType.ADVENTURE);
        BlockPos community = helper.absolutePos(new BlockPos(5, 2, 5));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlock(community.offset(x, 0, z),
                        BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        ItemStack scarecrow = new ItemStack(ItemRegistry.SCARECROW_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, scarecrow);
        BlockHitResult farmHit = new BlockHitResult(
                Vec3.atCenterOf(community).add(0.0D, 0.5D, 0.0D),
                net.minecraft.core.Direction.UP, community, false);
        InteractionResult scarecrowResult = ((AdventureScarecrowItem) scarecrow.getItem()).useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, farmHit));
        check(scarecrowResult.consumesAction(),
                "Adventure scarecrow was rejected on community farm blocks");
        long scarecrowCells = BlockPos.betweenClosedStream(
                        community.offset(-1, 1, -1), community.offset(1, 2, 1))
                .filter(pos -> helper.getLevel().getBlockState(pos).is(BlockRegistry.SCARECROW.get()))
                .count();
        check(scarecrowCells == 4, "Adventure scarecrow did not place all four structure cells");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

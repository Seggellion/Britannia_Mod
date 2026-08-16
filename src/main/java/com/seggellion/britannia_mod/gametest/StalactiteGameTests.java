package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.StalactiteBlock;
import com.seggellion.britannia_mod.block.TallDecorativeBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.CreativeTabRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
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

import java.util.List;

/** Placement, support, registration, parity, and stalagmite-regression checks. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StalactiteGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private StalactiteGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void allVariantsRegisterInParityAndMirrorTheStalagmiteShape(GameTestHelper helper) {
        check(BlockRegistry.STALAGMITES.size() == 7, "stalagmite registry parity baseline changed");
        check(BlockRegistry.STALACTITES.size() == 7, "not all seven stalactites registered");
        check(ItemRegistry.STALACTITE_ITEMS.size() == 7, "not all seven stalactite items registered");

        CreativeModeTab tab = CreativeTabRegistry.CREATIVE_DECOR_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));

        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        for (int index = 1; index <= 7; index++) {
            TallDecorativeBlock stalagmite = BlockRegistry.STALAGMITES.get(index).get();
            StalactiteBlock stalactite = BlockRegistry.STALACTITES.get(index).get();
            BlockItem item = ItemRegistry.STALACTITE_ITEMS.get(index).get();
            check(item.getBlock() == stalactite, "stalactite_" + index + " item targets the wrong block");
            check(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                    "stalactite_" + index + " is missing from the Decorative tab");

            List<AABB> floorBoxes = stalagmite.defaultBlockState()
                    .getShape(helper.getLevel(), pos).toAabbs();
            List<AABB> ceilingBoxes = stalactite.defaultBlockState()
                    .getShape(helper.getLevel(), pos).toAabbs();
            check(floorBoxes.size() == ceilingBoxes.size(),
                    "stalactite_" + index + " shape component count differs");
            for (int boxIndex = 0; boxIndex < floorBoxes.size(); boxIndex++) {
                AABB floor = floorBoxes.get(boxIndex);
                AABB ceiling = ceilingBoxes.get(boxIndex);
                check(close(ceiling.minX, floor.minX) && close(ceiling.maxX, floor.maxX)
                                && close(ceiling.minZ, floor.minZ) && close(ceiling.maxZ, floor.maxZ)
                                && close(ceiling.minY, 1.0D - floor.maxY)
                                && close(ceiling.maxY, 1.0D - floor.minY),
                        "stalactite_" + index + " shape is not the vertical inverse of stalagmite_"
                                + index);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void undersideClickPlacesDirectlyBelowTheCeiling(GameTestHelper helper) {
        BlockPos ceiling = helper.absolutePos(new BlockPos(3, 5, 3));
        BlockPos expected = ceiling.below();
        helper.getLevel().setBlock(ceiling, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(ceiling.getX() + 2.5D, ceiling.getY() - 2.0D, ceiling.getZ() + 2.5D);
        ItemStack stack = new ItemStack(ItemRegistry.STALACTITE_ITEMS.get(1).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, undersideOf(ceiling)));

        check(result.consumesAction(), "underside placement was rejected: " + result);
        check(helper.getLevel().getBlockState(ceiling).is(Blocks.STONE),
                "placing the stalactite replaced its ceiling support");
        check(helper.getLevel().getBlockState(expected).is(BlockRegistry.STALACTITES.get(1).get()),
                "stalactite was not placed in the block immediately below the ceiling");
        check(helper.getLevel().getBlockState(expected.below()).isAir(),
                "stalactite block state was placed one block too low");
        check(helper.getLevel().getBlockState(expected).canSurvive(helper.getLevel(), expected),
                "placed stalactite does not recognize its ceiling support");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unsupportedPlacementFailsAndRemovingSupportClearsTheStalactite(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 4, 3));
        StalactiteBlock block = BlockRegistry.STALACTITES.get(1).get();
        check(!block.defaultBlockState().canSurvive(helper.getLevel(), pos),
                "unsupported stalactite incorrectly survives");

        helper.getLevel().setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        check(helper.getLevel().getBlockState(pos).is(block), "supported stalactite did not remain placed");

        helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check(helper.getLevel().getBlockState(pos).isAir(),
                "stalactite remained after its ceiling support was removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void existingStalagmiteStillPlacesAboveAFloorAndPointsUpward(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos expected = floor.above();
        helper.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(floor.getX() + 2.5D, floor.getY() + 1.0D, floor.getZ() + 2.5D);
        ItemStack stack = new ItemStack(ItemRegistry.STALAGMITE_ITEMS.get(1).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0.0D, 0.5D, 0.0D),
                        Direction.UP, floor, false)));

        check(result.consumesAction(), "existing stalagmite placement regressed: " + result);
        check(helper.getLevel().getBlockState(expected).is(BlockRegistry.STALAGMITES.get(1).get()),
                "existing stalagmite no longer lands directly above the floor");
        AABB shape = helper.getLevel().getBlockState(expected)
                .getShape(helper.getLevel(), expected).bounds();
        check(close(shape.minY, 0.0D) && close(shape.maxY, 2.0D),
                "existing stalagmite shape/orientation changed");
        helper.succeed();
    }

    private static BlockHitResult undersideOf(BlockPos ceiling) {
        return new BlockHitResult(
                Vec3.atCenterOf(ceiling).add(0.0D, -0.5D, 0.0D),
                Direction.DOWN,
                ceiling,
                false);
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < 1.0E-9D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

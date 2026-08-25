package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.OrangeFruitBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropHarvestTool;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FruitTreeDefinition;
import com.seggellion.britannia_mod.farming.OrangeTreeStructurePlanner;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class FertileDirtLifecycleGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos PLOT = new BlockPos(2, 1, 2);

    private FertileDirtLifecycleGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void canonicalApplicationCountsFiveSuccessfulHarvestsThenReturnsToDirt(
            GameTestHelper helper
    ) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        applyCanonicalFertilizedDirt(helper, player, PLOT);
        assertRemaining(helper, PLOT, 5);

        CropDefinition annual = handHarvestAnnual();
        for (int expected = 4; expected >= 0; expected--) {
            mature(helper, PLOT, annual);
            harvest(helper, player, PLOT, ItemStack.EMPTY);
            if (expected > 0) {
                assertRemaining(helper, PLOT, expected);
            } else {
                check(helper.getBlockState(PLOT).is(Blocks.DIRT),
                        "fifth successful harvest did not exhaust ordinary soil to dirt");
                check(helper.getLevel().getBlockEntity(helper.absolutePos(PLOT)) == null,
                        "exhausted ordinary soil retained its farming block entity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void plantingFailedHarvestAndUnrelatedInteractionDoNotSpendFertility(
            GameTestHelper helper
    ) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        applyCanonicalFertilizedDirt(helper, player, PLOT);
        CropDefinition annual = handHarvestAnnual();
        FarmingBlockEntity soil = farm(helper, PLOT);

        soil.plant(annual);
        setHasSeeds(helper, PLOT, true);
        assertRemaining(helper, PLOT, 5);

        ItemInteractionResult failed = harvest(helper, player, PLOT, ItemStack.EMPTY);
        check(failed == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "immature crop was reported as a successful harvest");
        assertRemaining(helper, PLOT, 5);

        BlockPos unrelated = PLOT.offset(2, 0, 0);
        helper.setBlock(unrelated, Blocks.STONE.defaultBlockState());
        BlockPos absolute = helper.absolutePos(unrelated);
        helper.getBlockState(unrelated).useWithoutItem(
                helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        assertRemaining(helper, PLOT, 5);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void persistedReloadAndSecondPlayerCannotDoubleSpendOneHarvest(
            GameTestHelper helper
    ) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        applyCanonicalFertilizedDirt(helper, first, PLOT);
        mature(helper, PLOT, handHarvestAnnual());

        harvest(helper, first, PLOT, ItemStack.EMPTY);
        harvest(helper, second, PLOT, ItemStack.EMPTY);
        assertRemaining(helper, PLOT, 4);

        ServerLevel level = helper.getLevel();
        BlockPos absolutePlot = helper.absolutePos(PLOT);
        FarmingBlockEntity current = farm(helper, PLOT);
        CompoundTag saved = current.saveWithoutMetadata(level.registryAccess());
        BlockState state = level.getBlockState(absolutePlot);
        level.removeBlockEntity(absolutePlot);
        FarmingBlockEntity reloaded = new FarmingBlockEntity(absolutePlot, state);
        reloaded.loadWithComponents(saved, level.registryAccess());
        level.setBlockEntity(reloaded);

        assertRemaining(helper, PLOT, 4);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void perennialRegrowthAndCommunityReuseRemainIntact(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        applyCanonicalFertilizedDirt(helper, player, PLOT);
        helper.setBlock(PLOT.above(), BlockRegistry.TRELLIS_BLOCK.get().defaultBlockState());
        CropDefinition tomato = CropRegistry.byId("tomato").orElseThrow();
        mature(helper, PLOT, tomato);
        harvest(helper, player, PLOT, new ItemStack(ItemRegistry.SCISSORS.get()));
        FarmingBlockEntity perennial = farm(helper, PLOT);
        check(perennial.hasCrop() && "tomato".equals(perennial.getPlantedCropId()),
                "successful perennial harvest cleared the crop");
        check(!perennial.isMature()
                        && perennial.getGrowthStage() == tomato.clampedPostHarvestRegrowthAge(),
                "successful perennial harvest changed its established regrowth age");
        assertRemaining(helper, PLOT, 4);

        BlockPos community = PLOT.offset(4, 0, 0);
        helper.setBlock(community, BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get().defaultBlockState());
        applyCanonicalFertilizedDirt(helper, player, community);
        for (int expected = 4; expected >= 0; expected--) {
            mature(helper, community, handHarvestAnnual());
            harvest(helper, player, community, ItemStack.EMPTY);
            if (expected > 0) {
                FarmingBlockEntity reusable = farm(helper, community);
                check(reusable.isCommunityPlot(), "community origin was lost before exhaustion");
                check(reusable.getSeedableUntilGameTime() > helper.getLevel().getGameTime(),
                        "community seed window was not reopened for another paid use");
                assertRemaining(helper, community, expected);
            } else {
                check(helper.getBlockState(community).is(BlockRegistry.COMMUNITY_FARM_BLOCK.get()),
                        "fifth community harvest did not restore the existing community plot");
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void eachRipeFruitClickSpendsOneUseAndFifthCleansUpTheTree(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        applyCanonicalFertilizedDirt(helper, player, PLOT);
        FarmingBlockEntity soil = farm(helper, PLOT);
        CropDefinition crop = CropRegistry.byId("orange").orElseThrow();
        soil.plant(crop);
        setHasSeeds(helper, PLOT, true);

        ServerLevel level = helper.getLevel();
        BlockPos rootPos = helper.absolutePos(PLOT.above());
        FruitTreeDefinition definition = com.seggellion.britannia_mod.farming.FruitTreeRegistry
                .byIdOrDefault("orange");
        level.setBlock(rootPos, definition.rootBlock().get().defaultBlockState(), 3);
        OrangeTreeRootBlockEntity root = (OrangeTreeRootBlockEntity) level.getBlockEntity(rootPos);
        check(root != null, "tree root block entity was not created");
        root.initializeFromFarm(soil, level.getRandom(), "orange");

        OrangeTreeStructurePlanner.Plan plan = OrangeTreeStructurePlanner.plan(
                definition, root.getTreeSeed(), definition.maxGrowthStep(), rootPos);
        BlockPos fruitPos = plan.fruit().stream().findFirst()
                .orElseThrow(() -> new GameTestAssertException("tree plan had no fruit position"));
        BlockState ripeFruit = definition.fruitBlock().get().defaultBlockState()
                .setValue(OrangeFruitBlock.RIPE, true);
        ItemStack scissors = new ItemStack(ItemRegistry.SCISSORS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, scissors);

        for (int expected = 4; expected >= 0; expected--) {
            level.setBlock(fruitPos, ripeFruit, 3);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(fruitPos), Direction.UP, fruitPos, false);
            ripeFruit.useItemOn(scissors, level, player, InteractionHand.MAIN_HAND, hit);
            if (expected > 0) {
                assertRemaining(helper, PLOT, expected);
            } else {
                check(helper.getBlockState(PLOT).is(Blocks.DIRT),
                        "fifth ripe-fruit click did not exhaust the tree soil");
                check(level.getBlockState(rootPos).isAir(),
                        "fifth ripe-fruit click left the tree root behind");
            }
        }
        helper.succeed();
    }

    private static void applyCanonicalFertilizedDirt(
            GameTestHelper helper, ServerPlayer player, BlockPos relativePos
    ) {
        if (!helper.getBlockState(relativePos).is(BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get())) {
            helper.setBlock(relativePos, Blocks.DIRT.defaultBlockState());
        }
        ItemStack stack = new ItemStack(ItemRegistry.FERTILIZED_DIRT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(relativePos);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        ItemRegistry.FERTILIZED_DIRT.get().useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        check(helper.getBlockState(relativePos).is(BlockRegistry.FARMING_BLOCK.get()),
                "canonical fertilized dirt did not create farming soil");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static CropDefinition handHarvestAnnual() {
        CropDefinition crop = CropRegistry.byId("vanilla_potato").orElseThrow();
        check(crop.harvestTool() == CropHarvestTool.HAND && !crop.persistsAfterHarvest(),
                "test crop no longer provides an unsupported-free annual hand harvest");
        return crop;
    }

    private static void mature(GameTestHelper helper, BlockPos pos, CropDefinition crop) {
        FarmingBlockEntity soil = farm(helper, pos);
        soil.plantMigratedCrop(crop, "", crop.maxGrowthAge());
        setHasSeeds(helper, pos, true);
    }

    private static void setHasSeeds(GameTestHelper helper, BlockPos pos, boolean value) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        helper.getLevel().setBlock(absolute, state.setValue(FarmingBlock.HAS_SEEDS, value), 3);
    }

    private static ItemInteractionResult harvest(
            GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack tool
    ) {
        BlockPos absolute = helper.absolutePos(pos);
        FarmingBlockEntity soil = farm(helper, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return FarmingBlock.tryHarvestCrop(
                soil,
                helper.getLevel().getBlockState(absolute),
                helper.getLevel(),
                absolute,
                player,
                tool,
                InteractionHand.MAIN_HAND,
                "fertile_dirt_lifecycle_test");
    }

    private static FarmingBlockEntity farm(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof FarmingBlockEntity soil) {
            return soil;
        }
        throw new GameTestAssertException("missing farming block entity at " + pos.toShortString());
    }

    private static void assertRemaining(GameTestHelper helper, BlockPos pos, int expected) {
        int actual = farm(helper, pos).getRemainingFertileHarvests();
        check(actual == expected,
                "expected " + expected + " fertile harvests but found " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

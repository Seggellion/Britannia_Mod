package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.GrapeArborBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.GrapeVineBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropHarvestTool;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.LegacyGrapeVineMigration;
import com.seggellion.britannia_mod.farming.TallCropSupport;
import com.seggellion.britannia_mod.item.GrapeSeedsItem;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.winery.GrapeColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * World-level behaviour of the grape arbor: that it is the only way to grow grapes, that a private
 * plot only accepts its owner's seed, that the arbor reserves the blocks it fills, and that a
 * legacy vine converts exactly once.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrapeArborGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private GrapeArborGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void grapeSeedsPlantIntoAnUnownedPlotAndNeverOntoVanillaFarmland(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        useSeedsOn(helper, player, plot);
        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        check(farmBe.hasCrop(), "grape seeds did not plant into an unowned farming plot");
        check("grapes".equals(farmBe.getPlantedCropId()), "planted crop was not grapes");

        // Vanilla farmland used to receive the retired standalone vine. It must now receive nothing.
        BlockPos farmland = new BlockPos(3, 1, 1);
        helper.setBlock(farmland, Blocks.FARMLAND.defaultBlockState());
        useSeedsOn(helper, player, farmland);
        BlockState above = helper.getBlockState(farmland.above());
        check(!(above.getBlock() instanceof GrapeVineBlock),
            "grape seeds still create the retired standalone vine on vanilla farmland");
        check(above.isAir(), "grape seeds placed something onto vanilla farmland");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPrivatePlotAcceptsItsOwnerAndRefusesEveryoneElse(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper, plot);

        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        stranger.setGameMode(GameType.SURVIVAL);
        farmBe.setOwner(UUID.randomUUID());
        check(farmBe.hasOwner(), "a private plot did not record an owner");
        check(!farmBe.mayPlant(stranger), "a private plot accepted a stranger");

        useSeedsOn(helper, stranger, plot);
        check(!farmEntity(helper, plot).hasCrop(), "a stranger planted grapes in someone else's plot");

        farmBe.setOwner(stranger.getUUID());
        check(farmBe.mayPlant(stranger), "the owner was refused their own plot");
        useSeedsOn(helper, stranger, plot);
        check(farmEntity(helper, plot).hasCrop(), "the owner could not plant in their own plot");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCommunityPlotIsOpenToEveryoneAndNeverTakesAnOwner(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        ServerPlayer anyone = helper.makeMockServerPlayerInLevel();

        farmBe.setOwner(UUID.randomUUID());
        farmBe.startCommunitySeedWindow(helper.getLevel().getGameTime() + 20L);
        check(farmBe.isCommunityPlot(), "the plot did not become a community plot");
        check(!farmBe.hasOwner(), "a community plot kept an owner");
        check(farmBe.mayPlant(anyone), "a community plot refused a player");

        useSeedsOn(helper, anyone, plot);
        check(farmEntity(helper, plot).hasCrop(), "grapes could not be planted on a community plot");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theFruitingArborReservesTwoBlocksAndReleasesThemWhenItGoes(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        CropDefinition grapes = grapeCrop();

        farmBe.plantMigratedCrop(grapes, GrapesItem.DEFAULT_VARIETY_ID, 2);
        check(helper.getBlockState(plot.above()).isAir(), "a young vine reserved a block it does not fill");

        farmBe.plantMigratedCrop(grapes, GrapesItem.DEFAULT_VARIETY_ID, 7);
        for (int offset = 1; offset <= 3; offset++) {
            check(TallCropSupport.isGrapeSegment(helper.getBlockState(plot.above(offset))),
                "the arbor did not reserve the block " + offset + " above its plot");
        }
        check(helper.getBlockState(plot.above(4)).isAir(), "the arbor reserved more space than it fills");

        // Occupancy only, and solid: corn's walk-through block left the arbor with no collision.
        BlockState lower = helper.getBlockState(plot.above());
        check(lower.getBlock() instanceof GrapeArborBlock, "unexpected segment block");
        check(!lower.getCollisionShape(helper.getLevel(), helper.absolutePos(plot.above())).isEmpty(),
            "the arbor has no collision; a player would walk straight through it");

        TallCropSupport.clear(helper.getLevel(), helper.absolutePos(plot), grapes);
        for (int offset = 1; offset <= 3; offset++) {
            check(helper.getBlockState(plot.above(offset)).isAir(),
                "clearing the arbor orphaned the block " + offset + " above its plot");
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theDecoratorTurnsTheArborClockwiseAndWrapsBackAround(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        farmBe.plantMigratedCrop(grapeCrop(), GrapesItem.DEFAULT_VARIETY_ID, 7);
        check(farmBe.getVisualRotationQuarters() == 0, "a new arbor should start unrotated");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
        BlockPos absolutePlot = helper.absolutePos(plot);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePlot), Direction.UP, absolutePlot, false);

        // A blockstate y rotation is negated about the Y axis, so clockwise reads as -90 per turn.
        float[] expectedDegrees = {-90.0f, -180.0f, -270.0f, 0.0f};
        for (int turn = 0; turn < 4; turn++) {
            helper.getBlockState(plot).useItemOn(
                player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            FarmingBlockEntity rotated = farmEntity(helper, plot);
            check(rotated.getVisualRotationQuarters() == (turn + 1) % 4,
                "turn " + (turn + 1) + " left the arbor at quarter " + rotated.getVisualRotationQuarters());
            check(Math.abs(rotated.visualRotationDegrees() - expectedDegrees[turn]) < 1.0e-4,
                "turn " + (turn + 1) + " produced " + rotated.visualRotationDegrees() + " degrees");
        }

        // Four turns is a full circle: the arbor must be back where it started, not drifting.
        check(farmEntity(helper, plot).getVisualRotationQuarters() == 0, "rotation did not wrap after a full circle");
        check(farmEntity(helper, plot).hasCrop(), "rotating the arbor disturbed the crop");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aMatureArborIsCutWithScissorsRatherThanByHand(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        farmBe.plantMigratedCrop(grapeCrop(), GrapesItem.DEFAULT_VARIETY_ID, 7);

        check(grapeCrop().harvestTool() == CropHarvestTool.SCISSORS,
            "grapes must be cut from the arbor with scissors");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolutePlot = helper.absolutePos(plot);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePlot), Direction.UP, absolutePlot, false);

        // A bare hand must not take the bunches.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.getBlockState(plot).useItemOn(ItemStack.EMPTY, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(farmEntity(helper, plot).isMature(), "a bare hand harvested an arbor that needs scissors");

        ItemStack scissors = new ItemStack(ItemRegistry.SCISSORS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, scissors);
        helper.getBlockState(plot).useItemOn(scissors, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(!farmEntity(helper, plot).isMature(), "scissors did not harvest the mature arbor");

        helper.succeed();
    }

    /**
     * A grape arbor briefly borrowed corn's stalk block for occupancy. Worlds saved in that window
     * hold grape plots capped by corn stalks, which are registered walk-through and do not match the
     * arbor's structure check - so those plots rendered a vine you could walk through and refused to
     * be harvested, reporting that they needed full vertical growth they already had.
     */
    @GameTest(template = TEMPLATE)
    public static void anArborReclaimsCornStalksLeftBehindByTheEarlierBuild(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        farmBe.plantMigratedCrop(grapeCrop(), GrapesItem.DEFAULT_VARIETY_ID, 7);

        // Recreate the stale world state exactly: corn stalks standing over a grape plot.
        for (int offset = 1; offset <= 3; offset++) {
            helper.setBlock(plot.above(offset), BlockRegistry.CORN_STALK_BLOCK.get().defaultBlockState());
        }
        check(!TallCropSupport.hasRequiredVisuals(helper.getLevel(), helper.absolutePos(plot), grapeCrop(), 7),
            "this test is meaningless unless corn stalks really do break the arbor's structure check");

        check(TallCropSupport.repairStructureIfPossible(helper.getLevel(), helper.absolutePos(plot), grapeCrop(), 7),
            "the arbor could not reclaim the corn stalks left over from the earlier build");

        for (int offset = 1; offset <= 3; offset++) {
            BlockPos pos = plot.above(offset);
            check(TallCropSupport.isGrapeSegment(helper.getBlockState(pos)),
                "block " + offset + " above the plot was not reclaimed by the arbor");
            check(!helper.getBlockState(pos).getCollisionShape(helper.getLevel(), helper.absolutePos(pos)).isEmpty(),
                "reclaimed block " + offset + " still has no collision");
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theDecoratorAlsoTurnsTheArborWhenClickedOnTheVineItself(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        farmEntity(helper, plot).plantMigratedCrop(grapeCrop(), GrapesItem.DEFAULT_VARIETY_ID, 7);

        // The canopy is what a player can see and reach; the plot is buried under the whole plant.
        BlockPos canopy = plot.above(2);
        check(TallCropSupport.isGrapeSegment(helper.getBlockState(canopy)), "the arbor did not reserve its canopy");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
        BlockPos absoluteCanopy = helper.absolutePos(canopy);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absoluteCanopy), Direction.UP, absoluteCanopy, false);

        helper.getBlockState(canopy).useItemOn(
            player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(farmEntity(helper, plot).getVisualRotationQuarters() == 1,
            "clicking the vine with the decorator did not turn the arbor");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aLegacyVineConvertsOnceAndKeepsItsVarietyAndMaturity(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());

        BlockPos vine = plot.above();
        helper.setBlock(vine, BlockRegistry.GRAPE_VINE_BLOCK.get().defaultBlockState()
            .setValue(GrapeVineBlock.COLOR, GrapeColor.RED)
            .setValue(net.minecraft.world.level.block.CropBlock.AGE, 6));
        if (helper.getBlockEntity(vine) instanceof GrapeVineBlockEntity vineBe) {
            vineBe.setVariety(GrapesItem.DEFAULT_VARIETY_ID);
        }

        ServerLevel level = helper.getLevel();
        BlockPos absoluteVine = helper.absolutePos(vine);
        check(LegacyGrapeVineMigration.migrate(level, absoluteVine), "the legacy vine did not convert");
        check(helper.getBlockState(vine).isAir(), "the legacy vine survived its own conversion");

        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        check(farmBe.hasCrop(), "the converted vine did not become a crop");
        check("grapes".equals(farmBe.getPlantedCropId()), "the converted vine became the wrong crop");
        check(farmBe.getGrowthStage() == 6, "the converted vine lost its maturity");

        // Converting again must be a no-op rather than a second plant or a second refund.
        check(!LegacyGrapeVineMigration.migrate(level, absoluteVine), "the conversion ran twice");
        check(helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).isEmpty(),
            "the conversion dropped items into a plot that could host the crop");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aLegacyVineWithNoPlotBeneathRefundsInsteadOfVanishing(GameTestHelper helper) {
        BlockPos vine = new BlockPos(1, 2, 1);
        helper.setBlock(vine.below(), Blocks.DIRT.defaultBlockState());
        helper.setBlock(vine, BlockRegistry.GRAPE_VINE_BLOCK.get().defaultBlockState()
            .setValue(GrapeVineBlock.COLOR, GrapeColor.BLUE)
            .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));

        check(LegacyGrapeVineMigration.migrate(helper.getLevel(), helper.absolutePos(vine)),
            "the orphaned legacy vine did not convert");
        check(helper.getBlockState(vine).isAir(), "the orphaned legacy vine survived");
        helper.succeedWhen(() -> helper.assertItemEntityPresent(ItemRegistry.TRELLIS_ITEM.get()));
    }

    private static void useSeedsOn(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        ItemStack seeds = new ItemStack(ItemRegistry.GRAPE_SEEDS.get());
        GrapeSeedsItem.setVariety(seeds, GrapesItem.DEFAULT_VARIETY_ID);
        player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        seeds.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static FarmingBlockEntity farmEntity(GameTestHelper helper, BlockPos relativePos) {
        if (helper.getBlockEntity(relativePos) instanceof FarmingBlockEntity farmBe) {
            return farmBe;
        }
        throw new GameTestAssertException("no farming block entity at " + relativePos);
    }

    private static CropDefinition grapeCrop() {
        return CropRegistry.byId("grapes")
            .orElseThrow(() -> new GameTestAssertException("grapes crop is not registered"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

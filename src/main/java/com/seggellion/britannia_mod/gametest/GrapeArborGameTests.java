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
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
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

        // makeMockServerPlayerInLevel() hard-codes isCreative() to true, and a creative player
        // is a FlowerProtectionService administrator - allowed into anyone's plot by design.
        // Refusal can only be exercised by a player whose survival mode is real.
        ServerPlayer stranger = makeSurvivalMockServerPlayer(helper);
        com.seggellion.britannia_mod.skill.SkillManager.applyConfirmedValue(stranger,"farming",80);
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
        // A mature migrated crop immediately fills the space above its plot with arbor occupancy
        // blocks (TallCropSupport), so the cell is not air - the legacy vine itself must be gone.
        check(!(helper.getBlockState(vine).getBlock() instanceof GrapeVineBlock),
            "the legacy vine survived its own conversion");

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

    /**
     * Growth is a per-random-tick callback and nothing else: one random tick applies exactly one
     * increment of {@code growthMultiplier / baseGrowthTicks}, and an ordinary server tick applies
     * none. Halving the grape base value only means anything if that stays true -- a second
     * advancement pathway would quietly hand the old rate back.
     *
     * <p>Asserted with whichever crop can actually grow where the test structure sits, not with
     * grapes. The GameTest world places structures at roughly y -60, which resolves UNDERGROUND and
     * is far below the grape altitude floor of 55, so a grape vine there is legitimately blocked and
     * could only produce a vacuous pass. The dispatch path itself is crop-agnostic: {@code tickGrowth}
     * reads {@code crop.baseGrowthTicks()} for every crop through the same expression.
     */
    @GameTest(template = TEMPLATE)
    public static void growthAdvancesOncePerRandomTickAndNeverOnAPlainServerTick(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(plot);
        FarmingBlockEntity farmBe = farmEntity(helper, plot);

        CropDefinition growable = growableCropAt(helper, farmBe, plot);
        farmBe.plant(growable, "");
        farmBe.setHydration(3);
        check(farmBe.getGrowthProgress() == 0.0f, "a fresh planting did not start at zero progress");

        float multiplier = com.seggellion.britannia_mod.farming.CropQualityCalculator.growthMultiplier(
            farmBe.createGrowthContext(level, pos, growable, null));
        float expectedIncrement = multiplier / Math.max(1, growable.baseGrowthTicks());

        // One growth callback moves progress by exactly one increment, never two.
        farmBe.tickGrowth(level, pos, level.getBlockState(pos), level.random);
        float afterOne = farmBe.getGrowthProgress();
        check(Math.abs(afterOne - expectedIncrement) < 1.0e-4f,
            "one growth callback moved " + growable.id() + " by " + afterOne
                + ", expected " + expectedIncrement);

        // A plain server tick is not a growth callback.
        float beforeServerTicks = farmBe.getGrowthProgress();
        for (int tick = 0; tick < 20; tick++) {
            FarmingBlockEntity.serverTick(level, pos, level.getBlockState(pos), farmBe);
        }
        check(farmBe.getGrowthProgress() == beforeServerTicks,
            "twenty server ticks advanced growth from " + beforeServerTicks
                + " to " + farmBe.getGrowthProgress());

        // A random tick applies one callback. Hydration decay can lower the multiplier used inside,
        // so the guard is the ceiling: no dispatch may exceed a single maximum-multiplier increment.
        farmBe.setHydration(3);
        float beforeRandomTick = farmBe.getGrowthProgress();
        level.getBlockState(pos).randomTick(level, pos, level.random);
        float delta = farmEntity(helper, plot).getGrowthProgress() - beforeRandomTick;
        check(delta > 0.0f, "a random tick did not advance " + growable.id() + " at all");
        check(delta <= (3.9375f / Math.max(1, growable.baseGrowthTicks())) + 1.0e-4f,
            "a random tick advanced growth by " + delta + ", more than one callback can produce");

        helper.succeed();
    }

    /**
     * Grapes at the GameTest altitude are altitude-blocked, and a blocked plot must stay exactly
     * where it is however many ticks it receives. Nothing may advance a grape vine behind the
     * environmental gate.
     */
    @GameTest(template = TEMPLATE)
    public static void aBlockedGrapePlotNeverAdvancesOnAnyTick(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(plot);
        CropDefinition grapes = grapeCrop();
        check(grapes.baseGrowthTicks() == 16, "grapes are not on the approved base growth value");

        FarmingBlockEntity farmBe = farmEntity(helper, plot);
        farmBe.plant(grapes, GrapesItem.DEFAULT_VARIETY_ID);
        farmBe.setHydration(3);
        check(!grapes.canGrowAtAltitude(pos),
            "the test structure moved above the grape altitude floor; this test needs rewriting");

        for (int tick = 0; tick < 10; tick++) {
            farmBe.tickGrowth(level, pos, level.getBlockState(pos), level.random);
            FarmingBlockEntity.serverTick(level, pos, level.getBlockState(pos), farmBe);
            level.getBlockState(pos).randomTick(level, pos, level.random);
        }

        check(farmEntity(helper, plot).getGrowthProgress() == 0.0f,
            "a blocked grape plot advanced to " + farmEntity(helper, plot).getGrowthProgress());
        check(farmEntity(helper, plot).getGrowthStage() == 0, "a blocked grape plot changed age");
        check(farmEntity(helper, plot).isGrowthBlocked(), "the plot did not report itself blocked");

        helper.succeed();
    }

    /**
     * Perennial regrowth resumes where the Fabric reference left it -- age 4, half progress -- and
     * the halved base value slows the second lifecycle by the same factor as the first rather than
     * rewriting where it starts.
     */
    @GameTest(template = TEMPLATE)
    public static void regrowthResumesAtAgeFourAndRunsOnTheSameHalvedBase(GameTestHelper helper) {
        BlockPos plot = new BlockPos(1, 1, 1);
        helper.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState());
        CropDefinition grapes = grapeCrop();
        FarmingBlockEntity farmBe = farmEntity(helper, plot);

        farmBe.plant(grapes, GrapesItem.DEFAULT_VARIETY_ID);
        check(farmBe.getGrowthStage() == 0, "a fresh planting did not start at age 0");
        check(farmBe.getGrowthProgress() == 0.0f, "a fresh planting did not start at progress 0.0");

        farmBe.regrowAfterHarvest(grapes);
        check(farmBe.getGrowthStage() == 4, "regrowth did not resume at age 4");
        check(Math.abs(farmBe.getGrowthProgress() - 0.5f) < 1.0e-4f,
            "regrowth did not resume at progress 0.5, was " + farmBe.getGrowthProgress());
        check(!farmBe.isMature(), "a regrown vine came back already mature");
        check(grapes.baseGrowthTicks() == 16,
            "regrowth must finish on the same halved base as fresh growth");

        helper.succeed();
    }

    /**
     * A joined survival {@link ServerPlayer} whose {@code isCreative()} tells the truth.
     *
     * <p>{@link GameTestHelper#makeMockServerPlayerInLevel()} overrides {@code isCreative()} to
     * always return true, which makes every mock an administrator under
     * {@code FlowerProtectionService} - useless for proving that a stranger is refused.
     */
    private static ServerPlayer makeSurvivalMockServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(
            helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * A crop whose climate, altitude and support requirements are all satisfied where this test
     * structure sits, so the growth assertions measure a real advance rather than a blocked one.
     */
    private static CropDefinition growableCropAt(
        GameTestHelper helper, FarmingBlockEntity farmBe, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        for (CropDefinition candidate : CropRegistry.all()) {
            if (candidate.tallCrop()) {
                continue;
            }
            float multiplier = com.seggellion.britannia_mod.farming.CropQualityCalculator.growthMultiplier(
                farmBe.createGrowthContext(helper.getLevel(), pos, candidate, null));
            if (multiplier > 0.0f) {
                return candidate;
            }
        }
        throw new GameTestAssertException(
            "no crop can grow at " + pos.toShortString() + ", so growth dispatch cannot be measured");
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

package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.quest.action.QuestAction;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.check;
import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.checkCount;

/**
 * Rowan farming questline M5: crop-cycle attribution (protocol section 2.1 and playbook 6.5).
 *
 * <p>Quest credit for a harvest is stricter than permission to harvest. A public plot lets anyone
 * pull anyone's carrot, and it still will -- these tests never change what farming allows. What
 * they hold is that a harvest of somebody else's crop, of another crop, on another plot, of a
 * later cycle, or of produce merely picked up off the ground, never becomes the quest event a
 * stage-5 subscription is bound to.
 *
 * <p>Each test declares its own {@code batch}: the transport seam is process-wide and single-slot.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestCropAttributionGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 3);
    private static final BlockPos PLOT = new BlockPos(2, 2, 2);
    private static final BlockPos OTHER_PLOT = new BlockPos(4, 2, 2);

    private QuestCropAttributionGameTests() {}

    @GameTest(template = TEMPLATE, batch = "quest_attribution_persistence", timeoutTicks = 100)
    public static void theCycleAndPlanterSurviveChunkUnloadAndRestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer planter = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-attr-a", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(planter);
            BlockPos plot = helper.absolutePos(PLOT);
            FarmingBlockEntity soil = plantAs(helper, planter, PLOT);

            UUID cycle = soil.getCropCycleId();
            check(cycle != null, "planting did not mint a crop cycle");
            check(planter.getUUID().equals(soil.getPlanterId()), "planting did not record the planter");
            check(QuestActionEvents.plotKey(level, plot).equals(soil.getCropCyclePlotKey()),
                "the cycle did not record the plot it was minted at");
            check(soil.hasCropCycleAt(), "the cycle does not describe the plot it is standing on");

            // What a chunk unload and a restart do to a block entity: save it, drop it, load it.
            CompoundTag saved = soil.saveWithoutMetadata(level.registryAccess());
            BlockState state = level.getBlockState(plot);
            level.removeBlockEntity(plot);
            FarmingBlockEntity reloaded = new FarmingBlockEntity(plot, state);
            reloaded.loadWithComponents(saved, level.registryAccess());
            level.setBlockEntity(reloaded);

            FarmingBlockEntity afterRestart = (FarmingBlockEntity) level.getBlockEntity(plot);
            check(afterRestart != null, "the plot lost its block entity");
            check(cycle.equals(afterRestart.getCropCycleId()), "the crop cycle did not survive the reload");
            check(planter.getUUID().equals(afterRestart.getPlanterId()),
                "the planter did not survive the reload");
            check(afterRestart.hasCropCycleAt(), "the reloaded cycle no longer describes its plot");

            // And it is still the cycle a harvest reports afterwards.
            rails.clear();
            harvestAs(helper, planter, PLOT, afterRestart, carrot());
            checkCount(1, rails.forAction(QuestAction.CROP_HARVEST).size(),
                "the harvest after the reload did not report exactly one event");
            check(cycle.toString().equals(QuestActionTestSupport.subjectText(
                    rails.forAction(QuestAction.CROP_HARVEST).get(0), "crop_cycle_uuid")),
                "the harvest after the reload reported a different cycle");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(planter);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_attribution_two_players", timeoutTicks = 100)
    public static void harvestingWhatAnotherPlayerPlantedGivesTheHarvesterNoCredit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer planter = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-planter", STAND);
        ServerPlayer stranger = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-stranger", STAND);
        try {
            BlockPos plot = helper.absolutePos(PLOT);
            QuestActionTestSupport.subscribeToEveryAction(planter);
            FarmingBlockEntity soil = plantAs(helper, planter, PLOT);
            UUID cycle = soil.getCropCycleId();
            rails.clear();

            // Both players are on the same stage-5 objective, bound to the same plot and cycle.
            String plotKey = QuestActionEvents.plotKey(level, plot);
            QuestActionTestSupport.subscribeToBoundHarvest(planter, "carrot", plotKey, cycle);
            QuestActionTestSupport.subscribeToBoundHarvest(stranger, "carrot", plotKey, cycle);

            // The stranger may harvest the public plot -- and gets no quest event for it.
            harvestAs(helper, stranger, PLOT, soil, carrot());
            checkCount(0, rails.count(),
                "harvesting a crop somebody else planted produced a quest event");
            checkCount(0, QuestActionTestSupport.outbox(stranger).size(),
                "the stranger's harvest occupied an outbox row");

            // The planter's own cycle is still distinguishable: it names them, and nothing else.
            check(planter.getUUID().equals(soil.getPlanterId()) || !soil.hasCrop(),
                "the harvest rewrote the planter of the cycle");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(stranger);
            QuestActionTestSupport.disconnect(planter);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_attribution_wrong_subject", timeoutTicks = 100)
    public static void anotherCropAnotherPlotAndAnotherCycleCannotSatisfyTheBoundHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-wrong", STAND);
        try {
            BlockPos plot = helper.absolutePos(PLOT);
            QuestActionTestSupport.subscribeToEveryAction(player);
            FarmingBlockEntity soil = plantAs(helper, player, PLOT);
            UUID boundCycle = soil.getCropCycleId();
            String boundPlotKey = QuestActionEvents.plotKey(level, plot);
            QuestActionTestSupport.subscribeToBoundHarvest(player, "carrot", boundPlotKey, boundCycle);
            rails.clear();

            // Another crop, on the bound plot: refused.
            CropDefinition otherCrop = CropRegistry.byId("vanilla_potato").orElseThrow();
            soil.plantMigratedCrop(otherCrop, "", otherCrop.maxGrowthAge(), player.getUUID());
            setHasSeeds(level, plot, true);
            FarmingBlock.tryHarvestCrop(soil, level.getBlockState(plot), level, plot, player,
                ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
            checkCount(0, rails.count(), "harvesting a different crop satisfied the bound objective");

            // The right crop on another plot: refused.
            BlockPos elsewhere = helper.absolutePos(OTHER_PLOT);
            level.setBlock(elsewhere, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
            FarmingBlockEntity otherSoil = (FarmingBlockEntity) level.getBlockEntity(elsewhere);
            otherSoil.plantMigratedCrop(carrot(), "", carrot().maxGrowthAge(), player.getUUID());
            setHasSeeds(level, elsewhere, true);
            FarmingBlock.tryHarvestCrop(otherSoil, level.getBlockState(elsewhere), level, elsewhere, player,
                ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
            checkCount(0, rails.count(), "harvesting the right crop on another plot satisfied the objective");

            // The right crop on the right plot, but a later cycle: refused.
            soil.plantMigratedCrop(carrot(), "", carrot().maxGrowthAge(), player.getUUID());
            setHasSeeds(level, plot, true);
            check(!boundCycle.equals(soil.getCropCycleId()), "replanting did not rotate the cycle");
            FarmingBlock.tryHarvestCrop(soil, level.getBlockState(plot), level, plot, player,
                ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
            checkCount(0, rails.count(), "a later cycle in the same hole satisfied the bound objective");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_attribution_produce", timeoutTicks = 100)
    public static void droppedProduceAndInventoryPickupCannotSatisfyTheBoundHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-produce", STAND);
        try {
            BlockPos plot = helper.absolutePos(PLOT);
            QuestActionTestSupport.subscribeToEveryAction(player);
            FarmingBlockEntity soil = plantAs(helper, player, PLOT);
            QuestActionTestSupport.subscribeToBoundHarvest(player, "carrot",
                QuestActionEvents.plotKey(level, plot), soil.getCropCycleId());
            rails.clear();

            // Produce handed straight into the inventory: no harvest happened, so no event exists.
            player.getInventory().add(new ItemStack(ItemRegistry.CARROTS.get(), 3));
            com.seggellion.britannia_mod.quest.QuestObjectiveWatcher.onItemPickedUp(player,
                new ItemStack(ItemRegistry.CARROTS.get(), 3));
            checkCount(0, rails.count(),
                "picking produce up was reported as a harvest");

            // Produce dropped on the ground and picked back up: still not a harvest.
            ItemEntity dropped = new ItemEntity(level, plot.getX() + 0.5D, plot.getY() + 1.0D,
                plot.getZ() + 0.5D, new ItemStack(ItemRegistry.CARROTS.get(), 3));
            level.addFreshEntity(dropped);
            dropped.playerTouch(player);
            checkCount(0, rails.count(), "collecting a dropped stack was reported as a harvest");
            level.getEntitiesOfClass(ItemEntity.class,
                new AABB(plot).inflate(6.0D)).forEach(ItemEntity::discard);

            // The control: the real harvest of the bound cycle is what satisfies it.
            harvestAs(helper, player, PLOT, soil, carrot());
            checkCount(1, rails.forAction(QuestAction.CROP_HARVEST).size(),
                "the real harvest of the bound cycle did not satisfy the objective");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_attribution_rotation", timeoutTicks = 100)
    public static void theCycleRotatesWhenAPerennialRegrowsAndClearsWhenTheCropIsCleared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-rotation", STAND);
        try {
            BlockPos plot = helper.absolutePos(PLOT);
            QuestActionTestSupport.subscribeToEveryAction(player);

            // A perennial: harvesting ends one cycle and the regrowth opens another.
            level.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
            helper.setBlock(PLOT.above(), BlockRegistry.TRELLIS_BLOCK.get().defaultBlockState());
            FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
            CropDefinition tomato = CropRegistry.byId("tomato").orElseThrow();
            soil.plantMigratedCrop(tomato, "", tomato.maxGrowthAge(), player.getUUID());
            setHasSeeds(level, plot, true);
            UUID firstCycle = soil.getCropCycleId();

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.SCISSORS.get()));
            FarmingBlock.tryHarvestCrop(soil, level.getBlockState(plot), level, plot, player,
                player.getMainHandItem(), InteractionHand.MAIN_HAND, "rowan_m5_test");

            check(soil.hasCrop(), "the perennial did not regrow");
            check(soil.getCropCycleId() != null && !firstCycle.equals(soil.getCropCycleId()),
                "the perennial's regrowth reused the cycle the harvest had already ended");
            check(player.getUUID().equals(soil.getPlanterId()),
                "the regrowth forgot whose plant it was");
            check(firstCycle.toString().equals(QuestActionTestSupport.subjectText(
                    rails.forAction(QuestAction.CROP_HARVEST).get(0), "crop_cycle_uuid")),
                "the harvest reported the cycle that came after it instead of the one it ended");

            // Clearing the crop ends the attribution outright.
            soil.clearCrop();
            check(soil.getCropCycleId() == null, "clearing the crop left a cycle behind");
            check(soil.getPlanterId() == null, "clearing the crop left a planter behind");
            check(soil.getCropCyclePlotKey().isEmpty(), "clearing the crop left a plot key behind");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private static CropDefinition carrot() {
        return CropRegistry.byId("carrot").orElseThrow();
    }

    private static FarmingBlockEntity plantAs(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(relative);
        level.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
        ItemStack seed = new ItemStack(carrot().seedItem().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, seed);
        FarmingBlock.tryPlantSeed(level, plot, level.getBlockState(plot), player, seed, false, "rowan_m5_test");
        FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
        check(soil != null && soil.hasCrop(), "the test could not plant a carrot");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return soil;
    }

    private static void harvestAs(GameTestHelper helper, ServerPlayer player, BlockPos relative,
                                  FarmingBlockEntity soil, CropDefinition crop) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(relative);
        UUID cycle = soil.getCropCycleId();
        UUID planter = soil.getPlanterId();
        soil.plantMigratedCrop(crop, "", crop.maxGrowthAge(), planter);
        restoreCycle(soil, cycle, planter, level, plot);
        setHasSeeds(level, plot, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        FarmingBlock.tryHarvestCrop(soil, level.getBlockState(plot), level, plot, player,
            ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
    }

    /**
     * Maturing a crop in a test goes through the migration path, which mints a fresh cycle by
     * design. These tests are about the cycle the PLAYER planted, so the original identity is put
     * back through the same NBT the game itself persists it with -- no test-only setter exists,
     * and none should.
     */
    private static void restoreCycle(FarmingBlockEntity soil, UUID cycle, UUID planter,
                                     ServerLevel level, BlockPos plot) {
        CompoundTag tag = soil.saveWithoutMetadata(level.registryAccess());
        if (planter != null) tag.putUUID("PlanterUUID", planter);
        if (cycle != null) tag.putUUID("CropCycleUUID", cycle);
        tag.putString("CropCyclePlotKey", QuestActionEvents.plotKey(level, plot));
        soil.loadWithComponents(tag, level.registryAccess());
    }

    private static void setHasSeeds(ServerLevel level, BlockPos plot, boolean value) {
        BlockState state = level.getBlockState(plot);
        if (state.hasProperty(FarmingBlock.HAS_SEEDS)) {
            level.setBlock(plot, state.setValue(FarmingBlock.HAS_SEEDS, value), 3);
        }
    }
}

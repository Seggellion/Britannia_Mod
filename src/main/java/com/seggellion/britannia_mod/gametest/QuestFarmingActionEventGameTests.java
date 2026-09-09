package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.dirtgathering.DirtGatheringService;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.item.FarmingHoeItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.quest.action.QuestAction;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.util.WaterSourceInteraction;
import com.seggellion.britannia_mod.wildresource.WildResourceEntries;
import com.seggellion.britannia_mod.wildresource.WildResourceHarvestService;
import com.seggellion.britannia_mod.wildresource.WildResourceNode;
import com.seggellion.britannia_mod.wildresource.WildResourceSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.check;
import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.checkCount;

/**
 * Rowan farming questline M5: every one of the twelve authoritative success points of protocol
 * section 2.1 reports exactly one event after the real mutation, and nothing at all when the
 * interaction failed, was denied, was on cooldown, or was made with the wrong item.
 *
 * <p>Each test declares its own {@code batch}: the transport seam these drive is process-wide and
 * single-slot, so two of them running together would read each other's recorder.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestFarmingActionEventGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 3);
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);

    private QuestFarmingActionEventGameTests() {}

    @GameTest(template = TEMPLATE, batch = "quest_action_gathering", timeoutTicks = 100)
    public static void theThreeGatheringSuccessPointsEachReportExactlyOneEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-gather", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos target = helper.absolutePos(TARGET);

            // 1. Tracked dung harvest, through the event bridge the service has always posted.
            level.setBlock(target, BlockRegistry.DUNG.get().defaultBlockState(), 3);
            WildResourceSavedData.get(level).registerNode(
                new WildResourceNode(WildResourceEntries.DUNG, target, level.getGameTime()));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.SHOVEL.get()));
            check(WildResourceHarvestService.harvestOne(level, target, player,
                    BlockRegistry.DUNG.get(), ItemRegistry.DUNG.get()),
                "the tracked dung node did not harvest");
            List<JsonObject> dung = rails.forAction(QuestAction.WILD_RESOURCE_HARVEST);
            checkCount(1, dung.size(), "one dung harvest did not report exactly one event");
            check(WildResourceEntries.DUNG.toString()
                    .equals(QuestActionTestSupport.subjectText(dung.get(0), "resource_id")),
                "the dung event did not name the resource that was harvested");

            // 2. Custom dirt gathering.
            level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
            check(DirtGatheringService.attempt(level, target, player) == DirtGatheringService.Result.GATHERED,
                "the dirt gather did not succeed");
            List<JsonObject> dirt = rails.forAction(QuestAction.DIRT_GATHER);
            checkCount(1, dirt.size(), "one dirt gather did not report exactly one event");
            check("britannia_mod:dirt".equals(QuestActionTestSupport.subjectText(dirt.get(0), "item_id")),
                "the dirt event did not name the item that was granted");

            // 3. Filling a bucket at the Water Well.
            level.setBlock(target, BlockRegistry.WATER_WELL.get().defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            WaterSourceInteraction.fillFromSource(level, target, player, InteractionHand.MAIN_HAND,
                player.getMainHandItem());
            check(player.getMainHandItem().is(Items.WATER_BUCKET), "the bucket was not filled");
            List<JsonObject> fill = rails.forAction(QuestAction.WATER_CONTAINER_FILL);
            checkCount(1, fill.size(), "one bucket fill did not report exactly one event");
            check("minecraft:water_bucket"
                    .equals(QuestActionTestSupport.subjectText(fill.get(0), "container_item_id")),
                "the fill event did not name the container that came out of the exchange");
            check(QuestAction.SOURCE_WELL.equals(QuestActionTestSupport.subjectText(fill.get(0), "source")),
                "a fill at the Water Well was not reported as a well");

            checkCount(3, rails.count(), "the three gathering points reported more than three events");
            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_action_bowls", timeoutTicks = 100)
    public static void theFourBowlSuccessPointsEachReportExactlyOneEventNamingTheirOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-bowls", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);

            // 4. Bowl of Dirt.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get()));
            use(player);
            check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()), "the bowl of dirt was not made");

            // 5. Bowl of Fertile Dirt.
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DUNG.get()));
            use(player);
            ItemStack fertileBowl = player.getMainHandItem().copy();
            check(fertileBowl.is(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()), "the fertile bowl was not made");

            List<JsonObject> prepared = rails.forAction(QuestAction.BOWL_PREPARE);
            checkCount(2, prepared.size(), "the two dry preparation steps did not report one event each");
            check("britannia_mod:bowl_of_dirt"
                    .equals(QuestActionTestSupport.subjectText(prepared.get(0), "output_item_id")),
                "the first preparation did not name the bowl of dirt");
            check("britannia_mod:bowl_of_fertile_dirt"
                    .equals(QuestActionTestSupport.subjectText(prepared.get(1), "output_item_id")),
                "the second preparation did not name the fertile bowl");

            // 6. Bowl of Water, at the well: the contract's own two actions for one gesture.
            BlockPos well = helper.absolutePos(TARGET);
            level.setBlock(well, BlockRegistry.WATER_WELL.get().defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            WaterSourceInteraction.fillFromSource(level, well, player, InteractionHand.MAIN_HAND,
                player.getMainHandItem());
            ItemStack waterBowl = player.getMainHandItem().copy();
            check(waterBowl.is(ItemRegistry.BOWL_OF_WATER.get()), "the bowl of water was not filled");

            List<JsonObject> bowlFill = rails.forAction(QuestAction.BOWL_WATER_FILL);
            checkCount(1, bowlFill.size(), "one bowl fill did not report exactly one bowl_water_fill");
            check("britannia_mod:bowl_of_water"
                    .equals(QuestActionTestSupport.subjectText(bowlFill.get(0), "output_item_id")),
                "the bowl fill did not name its output");
            check(QuestAction.SOURCE_WELL.equals(QuestActionTestSupport.subjectText(bowlFill.get(0), "source")),
                "the bowl fill did not carry the source it happened at");
            checkCount(1, rails.forAction(QuestAction.WATER_CONTAINER_FILL).size(),
                "section 2.1 gives the bowl-at-a-well gesture both bowl_water_fill and "
                    + "water_container_fill; exactly one of each is expected");

            // 7. Final fertilized dirt mix.
            player.setItemInHand(InteractionHand.MAIN_HAND, fertileBowl);
            player.setItemInHand(InteractionHand.OFF_HAND, waterBowl);
            use(player);
            check(player.getInventory().countItem(ItemRegistry.FERTILIZED_DIRT.get()) == 1,
                "the final mix did not produce fertilized dirt");
            List<JsonObject> mixed = rails.forAction(QuestAction.FERTILE_DIRT_MIX);
            checkCount(1, mixed.size(), "one final mix did not report exactly one event");
            check("britannia_mod:fertilized_dirt"
                    .equals(QuestActionTestSupport.subjectText(mixed.get(0), "output_item_id")),
                "the mix did not name its output");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_action_plot", timeoutTicks = 100)
    public static void theFivePlotSuccessPointsEachReportExactlyOneEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-plot", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos plot = helper.absolutePos(TARGET);
            String plotKey = level.dimension().location() + ":" + plot.getX() + ":" + plot.getY()
                + ":" + plot.getZ();

            // 8. Hoeing a public plot.
            level.setBlock(plot, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            ItemStack hoe = new ItemStack(ItemRegistry.FARMING_HOE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            FarmingHoeItem.prepareCommunityPlot(level, plot, level.getBlockState(plot), player, hoe,
                InteractionHand.MAIN_HAND);
            check(level.getBlockState(plot).is(BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get()),
                "the public plot was not hoed");
            List<JsonObject> hoed = rails.forAction(QuestAction.PLOT_HOE);
            checkCount(1, hoed.size(), "one hoeing did not report exactly one event");
            check(plotKey.equals(QuestActionTestSupport.subjectText(hoed.get(0), "plot_key")),
                "the hoeing event did not carry the contract's plot_key");

            // 9. Fertilizing it.
            ItemStack fertilizer = new ItemStack(ItemRegistry.FERTILIZED_DIRT.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, fertilizer);
            CommunityHoedFarmBlock.fertilizeCommunityPlot(level, plot, player, fertilizer);
            check(level.getBlockState(plot).is(BlockRegistry.FARMING_BLOCK.get()),
                "the public plot was not fertilized");
            checkCount(1, rails.forAction(QuestAction.PLOT_FERTILIZE).size(),
                "one fertilizing did not report exactly one event");

            // 10. Planting the seed.
            CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
            ItemStack seed = new ItemStack(carrot.seedItem().get());
            player.setItemInHand(InteractionHand.MAIN_HAND, seed);
            FarmingBlock.tryPlantSeed(level, plot, level.getBlockState(plot), player, seed, false,
                "rowan_m5_test");
            FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
            check(soil != null && soil.hasCrop(), "the seed was not planted");
            List<JsonObject> planted = rails.forAction(QuestAction.CROP_PLANT);
            checkCount(1, planted.size(), "one planting did not report exactly one event");
            check(player.getStringUUID()
                    .equals(QuestActionTestSupport.subjectText(planted.get(0), "planter_uuid")),
                "the planting event did not name the planter");
            check(soil.getCropCycleId() != null && soil.getCropCycleId().toString()
                    .equals(QuestActionTestSupport.subjectText(planted.get(0), "crop_cycle_uuid")),
                "the planting event did not carry the cycle the block entity minted");

            // 11. Watering it.
            ItemStack can = new ItemStack(ItemRegistry.WATERING_CAN.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, can);
            WateringCanItem.waterFarmingBlock(level, plot, level.getBlockState(plot), player, can);
            List<JsonObject> watered = rails.forAction(QuestAction.CROP_WATER);
            checkCount(1, watered.size(), "one watering did not report exactly one event");
            check(soil.getCropCycleId().toString()
                    .equals(QuestActionTestSupport.subjectText(watered.get(0), "crop_cycle_uuid")),
                "the watering event did not name the cycle it watered");

            // 12. Harvesting it.
            soil.plantMigratedCrop(carrot, "", carrot.maxGrowthAge(), player.getUUID());
            java.util.UUID matureCycle = soil.getCropCycleId();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            FarmingBlock.tryHarvestCrop(soil, level.getBlockState(plot), level, plot, player,
                ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
            List<JsonObject> harvested = rails.forAction(QuestAction.CROP_HARVEST);
            checkCount(1, harvested.size(), "one harvest did not report exactly one event");
            check(matureCycle.toString()
                    .equals(QuestActionTestSupport.subjectText(harvested.get(0), "crop_cycle_uuid")),
                "the harvest reported a cycle other than the one that produced the crop");
            check(player.getStringUUID()
                    .equals(QuestActionTestSupport.subjectText(harvested.get(0), "planter_uuid")),
                "the harvest did not carry the planter of the cycle it ended");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_action_refusals", timeoutTicks = 100)
    public static void failedDeniedCooledDownAndWrongItemInteractionsReportNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-refused", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos target = helper.absolutePos(TARGET);

            // Wrong tool: dirt gathering needs the Britannia shovel and reports nothing without it.
            level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
            check(DirtGatheringService.attempt(level, target, player) == DirtGatheringService.Result.NOT_OURS,
                "a vanilla shovel was accepted for dirt gathering");
            checkCount(0, rails.count(), "a gesture that is not ours reported an event");

            // Cooldown: the second gather in a row is refused, and a refusal reports nothing.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.SHOVEL.get()));
            check(DirtGatheringService.attempt(level, target, player) == DirtGatheringService.Result.GATHERED,
                "the first dirt gather did not succeed");
            checkCount(1, rails.count(), "the successful gather did not report exactly one event");
            check(DirtGatheringService.attempt(level, target, player) == DirtGatheringService.Result.COOLDOWN,
                "the immediate second gather was not on cooldown");
            checkCount(1, rails.count(), "a cooled-down gesture reported an event");

            // Denied: a tracked node out of reach harvests nothing and reports nothing.
            BlockPos farAway = helper.absolutePos(new BlockPos(2, 2, 2)).offset(0, 0, 40);
            level.setBlock(farAway, BlockRegistry.DUNG.get().defaultBlockState(), 3);
            WildResourceSavedData.get(level).registerNode(
                new WildResourceNode(WildResourceEntries.DUNG, farAway, level.getGameTime()));
            check(!WildResourceHarvestService.harvestOne(level, farAway, player,
                    BlockRegistry.DUNG.get(), ItemRegistry.DUNG.get()),
                "an out-of-reach node was harvested");
            checkCount(1, rails.count(), "a denied harvest reported an event");

            // Failed: an immature crop cannot be harvested, and reports nothing.
            BlockPos plot = helper.absolutePos(new BlockPos(4, 2, 2));
            level.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
            FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
            CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
            soil.plant(carrot, "", player.getUUID());
            BlockState planted = level.getBlockState(plot).setValue(FarmingBlock.HAS_SEEDS, true);
            level.setBlock(plot, planted, 3);
            ItemInteractionResult immature = FarmingBlock.tryHarvestCrop(soil, planted, level, plot, player,
                ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
            check(immature == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "an immature crop reported a successful harvest");
            checkCount(0, rails.forAction(QuestAction.CROP_HARVEST).size(),
                "a failed harvest reported an event");

            // Wrong item: planting into an occupied plot changes nothing and reports nothing.
            ItemStack seed = new ItemStack(carrot.seedItem().get());
            player.setItemInHand(InteractionHand.MAIN_HAND, seed);
            FarmingBlock.tryPlantSeed(level, plot, planted, player, seed, false, "rowan_m5_test");
            checkCount(0, rails.forAction(QuestAction.CROP_PLANT).size(),
                "planting into an occupied plot reported an event");
            check(seed.getCount() == 1, "the refused planting still consumed the seed");

            // A full watering can at full soil: no charge spent, nothing reported.
            ItemStack can = new ItemStack(ItemRegistry.WATERING_CAN.get());
            soil.setHydration(FarmingBlockEntity.MAX_HYDRATION);
            player.setItemInHand(InteractionHand.MAIN_HAND, can);
            WateringCanItem.waterFarmingBlock(level, plot, level.getBlockState(plot), player, can);
            checkCount(0, rails.forAction(QuestAction.CROP_WATER).size(),
                "watering soil that was already full reported an event");

            // An empty watering can on dry soil: nothing happens, nothing is reported.
            soil.setHydration(0);
            WateringCanItem.setWaterCharges(can, 0);
            WateringCanItem.waterFarmingBlock(level, plot, level.getBlockState(plot), player, can);
            checkCount(0, rails.forAction(QuestAction.CROP_WATER).size(),
                "watering with an empty can reported an event");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    /**
     * The tracked-dung support question the milestone asks to settle (playbook item 8).
     *
     * <p>Discovery found that {@code harvestOne} does not re-check a dung node's support: a pile
     * whose dirt has gone still harvests once. The correction chosen here is <b>no rule change</b>,
     * and this test is what makes that choice safe to hold: quest credit is bounded by the tracked
     * node, not by the block. An unsupported but still-tracked pile is worth exactly one event,
     * the same as a supported one, because the ledger row is removed with it. A pile that was never
     * tracked -- a {@code /setblock} pile, the only other way to stand dung on nothing -- is worth
     * none at all, because the service refuses it before any mutation.
     *
     * <p>Revalidating support here would instead DENY a harvest that succeeds today, which is a
     * farming rule change and outside M5's remit.
     */
    @GameTest(template = TEMPLATE, batch = "quest_action_dung_support", timeoutTicks = 100)
    public static void anUnsupportedTrackedPileIsWorthOneEventAndAnUntrackedPileIsWorthNone(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-dung", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos target = helper.absolutePos(TARGET);

            // A tracked pile standing on nothing: still harvestable today, still one event.
            level.setBlock(target.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(target, BlockRegistry.DUNG.get().defaultBlockState(), 3);
            WildResourceSavedData.get(level).registerNode(
                new WildResourceNode(WildResourceEntries.DUNG, target, level.getGameTime()));
            check(WildResourceHarvestService.harvestOne(level, target, player,
                    BlockRegistry.DUNG.get(), ItemRegistry.DUNG.get()),
                "an unsupported tracked pile stopped harvesting; this test's premise has changed");
            checkCount(1, rails.forAction(QuestAction.WILD_RESOURCE_HARVEST).size(),
                "an unsupported tracked pile did not credit exactly one harvest");

            // The node is gone with it, so the same pile cannot be worked twice.
            level.setBlock(target, BlockRegistry.DUNG.get().defaultBlockState(), 3);
            check(!WildResourceHarvestService.harvestOne(level, target, player,
                    BlockRegistry.DUNG.get(), ItemRegistry.DUNG.get()),
                "a pile with no ledger row was harvested");
            checkCount(1, rails.forAction(QuestAction.WILD_RESOURCE_HARVEST).size(),
                "an untracked pile credited a harvest");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_action_unsubscribed", timeoutTicks = 100)
    public static void nothingIsReportedWhenNoQuestSubscribesToTheAction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-quiet", STAND);
        try {
            QuestActionTestSupport.clearJournal(player);
            BlockPos target = helper.absolutePos(TARGET);
            level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.SHOVEL.get()));

            check(DirtGatheringService.attempt(level, target, player) == DirtGatheringService.Result.GATHERED,
                "the dirt gather did not succeed");
            checkCount(0, rails.count(),
                "a farming action with no subscriber must never reach Rails (section 2.2)");
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                "an unsubscribed action must not occupy an outbox row either");

            // A quest with only the legacy observers subscribes to no farming action.
            QuestActionTestSupport.subscribeToLegacyObserversOnly(player, "britannia_mod:dirt");
            level.setBlock(target, Blocks.COARSE_DIRT.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get()));
            use(player);
            check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()),
                "the control gesture did not actually succeed");
            checkCount(0, rails.count(), "a pickup-only quest reported a farming action");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    private static void use(ServerPlayer player) {
        player.getMainHandItem().getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
    }
}

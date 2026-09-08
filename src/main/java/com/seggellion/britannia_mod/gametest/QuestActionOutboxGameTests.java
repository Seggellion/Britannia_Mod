package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.dirtgathering.DirtGatheringService;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;
import com.seggellion.britannia_mod.quest.action.QuestAction;
import com.seggellion.britannia_mod.quest.action.QuestActionDispatcher;
import com.seggellion.britannia_mod.quest.action.QuestActionOutbox;
import com.seggellion.britannia_mod.quest.action.QuestActionOutboxEntry;
import com.seggellion.britannia_mod.quest.action.QuestActionOutboxStore;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.check;
import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.checkCount;

/**
 * Rowan farming questline M5: the durable outbox of protocol section 2.2 -- what happens to a real
 * harvest when Rails is not there to hear about it.
 *
 * <p>Each test declares its own {@code batch}: the transport seam is process-wide and single-slot.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestActionOutboxGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 3);
    private static final BlockPos PLOT = new BlockPos(2, 2, 2);

    private QuestActionOutboxGameTests() {}

    @GameTest(template = TEMPLATE, batch = "quest_outbox_outage", timeoutTicks = 120)
    public static void anOutageAtHarvestPersistsTheEventAndARestartRetriesItAndAdvancesOnce(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.OUTAGE);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-outage", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            harvestOneCarrot(helper, player);

            checkCount(1, rails.count(), "the harvest did not reach the transport once");
            List<QuestActionOutboxEntry> owed = QuestActionTestSupport.outbox(player);
            checkCount(1, owed.size(), "an outage at harvest did not leave the event in the outbox");
            QuestActionOutboxEntry entry = owed.get(0);
            check(entry.event().action() == QuestAction.CROP_HARVEST, "the wrong event was persisted");
            check(entry.attempts() == 1, "the failed attempt was not counted");
            check(!entry.dueAt(QuestActionDispatcher.now()),
                "a failed event must wait out its backoff rather than spin");

            // Section 2.2's ordering requirement, observed rather than assumed: when the first
            // attempt left, the row was already in the durable store AND the store had been
            // flushed. An unclean kill at that instant would still have found the harvest on disk.
            List<QuestActionTestSupport.OutboxAtSend> atSend = rails.outboxAtSend();
            checkCount(1, atSend.size(), "the attempt did not observe the outbox");
            check(atSend.get(0).persisted(),
                "the event was posted before it was written to the outbox");
            check(!atSend.get(0).dirty(),
                "the outbox was still dirty when the first attempt left; it was not fsync'd first");

            // What a restart does: the same durable rows, scheduled again at once.
            UUID eventUuid = entry.eventUuid();
            rails.answerWith(QuestActionTestSupport.Answer.IRRELEVANT);
            QuestActionDispatcher.onServerStarted(level.getServer());

            checkCount(2, rails.count(), "the restart did not retry the persisted event exactly once");
            check(eventUuid.toString().equals(rails.sent().get(1).get("event_uuid").getAsString()),
                "the retry minted a new event uuid; Rails could not have deduplicated it");
            check(!rails.sent().get(0).get("request_uuid").getAsString()
                    .equals(rails.sent().get(1).get("request_uuid").getAsString()),
                "each attempt carries its own request_uuid (section 0)");
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                "a terminal result did not remove the row");

            // And a second restart has nothing left to send: it advanced exactly once.
            QuestActionDispatcher.onServerStarted(level.getServer());
            checkCount(2, rails.count(), "an already-answered event was sent again after the row was gone");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_outbox_repeat", timeoutTicks = 120)
    public static void repeatingTheGestureDuringAnOutageDoesNotFillTheOutbox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.OUTAGE);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-repeat", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos plot = helper.absolutePos(PLOT);

            for (int attempt = 0; attempt < 4; attempt++) {
                level.setBlock(plot, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
                ItemStack hoe = new ItemStack(ItemRegistry.FARMING_HOE.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
                com.seggellion.britannia_mod.item.FarmingHoeItem.prepareCommunityPlot(level, plot,
                    level.getBlockState(plot), player, hoe, InteractionHand.MAIN_HAND);
                check(level.getBlockState(plot).is(BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get()),
                    "hoeing attempt " + attempt + " did not actually hoe the plot");
            }

            checkCount(1, QuestActionTestSupport.outbox(player).size(),
                "four hoeings of the same objective during an outage must owe Rails one event, not four");
            checkCount(1, rails.count(),
                "the same objective was posted more than once while the first was still owed");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_outbox_unsupported", timeoutTicks = 120)
    public static void anOlderRailsWithoutTheRouteIsAskedOnceAndTheRowsAreDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.ENDPOINT_MISSING);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-legacy-rails", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            gatherOneDirt(helper, player);

            checkCount(1, rails.count(), "the first event must still be attempted once");
            check(QuestActionDispatcher.isEndpointUnsupported(level.getServer()),
                "a 404 with no contract code must be recorded as an unsupported endpoint");
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                "section 4: rows are dropped once the endpoint is confirmed absent");

            // Nothing else is sent for the rest of the boot, for any action.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get()));
            player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND);
            check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()),
                "the second gesture did not actually succeed");
            checkCount(1, rails.count(), "the mod kept asking a Rails that has no such route");
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                "an unsupported endpoint must not accumulate rows");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_outbox_applied", timeoutTicks = 120)
    public static void anAppliedAnswerInstallsTheNewObjectivesAndClosesTheRow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.APPLIED);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-applied", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            check(QuestActionTestSupport.currentTriggers(player).hasActionSubscriptions(),
                "the test did not start from a subscribed journal");

            harvestOneCarrot(helper, player);

            checkCount(1, rails.count(), "the harvest did not reach Rails once");
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                "an applied answer is terminal and must remove the row");
            QuestObjectiveTriggers after = QuestActionTestSupport.currentTriggers(player);
            check(!after.hasActionSubscriptions(),
                "the journal was not replaced by the objectives Rails published, so the same gesture "
                    + "would fire again");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    @GameTest(template = TEMPLATE, batch = "quest_outbox_legacy_observers", timeoutTicks = 120)
    public static void theLegacyObserversStillFireAndCostNoActionEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
            QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m5-observers", STAND);
        try {
            // The three legacy observers are parsed from the same journal the farming ones are.
            QuestActionTestSupport.installJournal(player,
                "{\"triggers\": {\"location\": {\"trigger_key\": \"arrived\", \"min_x\": -8, \"min_y\": -8,"
                    + " \"min_z\": -8, \"max_x\": 8, \"max_y\": 8, \"max_z\": 8},"
                    + " \"pickup\": {\"trigger_key\": \"picked_up\", \"item_tag\": \"britannia_mod:dirt\"},"
                    + " \"destroy\": {\"trigger_key\": \"destroyed\", \"item_tag\": \"britannia_mod:dirt\","
                    + " \"min_x\": -8, \"min_y\": -8, \"min_z\": -8, \"max_x\": 8, \"max_y\": 8, \"max_z\": 8}}}");

            QuestObjectiveTriggers triggers = QuestActionTestSupport.currentTriggers(player);
            check(triggers.location() != null && "arrived".equals(triggers.location().triggerKey()),
                "the location observer stopped being parsed");
            check(triggers.pickup() != null && "picked_up".equals(triggers.pickup().triggerKey()),
                "the pickup observer stopped being parsed");
            check(triggers.destroy() != null && "destroyed".equals(triggers.destroy().triggerKey()),
                "the destroy observer stopped being parsed");
            check(!triggers.hasActionSubscriptions(),
                "a journal with only the three legacy observers must subscribe to no farming action");
            check(!triggers.isEmpty(), "a journal with three observers is not empty");

            // A real farming success with that journal installed reports nothing.
            gatherOneDirt(helper, player);
            checkCount(0, rails.count(), "a legacy-observer quest was charged an action event");
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                "a legacy-observer quest occupied an outbox row");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private static void gatherOneDirt(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(PLOT);
        level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ToolRegistry.SHOVEL.get()));
        check(DirtGatheringService.attempt(level, target, player) == DirtGatheringService.Result.GATHERED,
            "the test could not gather dirt");
    }

    private static void harvestOneCarrot(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(PLOT);
        level.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
        FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
        check(soil != null, "the test plot has no farming block entity");
        CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
        soil.plantMigratedCrop(carrot, "", carrot.maxGrowthAge(), player.getUUID());
        BlockState state = level.getBlockState(plot);
        if (state.hasProperty(FarmingBlock.HAS_SEEDS)) {
            level.setBlock(plot, state.setValue(FarmingBlock.HAS_SEEDS, true), 3);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        FarmingBlock.tryHarvestCrop(soil, level.getBlockState(plot), level, plot, player,
            ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m5_test");
    }
}

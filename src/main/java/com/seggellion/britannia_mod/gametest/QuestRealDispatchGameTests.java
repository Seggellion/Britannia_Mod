package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.network.payload.QuestTriggerResultS2CPayload;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;
import com.seggellion.britannia_mod.quest.action.QuestAction;
import com.seggellion.britannia_mod.quest.action.QuestActionDispatcher;
import com.seggellion.britannia_mod.quest.action.QuestActionEventClient;
import com.seggellion.britannia_mod.quest.action.QuestActionEventProtocol;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.quest.action.QuestActionOutbox;
import com.seggellion.britannia_mod.quest.network.QuestClientPayload;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.check;
import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.checkCount;

/**
 * Rowan farming questline M11: the questline driven by <b>real block and item dispatch</b>, and
 * what comes back out of the server as a result.
 *
 * <h2>Why this class exists</h2>
 * M5 proved every one of the twelve success points of protocol section 2.1 reports exactly one
 * event, and M5's tests are right about that. But they enter production code one layer below the
 * interaction handler -- {@code FarmingHoeItem.prepareCommunityPlot},
 * {@code CommunityHoedFarmBlock.fertilizeCommunityPlot}, {@code FarmingBlock.tryPlantSeed},
 * {@code WateringCanItem.waterFarmingBlock}, {@code FarmingBlock.tryHarvestCrop} -- so if a block's
 * {@code useItemOn} stopped calling one of them, every M5 test would still pass and no player could
 * complete the questline. The M11 audit found the same shape everywhere in the quest suite: the
 * capability to drive {@code ServerPlayerGameMode.useItemOn} exists and is used elsewhere in this
 * repository, and no quest test used it.
 *
 * <p>So everything here goes through {@code player.gameMode.useItemOn} -- the method the vanilla
 * packet handler calls, hit result and all -- and the assertions are about what the world, the
 * outbox and the player's own connection show afterwards.
 *
 * <h2>The two seams, and why every test has its own batch</h2>
 * The Rails transport ({@code QuestActionDispatcher.installSender}) is a single process-wide slot,
 * and GameTests inside one batch tick concurrently. Every test here therefore declares a batch of
 * its own and restores the real transport in a {@code finally}, the same discipline
 * {@code QuestActionTestSupport} documents. The player's own {@code EmbeddedChannel} is per-test and
 * needs no such care.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestRealDispatchGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 3);
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);
    /** The second plot, at the position {@code QuestCropAttributionGameTests} already uses. */
    private static final BlockPos OTHER_PLOT = new BlockPos(4, 2, 2);

    private QuestRealDispatchGameTests() {
    }

    // ================================================================ farming boundaries

    /**
     * Each of the five plot boundaries reached by a right-click, in the order a player performs
     * them, each mutating the world and each worth exactly one action event.
     *
     * <p>This is M5's {@code theFivePlotSuccessPointsEachReportExactlyOneEvent} with the static
     * calls replaced by dispatch. It asserts the same two things per boundary -- the world changed,
     * one event was reported -- so a regression in either the interaction wiring or the publish
     * point fails here.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_plot", timeoutTicks = 200)
    public static void everyFarmingBoundaryIsReachedByARealRightClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
                QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        Joined joined = join(helper, "rowan-m11-dispatch");
        ServerPlayer player = joined.player();
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos plot = helper.absolutePos(TARGET);
            String plotKey = level.dimension().location() + ":" + plot.getX() + ":" + plot.getY()
                    + ":" + plot.getZ();

            // 1. Hoeing. The item's useOn is what the click reaches, through the block's PASS.
            level.setBlock(plot, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            rightClick(player, level, plot, new ItemStack(ItemRegistry.FARMING_HOE.get()));
            check(level.getBlockState(plot).is(BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get()),
                    "right-clicking a public plot with the Farming Hoe did not hoe it");
            List<JsonObject> hoed = rails.forAction(QuestAction.PLOT_HOE);
            checkCount(1, hoed.size(), "one real hoeing did not report exactly one event");
            check(plotKey.equals(QuestActionTestSupport.subjectText(hoed.get(0), "plot_key")),
                    "the hoeing event did not carry the contract's plot_key");

            // 2. Fertilizing, through CommunityHoedFarmBlock.useItemOn.
            rightClick(player, level, plot, new ItemStack(ItemRegistry.FERTILIZED_DIRT.get()));
            check(level.getBlockState(plot).is(BlockRegistry.FARMING_BLOCK.get()),
                    "right-clicking a hoed plot with fertilized dirt did not fertilize it");
            checkCount(1, rails.forAction(QuestAction.PLOT_FERTILIZE).size(),
                    "one real fertilizing did not report exactly one event");

            // 3. Planting, through FarmingBlock.useItemOn.
            CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
            rightClick(player, level, plot, new ItemStack(carrot.seedItem().get(), 3));
            FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
            check(soil != null && soil.hasCrop(),
                    "right-clicking a fertilized plot with a carrot seed did not plant it");
            List<JsonObject> planted = rails.forAction(QuestAction.CROP_PLANT);
            checkCount(1, planted.size(), "one real planting did not report exactly one event");
            check(player.getStringUUID()
                            .equals(QuestActionTestSupport.subjectText(planted.get(0), "planter_uuid")),
                    "the planting event did not name the planter of the real click");

            // 4. Watering.
            rightClick(player, level, plot, new ItemStack(ItemRegistry.WATERING_CAN.get()));
            List<JsonObject> watered = rails.forAction(QuestAction.CROP_WATER);
            checkCount(1, watered.size(), "one real watering did not report exactly one event");
            check(soil.getCropCycleId() != null && soil.getCropCycleId().toString()
                            .equals(QuestActionTestSupport.subjectText(watered.get(0), "crop_cycle_uuid")),
                    "the watering event did not name the cycle the block entity holds");

            // 5. Harvesting, empty-handed: the block's useItemOn is called with an empty stack.
            soil.plantMigratedCrop(carrot, "", carrot.maxGrowthAge(), player.getUUID());
            UUID matureCycle = soil.getCropCycleId();
            rightClick(player, level, plot, ItemStack.EMPTY);
            List<JsonObject> harvested = rails.forAction(QuestAction.CROP_HARVEST);
            checkCount(1, harvested.size(), "one real harvest did not report exactly one event");
            check(matureCycle.toString()
                            .equals(QuestActionTestSupport.subjectText(harvested.get(0), "crop_cycle_uuid")),
                    "the harvest reported a cycle other than the one that produced the crop");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    /**
     * Planter attribution, driven by real clicks on both sides: the player whose click planted the
     * crop owns it, and a stranger whose click pulls it gets nothing and owes Rails nothing.
     *
     * <p>M5 proves the rule at {@code FarmingBlock.tryHarvestCrop}. What this adds is that the rule
     * survives the gesture: the interaction that reaches the harvest carries the clicking player,
     * so a wiring change cannot silently credit the wrong one.
     *
     * <p>Two plots, because a negative result is worth nothing without a positive one. On the first
     * the stranger's subscription is the permissive one, and their click IS worth an event -- which
     * is what proves the click reaches the publish point at all. On the second it is the stage-5
     * bound subscription, {@code require_planter} and all, and the identical click is worth nothing.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_attribution", timeoutTicks = 200)
    public static void aStrangersRealRightClickHarvestsWithoutCredit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails =
                QuestActionTestSupport.install(level.getServer(), QuestActionTestSupport.Answer.IRRELEVANT);
        Joined planterJoin = join(helper, "rowan-m11-planter");
        Joined strangerJoin = join(helper, "rowan-m11-stranger");
        ServerPlayer planter = planterJoin.player();
        ServerPlayer stranger = strangerJoin.player();
        try {
            QuestActionTestSupport.subscribeToEveryAction(planter);

            // ---- the control: a permissive subscription makes the stranger's click count ----
            QuestActionTestSupport.subscribeToEveryAction(stranger);
            FarmingBlockEntity control = plantByHand(helper, planter, TARGET);
            check(planter.getUUID().equals(control.getPlanterId()),
                    "the block entity recorded somebody other than the clicking player as planter");
            rails.clear();
            rightClick(stranger, level, helper.absolutePos(TARGET), ItemStack.EMPTY);
            check(!rails.forAction(QuestAction.CROP_HARVEST).isEmpty(),
                    "the stranger's real click never reached the harvest publish point, so the "
                            + "negative half below would prove nothing");

            // ---- the rule: the stage-5 subscription refuses the same click ----
            FarmingBlockEntity bound = plantByHand(helper, planter, OTHER_PLOT);
            BlockPos otherPlot = helper.absolutePos(OTHER_PLOT);
            QuestActionTestSupport.subscribeToBoundHarvest(stranger, "carrot",
                    QuestActionEvents.plotKey(level, otherPlot), bound.getCropCycleId());
            rails.clear();

            rightClick(stranger, level, otherPlot, ItemStack.EMPTY);
            checkCount(0, rails.forAction(QuestAction.CROP_HARVEST).size(),
                    "a stranger's real harvest was reported as their objective");
            checkCount(0, QuestActionTestSupport.outbox(stranger).size(),
                    "a stranger's real harvest left a row owed to Rails");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(stranger);
            QuestActionTestSupport.disconnect(planter);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    /**
     * A mature carrot on {@code relative}, planted by {@code player}'s own right-click so the
     * planter recorded on the block entity is the one the interaction carried.
     */
    private static FarmingBlockEntity plantByHand(GameTestHelper helper, ServerPlayer player,
                                                  BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(relative);
        level.setBlock(plot, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
        CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
        rightClick(player, level, plot, new ItemStack(carrot.seedItem().get(), 3));
        FarmingBlockEntity soil = (FarmingBlockEntity) level.getBlockEntity(plot);
        check(soil != null && soil.hasCrop(), "a real right-click with a seed planted nothing");
        soil.plantMigratedCrop(carrot, "", carrot.maxGrowthAge(), player.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return soil;
    }

    // ================================================================ M11 deferred defect 3

    /**
     * A {@code window_expired} refusal is said out loud.
     *
     * <p>Rails owns the authoritative deadline; when a planting arrives after it, Rails answers
     * {@code rejected} with {@code reason: "window_expired"} and the step to reset to, and clears
     * the progress from there. Until M11 the mod logged that and took the generic refusal path, so
     * the player watched a step un-tick with nothing said. The refusal path itself is unchanged --
     * the row is still removed and the objective still cools down -- and the only difference is
     * that the player is told.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_window", timeoutTicks = 200)
    public static void aWindowExpiredRefusalIsExplainedToThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandInRails rails = new StandInRails(StandInRails.Kind.WINDOW_EXPIRED);
        QuestActionDispatcher.installSender(rails);
        QuestActionDispatcher.resetEndpointSupport(level.getServer());
        QuestActionOutbox.store(level.getServer()).removeAll();
        Joined joined = join(helper, "rowan-m11-window");
        ServerPlayer player = joined.player();
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos plot = helper.absolutePos(TARGET);
            level.setBlock(plot, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            joined.drain();

            rightClick(player, level, plot, new ItemStack(ItemRegistry.FARMING_HOE.get()));
            check(rails.count() >= 1, "the real hoeing reported nothing to Rails");

            List<String> said = joined.said();
            check(said.contains(QuestScreenText.STEP_WINDOW_EXPIRED),
                    "a window_expired refusal said nothing to the player; saw " + said);

            // The refusal path itself is untouched: terminal, so the row is gone.
            checkCount(0, QuestActionTestSupport.outbox(player).size(),
                    "a rejected event left a row in the outbox");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    /**
     * The other refusals stay quiet. {@code mismatch} describes a request the player cannot have
     * caused on purpose and could not act on, so a line of text for it is noise.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_quiet_refusal", timeoutTicks = 200)
    public static void anOrdinaryRefusalStillSaysNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandInRails rails = new StandInRails(StandInRails.Kind.MISMATCH);
        QuestActionDispatcher.installSender(rails);
        QuestActionDispatcher.resetEndpointSupport(level.getServer());
        QuestActionOutbox.store(level.getServer()).removeAll();
        Joined joined = join(helper, "rowan-m11-quiet");
        ServerPlayer player = joined.player();
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos plot = helper.absolutePos(TARGET);
            level.setBlock(plot, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            joined.drain();

            rightClick(player, level, plot, new ItemStack(ItemRegistry.FARMING_HOE.get()));
            check(rails.count() >= 1, "the real hoeing reported nothing to Rails");

            List<String> said = joined.said();
            check(!said.contains(QuestScreenText.STEP_WINDOW_EXPIRED),
                    "a mismatch refusal borrowed the window_expired sentence; saw " + said);

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    // ================================================================ M11 deferred defect 2

    /**
     * What the server hands the client after a real harvest carries none of the answer key.
     *
     * <p>The stand-in answers with the published journal entry Rails really sends -- the one whose
     * {@code require_bound} has been reduced to the plot key and crop-cycle UUID currently bound --
     * and with node metadata carrying {@code action_trigger} and {@code action_steps}. The body the
     * dispatcher forwards must carry none of it, and must still carry everything M8's journal
     * refresh and M10's achievement path read.
     *
     * <h2>Why this stops one step short of the socket</h2>
     * A GameTest player joins over a bare {@code Connection}, which never negotiates NeoForge's
     * payload channels, so a clientbound <b>custom payload</b> is dropped before it reaches the
     * channel -- silently, and for every test in this repository, which is why none of them assert
     * on one. Chat packets do arrive, which is how the {@code window_expired} test above reads its
     * line. So this drives the real gesture, the real dispatcher and the real answer, asserts the
     * server installed state from that body, and then asserts the payload contents by putting the
     * same answer through the same production call the dispatcher makes -- and through the payload's
     * own {@code STREAM_CODEC}, so what is asserted is what the wire would carry.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_sanitized", timeoutTicks = 200)
    public static void theAnswerThatReachesTheClientCarriesNoBoundValues(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandInRails rails = new StandInRails(StandInRails.Kind.APPLIED_WITH_BOUND_TRIGGERS);
        QuestActionDispatcher.installSender(rails);
        QuestActionDispatcher.resetEndpointSupport(level.getServer());
        QuestActionOutbox.store(level.getServer()).removeAll();
        Joined joined = join(helper, "rowan-m11-wire");
        ServerPlayer player = joined.player();
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            BlockPos plot = helper.absolutePos(TARGET);
            level.setBlock(plot, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);

            rightClick(player, level, plot, new ItemStack(ItemRegistry.FARMING_HOE.get()));
            check(rails.count() >= 1, "the real hoeing reported nothing to Rails");
            JsonObject answered = rails.lastAnswer();
            check(answered != null, "the stand-in never answered");

            // The server installed state from this body -- installState ran -- and it KEPT the
            // bound values. Server authority is untouched; only what the client is handed changed.
            QuestObjectiveTriggers serverSide = QuestActionTestSupport.currentTriggers(player);
            check(serverSide.action() != null,
                    "the server did not install the advanced objective, so nothing here was reached");
            check(StandInRails.BOUND_PLOT_KEY.equals(serverSide.action().requireBound().get("plot_key")),
                    "the server lost the bound plot key it decides with");
            check(StandInRails.BOUND_CROP_CYCLE
                            .equals(serverSide.action().requireBound().get("crop_cycle_uuid")),
                    "the server lost the bound crop cycle it decides with");

            // What the client would be handed, through the wire codec.
            String body = throughTheWire(QuestClientPayload.toAppliedResultJson(answered));
            for (String forbidden : new String[]{
                    StandInRails.BOUND_PLOT_KEY, StandInRails.BOUND_CROP_CYCLE,
                    "\"triggers\"", "\"action_trigger\"", "\"action_steps\"",
                    "\"require_bound\"", "\"match\""}) {
                check(!body.contains(forbidden),
                        forbidden + " reached the client: " + body);
            }
            check(QuestClientPayload.isSanitized(JsonParser.parseString(body)),
                    "the body the client is handed is not sanitized: " + body);

            // And everything the client reads is still there.
            JsonObject sent = JsonParser.parseString(body).getAsJsonObject();
            check(sent.has("success") && sent.get("success").getAsBoolean(),
                    "the client would discard this body: it does not say it succeeded");
            check(sent.has("accepted_quest"), "M8's journal refresh has no journal entry to read");
            check("Return to Rowan with your harvest.".equals(sent.getAsJsonObject("accepted_quest")
                            .get("objective").getAsString()),
                    "the advanced objective did not survive sanitizing");
            check(sent.has("client_actions") && sent.getAsJsonArray("client_actions").size() == 1,
                    "M10's achievement client action did not survive sanitizing");
            check(sent.getAsJsonObject("node").getAsJsonObject("metadata").has("journal_objective"),
                    "the node's presentation metadata did not survive sanitizing");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    /** One trip through {@code QuestTriggerResultS2CPayload}'s own codec, buffer and all. */
    private static String throughTheWire(String responseJson) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            net.minecraft.network.RegistryFriendlyByteBuf out =
                    new net.minecraft.network.RegistryFriendlyByteBuf(buffer,
                            net.minecraft.core.RegistryAccess.EMPTY);
            QuestTriggerResultS2CPayload.STREAM_CODEC.encode(out,
                    new QuestTriggerResultS2CPayload(responseJson, 45L, "step_plot_hoe"));
            return QuestTriggerResultS2CPayload.STREAM_CODEC.decode(out).responseJson();
        } finally {
            buffer.release();
        }
    }

    // ================================================================ M11 deferred defect F14

    /**
     * A preparation that succeeds says nothing about flowing water.
     *
     * <p>The warning is an action-bar line and the dry preparation runs on the same click, so the
     * old order -- warn, then mix -- left "the water here is flowing" standing over a bowl that had
     * just been filled with dirt, until something else happened to write to the action bar. The
     * player is aiming at a stream here and the mix still applies; the only difference is silence.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_bowl_success", timeoutTicks = 200)
    public static void aSuccessfulPreparationLeavesNoFlowingWaterWarning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Joined joined = join(helper, "rowan-m11-bowl-ok");
        ServerPlayer player = joined.player();
        try {
            BlockPos water = flowingWaterUnderfoot(helper, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.DIRT.get()));
            joined.drain();

            player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);

            check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()),
                    "the preparation did not apply, so this test proves nothing about its message");
            checkCount(0, occurrences(joined.said(), QuestScreenText.WATER_FLOWING),
                    "a successful preparation still warned about the water at " + water);

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
        }
    }

    /**
     * One right-click, one warning, however many hands hold a bowl.
     *
     * <p>The flowing-water check sat above the main-hand guard, so a click with a fillable bowl in
     * each hand ran it twice: the client tries the main hand, gets PASS, and tries the off hand.
     * Both hands are still allowed to fill and both still refuse a stream; only the sentence is
     * spoken once, by the hand that would have done the filling.
     */
    @GameTest(template = TEMPLATE, batch = "quest_real_dispatch_bowl_hands", timeoutTicks = 200)
    public static void oneRightClickWarnsAboutFlowingWaterExactlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Joined joined = join(helper, "rowan-m11-bowl-hands");
        ServerPlayer player = joined.player();
        try {
            flowingWaterUnderfoot(helper, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
            joined.drain();

            // Exactly what the client does: try the main hand, and when it passes, try the off hand.
            player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
            player.gameMode.useItem(player, level, player.getOffhandItem(), InteractionHand.OFF_HAND);

            checkCount(1, occurrences(joined.said(), QuestScreenText.WATER_FLOWING),
                    "one right-click did not produce exactly one flowing-water warning");
            check(player.getMainHandItem().is(ItemRegistry.EMPTY_BOWL.get())
                            && player.getOffhandItem().is(ItemRegistry.EMPTY_BOWL.get()),
                    "flowing water filled a bowl, which the SOURCE_ONLY clip must never allow");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
        }
    }

    /**
     * A stream the player is looking straight down at, with stone underneath so the SOURCE_ONLY
     * clip finds no source further along the ray. Returns the water's position.
     */
    private static BlockPos flowingWaterUnderfoot(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos water = helper.absolutePos(TARGET);
        level.setBlock(water.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(water, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 3);
        check(!level.getFluidState(water).isSource() && level.getFluidState(water).is(FluidTags.WATER),
                "the arena did not end up holding flowing water");
        player.setPos(water.getX() + 0.5D, water.getY() + 2.0D, water.getZ() + 0.5D);
        player.setXRot(90.0F);
        player.setYRot(0.0F);
        return water;
    }

    private static int occurrences(List<String> lines, String wanted) {
        int seen = 0;
        for (String line : lines) {
            if (wanted.equals(line)) seen++;
        }
        return seen;
    }

    // ================================================================ rig

    /** A joined survival player and the channel the server talks to them on. */
    private record Joined(ServerPlayer player, EmbeddedChannel channel) {

        /** Throws away whatever the join itself produced, so a test reads only its own traffic. */
        void drain() {
            channel.flushOutbound();
            while (channel.outboundMessages().poll() != null) {
                // discarded on purpose
            }
        }

        /** Every chat line since the last drain: translation key, or the literal text. */
        List<String> said() {
            List<String> lines = new ArrayList<>();
            for (Object message : outbound()) {
                collectChat(message, lines);
            }
            return lines;
        }

        private List<Object> outbound() {
            // The server suspends flushing on every connection for the length of a tick
            // (MinecraftServer.tickChildren) and a GameTest runs inside one, so anything sent since
            // the last drain is still unflushed in the channel's outbound buffer.
            channel.flushOutbound();
            List<Object> messages = new ArrayList<>();
            Object message;
            while ((message = channel.outboundMessages().poll()) != null) {
                messages.add(message);
            }
            return messages;
        }

        private static void collectChat(Object message, List<String> into) {
            if (message instanceof ClientboundBundlePacket bundle) {
                for (Packet<?> sub : bundle.subPackets()) {
                    collectChat(sub, into);
                }
            } else if (message instanceof ClientboundSystemChatPacket chat) {
                Component content = chat.content();
                into.add(content.getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey()
                        : content.getString());
            }
        }
    }

    /**
     * Mirrors {@code ManagedResourceTestPlayers.survival}, keeping the channel and adding what a
     * farming interaction needs: a position beside the plot and a loaded Farming skill.
     *
     * <p>The skill matters. {@code FarmingCultivationGate} refuses planting outright when a player's
     * skill data never loaded, and on a GameTest server it never does -- there is no Rails to load
     * it from. {@code applyConfirmedValue} is the production path for "Rails already committed this
     * value": it writes locally and posts nothing.
     */
    private static Joined join(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        BlockPos stand = helper.absolutePos(STAND);
        player.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        SkillManager.applyConfirmedValue(player, FarmingSkill.SKILL_ID, 50.0f);
        return new Joined(player, channel);
    }

    /**
     * One right-click on the top face of {@code target}, exactly as the packet handler performs it:
     * the item goes in the main hand, the player is stood beside the block, and
     * {@code ServerPlayerGameMode.useItemOn} decides what happens -- block first, then the item.
     */
    private static void rightClick(ServerPlayer player, ServerLevel level, BlockPos target,
                                   ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 1.5D);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D),
                        Direction.UP, target, false));
    }

    /**
     * A Rails that answers one fixed body. Separate from
     * {@code QuestActionTestSupport.RecordingRails} because the two bodies M11 needs -- a
     * {@code window_expired} refusal and an {@code applied} carrying real bound values -- are M11's,
     * and widening a helper five other batches share to carry them would be the larger change.
     */
    private static final class StandInRails implements QuestActionDispatcher.Sender {

        enum Kind { WINDOW_EXPIRED, MISMATCH, APPLIED_WITH_BOUND_TRIGGERS }

        /** The two values the published journal entry resolves; neither may reach a client. */
        static final String BOUND_PLOT_KEY = "minecraft:overworld:1203:64:-488";
        static final String BOUND_CROP_CYCLE = "b1b3d4a0-1f7e-4c0d-8f4c-6a2e9c1f0a11";

        private final Kind kind;
        private final List<JsonObject> sent = new CopyOnWriteArrayList<>();
        private final List<JsonObject> answers = new CopyOnWriteArrayList<>();

        StandInRails(Kind kind) {
            this.kind = kind;
        }

        int count() {
            return sent.size();
        }

        @Override
        public CompletableFuture<QuestActionEventClient.SendResult> send(MinecraftServer server,
                                                                         byte[] body) {
            JsonObject request =
                    JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            sent.add(request);
            String eventUuid = request.get("event_uuid").getAsString();
            String answer = switch (kind) {
                case WINDOW_EXPIRED -> rejected(eventUuid, QuestActionEventProtocol.REASON_WINDOW_EXPIRED,
                        ", \"reset_to\": \"plot_hoed\"");
                case MISMATCH -> rejected(eventUuid, "mismatch", "");
                case APPLIED_WITH_BOUND_TRIGGERS -> appliedWithBoundTriggers(eventUuid);
            };
            answers.add(JsonParser.parseString(answer).getAsJsonObject());
            return CompletableFuture.completedFuture(new QuestActionEventClient.Answered(
                    QuestActionEventProtocol.parse(answer.getBytes(StandardCharsets.UTF_8))));
        }

        /** The body this stand-in last answered with, so a test can assert on the real thing. */
        JsonObject lastAnswer() {
            return answers.isEmpty() ? null : answers.get(answers.size() - 1);
        }

        private static String rejected(String eventUuid, String reason, String extra) {
            return "{\"protocol_version\": 1, \"event_uuid\": \"" + eventUuid + "\","
                    + " \"result\": \"rejected\", \"reason\": \"" + reason + "\","
                    + " \"quest_state_id\": \"" + QuestActionTestSupport.QUEST_STATE_ID + "\""
                    + extra + "}";
        }

        /**
         * The shape Rails really answers with: {@code accepted_quest.triggers} published with its
         * placeholders resolved and its bound values filled in, and node metadata carrying the
         * objective machinery -- plus the achievement client action and the presentation keys that
         * both have to survive.
         */
        private static String appliedWithBoundTriggers(String eventUuid) {
            return "{\"protocol_version\": 1, \"event_uuid\": \"" + eventUuid + "\","
                    + " \"result\": \"applied\", \"quest_id\": 45,"
                    + " \"quest_state_id\": \"" + QuestActionTestSupport.QUEST_STATE_ID + "\","
                    + " \"completed\": false, \"granted_items\": [], \"reward_delivery\": null,"
                    + " " + QuestActionTestSupport.ACHIEVEMENT_CLIENT_ACTIONS + ","
                    + " \"node\": {\"id\": 1306, \"title\": \"A Full Basket\", \"text\": \"...\","
                    + " \"type\": \"dialogue\", \"metadata\": {"
                    + "   \"journal_objective\": \"Return to Rowan with your harvest.\","
                    + "   \"action_trigger\": {\"trigger_key\": \"crop_harvested\","
                    + "     \"action\": \"crop_harvest\","
                    + "     \"match\": {\"crop_id\": \"carrot\", \"require_planter\": true},"
                    + "     \"require_bound\": {\"plot_key\": \"" + BOUND_PLOT_KEY + "\"}},"
                    + "   \"action_steps\": [{\"key\": \"crop_watered\", \"action\": \"crop_water\","
                    + "     \"require_bound\": {\"crop_cycle_uuid\": \"" + BOUND_CROP_CYCLE + "\"},"
                    + "     \"done\": true}]}},"
                    + " \"accepted_quest\": {\"id\": \"" + QuestActionTestSupport.QUEST_STATE_ID + "\","
                    + " \"quest_state_id\": \"" + QuestActionTestSupport.QUEST_STATE_ID + "\","
                    + " \"quest_id\": \"" + QuestActionTestSupport.QUEST_ID + "\","
                    + " \"quest_key\": \"rowan_farming_5\", \"name\": \"From Soil to Supper\","
                    + " \"quest_giver_name\": \"Rowan\", \"status\": \"accepted\","
                    + " \"objective\": \"Return to Rowan with your harvest.\","
                    + " \"claim_pending\": true,"
                    + " \"triggers\": {\"action\": {\"trigger_key\": \"crop_harvested\","
                    + "   \"action\": \"crop_harvest\","
                    + "   \"match\": {\"crop_id\": \"carrot\", \"require_planter\": true},"
                    + "   \"require_bound\": {\"plot_key\": \"" + BOUND_PLOT_KEY + "\","
                    + "     \"crop_cycle_uuid\": \"" + BOUND_CROP_CYCLE + "\"}}}}}";
        }
    }
}

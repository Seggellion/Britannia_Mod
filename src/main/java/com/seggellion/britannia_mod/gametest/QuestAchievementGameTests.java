package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.quest.achievement.QuestAchievementAward;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.check;
import static com.seggellion.britannia_mod.gametest.QuestActionTestSupport.checkCount;

/**
 * Rowan farming questline M10: the third result of a quest achievement, against a real
 * {@link ServerPlayer} and the server's real advancement registry.
 *
 * <p>What is being proved here is the pair of claims the milestone rests on:
 *
 * <ol>
 *   <li>one qualifying completion grants exactly one advancement and announces it once;</li>
 *   <li>every way the same authoritative answer can arrive a second time -- a replayed transition
 *       response, a repeated claim, a duplicate action event -- grants nothing more and announces
 *       nothing more, because {@code PlayerAdvancements#award} is the per-player token.</li>
 * </ol>
 *
 * <p>The last test drives the real action-event boundary end to end (a real harvest, the real
 * dispatcher, a stand-in Rails answering {@code applied} with the achievement) so the wiring is
 * proved and not only the rule. It declares its own batch because the transport seam is a
 * process-wide single slot.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestAchievementGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 3);
    private static final BlockPos PLOT = new BlockPos(2, 2, 2);

    private static final String KEY = "first_harvest";
    private static final ResourceLocation ADVANCEMENT =
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest/first_harvest");

    /** The turn-in answer Rails returns for quest 5's claim: five gold and the achievement. */
    private static final String CLAIM_RESPONSE = """
        {"success": true, "quest_state_id": "9005", "quest_id": 45, "completed": true,
         "granted_items": [{"id": "britannia_mod:gold_coin", "count": 5}],
         "client_actions": [{"type": "achievement", "key": "first_harvest",
                             "name": "First Harvest", "sound": "ui.toast.challenge_complete"}],
         "reward_delivery": null,
         "node": {"id": 1307, "title": "Rowan's Thanks", "text": "...", "type": "ending",
                  "metadata": {}}}""";

    /** What Rails answers a second claim under a NEW request uuid: a refusal, announcing nothing. */
    private static final String ALREADY_COMPLETED_RESPONSE =
        "{\"success\": false, \"error\": \"Quest already completed.\"}";

    private QuestAchievementGameTests() {
    }

    // ------------------------------------------------------------------ one completion, once

    /**
     * The qualifying claim: the advancement is granted and the announcement survives to the client.
     * Applying the very same answer again -- which is what a replayed transition response and a
     * repeated claim both are -- grants nothing more and announces nothing more.
     */
    @GameTest(template = TEMPLATE, batch = "quest_achievement_once")
    public static void oneQualifyingCompletionGrantsOneAdvancementAndAnnouncesItOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper, "rowan-m10-once");
        try {
            check(!holdsAdvancement(player), "the test did not start from a player without the advancement");

            JsonObject first = QuestAchievementAward.grantAndFilter(
                player, body(CLAIM_RESPONSE), "11111111-1111-4111-8111-111111111111", "turn_in");

            check(holdsAdvancement(player), "the qualifying claim did not grant the advancement");
            checkCount(1, announcements(first),
                "the first claim must still announce the achievement to the client");

            // The replay: the same stored answer, applied again.
            JsonObject replay = QuestAchievementAward.grantAndFilter(
                player, body(CLAIM_RESPONSE), "11111111-1111-4111-8111-111111111111", "turn_in");

            check(holdsAdvancement(player), "the replay revoked the advancement");
            check(QuestAchievementAward.grant(player, KEY) == QuestAchievementAward.Grant.ALREADY_EARNED,
                "the advancement was granted a second time, so it is not the idempotency token");
            checkCount(0, announcements(replay),
                "a replayed answer announced the achievement a second time");

            // Nothing else about the answer was touched by the filter.
            check(replay.get("quest_state_id").getAsString().equals("9005"),
                "filtering the announcement changed the rest of the response");
            checkCount(1, replay.getAsJsonArray("granted_items").size(),
                "filtering the announcement changed the grant");

            helper.succeed();
        } finally {
            disconnect(helper, player);
        }
    }

    /**
     * A duplicate action event is answered from its stored row -- client actions and all -- so it
     * arrives at this boundary identical to the event that was applied. It must announce nothing.
     */
    @GameTest(template = TEMPLATE, batch = "quest_achievement_duplicate")
    public static void aDuplicateActionEventAnnouncesNothingAndGrantsNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper, "rowan-m10-duplicate");
        try {
            String applied = "{\"protocol_version\": 1, \"event_uuid\": \"22222222-2222-4222-8222-222222222222\","
                + " \"result\": \"applied\", \"quest_id\": 45, \"quest_state_id\": \"9005\","
                + " \"completed\": false, \"granted_items\": [], \"reward_delivery\": null,"
                + " " + QuestActionTestSupport.ACHIEVEMENT_CLIENT_ACTIONS + "}";
            String duplicate = applied.replace("\"result\": \"applied\"", "\"result\": \"duplicate\"");

            JsonObject firstAnswer = QuestAchievementAward.grantAndFilter(
                player, body(applied), "33333333-3333-4333-8333-333333333333", "action_event");
            check(holdsAdvancement(player), "the applied action event did not grant the advancement");
            checkCount(1, announcements(firstAnswer), "the applied action event announced nothing");

            JsonObject duplicateAnswer = QuestAchievementAward.grantAndFilter(
                player, body(duplicate), "44444444-4444-4444-8444-444444444444", "action_event");
            checkCount(0, announcements(duplicateAnswer),
                "the duplicate answer announced the achievement a second time");
            check(holdsAdvancement(player), "the duplicate revoked the advancement");

            helper.succeed();
        } finally {
            disconnect(helper, player);
        }
    }

    /**
     * A fresh request uuid against a finished quest is refused by Rails rather than replayed, so
     * this boundary is reached with {@code success:false} and no client actions at all: nothing to
     * grant and nothing to announce, whether or not the player already holds the advancement.
     */
    @GameTest(template = TEMPLATE, batch = "quest_achievement_refused")
    public static void aRefusedSecondClaimGrantsNothingAndAnnouncesNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper, "rowan-m10-refused");
        try {
            JsonObject refusal = body(ALREADY_COMPLETED_RESPONSE);
            JsonObject answer = QuestAchievementAward.grantAndFilter(
                player, refusal, "55555555-5555-4555-8555-555555555555", "turn_in");

            check(!holdsAdvancement(player),
                "a refused claim granted the advancement; the refusal is not a completion");
            checkCount(0, announcements(answer), "a refused claim announced an achievement");
            check(answer == refusal, "a response with nothing to announce must be forwarded unchanged");

            helper.succeed();
        } finally {
            disconnect(helper, player);
        }
    }

    /**
     * Attribution: the advancement and its announcement belong to the player the completion was
     * for. One player earning it neither gives it to another nor silences the other's first time.
     */
    @GameTest(template = TEMPLATE, batch = "quest_achievement_per_player")
    public static void theAdvancementAndItsAnnouncementArePerPlayer(GameTestHelper helper) {
        ServerPlayer harvester = player(helper, "rowan-m10-harvester");
        ServerPlayer bystander = player(helper, "rowan-m10-bystander");
        try {
            JsonObject forHarvester = QuestAchievementAward.grantAndFilter(
                harvester, body(CLAIM_RESPONSE), "66666666-6666-4666-8666-666666666666", "turn_in");
            checkCount(1, announcements(forHarvester), "the harvester was not told about their own achievement");
            check(holdsAdvancement(harvester), "the harvester did not get the advancement");
            check(!holdsAdvancement(bystander),
                "another player's completion granted this player the advancement");

            // The bystander's own qualifying completion is still their first time.
            JsonObject forBystander = QuestAchievementAward.grantAndFilter(
                bystander, body(CLAIM_RESPONSE), "77777777-7777-4777-8777-777777777777", "turn_in");
            checkCount(1, announcements(forBystander),
                "one player having earned the advancement silenced another player's first time");
            check(holdsAdvancement(bystander), "the bystander's own completion did not grant it");

            helper.succeed();
        } finally {
            disconnect(helper, bystander);
            disconnect(helper, harvester);
        }
    }

    /**
     * An achievement with no advancement resource has no token to decide with, so the announcement
     * is left exactly as it was before M10 rather than silently dropped.
     */
    @GameTest(template = TEMPLATE, batch = "quest_achievement_no_advancement")
    public static void anAchievementWithNoAdvancementIsStillAnnounced(GameTestHelper helper) {
        ServerPlayer player = player(helper, "rowan-m10-unknown");
        try {
            String unknown = CLAIM_RESPONSE.replace("first_harvest", "no_such_achievement");
            check(QuestAchievementAward.grant(player, "no_such_achievement")
                    == QuestAchievementAward.Grant.NO_ADVANCEMENT,
                "an unknown achievement must report NO_ADVANCEMENT, not pretend it granted");

            JsonObject answer = QuestAchievementAward.grantAndFilter(
                player, body(unknown), "88888888-8888-4888-8888-888888888888", "turn_in");
            checkCount(1, announcements(answer),
                "an achievement this pack has no advancement for was silently dropped");

            helper.succeed();
        } finally {
            disconnect(helper, player);
        }
    }

    // ------------------------------------------------------------------ the real boundary

    /**
     * End to end through the M4 action-event boundary: a real harvest, the real outbox and
     * dispatcher, and a Rails that answers {@code applied} with the achievement. The advancement is
     * granted by {@code QuestActionDispatcher#installState} and by nothing the client said.
     */
    @GameTest(template = TEMPLATE, batch = "quest_achievement_action_event", timeoutTicks = 120)
    public static void harvestingThroughTheActionEventBoundaryGrantsTheAdvancement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestActionTestSupport.RecordingRails rails = QuestActionTestSupport.install(
            level.getServer(), QuestActionTestSupport.Answer.APPLIED_WITH_ACHIEVEMENT);
        ServerPlayer player = QuestActionTestSupport.survivalPlayer(helper, "rowan-m10-boundary", STAND);
        try {
            QuestActionTestSupport.subscribeToEveryAction(player);
            check(!holdsAdvancement(player), "the test did not start from a player without the advancement");

            harvestOneCarrot(helper, player);

            checkCount(1, rails.count(), "the harvest did not reach the action-event boundary once");
            check(holdsAdvancement(player),
                "the action-event boundary did not grant the advancement the answer announced");

            // The same answer again: the outbox row is new, the advancement is not.
            QuestActionTestSupport.subscribeToEveryAction(player);
            harvestOneCarrot(helper, player);
            checkCount(2, rails.count(), "the second harvest did not reach the boundary");
            check(QuestAchievementAward.grant(player, KEY) == QuestAchievementAward.Grant.ALREADY_EARNED,
                "the second answer granted the advancement again");

            helper.succeed();
        } finally {
            QuestActionTestSupport.disconnect(player);
            QuestActionTestSupport.uninstall(level.getServer());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static JsonObject body(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** How many achievement announcements a forwarded response still carries. */
    private static int announcements(JsonObject response) {
        return QuestAchievementAward.announced(response).size();
    }

    private static boolean holdsAdvancement(ServerPlayer player) {
        AdvancementHolder holder = player.server.getAdvancements().get(ADVANCEMENT);
        check(holder != null, "britannia_mod:quest/first_harvest is not loaded on this server");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        player.getInventory().clearContent();
        return player;
    }

    private static void disconnect(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** The same gesture {@code QuestActionOutboxGameTests} reports a harvest with. */
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
            ItemStack.EMPTY, InteractionHand.MAIN_HAND, "rowan_m10_test");
    }

}

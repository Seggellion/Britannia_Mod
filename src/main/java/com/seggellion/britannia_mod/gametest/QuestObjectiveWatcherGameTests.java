package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestActionTelemetry;
import com.seggellion.britannia_mod.quest.QuestEntryParser;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;
import com.seggellion.britannia_mod.quest.QuestProxyService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Milestone 6 (findings Q-02, Q-05, Q-06): objectives are the server's to decide.
 *
 * <p>Location, pickup and destroy objectives used to be detected on the client, which then
 * asserted the trigger. The client drove them from a single static holding ONE quest that nothing
 * restores at login, so environmental progress was dead after every relog and only ever applied
 * to the most recently touched quest; a modified client could claim any objective; and the
 * location check re-fired every twenty ticks with no debounce.
 *
 * <p>These tests pin the parts that can be asserted without a Rails server: that objectives reach
 * the server's journal, that they cover EVERY active quest rather than one, that they are read
 * from the journal rather than from a client, and that a client-sent trigger is now refused.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestObjectiveWatcherGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private QuestObjectiveWatcherGameTests() {
    }

    /** Q-05: the client no longer gets a say in whether an objective was met. */
    @GameTest(template = TEMPLATE)
    public static void aClientAssertedTriggerIsRefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        ServerQuestTable.replaceFromBootstrap(player, List.of(entryWithLocation(1L, "77", "arrived")));

        try {
            QuestProxyService.handle(player, new QuestActionC2SPayload(
                1L, QuestActionC2SPayload.Action.TRIGGER, 1L, "arrived", -1,
                new UUID(0L, 0L), UUID.randomUUID().toString()));
        } catch (RuntimeException mockConnectionLimit) {
            if (!String.valueOf(mockConnectionLimit.getMessage()).contains("may not be sent to the client")) {
                throw mockConnectionLimit;
            }
        }

        QuestActionTelemetry.Rejection rejection = QuestActionTelemetry.lastRejection(player.getUUID())
            .orElseThrow(() -> new GameTestAssertException("a client trigger was accepted silently"));
        check("client_trigger_not_authoritative".equals(rejection.reason()),
            "expected reason=client_trigger_not_authoritative, got " + rejection.reason());
        helper.succeed();
    }

    /** Q-02: objectives live on the journal, so EVERY active quest has its own. */
    @GameTest(template = TEMPLATE)
    public static void everyActiveQuestCarriesItsOwnObjectives(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerQuestTable.replaceFromBootstrap(player, List.of(
            entryWithLocation(1L, "77", "arrived_britain"),
            entryWithLocation(2L, "78", "arrived_jhelom")));

        check("arrived_britain".equals(ServerQuestTable.triggersFor(player.getUUID(), "77").location().triggerKey()),
            "the first quest lost its objective");
        check("arrived_jhelom".equals(ServerQuestTable.triggersFor(player.getUUID(), "78").location().triggerKey()),
            "the second quest lost its objective -- only one quest could progress before M6");
        helper.succeed();
    }

    /** Q-06: advancing a node replaces the objective, so it cannot fire again next tick. */
    @GameTest(template = TEMPLATE)
    public static void advancingTheNodeReplacesTheObjective(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerQuestTable.replaceFromBootstrap(player, List.of(entryWithLocation(1L, "77", "arrived")));

        ServerQuestTable.updateTriggers(player, "77", QuestObjectiveTriggers.NONE);

        check(ServerQuestTable.triggersFor(player.getUUID(), "77").isEmpty(),
            "a completed objective must not remain on the journal");
        helper.succeed();
    }

    /** The journal payload Rails publishes is parsed into objectives the server can act on. */
    @GameTest(template = TEMPLATE)
    public static void objectivesAreParsedFromTheJournalPayload(GameTestHelper helper) {
        JsonObject root = JsonParser.parseString("""
            {"accepted_quests":[{"quest_state_id":"501","quest_id":"9","name":"Deliver",
             "triggers":{
               "location":{"trigger_key":"arrived","min_x":-5,"min_y":60,"min_z":-5,
                           "max_x":5,"max_y":70,"max_z":5},
               "pickup":{"trigger_key":"took_relic","item_tag":"magic_ring"},
               "destroy":{"trigger_key":"burned","item_tag":"cursed_doll","min_x":0,"min_y":0,
                          "max_x":10,"max_y":10,"max_z":10,"min_z":0}}}]}
            """).getAsJsonObject();

        List<ClientQuestEntry> entries = QuestEntryParser.parseAcceptedQuests(root);
        check(entries.size() == 1, "expected one journal entry, got " + entries.size());

        QuestObjectiveTriggers triggers = entries.get(0).triggers();
        check(triggers.location() != null && "arrived".equals(triggers.location().triggerKey()),
            "the location objective was lost in parsing");
        check(triggers.location().contains(new BlockPos(0, 65, 0)),
            "a position inside the volume was not recognised");
        check(!triggers.location().contains(new BlockPos(100, 65, 0)),
            "a position outside the volume was recognised");
        check(triggers.pickup() != null && "magic_ring".equals(triggers.pickup().itemTag()),
            "the pickup objective was lost in parsing");
        check(triggers.destroy() != null && "burned".equals(triggers.destroy().triggerKey()),
            "the destroy objective was lost in parsing");
        helper.succeed();
    }

    /** An objective without a trigger key is dropped: the server never claims a nameless one. */
    @GameTest(template = TEMPLATE)
    public static void anIncompleteObjectiveIsDropped(GameTestHelper helper) {
        JsonObject metadata = JsonParser.parseString("""
            {"location_trigger":{"min_x":0,"max_x":5},
             "pickup_trigger":{"trigger_key":"took"},
             "destroy_trigger":{"item_tag":"doll"}}
            """).getAsJsonObject();

        QuestObjectiveTriggers triggers = QuestObjectiveTriggers.fromNodeMetadata(metadata);

        check(triggers.location() == null, "a location objective with no trigger key must be dropped");
        check(triggers.pickup() == null, "a pickup objective with no item tag must be dropped");
        check(triggers.destroy() == null, "a destroy objective with no trigger key must be dropped");
        check(triggers.isEmpty(), "nothing should have survived");
        helper.succeed();
    }

    /** Objectives are server-side state and must never be written onto the client's journal. */
    @GameTest(template = TEMPLATE)
    public static void objectivesAreNotSentToTheClient(GameTestHelper helper) {
        ClientQuestEntry entry = entryWithLocation(1L, "77", "arrived");
        net.minecraft.network.FriendlyByteBuf buffer =
            new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload payload =
            new com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload(List.of(entry));
        com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload.STREAM_CODEC
            .encode(buffer, payload);
        ClientQuestEntry decoded = com.seggellion.britannia_mod.network.payload.ClientboundSyncQuestsPayload
            .STREAM_CODEC.decode(buffer).quests().get(0);

        check(decoded.questStateId().equals(entry.questStateId()), "the journal entry did not survive the wire");
        check(decoded.triggers().isEmpty(),
            "quest objectives must not travel to the client: they are its solutions");
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static ClientQuestEntry entryWithLocation(long questId, String questStateId, String triggerKey) {
        return new ClientQuestEntry(questStateId, Long.toString(questId), "quest_" + questId,
            "Giver", "Quest " + questId, "", "", "accepted",
            new QuestObjectiveTriggers(
                new QuestObjectiveTriggers.Location(triggerKey,
                    new BlockPos(-5, 60, -5), new BlockPos(5, 70, 5)),
                null, null));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

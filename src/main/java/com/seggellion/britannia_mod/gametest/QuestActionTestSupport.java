package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.action.QuestAction;
import com.seggellion.britannia_mod.quest.action.QuestActionDispatcher;
import com.seggellion.britannia_mod.quest.action.QuestActionEventClient;
import com.seggellion.britannia_mod.quest.action.QuestActionEventProtocol;
import com.seggellion.britannia_mod.quest.action.QuestActionOutbox;
import com.seggellion.britannia_mod.quest.action.QuestActionOutboxEntry;
import com.seggellion.britannia_mod.quest.action.QuestActionOutboxStore;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared scaffolding for the Rowan questline M5 GameTests: a journal that subscribes to farming
 * actions, and a stand-in for the Rails transport.
 *
 * <p><b>The transport seam is process-wide and single-slot.</b> Every test that installs one must
 * therefore declare its own {@code batch}, or two tests running together will read each other's
 * recorder. Every test here does, and every one of them restores the real transport in a
 * {@code finally}.
 */
final class QuestActionTestSupport {
    static final String QUEST_STATE_ID = "9005";
    static final String QUEST_ID = "45";

    private QuestActionTestSupport() {}

    /** What the stand-in Rails does with the next event. */
    enum Answer {
        /** {@code 200 irrelevant}: terminal, installs nothing, so the row is simply removed. */
        IRRELEVANT,
        /** {@code 200 applied} with the stage-5 journal entry, so the state-install path runs. */
        APPLIED,
        /** A retryable transport failure: the outage the outbox exists for. */
        OUTAGE,
        /** A 404 with no contract code: an older Rails without the route at all. */
        ENDPOINT_MISSING
    }

    /** What the durable outbox looked like at the exact moment one attempt was made. */
    record OutboxAtSend(UUID eventUuid, boolean persisted, boolean dirty) {}

    /** Records every event body and answers whatever the test currently asks for. */
    static final class RecordingRails implements QuestActionDispatcher.Sender {
        private final List<JsonObject> sent = new CopyOnWriteArrayList<>();
        private final List<OutboxAtSend> outboxAtSend = new CopyOnWriteArrayList<>();
        private volatile Answer answer;

        RecordingRails(Answer answer) {
            this.answer = answer;
        }

        /**
         * The state of the durable outbox as each attempt left, in order. Section 2.2 requires the
         * row to be written AND flushed before the first HTTP attempt, and this is the only place
         * a test can observe that ordering rather than assume it.
         */
        List<OutboxAtSend> outboxAtSend() {
            return List.copyOf(outboxAtSend);
        }

        void answerWith(Answer next) {
            this.answer = next;
        }

        List<JsonObject> sent() {
            return List.copyOf(sent);
        }

        int count() {
            return sent.size();
        }

        /** Every event this recorder was given for one action, in order. */
        List<JsonObject> forAction(QuestAction action) {
            List<JsonObject> found = new ArrayList<>();
            for (JsonObject request : sent) {
                if (action.wireName().equals(request.get("action").getAsString())) found.add(request);
            }
            return found;
        }

        void clear() {
            sent.clear();
        }

        @Override
        public CompletableFuture<QuestActionEventClient.SendResult> send(MinecraftServer server, byte[] body) {
            JsonObject request = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            sent.add(request);
            if (server != null) {
                UUID eventUuid = UUID.fromString(request.get("event_uuid").getAsString());
                QuestActionOutboxStore store = QuestActionOutbox.store(server);
                outboxAtSend.add(new OutboxAtSend(eventUuid, store.find(eventUuid).isPresent(), store.isDirty()));
            }
            return CompletableFuture.completedFuture(switch (answer) {
                case IRRELEVANT -> answered(irrelevant(request.get("event_uuid").getAsString()));
                case APPLIED -> answered(applied(request.get("event_uuid").getAsString()));
                case OUTAGE -> new QuestActionEventClient.Failure("service_unavailable");
                case ENDPOINT_MISSING -> new QuestActionEventClient.EndpointUnsupported();
            });
        }

        private static QuestActionEventClient.SendResult answered(String body) {
            return new QuestActionEventClient.Answered(
                QuestActionEventProtocol.parse(body.getBytes(StandardCharsets.UTF_8)));
        }

        private static String irrelevant(String eventUuid) {
            return "{\"protocol_version\": 1, \"event_uuid\": \"" + eventUuid + "\", \"result\": \"irrelevant\"}";
        }

        private static String applied(String eventUuid) {
            return "{\"protocol_version\": 1, \"event_uuid\": \"" + eventUuid + "\","
                + " \"result\": \"applied\", \"quest_id\": 45, \"quest_state_id\": \"" + QUEST_STATE_ID + "\","
                + " \"completed\": false, \"granted_items\": [], \"reward_delivery\": null,"
                + " \"node\": {\"id\": 1306, \"title\": \"A Full Basket\", \"text\": \"...\","
                + " \"type\": \"dialogue\", \"metadata\": {}},"
                + " \"accepted_quest\": {\"id\": \"" + QUEST_STATE_ID + "\","
                + " \"quest_state_id\": \"" + QUEST_STATE_ID + "\", \"quest_id\": \"" + QUEST_ID + "\","
                + " \"quest_key\": \"rowan_farming_5\", \"name\": \"From Soil to Supper\","
                + " \"quest_giver_name\": \"Rowan\", \"status\": \"accepted\", \"triggers\": {}}}";
        }
    }

    /** Installs the recorder and returns it. Always restore with {@link #uninstall}. */
    static RecordingRails install(MinecraftServer server, Answer answer) {
        RecordingRails rails = new RecordingRails(answer);
        QuestActionDispatcher.installSender(rails);
        QuestActionDispatcher.resetEndpointSupport(server);
        QuestActionOutbox.store(server).removeAll();
        return rails;
    }

    static void uninstall(MinecraftServer server) {
        QuestActionDispatcher.resetSender();
        QuestActionDispatcher.resetClock();
        QuestActionDispatcher.resetEndpointSupport(server);
        QuestActionOutbox.store(server).removeAll();
    }

    /**
     * A journal entry subscribing to every farming action as an ordered step, plus the advancing
     * harvest trigger. It is deliberately permissive -- no {@code match}, no {@code require_bound}
     * -- so a test about ONE success point is not also a test of the matcher, which
     * {@code QuestActionSubscriptionsTest} covers against the frozen fixture.
     */
    static void subscribeToEveryAction(ServerPlayer player) {
        StringBuilder steps = new StringBuilder();
        for (QuestAction action : QuestAction.values()) {
            if (action == QuestAction.CROP_HARVEST) continue;
            if (steps.length() > 0) steps.append(',');
            steps.append("{\"key\": \"step_").append(action.wireName())
                .append("\", \"action\": \"").append(action.wireName()).append("\"}");
        }
        installJournal(player, "{\"triggers\": {\"action\": {\"trigger_key\": \"crop_harvested\","
            + " \"action\": \"crop_harvest\"}, \"steps\": [" + steps + "]}}");
    }

    /** The stage-5 subscription: the harvest must be of this crop, this cycle, by this planter. */
    static void subscribeToBoundHarvest(ServerPlayer player, String cropId, String plotKey, UUID cropCycleUuid) {
        installJournal(player, "{\"triggers\": {\"action\": {\"trigger_key\": \"crop_harvested\","
            + " \"action\": \"crop_harvest\","
            + " \"match\": {\"crop_id\": \"" + cropId + "\", \"require_planter\": true},"
            + " \"require_bound\": {\"plot_key\": \"" + plotKey + "\","
            + " \"crop_cycle_uuid\": \"" + cropCycleUuid + "\"}}}}");
    }

    /** A journal with the three legacy observers and no farming subscription at all. */
    static void subscribeToLegacyObserversOnly(ServerPlayer player, String itemTag) {
        installJournal(player, "{\"triggers\": {\"pickup\": {\"trigger_key\": \"picked_up\","
            + " \"item_tag\": \"" + itemTag + "\"}}}");
    }

    static void installJournal(ServerPlayer player, String triggersJson) {
        JsonObject entry = JsonParser.parseString(triggersJson).getAsJsonObject();
        ServerQuestTable.replaceFromBootstrap(player, List.of(new ClientQuestEntry(
            QUEST_STATE_ID, QUEST_ID, "rowan_farming_5", "Rowan", "From Soil to Supper", "", "", "accepted",
            QuestObjectiveTriggers.fromJournalEntry(entry))));
    }

    static void clearJournal(ServerPlayer player) {
        ServerQuestTable.replaceFromBootstrap(player, List.of());
    }

    static QuestObjectiveTriggers currentTriggers(ServerPlayer player) {
        return ServerQuestTable.triggersFor(player.getUUID(), QUEST_STATE_ID);
    }

    /** The player's outbox rows, oldest first. */
    static List<QuestActionOutboxEntry> outbox(ServerPlayer player) {
        return QuestActionOutbox.entriesFor(player.server, player.getUUID());
    }

    static String subjectText(JsonObject request, String field) {
        JsonObject subject = request.getAsJsonObject("subject");
        return subject.has(field) ? subject.get(field).getAsString() : "";
    }

    /**
     * A named survival player standing at {@code stand}, with Farming loaded.
     *
     * <p>The skill matters: {@code FarmingCultivationGate} refuses planting outright when a
     * player's skill data never loaded, and in a GameTest server it never does -- there is no Rails
     * to load it from. {@code applyConfirmedValue} is the production path for "Rails already
     * committed this value"; it writes locally and posts nothing, which is exactly what a test
     * needs to reach the planting transaction it is actually about.
     */
    static ServerPlayer survivalPlayer(net.minecraft.gametest.framework.GameTestHelper helper,
                                       String name, net.minecraft.core.BlockPos stand) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), name);
        player.getInventory().clearContent();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        net.minecraft.core.BlockPos absolute = helper.absolutePos(stand);
        player.setPos(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        com.seggellion.britannia_mod.skill.SkillManager.applyConfirmedValue(player,
            com.seggellion.britannia_mod.farming.FarmingSkill.SKILL_ID, 50.0f);
        return player;
    }

    static void disconnect(ServerPlayer player) {
        ServerQuestTable.forget(player.getUUID());
        player.server.getPlayerList().remove(player);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    static void checkCount(int expected, int actual, String message) {
        check(expected == actual, message + " (expected " + expected + ", saw " + actual + ")");
    }
}

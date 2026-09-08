package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.client.gui.QuestTriggerResultPresentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trigger result advances the quest, so the client's journal has to advance with it.
 *
 * <h2>What was wrong</h2>
 * {@link ClientQuestTable} was written by two things only: the login/quit sync payload, and talking
 * to a quest giver. {@code QuestTriggerResultS2CPayload} -- the packet that says an objective just
 * completed -- wrote neither, even though it carries the advanced journal entry. Three consequences,
 * all of them things a player sees:
 * <ul>
 *   <li>the quiet notice read {@code detail().objective()} and therefore printed the <b>same</b>
 *       line after hoeing, after fertilizing, after planting and after watering;</li>
 *   <li>{@code claimPending} was stale-false, so "Return to Rowan to claim your reward" never fired
 *       at the moment it exists for;</li>
 *   <li>the journal's next action, progress and claim badge were frozen between conversations.</li>
 * </ul>
 *
 * <p>The pipeline below is the real one -- {@link QuestEntryParser#parseAcceptedQuests} over the
 * body the packet carries, into {@link ClientQuestTable#updateFromTriggerResult}, read back out and
 * composed by {@link QuestTriggerResultPresentation#objectiveNotice} -- with only the
 * {@code Minecraft}-typed shell of {@code ClientNetworkHandler} left out, because no test harness
 * here can construct one. That the shell calls this pipeline, and calls it before it composes the
 * notice, is asserted against the source at the bottom.
 */
class QuestTriggerResultJournalRefreshTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final String QUEST_ID = "4412";
    private static final String STATE_ID = "state-rowan-5";

    @BeforeEach
    @AfterEach
    void emptyTheJournal() {
        ClientQuestTable.clear();
    }

    // ---------------------------------------------------------------- the objective line

    @Test
    void aSecondObjectiveSaysSomethingDifferentFromTheFirst() {
        arrive(triggerResult("Hoe a public plot with your Farming Hoe.", false));
        QuestTriggerResultPresentation.Notice first = notice();

        arrive(triggerResult("Apply your fertilized dirt to the plot.", false));
        QuestTriggerResultPresentation.Notice second = notice();

        assertEquals(QuestTriggerResultPresentation.NoticeKind.ADVANCED, first.kind());
        assertEquals("Hoe a public plot with your Farming Hoe.", first.argument());
        assertEquals("Apply your fertilized dirt to the plot.", second.argument());
        assertNotEquals(first.argument(), second.argument(),
                "every farming step printed the same sentence, which is the defect");
    }

    @Test
    void allFourFarmingStepsProduceFourDistinctLines() {
        List<String> objectives = List.of(
                "Hoe a public plot with your Farming Hoe.",
                "Apply your fertilized dirt to the plot.",
                "Plant your carrot seed.",
                "Water the plot with your watering can.");
        for (String objective : objectives) {
            arrive(triggerResult(objective, false));
            assertEquals(objective, notice().argument(),
                    "the notice did not follow the advance to " + objective);
        }
    }

    @Test
    void theFinalObjectiveSetsTheClaimPendingStateAndSwitchesTheSentence() {
        arrive(triggerResult("Water the plot with your watering can.", false));
        assertEquals(QuestTriggerResultPresentation.NoticeKind.ADVANCED, notice().kind());

        arrive(triggerResult("Return to Rowan.", true));

        ClientQuestEntry entry = entry();
        assertTrue(entry.detail().claimPending(),
                "the journal never learned the quest was ready to claim");
        QuestTriggerResultPresentation.Notice claim = notice();
        assertEquals(QuestTriggerResultPresentation.NoticeKind.READY_TO_CLAIM, claim.kind());
        assertEquals(QuestScreenText.OBJECTIVE_READY_TO_CLAIM, claim.translationKey());
        assertEquals("Rowan", claim.argument(), "the claim line names the giver, not the step");
    }

    // ---------------------------------------------------------------- the journal itself

    @Test
    void theJournalRowFollowsTheAdvanceRatherThanWaitingForAConversation() {
        arrive(triggerResult("Hoe a public plot with your Farming Hoe.", false, 0));
        assertEquals(0, entry().detail().completedSteps());

        arrive(triggerResult("Apply your fertilized dirt to the plot.", false, 2));
        ClientQuestEntry entry = entry();
        assertEquals("Apply your fertilized dirt to the plot.", entry.detail().objective());
        assertEquals(2, entry.detail().completedSteps(),
                "the ordered progress was frozen at whatever the last sync said");
        assertEquals(1, ClientQuestTable.snapshot().size(),
                "a refreshed entry replaces its old self rather than joining it");
    }

    @Test
    void aResponseCarryingNoJournalEntryLeavesTheJournalAlone() {
        // QuestObjectiveWatcher and QuestEventHandlers re-serialize the parsed QuestResponse, which
        // models no accepted_quest at all. Absent is "this response said nothing about the journal",
        // never "the journal is empty" -- blanking it here would lose a row the player still has.
        arrive(triggerResult("Plant your carrot seed.", false));
        List<ClientQuestEntry> before = ClientQuestTable.snapshot();

        arrive(JsonParser.parseString("{\"success\":true,\"quest_id\":4412}").getAsJsonObject());

        assertEquals(before, ClientQuestTable.snapshot());
        assertEquals("Plant your carrot seed.", entry().detail().objective());
    }

    @Test
    void anEntryWithNoQuestStateIdIsRefusedTheSameWayASyncRefusesIt() {
        JsonObject root = triggerResult("Plant your carrot seed.", false);
        root.getAsJsonObject("accepted_quest").remove("quest_state_id");

        arrive(root);

        assertTrue(ClientQuestTable.snapshot().isEmpty(),
                "a non-authoritative entry got in through the trigger-result door");
    }

    // ---------------------------------------------------------------- the notice, on its own

    @Test
    void withNoObjectiveAtAllTheNoticeFallsBackToTheQuestTitle() {
        QuestTriggerResultPresentation.Notice notice =
                QuestTriggerResultPresentation.objectiveNotice("  ", "From Soil to Supper", false, "Rowan");
        assertEquals(QuestTriggerResultPresentation.NoticeKind.ADVANCED, notice.kind());
        assertEquals("From Soil to Supper", notice.argument());
    }

    @Test
    void theClaimLineStripsTheApiIdentitySuffixAndReportsAnUnnamedGiverAsBlank() {
        assertEquals("Rowan", QuestTriggerResultPresentation
                .objectiveNotice("anything", "", true, "Rowan:npc_4417").argument());
        // Blank is the signal to substitute a translated stand-in; it is never printed as itself.
        assertEquals("", QuestTriggerResultPresentation
                .objectiveNotice("anything", "", true, null).argument());
    }

    @Test
    void claimPendingWinsOverWhateverObjectiveIsStillNamed() {
        QuestTriggerResultPresentation.Notice notice = QuestTriggerResultPresentation
                .objectiveNotice("Water the plot.", "From Soil to Supper", true, "Rowan");
        assertEquals(QuestTriggerResultPresentation.NoticeKind.READY_TO_CLAIM, notice.kind());
        assertEquals(QuestScreenText.OBJECTIVE_READY_TO_CLAIM, notice.translationKey());
    }

    // ---------------------------------------------------------------- the wiring

    @Test
    void theHandlerRefreshesTheJournalBeforeItComposesAnything() {
        String handler = read(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/network/ClientNetworkHandler.java"));
        assertTrue(handler.contains("QuestEntryParser.parseAcceptedQuests("),
                "the trigger result path does not read the journal entry it was sent");
        assertTrue(handler.contains("ClientQuestTable.updateFromTriggerResult("),
                "the trigger result path does not apply the journal entry it was sent");
        assertTrue(handler.contains("QuestTriggerResultPresentation.objectiveNotice("),
                "the notice is composed inline again instead of through the pure decision");

        int refresh = handler.indexOf("refreshJournalFromTriggerResult(payload.responseJson()");
        int notice = handler.indexOf("case QUIET_NOTICE ->");
        assertTrue(refresh >= 0, "nothing refreshes the journal from the trigger result");
        assertTrue(notice > refresh,
                "the notice is composed before the journal is brought level with the advance");
    }

    @Test
    void theServerMirrorCanCarryTheAdvancedDetailToo() {
        // ServerQuestTable.updateTriggers wrote withTriggers and left detail alone, so even a sync
        // raised after an advance carried pre-advance detail. The dispatcher now feeds both halves
        // from the one authoritative response.
        String table = read(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/quest/ServerQuestTable.java"));
        assertTrue(table.contains("public static void updateJournal("),
                "the server mirror still has no way to update the journal detail");

        String dispatcher = read(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/quest/action/QuestActionDispatcher.java"));
        assertTrue(dispatcher.contains("ServerQuestTable.updateJournal("),
                "the dispatcher still updates only the triggers after an advance");
        assertTrue(dispatcher.contains("advanced.detail()"),
                "the dispatcher does not pass the advanced journal detail through");
    }

    // ---------------------------------------------------------------- helpers

    /** What {@code ClientNetworkHandler.refreshJournalFromTriggerResult} does with a body. */
    private static void arrive(JsonObject root) {
        ClientQuestTable.updateFromTriggerResult(QuestEntryParser.parseAcceptedQuests(root));
    }

    private static ClientQuestEntry entry() {
        ClientQuestEntry entry = ClientQuestTable.findByQuestId(QUEST_ID);
        assertNotNull(entry, "the journal has no row for quest " + QUEST_ID);
        return entry;
    }

    private static QuestTriggerResultPresentation.Notice notice() {
        ClientQuestEntry entry = entry();
        return QuestTriggerResultPresentation.objectiveNotice(
                entry.detail().objective(), "From Soil to Supper",
                entry.detail().claimPending(), entry.questGiverName());
    }

    private static JsonObject triggerResult(String objective, boolean claimPending) {
        return triggerResult(objective, claimPending, 0);
    }

    /** The shape {@code QuestActionDispatcher} ships: the raw Rails body, journal entry and all. */
    private static JsonObject triggerResult(String objective, boolean claimPending, int stepsDone) {
        StringBuilder progress = new StringBuilder();
        String[] labels = {"Hoe the plot", "Fertilize it", "Plant the seed", "Water it", "Harvest"};
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) progress.append(',');
            progress.append("{\"key\":\"step_").append(i).append("\",\"label\":\"")
                    .append(labels[i]).append("\",\"done\":").append(i < stepsDone).append('}');
        }

        String json = """
                {
                  "success": true,
                  "quest_id": %s,
                  "quest_state_id": "%s",
                  "accepted_quest": {
                    "quest_state_id": "%s",
                    "quest_id": "%s",
                    "quest_key": "from_soil_to_supper",
                    "quest_giver_name": "Rowan:npc_4417",
                    "name": "From Soil to Supper",
                    "status": "accepted",
                    "objective": "%s",
                    "claim_pending": %s,
                    "stage": {"questline_key": "rowan", "index": 5, "count": 5, "label": "Plant"},
                    "progress": [%s]
                  }
                }
                """.formatted(QUEST_ID, STATE_ID, STATE_ID, QUEST_ID, objective,
                        claimPending, progress);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    @Test
    void theFixtureThisTestUsesReallyDoesParse() {
        // A guard on the guard: a typo in the JSON above would make every assertion here pass by
        // parsing nothing at all.
        arrive(triggerResult("Hoe a public plot.", false, 1));
        assertFalse(ClientQuestTable.snapshot().isEmpty(), "the sample body parsed to nothing");
        assertEquals(STATE_ID, entry().questStateId());
        assertEquals(5, entry().detail().progress().size());
        assertEquals(5, entry().detail().stage().count());
    }
}

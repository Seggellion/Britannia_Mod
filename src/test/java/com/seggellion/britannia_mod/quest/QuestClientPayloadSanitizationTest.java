package com.seggellion.britannia_mod.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.gui.QuestNodePresentation;
import com.seggellion.britannia_mod.client.gui.RowanQuestContent;
import com.seggellion.britannia_mod.quest.achievement.QuestAchievementAward;
import com.seggellion.britannia_mod.quest.network.QuestClientPayload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M11, deferred defect 2: the client is no longer handed the answer key.
 *
 * <h2>The body under test</h2>
 * Not an invented one. The frozen {@code action_event_response_applied.json} carries an empty
 * {@code triggers} object, so it cannot show the exposure; the frozen
 * {@code journal_entry_stage5.json} carries the real published form -- {@code require_bound} already
 * reduced to <b>the plot key and the crop-cycle UUID currently bound</b> -- and
 * {@code node_metadata_stage5.json} carries the real {@code action_trigger} and {@code action_steps}.
 * The body assembled here is the applied response with those two fixtures in the two places Rails
 * puts them, which is exactly the shape {@code QuestActionDispatcher} was forwarding. No fixture is
 * modified; they are read and composed.
 *
 * <h2>What has to survive</h2>
 * M8's journal refresh reads {@code accepted_quest} out of this same body, and M10's achievement
 * path reads and rewrites {@code client_actions}. Both are asserted here against the sanitized
 * body, because a sanitizer that breaks either is worse than the exposure it closes.
 */
class QuestClientPayloadSanitizationTest {

    /** The two bound values the published journal entry resolves. Neither may cross the wire. */
    private static final String BOUND_PLOT_KEY = "minecraft:overworld:1203:64:-488";
    private static final String BOUND_CROP_CYCLE = "b1b3d4a0-1f7e-4c0d-8f4c-6a2e9c1f0a11";

    @BeforeEach
    @AfterEach
    void emptyTheJournal() {
        ClientQuestTable.clear();
    }

    // ---------------------------------------------------------------- a guard on the guard

    @Test
    void theBodyThisTestUsesReallyDoesCarryTheAnswerKey() {
        // Without this, every assertion below could pass by sanitizing a body that never held
        // anything worth stripping.
        String raw = body().toString();
        assertTrue(raw.contains(BOUND_PLOT_KEY), "the fixture no longer binds a plot key");
        assertTrue(raw.contains(BOUND_CROP_CYCLE), "the fixture no longer binds a crop cycle");
        assertTrue(raw.contains("\"action_steps\""), "the node metadata carries no action_steps");
        assertTrue(raw.contains("\"action_trigger\""), "the node metadata carries no action_trigger");

        JsonObject entry = body().getAsJsonObject("accepted_quest");
        QuestObjectiveTriggers before = QuestObjectiveTriggers.fromJournalEntry(entry);
        assertNotNull(before.action(), "the journal entry publishes no advancing objective");
        assertEquals(BOUND_PLOT_KEY, before.action().requireBound().get("plot_key"));
        assertEquals(BOUND_CROP_CYCLE, before.action().requireBound().get("crop_cycle_uuid"));
        assertEquals("carrot", before.action().match().get("crop_id"));
        assertEquals(4, before.steps().size());
    }

    // ---------------------------------------------------------------- what is stripped

    @Test
    void noBoundValueSurvivesInAnyForm() {
        String sent = QuestClientPayload.toJson(body());
        assertFalse(sent.contains(BOUND_PLOT_KEY), "the bound plot key reached the client");
        assertFalse(sent.contains(BOUND_CROP_CYCLE), "the bound crop cycle reached the client");
        for (String key : new String[]{"\"triggers\"", "\"action_trigger\"", "\"action_steps\"",
                "\"require_bound\"", "\"match\"", "\"require_planter\"",
                "\"location_trigger\"", "\"pickup_trigger\"", "\"destroy_trigger\""}) {
            assertFalse(sent.contains(key), key + " reached the client");
        }
    }

    @Test
    void theServerSideParsersFindNothingLeftToParse() {
        // The strongest statement available: run the very parsers the server uses to decide, over
        // the body the client is given. Both must come back with nothing.
        JsonObject sanitized = QuestClientPayload.sanitize(body());
        assertEquals(QuestObjectiveTriggers.NONE,
                QuestObjectiveTriggers.fromJournalEntry(sanitized.getAsJsonObject("accepted_quest")),
                "the published journal triggers are still reconstructible on the client");
        assertEquals(QuestObjectiveTriggers.NONE,
                QuestObjectiveTriggers.fromNodeMetadata(
                        sanitized.getAsJsonObject("node").getAsJsonObject("metadata")),
                "the node's objective machinery is still reconstructible on the client");
    }

    @Test
    void everyServerOnlyKeyIsStrippedAtEveryDepth() {
        // Rails may nest a journal entry inside another (QuestEntryParser accepts
        // accepted_quest.accepted_quest), and may publish the list under three different names.
        for (String listName : new String[]{"accepted_quests", "current_quests", "active_quests"}) {
            JsonObject root = new JsonObject();
            JsonArray list = new JsonArray();
            list.add(RowanQuestContent.stage5JournalEntry());
            root.add(listName, list);
            assertTrue(QuestClientPayload.isSanitized(QuestClientPayload.sanitize(root)),
                    listName + " kept its triggers");
        }

        JsonObject nested = new JsonObject();
        JsonObject outer = new JsonObject();
        outer.add("accepted_quest", RowanQuestContent.stage5JournalEntry());
        nested.add("accepted_quest", outer);
        assertNull(QuestClientPayload.findServerOnlyKey(QuestClientPayload.sanitize(nested), 0),
                "a nested journal entry kept its triggers");
    }

    @Test
    void theInputIsNeverMutated() {
        // The server has already installed state from this object, and grantAndFilter can hand back
        // the caller's own body by identity. Sanitizing must not reach back into it.
        JsonObject original = body();
        String before = original.toString();
        QuestClientPayload.sanitize(original);
        assertEquals(before, original.toString(), "sanitizing mutated the caller's body");
    }

    @Test
    void aBodyWithNothingToStripIsCarriedThroughUnchanged() {
        JsonObject clean = JsonParser.parseString(
                "{\"success\":true,\"quest_id\":4412,\"client_actions\":[]}").getAsJsonObject();
        assertEquals(clean, QuestClientPayload.sanitize(clean));
        assertNull(QuestClientPayload.sanitize(null), "a null body is still a null body");
    }

    @Test
    void theStrippedSetIsTheWholeOfWhatTheObjectiveParserReads() {
        // A list that drifts from QuestObjectiveTriggers is how the next observer added leaks. The
        // five node keys below are exactly the five that class reads from node metadata, and
        // "triggers" is the one it reads from a journal entry.
        assertEquals(Set.of("action_trigger", "action_steps", "location_trigger",
                        "pickup_trigger", "destroy_trigger"),
                QuestClientPayload.NODE_OBSERVER_KEYS);
        assertTrue(QuestClientPayload.SERVER_ONLY_KEYS.containsAll(
                QuestClientPayload.NODE_OBSERVER_KEYS));
        assertTrue(QuestClientPayload.SERVER_ONLY_KEYS.contains(
                QuestClientPayload.JOURNAL_TRIGGERS_KEY));
        assertEquals(6, QuestClientPayload.SERVER_ONLY_KEYS.size());
    }

    // ---------------------------------------------------------------- what must still work

    @Test
    void theJournalRefreshStillGetsEverythingItReads() {
        // M8's pipeline, over the sanitized body: parseAcceptedQuests into ClientQuestTable, which
        // is what the quiet notice and the journal screen read.
        List<ClientQuestEntry> fromRaw = QuestEntryParser.parseAcceptedQuests(body());
        List<ClientQuestEntry> fromSanitized =
                QuestEntryParser.parseAcceptedQuests(QuestClientPayload.sanitize(body()));

        assertEquals(1, fromSanitized.size(), "the journal entry did not survive sanitizing");
        ClientQuestEntry raw = fromRaw.get(0);
        ClientQuestEntry sent = fromSanitized.get(0);

        assertEquals(raw.questStateId(), sent.questStateId());
        assertEquals(raw.questId(), sent.questId());
        assertEquals(raw.questKey(), sent.questKey());
        assertEquals(raw.name(), sent.name());
        assertEquals(raw.questGiverName(), sent.questGiverName());
        // The whole journal detail: stage, objective, ordered progress, previews, claim state.
        assertEquals(raw.detail(), sent.detail(),
                "sanitizing changed something the journal screen shows");

        // And the one thing that must differ.
        assertEquals(QuestObjectiveTriggers.NONE, sent.triggers());
        assertFalse(raw.triggers().isEmpty(), "the raw entry had no triggers to lose");
    }

    @Test
    void theJournalScreenStillShowsTheAdvanceItWasSent() {
        ClientQuestTable.updateFromTriggerResult(
                QuestEntryParser.parseAcceptedQuests(QuestClientPayload.sanitize(body())));

        ClientQuestEntry entry = ClientQuestTable.findByQuestId("45");
        assertNotNull(entry, "the journal has no row after a sanitized trigger result");
        assertEquals("Return to Rowan with your harvest.", entry.detail().objective());
        assertTrue(entry.detail().claimPending(), "the claim state did not survive");
        assertEquals(5, entry.detail().progress().size(), "the ordered progress did not survive");
        assertEquals(5, entry.detail().stage().count(), "the stage line did not survive");
    }

    @Test
    void theAchievementPathIsUntouched() {
        JsonObject withAchievement = body();
        withAchievement.add("client_actions", JsonParser.parseString(
                        "[{\"type\":\"achievement\",\"key\":\"first_harvest\",\"name\":\"First Harvest\"},"
                        + "{\"type\":\"karma\",\"karma\":5}]")
                .getAsJsonArray());

        JsonObject sanitized = QuestClientPayload.sanitize(withAchievement);
        assertEquals(withAchievement.get("client_actions"), sanitized.get("client_actions"),
                "sanitizing rewrote the client actions");
        assertEquals(QuestAchievementAward.announced(withAchievement),
                QuestAchievementAward.announced(sanitized),
                "the announced achievements changed on the way out");

        // And the M10 filtering composes with it in either order: what the server sends is the
        // filtered list, sanitized -- never the unfiltered one.
        JsonObject filtered =
                QuestAchievementAward.withoutAnnouncements(withAchievement, Set.of("first_harvest"));
        JsonObject sent = QuestClientPayload.sanitize(filtered);
        assertTrue(QuestAchievementAward.announced(sent).isEmpty(),
                "a suppressed achievement came back after sanitizing");
        assertEquals(1, sent.getAsJsonArray("client_actions").size(),
                "the non-achievement client action was lost");
    }

    @Test
    void theNodeStillCarriesEverythingAScreenDraws() {
        JsonObject sanitizedMetadata = QuestClientPayload.sanitize(body())
                .getAsJsonObject("node").getAsJsonObject("metadata");
        QuestNodePresentation fromSanitized =
                QuestNodePresentation.fromNodeMetadata(sanitizedMetadata);
        QuestNodePresentation fromRaw =
                QuestNodePresentation.fromNodeMetadata(RowanQuestContent.stage5NodeMetadata());

        assertEquals(fromRaw, fromSanitized,
                "the dialogue screen lost something when the node was sanitized");
        assertFalse(fromSanitized.journalObjective().isBlank());
        assertFalse(fromSanitized.help().guide().isEmpty(), "the mixing guide did not survive");
        assertFalse(fromSanitized.keepItems().isEmpty(), "the keep preview did not survive");
        assertEquals("help", fromSanitized.presentationChoices().get("help"),
                "the presentation flag on the help choice did not survive");
    }

    @Test
    void theResponseStillParsesAsTheModelTheClientBinds() {
        // ClientNetworkHandler binds the body to QuestModels.QuestResponse before anything else. A
        // sanitized body has to remain a legal response, or the client drops it entirely.
        var response = parse(QuestClientPayload.toJson(body()));
        assertNotNull(response);
        assertEquals(45, response.quest_id);
        assertNotNull(response.currentNode, "the node was lost, so the client would show nothing");
        assertEquals("A Full Basket", response.currentNode.title);
        assertNotNull(response.choices);
        assertEquals(1, response.choices.size());
    }

    @Test
    void anActionEventAnswerSaysItSucceededOrTheClientThrowsItAway() {
        // Found while proving the journal refresh. The quest_action_event envelope has no `success`
        // field -- section 2.3's outcome is `result`, and none of the five frozen
        // action_event_response_* fixtures carries one -- while ClientNetworkHandler returns early
        // on any body whose `success` is not true, BEFORE it refreshes the journal. Forwarding the
        // envelope unchanged therefore discarded every action-event result on the client.
        for (String fixture : new String[]{"action_event_response_applied.json",
                "action_event_response_duplicate.json"}) {
            assertFalse(RowanQuestContent.readFixture(fixture).has("success"),
                    fixture + " now carries success; toAppliedResultJson may be redundant");
        }

        assertFalse(parse(QuestClientPayload.toJson(body())).success,
                "the plain sanitizer must not invent an outcome");
        assertTrue(parse(QuestClientPayload.toAppliedResultJson(body())).success,
                "an applied action event still reaches the client as a failure");

        // Added, never overwritten: a Rails that does answer `success` keeps its own answer.
        JsonObject refused = body();
        refused.addProperty("success", false);
        assertFalse(parse(QuestClientPayload.toAppliedResultJson(refused)).success,
                "the sender overwrote an explicit answer from Rails");

        // And it is still sanitized -- adding a field must not have skipped the stripping.
        assertTrue(QuestClientPayload.isSanitized(
                        JsonParser.parseString(QuestClientPayload.toAppliedResultJson(body()))),
                "the applied-result form is not sanitized");
    }

    @Test
    void theClientStillDropsABodyThatDoesNotSayItSucceeded() {
        // The guard the fix above exists for, asserted against the handler rather than assumed: the
        // early return on !success sits ahead of the journal refresh.
        String handler = readSource(
                "src/main/java/com/seggellion/britannia_mod/network/ClientNetworkHandler.java");
        int guard = handler.indexOf("if (!response.success)");
        int refresh = handler.indexOf("refreshJournalFromTriggerResult(payload.responseJson()");
        assertTrue(guard >= 0, "the handler no longer guards on success");
        assertTrue(refresh > guard,
                "the journal refresh no longer sits behind the success guard, so the finding this "
                        + "milestone fixed may no longer apply");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The applied action-event response with the two frozen fixtures in the two places Rails puts
     * them: the published journal entry under {@code accepted_quest}, and the node metadata under
     * {@code node.metadata}.
     */
    private static JsonObject body() {
        JsonObject root = RowanQuestContent.readFixture("action_event_response_applied.json");
        root.add("accepted_quest", RowanQuestContent.stage5JournalEntry());
        root.getAsJsonObject("node").add("metadata", RowanQuestContent.stage5NodeMetadata());
        // The applied response says the harvest is done; the stage-5 journal entry is the state one
        // step earlier. Bring the one field the claim assertions read into line, so the composition
        // is coherent rather than two fixtures stapled together.
        root.getAsJsonObject("accepted_quest").addProperty("claim_pending", true);
        root.getAsJsonObject("accepted_quest")
                .addProperty("objective", "Return to Rowan with your harvest.");
        return root;
    }

    private static com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse parse(
            String json) {
        return new com.google.gson.Gson().fromJson(json,
                com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse.class);
    }

    private static String readSource(String relative) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of(System.getProperty("britannia.projectDir", "."))
                            .resolve(relative),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + relative, e);
        }
    }
}

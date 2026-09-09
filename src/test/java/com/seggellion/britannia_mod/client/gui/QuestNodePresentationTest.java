package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the client reads off a node and off a journal entry, checked against the frozen
 * {@code quest_contract/v1} fixtures.
 *
 * <p>The fixtures are M7's and are covered by a SHA-256 manifest; nothing here writes to them.
 * These tests are the other half of the contract: that the mod reads the shapes M7 froze, and that
 * it reads <b>only</b> the client-visible half of them.
 */
class QuestNodePresentationTest {

    // ---------------------------------------------------------------- node metadata

    @Test
    void readsTheStageFiveNodeAsAuthored() {
        QuestNodePresentation node =
                QuestNodePresentation.fromNodeMetadata(RowanQuestContent.stage5NodeMetadata());

        assertTrue(node.journalObjective().startsWith("Hoe a public plot"));
        assertEquals(1, node.keepItems().size());
        assertEquals("britannia_mod:fertilized_dirt", node.keepItems().get(0).id());
        assertEquals(1, node.keepItems().get(0).count());

        assertTrue(node.hasHelp());
        assertEquals("Rowan's planting guide", node.help().title());
        assertEquals(4, node.help().guide().size());
        assertEquals("britannia_mod:farming_hoe", node.help().guide().get(0).mainHand());
        assertFalse(node.help().guide().get(0).usesOffHand(), "off_hand is null in the fixture");
        assertEquals("britannia_mod:watering_can", node.help().guide().get(3).returned().get(0),
                "the watering can comes back");
    }

    @Test
    void neverReadsTheObjectiveMachineryOffANode() {
        // The stage 5 node carries action_trigger and action_steps with the match criteria that
        // solve it. QuestNodePresentation must not be a second way for those to reach a screen.
        QuestNodePresentation node =
                QuestNodePresentation.fromNodeMetadata(RowanQuestContent.stage5NodeMetadata());
        String rendered = node.toString();
        for (String solution : new String[]{"action_trigger", "action_steps", "require_bound",
                "plot_key", "crop_cycle_uuid", "require_planter", "$flag:"}) {
            assertFalse(rendered.contains(solution),
                    "a quest solution reached the client view model: " + solution);
        }
    }

    @Test
    void findsThePresentationChoiceAndOnlyThePresentationChoice() {
        // The stage 5 node has two choices: "harvested" is a real transition and "help" is
        // presentation only. Sending the second one would move the node that owns the observers.
        QuestNodePresentation node =
                QuestNodePresentation.fromNodeMetadata(RowanQuestContent.stage5NodeMetadata());
        assertEquals("help", node.presentationFor("help"));
        assertEquals("", node.presentationFor("harvested"));
        assertEquals("", node.presentationFor("no_such_choice"));
        assertEquals("", node.presentationFor(null));
        assertEquals(1, node.presentationChoices().size());
    }

    @Test
    void treatsAnyPresentationValueAsPresentationOnly() {
        // An unrecognised presentation kind is still not a transition. Guessing that it is would
        // be the failure that moves the quest.
        JsonObject metadata = JsonParser.parseString(
                "{\"choices\":{\"a\":{\"presentation\":\"diagram\"},\"b\":{\"text\":\"go\"}}}")
                .getAsJsonObject();
        QuestNodePresentation node = QuestNodePresentation.fromNodeMetadata(metadata);
        assertEquals("diagram", node.presentationFor("a"));
        assertEquals("", node.presentationFor("b"));
    }

    @Test
    void survivesAMissingOrMalformedNode() {
        assertEquals(QuestNodePresentation.NONE, QuestNodePresentation.fromNodeMetadata(null));

        JsonObject rubbish = JsonParser.parseString(
                "{\"help\":42,\"keep_items\":\"nope\",\"stage\":[],\"rewards_preview\":null}")
                .getAsJsonObject();
        QuestNodePresentation node = QuestNodePresentation.fromNodeMetadata(rubbish);
        assertFalse(node.hasHelp());
        assertTrue(node.keepItems().isEmpty());
        assertFalse(node.stage().known());
        assertEquals(0, node.totalRewardIcons());
    }

    @Test
    void dropsAGuideRowThatDescribesNothing() {
        JsonObject metadata = JsonParser.parseString(
                "{\"help\":{\"guide\":[{\"result\":\"x\"},{\"main_hand\":\"a\",\"gesture\":\"do it\"}]}}")
                .getAsJsonObject();
        QuestNodePresentation node = QuestNodePresentation.fromNodeMetadata(metadata);
        assertEquals(1, node.help().guide().size());
        assertEquals("a", node.help().guide().get(0).mainHand());
    }

    @Test
    void acceptsReturnedAsEitherASingleIdOrAList() {
        JsonObject metadata = JsonParser.parseString(
                "{\"help\":{\"guide\":["
                + "{\"main_hand\":\"a\",\"gesture\":\"g\",\"returned\":\"one\"},"
                + "{\"main_hand\":\"b\",\"gesture\":\"g\",\"returned\":[\"x\",\"y\"]},"
                + "{\"main_hand\":\"c\",\"gesture\":\"g\",\"returned\":null}]}}")
                .getAsJsonObject();
        QuestNodePresentation node = QuestNodePresentation.fromNodeMetadata(metadata);
        assertEquals(List.of("one"), node.help().guide().get(0).returned());
        assertEquals(List.of("x", "y"), node.help().guide().get(1).returned());
        assertTrue(node.help().guide().get(2).returned().isEmpty());
    }

    // ---------------------------------------------------------------- journal entry

    @Test
    void readsTheStageFiveJournalEntryAsAuthored() {
        ClientQuestEntry.JournalDetail detail =
                ClientQuestEntry.JournalDetail.fromJournalEntry(RowanQuestContent.stage5JournalEntry());

        assertEquals("rowan_farming", detail.stage().questlineKey());
        assertEquals(5, detail.stage().index());
        assertEquals(5, detail.stage().count());
        assertTrue(detail.stage().known());

        assertEquals("Farmer", detail.questGiverProfession());
        assertTrue(detail.objective().startsWith("Hoe a public plot"));
        assertFalse(detail.claimPending());

        assertEquals(5, detail.progress().size());
        assertEquals("plot_hoed", detail.progress().get(0).key());
        assertEquals("Prepare a public plot", detail.progress().get(0).label());
        assertTrue(detail.progress().get(0).done());
        assertEquals("crop_harvested", detail.progress().get(4).key());
        assertFalse(detail.progress().get(4).done());
        assertEquals(4, detail.completedSteps());

        assertTrue(detail.rewardsOnAccept().isEmpty());
        assertEquals(1, detail.rewardsOnComplete().size());
        assertEquals("britannia_mod:gold_coin", detail.rewardsOnComplete().get(0).id());
        assertEquals(5, detail.rewardsOnComplete().get(0).count());
        assertEquals(1, detail.keepItems().size());
        assertEquals("britannia_mod:fertilized_dirt", detail.keepItems().get(0).id());
        assertEquals(1, detail.achievements().size());
        assertEquals("first_harvest", detail.achievements().get(0).key());
        assertEquals("First Harvest", detail.achievements().get(0).title());
    }

    @Test
    void theJournalDetailNeverCarriesTheObjectiveMachinery() {
        // The same fixture carries a full triggers block, including the bound plot key and the
        // crop cycle UUID. JournalDetail is what QuestEntryCodecs writes to a client, so anything
        // that got in here would be on the wire.
        ClientQuestEntry.JournalDetail detail =
                ClientQuestEntry.JournalDetail.fromJournalEntry(RowanQuestContent.stage5JournalEntry());
        String rendered = detail.toString();
        for (String solution : new String[]{"minecraft:overworld:1203", "b1b3d4a0", "require_bound",
                "crop_cycle_uuid", "plot_key", "trigger_key", "require_planter"}) {
            assertFalse(rendered.contains(solution),
                    "a quest solution reached the journal detail: " + solution);
        }
    }

    @Test
    void survivesAMissingOrMalformedJournalEntry() {
        assertEquals(ClientQuestEntry.JournalDetail.NONE,
                ClientQuestEntry.JournalDetail.fromJournalEntry(null));

        JsonObject rubbish = JsonParser.parseString(
                "{\"stage\":7,\"progress\":{},\"rewards_preview\":[],\"keep_items\":null,"
                + "\"claim_pending\":\"maybe\"}").getAsJsonObject();
        ClientQuestEntry.JournalDetail detail = ClientQuestEntry.JournalDetail.fromJournalEntry(rubbish);
        assertFalse(detail.stage().known());
        assertTrue(detail.progress().isEmpty());
        assertTrue(detail.keepItems().isEmpty());
        assertFalse(detail.claimPending());
    }

    @Test
    void dropsAProgressStepTheJournalCannotName() {
        JsonObject entry = JsonParser.parseString(
                "{\"progress\":[{\"done\":true},{\"key\":\"a\",\"label\":\"Do a\",\"done\":false}]}")
                .getAsJsonObject();
        ClientQuestEntry.JournalDetail detail = ClientQuestEntry.JournalDetail.fromJournalEntry(entry);
        assertEquals(1, detail.progress().size());
        assertEquals("a", detail.progress().get(0).key());
    }

    // ---------------------------------------------------------------- what the docs may claim

    @Test
    void neitherClassClaimsTriggerDataHasNeverReachedAClient() {
        // Both javadocs once asserted a property of the whole mod that the mod did not have.
        // QuestEntryCodecs said the trigger data "has never reached a client"; this class said "a
        // screen that never sees it cannot leak it". Meanwhile QuestActionDispatcher shipped the
        // raw Rails body -- accepted_quest.triggers, resolved bound values and all -- in
        // QuestTriggerResultS2CPayload, and QuestObjectiveWatcher and QuestEventHandlers shipped
        // node.metadata whole, action_steps and action_trigger included.
        //
        // M8 corrected the documentation and deferred the payloads. M11 fixed the payloads, and the
        // prose is corrected again below -- but neither sentence may come back, because what made
        // them wrong was that they described the codec's own rule as a property of the mod. The
        // payloads being clean today is a fact about three call sites, not about this file.
        //
        // The two sentences, verbatim, rather than a keyword ban: the replacement prose has to be
        // able to say what it corrected, and a ban on the words would fail on the correction.
        assertFalse(oneLine(javadocOf(CODECS)).contains(
                        "that list is the reason none of it has ever reached a client"),
                "QuestEntryCodecs still claims the trigger data has never reached a client");
        assertFalse(oneLine(javadocOf(PRESENTATION)).contains(
                        "a screen that never sees it cannot leak it"),
                "QuestNodePresentation still claims a screen that never sees it cannot leak it");
    }

    @Test
    void bothClassesNameTheRawPayloadPathsThatCarriedIt() {
        String codecs = javadocOf(CODECS);
        String presentation = javadocOf(PRESENTATION);
        for (String path : new String[]{"QuestActionDispatcher", "QuestTriggerResultS2CPayload"}) {
            assertTrue(codecs.contains(path), "QuestEntryCodecs does not name " + path);
            assertTrue(presentation.contains(path), "QuestNodePresentation does not name " + path);
        }
        assertTrue(presentation.contains("QuestObjectiveWatcher")
                        && presentation.contains("QuestEventHandlers"),
                "the two handlers that shipped node.metadata are not named");
        // And both must now name what closed it, or a reader is left believing the exposure stands.
        assertTrue(codecs.contains("QuestClientPayload"),
                "QuestEntryCodecs does not name what closed the exposure it describes");
        assertTrue(presentation.contains("QuestClientPayload"),
                "QuestNodePresentation does not name what closed the exposure it describes");
    }

    @Test
    void theExposureIsClosedAtEveryOneOfTheThreeSendingSites() {
        // M8's version of this test asserted the opposite -- that the payloads still shipped whole
        // bodies -- so that hardening them would fail here rather than leave stale prose behind.
        // M11 hardened them, so it is inverted: every site that builds a QuestTriggerResultS2CPayload
        // must build it through QuestClientPayload, and none may serialize a body itself.
        //
        // Source text rather than behaviour on purpose. The behaviour is asserted in
        // QuestClientPayloadSanitizationTest against the frozen fixtures; what this adds is that a
        // FOURTH sending site cannot appear without turning this red.
        for (String file : new String[]{
                "quest/action/QuestActionDispatcher.java",
                "quest/QuestObjectiveWatcher.java",
                "quest/events/QuestEventHandlers.java"}) {
            String source = JavaSource.withoutComments(JavaSource.MOD.resolve(file));
            assertTrue(source.contains("QuestClientPayload."),
                    file + " builds a trigger result without sanitizing it");
            // The three pre-M11 spellings, all of which serialized a whole body straight out.
            for (String raw : new String[]{
                    "GSON.toJson(forwarded)", "GSON.toJson(response)", "GSON.toJson(root)"}) {
                assertFalse(source.contains(raw),
                        file + " still serializes the whole body with " + raw);
            }
        }

        // The whole mod, not only the three known sites: any file that builds this payload has to
        // be a file that sanitizes, so a fourth sending site cannot appear quietly.
        for (java.nio.file.Path source : JavaSource.modSources()) {
            String text = JavaSource.withoutComments(source);
            if (!text.contains("new QuestTriggerResultS2CPayload(")) continue;
            assertTrue(text.contains("QuestClientPayload."),
                    source.getFileName() + " builds a trigger result without sanitizing it");
        }
    }

    private static final java.nio.file.Path CODECS =
            JavaSource.MOD.resolve("network/payload/QuestEntryCodecs.java");
    private static final java.nio.file.Path PRESENTATION =
            JavaSource.MOD.resolve("client/gui/QuestNodePresentation.java");

    /** Javadoc wraps; a sentence in it is only findable once the line breaks and stars are gone. */
    private static String oneLine(String javadoc) {
        return javadoc.replaceAll("[\\r\\n]+\\s*\\*?", " ")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    /** Everything above the type declaration: the class javadoc, comments and all. */
    private static String javadocOf(java.nio.file.Path file) {
        String source = read(file);
        int at = source.indexOf("public record");
        if (at < 0) at = source.indexOf("final class");
        assertTrue(at > 0, "cannot find the type declaration in " + file.getFileName());
        return source.substring(0, at);
    }

    private static String read(java.nio.file.Path path) {
        try {
            return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}

package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 item 4: farming objectives stop opening screens, and quest results stop being attributed to
 * "The Guardian".
 */
class QuestTriggerResultPresentationTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    // ---------------------------------------------------------------- the popup

    @Test
    void aFarmingObjectiveGetsAQuietNoticeAndNoScreen() {
        // The player is hoeing, fertilizing, planting or watering. A screen here appears over the
        // plot they are standing on and takes the mouse.
        assertEquals(QuestTriggerResultPresentation.Outcome.QUIET_NOTICE,
                QuestTriggerResultPresentation.decide(true, false));
    }

    @Test
    void aResultTheyAskedForStillOpensTheDialogue() {
        // The player is talking to a quest giver, so a screen is what they expect and it is
        // already covering the world.
        assertEquals(QuestTriggerResultPresentation.Outcome.OPEN_DIALOGUE,
                QuestTriggerResultPresentation.decide(true, true));
    }

    @Test
    void aResponseWithNoNodeShowsNothingAtAll() {
        assertEquals(QuestTriggerResultPresentation.Outcome.NOTHING,
                QuestTriggerResultPresentation.decide(false, false));
        assertEquals(QuestTriggerResultPresentation.Outcome.NOTHING,
                QuestTriggerResultPresentation.decide(false, true));
    }

    // ---------------------------------------------------------------- attribution

    @Test
    void prefersTheGiverThePlayerIsActuallyTalkingTo() {
        assertEquals("Rowan",
                QuestTriggerResultPresentation.attribution("Rowan", "Someone Else", "Third"));
    }

    @Test
    void fallsBackToTheResponseThenToTheJournal() {
        assertEquals("Rowan", QuestTriggerResultPresentation.attribution("", "Rowan", "Third"));
        assertEquals("Rowan", QuestTriggerResultPresentation.attribution("", "", "Rowan"));
        assertEquals("Rowan", QuestTriggerResultPresentation.attribution(null, null, "Rowan"));
    }

    @Test
    void returnsBlankRatherThanInventingAName() {
        // Blank is the signal to use a translated "your quest giver". The bug being fixed here is
        // precisely that the old code invented one.
        assertEquals("", QuestTriggerResultPresentation.attribution("", "", ""));
        assertEquals("", QuestTriggerResultPresentation.attribution(null, null, null));
        assertEquals("", QuestTriggerResultPresentation.attribution("  ", "  ", "  "));
    }

    @Test
    void stripsTheApiIdentitySuffixTheJournalAlsoStrips() {
        // Names arrive as "Rowan" or "Rowan:<api id>". The suffix is an identity, not a name, and
        // the dialogue header and the journal row must not disagree about who somebody is.
        assertEquals("Rowan", QuestTriggerResultPresentation.displayName("Rowan:npc_4417"));
        assertEquals("Rowan", QuestTriggerResultPresentation.attribution("Rowan:npc_4417", "", ""));
        assertEquals("Rowan", QuestTriggerResultPresentation.displayName("  Rowan  "));
        assertEquals("", QuestTriggerResultPresentation.displayName(":npc_4417"));
    }

    // ---------------------------------------------------------------- the regression

    @Test
    void theGuardianAttributionIsGoneFromTheWholeMod() {
        // ClientNetworkHandler named every unattributed trigger result "The Guardian" -- a
        // hardcoded string, in a questline whose quest giver is a farmer called Rowan.
        //
        // Comments are stripped first: the class docs next door explain the fix by naming the
        // string it removed, and the assertion is about what the mod says, not about its prose.
        for (Path file : JavaSource.modSources()) {
            assertFalse(JavaSource.withoutComments(file).contains("\"The Guardian\""),
                    file + " still hardcodes \"The Guardian\" as a quest attribution");
        }
    }

    @Test
    void theTriggerResultPathGoesThroughThisClass() {
        // A guard against the fix being undone by a well-meaning revert: the handler must call the
        // decision, not re-derive it inline.
        String handler = read(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/network/ClientNetworkHandler.java"));
        assertTrue(handler.contains("QuestTriggerResultPresentation.decide("),
                "the trigger result path no longer asks for the presentation decision");
        assertTrue(handler.contains("QuestTriggerResultPresentation.attribution("),
                "the trigger result path no longer asks for the attribution");
        assertTrue(handler.contains(QuestScreenText.GIVER_UNKNOWN.substring(
                        QuestScreenText.GIVER_UNKNOWN.lastIndexOf('.') + 1))
                        || handler.contains("QuestScreenText.GIVER_UNKNOWN"),
                "no translated fallback for an unnamed quest giver");
    }

    private static List<Path> modSources() {
        try (Stream<Path> files = Files.walk(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod"))) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list the mod sources", e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}

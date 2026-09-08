package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 item 7: instructions name the key the player has bound, not a letter somebody typed once.
 *
 * <p>The default binding is O. Every assertion below that uses a different key is the real test --
 * a hardcoded letter passes at the default and fails the moment anybody rebinds, and the point of
 * making this a pure function was to be able to prove it does not.
 */
class QuestKeyPromptTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path SOURCE = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    @Test
    void namesTheDefaultBinding() {
        assertEquals(QuestScreenText.KEY_OPEN_JOURNAL,
                QuestKeyPrompt.openJournalMessageKey("key.keyboard.o"));
        assertEquals("key.keyboard.o", QuestKeyPrompt.openJournalArgumentKey("key.keyboard.o"));
    }

    @Test
    void followsARebindingRatherThanStayingOnTheDefault() {
        // A player who moved the journal to J must be told J.
        assertEquals("key.keyboard.j", QuestKeyPrompt.openJournalArgumentKey("key.keyboard.j"));
        assertEquals(QuestScreenText.KEY_OPEN_JOURNAL,
                QuestKeyPrompt.openJournalMessageKey("key.keyboard.j"));

        // ...and one who bound it to a mouse button or a function key, likewise. Nothing here
        // assumes a keyboard letter, because InputConstants.Key names are not all letters.
        assertEquals("key.mouse.middle", QuestKeyPrompt.openJournalArgumentKey("key.mouse.middle"));
        assertEquals("key.keyboard.f6", QuestKeyPrompt.openJournalArgumentKey("key.keyboard.f6"));
        assertEquals("key.keyboard.keypad.7",
                QuestKeyPrompt.openJournalArgumentKey("key.keyboard.keypad.7"));
    }

    @Test
    void switchesToTheControlsSentenceWhenNothingIsBound() {
        // "Press <nothing> to open your journal" is worse than useless. A cleared binding gets a
        // sentence naming the control, so the player knows where to go and bind it.
        for (String unbound : new String[]{"key.keyboard.unknown", "KEY.KEYBOARD.UNKNOWN", "", "   ", null}) {
            assertTrue(QuestKeyPrompt.isUnbound(unbound), "should read as unbound: " + unbound);
            assertEquals(QuestScreenText.KEY_OPEN_JOURNAL_UNBOUND,
                    QuestKeyPrompt.openJournalMessageKey(unbound));
            assertEquals(QuestKeyPrompt.OPEN_JOURNAL_BINDING,
                    QuestKeyPrompt.openJournalArgumentKey(unbound));
        }
    }

    @Test
    void aBoundKeyIsNeverReportedAsUnbound() {
        for (String bound : new String[]{"key.keyboard.o", "key.keyboard.j", "key.mouse.left",
                "key.keyboard.unknown.but.not.really"}) {
            assertFalse(QuestKeyPrompt.isUnbound(bound), bound);
        }
    }

    @Test
    void trimsWhitespaceRatherThanPassingItThrough() {
        assertEquals("key.keyboard.j", QuestKeyPrompt.openJournalArgumentKey("  key.keyboard.j  "));
    }

    @Test
    void theBindingItNamesIsTheOneKeybindsActuallyRegisters() {
        // The unbound sentence substitutes the control's own translation key. If Keybinds were
        // renamed, the sentence would name a key that does not exist; this ties the two together.
        String keybinds = read(SOURCE.resolve("client/Keybinds.java"));
        assertTrue(keybinds.contains("\"" + QuestKeyPrompt.OPEN_JOURNAL_BINDING + "\""),
                "Keybinds.java does not declare " + QuestKeyPrompt.OPEN_JOURNAL_BINDING);
    }

    @Test
    void thePromptDescribesWhatTheKeyReallyOpens() {
        // It said "Press %s to open your journal", which the key does not do. The binding it uses
        // is Keybinds.OPEN_SKILL_SCREEN, which opens MenuScreen; the journal is one click further
        // on, behind that screen's "Quests" link. A player who pressed the key and expected a
        // journal got a menu, with no idea whether the instruction or the game was wrong.
        String keybinds = JavaSource.withoutComments(SOURCE.resolve("client/Keybinds.java"));
        assertTrue(keybinds.contains("new MenuScreen()"),
                "the binding no longer opens MenuScreen; the sentence needs revisiting");

        String menu = JavaSource.withoutComments(SOURCE.resolve("client/gui/MenuScreen.java"));
        assertTrue(menu.contains("new QuestJournalScreen()"),
                "the journal is no longer reached from MenuScreen; the sentence needs revisiting");

        String bound = langValue(QuestScreenText.KEY_OPEN_JOURNAL);
        String unbound = langValue(QuestScreenText.KEY_OPEN_JOURNAL_UNBOUND);
        for (String sentence : new String[]{bound, unbound}) {
            assertFalse(sentence.matches("(?i).*\\b(to|and) open your journal\\b.*"),
                    "still promises the key opens the journal directly: " + sentence);
            assertTrue(sentence.contains("Quests"),
                    "does not mention the step that actually reaches the journal: " + sentence);
        }
    }

    @Test
    void noQuestScreenHardcodesAKeyLetterInAnInstruction() {
        // The regression this class exists to prevent. Any "press O"-shaped literal in the quest
        // screens is a string that stops being true the first time somebody opens Controls.
        //
        // Comments are stripped first: this file's own javadoc explains the rule using the shape
        // it forbids, and a test that fails on its own explanation gets deleted rather than fixed.
        Pattern hardcoded = Pattern.compile(
                "\"[^\"\\n]*\\b[Pp]ress\\s+(the\\s+)?[\\[(]?[A-Za-z0-9]{1,6}[\\])]?\\s+(key\\b|to\\b)[^\"\\n]*\"");
        for (Path file : JavaSource.questScreenSources()) {
            Matcher matcher = hardcoded.matcher(JavaSource.withoutComments(file));
            boolean found = matcher.find();
            assertFalse(found, file.getFileName() + " hardcodes a key in an instruction: "
                    + (found ? matcher.group() : ""));
        }
    }

    private static String langValue(String key) {
        String lang = read(PROJECT.resolve("src/main/resources/assets/britannia_mod/lang/en_us.json"));
        Matcher matcher = Pattern.compile(
                Pattern.quote("\"" + key + "\"") + "\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(lang);
        assertTrue(matcher.find(), "en_us.json is missing " + key);
        return matcher.group(1).replace("\\\"", "\"");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}

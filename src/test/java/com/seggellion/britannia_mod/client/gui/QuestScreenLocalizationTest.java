package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 item 10: every player-facing string the quest screens and the farming messages say is a
 * translation key, and every one of those keys exists.
 *
 * <p>Three directions, because each catches a different mistake:
 * <ol>
 *   <li>Every key in {@link QuestScreenText#ALL_KEYS} is defined in {@code en_us.json} -- catches
 *       a key added to the code and not to the lang file.</li>
 *   <li>Every key in {@code ALL_KEYS} is used somewhere -- catches a key the screens stopped
 *       saying, which would otherwise sit in the lang file forever.</li>
 *   <li>Every {@code Component.translatable("...")} literal in the files this milestone touched is
 *       defined -- catches a key written inline rather than through the registry.</li>
 * </ol>
 */
class QuestScreenLocalizationTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path LANG =
            PROJECT.resolve("src/main/resources/assets/britannia_mod/lang/en_us.json");
    private static final Path MOD_SOURCE =
            PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    /** Files this milestone gave new or reworded player-facing strings. */
    private static final List<String> TOUCHED_MESSAGE_SITES = List.of(
            "bowlpreparation/BowlMixingMessages.java",
            "bowlpreparation/BowlPreparationItem.java",
            "bowlpreparation/BowlPreparationOutput.java",
            "bowlpreparation/FertileDirtMixingItem.java",
            "util/WaterSourceInteraction.java",
            "block/FarmingBlock.java",
            "block/CommunityHoedFarmBlock.java",
            "block/entity/CommunityFarmBlockEntity.java",
            "item/FarmingHoeItem.java",
            "network/ClientNetworkHandler.java",
            // M9: the countdown and expiry sentences, the moisture reading and the equipment
            // recovery messages. Listed so a future edit that writes a key inline instead of
            // through QuestScreenText fails the build rather than reaching a player as its own name.
            "item/WateringCanItem.java",
            "quest/equipment/QuestEquipmentReissueService.java",
            // M11: the window_expired sentence, sent from the server side of the action-event
            // boundary. Listed for the same reason as the rest -- a key written inline there would
            // reach a player as its own name.
            "quest/action/QuestActionDispatcher.java");

    // ---------------------------------------------------------------- 1: keys exist

    @Test
    void everyQuestScreenKeyIsDefinedExactlyOnceAndIsNotBlank() {
        JsonObject lang = lang();
        List<String> lines = langLines();
        for (String key : QuestScreenText.ALL_KEYS) {
            assertTrue(lang.has(key), "en_us.json is missing " + key);
            JsonElement value = lang.get(key);
            assertTrue(value.isJsonPrimitive(), key + " is not a string");
            assertFalse(value.getAsString().isBlank(), key + " is blank");
            assertEquals(1, occurrences(lines, key), key + " is defined more than once");
        }
    }

    // ---------------------------------------------------------------- 2: keys are used

    @Test
    void everyQuestScreenKeyIsActuallySaidBySomething() {
        // Constant names come from reflection rather than from a rule for turning a key back into
        // a name: OBJECTIVE_TOAST_TITLE holds "...objective.title", and any such heuristic would
        // either miss it or quietly pass on a key nothing uses.
        StringBuilder allSources = new StringBuilder();
        for (Path file : JavaSource.modSources()) {
            allSources.append(JavaSource.withoutComments(file)).append('\n');
        }
        String code = allSources.toString();

        Set<String> keysDeclared = new LinkedHashSet<>();
        for (Field field : QuestScreenText.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            if (!Modifier.isPublic(field.getModifiers())) continue;
            String value;
            try {
                value = (String) field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot read " + field.getName(), e);
            }
            keysDeclared.add(value);

            // Once for the declaration itself; anything more is a real use.
            int uses = countSubstring(code, field.getName());
            assertTrue(uses > 1, "nothing says " + value + " (constant " + field.getName()
                    + "); it is dead vocabulary");
        }

        // ...and the registry must list every key the class declares, or ALL_KEYS is not a registry.
        for (String key : keysDeclared) {
            assertTrue(QuestScreenText.ALL_KEYS.contains(key), key + " is missing from ALL_KEYS");
        }
        assertEquals(keysDeclared.size(), QuestScreenText.ALL_KEYS.size(),
                "ALL_KEYS and the declared constants disagree");
    }

    // ---------------------------------------------------------------- 3: inline keys exist

    @Test
    void everyInlineTranslationKeyInTheTouchedFilesIsDefined() {
        JsonObject lang = lang();
        Pattern translatable = Pattern.compile(
                "Component\\.translatable\\(\\s*\"((?:message|screen|item|block|key)\\.britannia_mod\\.[^\"]+)\"");
        Set<String> found = new LinkedHashSet<>();
        for (String relative : TOUCHED_MESSAGE_SITES) {
            Path file = MOD_SOURCE.resolve(relative);
            assertTrue(Files.exists(file), "expected to have touched " + relative);
            Matcher matcher = translatable.matcher(read(file));
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
        }
        assertFalse(found.isEmpty(), "no inline translation keys found in the touched files at all");
        for (String key : found) {
            assertTrue(lang.has(key), "en_us.json is missing the inline key " + key);
            assertFalse(lang.get(key).getAsString().isBlank(), key + " is blank");
        }
    }

    // ---------------------------------------------------------------- no raw registry ids

    @Test
    void noQuestScreenPrintsARawRegistryId() {
        // An item id on screen is the defect the acceptance list calls out. The screens go through
        // the registry and fall back to a translated "Unknown item"; nothing formats an id.
        Pattern rawId = Pattern.compile("\"britannia_mod:[a-z0-9_/]+\"");
        for (Path file : questScreenSources()) {
            Matcher matcher = rawId.matcher(read(file));
            assertFalse(matcher.find(),
                    file.getFileName() + " embeds a raw registry id: "
                            + (matcher.hitEnd() ? "" : matcher.group()));
        }
    }

    @Test
    void noQuestlineMessageSitePrintsARawRegistryId() {
        // The same rule as the screens, applied to every file this project sends a player-facing
        // message from. A message is as visible as a screen, and the sites that send them are not
        // under client/gui, so questScreenSources() never covered them.
        Pattern rawId = Pattern.compile("\"britannia_mod:[a-z0-9_/]+\"");
        for (String relative : TOUCHED_MESSAGE_SITES) {
            Path file = MOD_SOURCE.resolve(relative);
            Matcher matcher = rawId.matcher(JavaSource.withoutComments(file));
            assertFalse(matcher.find(),
                    relative + " embeds a raw registry id in a file that talks to players: "
                            + (matcher.hitEnd() ? "" : matcher.group()));
        }
    }

    @Test
    void theTwoGuideMarkersAreDifferentSymbols() {
        // M11 deferred defect F13. The result and the returned items are two runs of identical item
        // icons on one line, and their markers are what separates them on screen. Two markers that
        // read the same are the defect with extra steps -- and neither may be blank, or the group
        // it labels goes back to being told apart by position alone.
        JsonObject lang = lang();
        String produces = lang.get(QuestScreenText.GUIDE_PRODUCES_MARKER).getAsString();
        String returned = lang.get(QuestScreenText.GUIDE_RETURNED_MARKER).getAsString();
        assertFalse(produces.isBlank(), "the produces marker is blank");
        assertFalse(returned.isBlank(), "the returned marker is blank");
        assertNotEquals(produces, returned,
                "the two guide markers read the same, so the icon groups are still told apart only "
                        + "by which side of the row they are on");

        // And they are drawn, not just declared: a key nothing renders is not a distinction.
        String screen = JavaSource.withoutComments(MOD_SOURCE.resolve("client/gui/QuestDecisionScreen.java"));
        assertTrue(screen.contains("QuestScreenText.GUIDE_PRODUCES_MARKER"),
                "the produces marker is never drawn");
        assertTrue(screen.contains("QuestScreenText.GUIDE_RETURNED_MARKER"),
                "the returned marker is never drawn");
    }

    @Test
    void everyItemTheAuthoredContentNamesHasARealDisplayName() {
        // The acceptance list requires reward icons and tooltips to resolve actual item names. At
        // runtime that goes through the item registry; here it goes through the same naming rule
        // the registry follows, so a reward the content names but the lang file does not is a
        // build failure rather than an "Unknown item" a player finds.
        JsonObject lang = lang();
        List<String> ids = new ArrayList<>(RowanQuestContent.mixingGuideItemIds());
        ids.add("britannia_mod:gold_coin");
        ids.add("britannia_mod:carrot_seeds");
        ids.add("britannia_mod:watering_can");
        ids.add("britannia_mod:farming_hoe");

        for (String id : ids) {
            List<String> candidates = QuestScreenText.itemNameCandidates(id);
            assertFalse(candidates.isEmpty(), "cannot even form a name key for " + id);
            boolean named = candidates.stream().anyMatch(lang::has);
            assertTrue(named, id + " has no display name; tried " + candidates);
        }
    }

    @Test
    void itemNameCandidatesRefuseToFallBackToTheIdItself() {
        assertTrue(QuestScreenText.itemNameCandidates(null).isEmpty());
        assertTrue(QuestScreenText.itemNameCandidates("").isEmpty());
        assertTrue(QuestScreenText.itemNameCandidates("  ").isEmpty());
        assertTrue(QuestScreenText.itemNameCandidates("britannia_mod:").isEmpty());
        assertTrue(QuestScreenText.itemNameCandidates(":gold_coin").isEmpty());

        assertEquals(List.of("item.britannia_mod.gold_coin", "block.britannia_mod.gold_coin"),
                QuestScreenText.itemNameCandidates("britannia_mod:gold_coin"));
        assertEquals(List.of("item.minecraft.bucket", "block.minecraft.bucket"),
                QuestScreenText.itemNameCandidates("bucket"), "a bare path is minecraft's");
    }

    // ---------------------------------------------------------------- symbols, not only colour

    @Test
    void doneAndPendingStepsDifferBySymbolNotOnlyByColour() {
        JsonObject lang = lang();
        String done = lang.get(QuestScreenText.PROGRESS_DONE).getAsString();
        String pending = lang.get(QuestScreenText.PROGRESS_PENDING).getAsString();
        assertFalse(done.isBlank(), "a done step needs a visible marker");
        assertFalse(pending.isBlank(), "a pending step needs a visible marker");
        assertFalse(done.equals(pending),
                "the two markers are identical, so colour is the only signal");
        assertEquals(QuestScreenText.PROGRESS_DONE, QuestScreenText.progressMarker(true));
        assertEquals(QuestScreenText.PROGRESS_PENDING, QuestScreenText.progressMarker(false));
    }

    @Test
    void eachRewardSectionHasItsOwnHeadingRatherThanATint() {
        JsonObject lang = lang();
        Set<String> headings = new LinkedHashSet<>();
        for (QuestDialogueLayout.SectionKind kind : QuestDialogueLayout.SectionKind.values()) {
            String key = QuestScreenText.sectionHeading(kind);
            assertTrue(lang.has(key), "no heading for " + kind);
            headings.add(lang.get(key).getAsString());
        }
        assertEquals(QuestDialogueLayout.SectionKind.values().length, headings.size(),
                "two reward sections share a heading: " + headings);
    }

    // ---------------------------------------------------------------- format arguments

    @Test
    void formatArgumentCountsMatchWhatTheScreensPassIn() {
        JsonObject lang = lang();
        assertEquals(2, formatArguments(lang, QuestScreenText.STAGE), "Quest %s of %s");
        assertEquals(2, formatArguments(lang, QuestScreenText.REWARDS_HEADING_COUNT));
        assertEquals(2, formatArguments(lang, QuestScreenText.REWARDS_TOOLTIP));
        assertEquals(2, formatArguments(lang, QuestScreenText.JOURNAL_COUNT));
        assertEquals(2, formatArguments(lang, QuestScreenText.PROGRESS_LINE));
        assertEquals(2, formatArguments(lang, QuestScreenText.GUIDE_STEP));
        assertEquals(2, formatArguments(lang, QuestScreenText.MIX_SWAP_HANDS));
        assertEquals(1, formatArguments(lang, QuestScreenText.JOURNAL_OBJECTIVE));
        assertEquals(1, formatArguments(lang, QuestScreenText.JOURNAL_RETURN_TO));
        assertEquals(1, formatArguments(lang, QuestScreenText.JOURNAL_MORE_STEPS));
        assertEquals(1, formatArguments(lang, QuestScreenText.REWARDS_MORE));
        assertEquals(1, formatArguments(lang, QuestScreenText.DIRECTIONS));
        assertEquals(1, formatArguments(lang, QuestScreenText.QUIT_CONFIRM_MESSAGE));
        assertEquals(1, formatArguments(lang, QuestScreenText.KEY_OPEN_JOURNAL));
        assertEquals(1, formatArguments(lang, QuestScreenText.KEY_OPEN_JOURNAL_UNBOUND));
        assertEquals(1, formatArguments(lang, QuestScreenText.OBJECTIVE_ADVANCED));
        assertEquals(1, formatArguments(lang, QuestScreenText.OBJECTIVE_READY_TO_CLAIM));
        assertEquals(1, formatArguments(lang, QuestScreenText.INVENTORY_FULL_DROPPED));
        assertEquals(1, formatArguments(lang, QuestScreenText.MIX_MISSING_OFF_HAND),
                "the missing off-hand item has to be named, so the sentence takes it");

        // The ones that take nothing must take nothing: a stray %s renders as itself.
        for (String key : List.of(QuestScreenText.FAREWELL, QuestScreenText.NEXT,
                QuestScreenText.JOURNAL_EMPTY, QuestScreenText.JOURNAL_CLOSE,
                QuestScreenText.JOURNAL_QUIT, QuestScreenText.HELP_BACK,
                QuestScreenText.STAGE_UNKNOWN, QuestScreenText.ITEM_UNKNOWN,
                QuestScreenText.GIVER_UNKNOWN, QuestScreenText.PENDING_CONFIRMATION,
                QuestScreenText.PLOT_OCCUPIED, QuestScreenText.PLOT_EXPIRED,
                QuestScreenText.CROP_LOST, QuestScreenText.WATER_FLOWING,
                QuestScreenText.WATER_PROTECTED,
                QuestScreenText.MIX_WRONG_BOWL, QuestScreenText.MIX_WRONG_DIRT)) {
            assertEquals(0, formatArguments(lang, key), key + " takes an argument nobody passes");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static int formatArguments(JsonObject lang, String key) {
        String value = lang.get(key).getAsString();
        Matcher matcher = Pattern.compile("%(?:(\\d+)\\$)?[sd]").matcher(value);
        int highest = 0;
        int positional = 0;
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            } else {
                positional++;
            }
        }
        return Math.max(highest, positional);
    }


    private static int countSubstring(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static int occurrences(List<String> lines, String key) {
        String marker = "\"" + key + "\":";
        int count = 0;
        for (String line : lines) {
            if (line.trim().startsWith(marker)) count++;
        }
        return count;
    }

    private static JsonObject lang() {
        return JsonParser.parseString(stripBom(readLang())).getAsJsonObject();
    }

    private static List<String> langLines() {
        return List.of(stripBom(readLang()).split("\r?\n"));
    }

    private static String readLang() {
        try {
            return Files.readString(LANG, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read en_us.json", e);
        }
    }

    /** en_us.json is UTF-8 with a BOM; JsonParser will not accept one. */
    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '﻿' ? text.substring(1) : text;
    }

    private static List<Path> questScreenSources() {
        try (Stream<Path> files = Files.walk(MOD_SOURCE.resolve("client/gui"))) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().startsWith("Quest"))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list the quest screen sources", e);
        }
    }

    private static List<Path> modSources() {
        try (Stream<Path> files = Files.walk(MOD_SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list the mod sources", e);
        }
    }

    private static List<String> readAll(List<Path> files) {
        List<String> contents = new ArrayList<>(files.size());
        for (Path file : files) contents.add(read(file));
        return contents;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}

package com.seggellion.britannia_mod.quest.achievement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M10: the {@code britannia_mod:quest/first_harvest} advancement resource
 * itself.
 *
 * <p>A missing or misnamed file is invisible at runtime -- {@code ServerAdvancementManager#get}
 * simply answers null, the grant logs {@code NO_ADVANCEMENT} and the questline quietly loses its
 * third result. This asserts the file is where {@link QuestAchievementAward#advancementId} looks
 * for it, in the shape the established {@code quest/ring_destroyed} uses, and that both of its
 * player-facing strings are translation keys with lines in {@code en_us.json}.
 */
class QuestAchievementResourceTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path DATA = PROJECT.resolve("src/main/resources/data/britannia_mod/advancement/quest");
    private static final Path LANG = PROJECT.resolve("src/main/resources/assets/britannia_mod/lang/en_us.json");

    private static final String KEY = "first_harvest";

    private static JsonObject read(Path path) throws IOException {
        // en_us.json carries a BOM; strip it rather than let the parser trip on it.
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') raw = raw.substring(1);
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test
    void theAdvancementSitsWhereTheGrantLooksForIt() {
        assertEquals("britannia_mod:quest/" + KEY,
            String.valueOf(QuestAchievementAward.advancementId(KEY)));
        assertTrue(Files.isRegularFile(DATA.resolve(KEY + ".json")),
            "data/britannia_mod/advancement/quest/" + KEY + ".json is missing; the grant would "
                + "silently answer NO_ADVANCEMENT");
    }

    @Test
    void theAdvancementHasTheShapeAQuestAdvancementIsGrantedBy() throws IOException {
        JsonObject advancement = read(DATA.resolve(KEY + ".json"));
        JsonObject established = read(DATA.resolve("ring_destroyed.json"));

        // Granted server-side by awarding every criterion, so it must have at least one and none
        // of them may be earnable by playing: minecraft:impossible, exactly as ring_destroyed.
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertFalse(criteria.keySet().isEmpty(), "an advancement with no criteria can never be awarded");
        assertEquals(established.getAsJsonObject("criteria").keySet(), criteria.keySet());
        for (String criterion : criteria.keySet()) {
            assertEquals("minecraft:impossible",
                criteria.getAsJsonObject(criterion).get("trigger").getAsString());
        }

        JsonObject display = advancement.getAsJsonObject("display");
        assertEquals("challenge", display.get("frame").getAsString(),
            "the challenge frame is what makes this read as a milestone rather than a step");
        assertFalse(display.get("hidden").getAsBoolean());
        assertTrue(display.getAsJsonObject("icon").has("id"));

        // Deliberately unlike ring_destroyed. The questline raises its OWN challenge-style toast
        // and plays ui.toast.challenge_complete itself (QuestClientActions), so leaving vanilla's
        // popup on would show two toasts and play the same sound twice in the same tick. The chat
        // announcement is kept: it is the public half, and nothing else says it.
        assertFalse(display.get("show_toast").getAsBoolean(),
            "vanilla's toast would double the questline's own toast and its sound");
        assertTrue(display.get("announce_to_chat").getAsBoolean());
    }

    @Test
    void bothOfItsPlayerFacingStringsAreTranslationKeysWithLines() throws IOException {
        JsonObject display = read(DATA.resolve(KEY + ".json")).getAsJsonObject("display");
        JsonObject lang = read(LANG);

        for (String field : new String[] { "title", "description" }) {
            assertTrue(display.get(field).isJsonObject(),
                "display." + field + " must be a translatable component, not a literal");
            String key = display.getAsJsonObject(field).get("translate").getAsString();
            assertTrue(lang.has(key), "en_us.json is missing " + key);
            assertFalse(lang.get(key).getAsString().isBlank(), key + " is blank");
        }

        // The toast the questline raises names the achievement by this same title key, so the
        // advancements screen and the toast can never disagree.
        assertEquals(display.getAsJsonObject("title").get("translate").getAsString(),
            QuestAchievementAward.display(KEY, "First Harvest").translationKey());
    }

    /**
     * Every server-side path an authoritative Rails answer can reach the game by grants the
     * advancement, and no client-side path does.
     *
     * <p>Only one of the three is driven end to end by a GameTest (the action-event boundary, in
     * {@code QuestAchievementGameTests}); the turn-in needs a live Rails and the legacy observer
     * needs a Rails without the v2 route. A path that quietly stopped granting would leave the
     * questline announcing an achievement with no advancement behind it and no test would notice,
     * so the call is asserted where it is made.
     */
    @Test
    void everyAuthoritativeAnswerPathGrantsTheAdvancementAndNoClientPathDoes() throws IOException {
        Path source = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
        for (String relative : new String[] {
                "quest/QuestProxyService.java",              // the turn-in: quest 5's claim
                "quest/action/QuestActionDispatcher.java",   // the M4 action-event boundary
                "quest/QuestObjectiveWatcher.java" }) {      // the pre-M4 observer fallback
            String code = Files.readString(source.resolve(relative), StandardCharsets.UTF_8);
            assertTrue(code.contains("QuestAchievementAward.grantAndFilter("),
                relative + " no longer grants the quest advancement at its authoritative boundary");
        }

        // The client renders what it is given and decides nothing: a grant on the client would be
        // a claim the player could make for themselves.
        String clientActions = Files.readString(
            source.resolve("client/quest/QuestClientActions.java"), StandardCharsets.UTF_8);
        assertFalse(clientActions.contains("grantAndFilter"),
            "the client must not grant advancements; the server decides at the completion boundary");
        assertFalse(clientActions.contains("getAdvancements()"),
            "the client must not touch advancements");
    }
}

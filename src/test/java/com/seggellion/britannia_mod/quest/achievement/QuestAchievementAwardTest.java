package com.seggellion.britannia_mod.quest.achievement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M10: the pure half of the achievement boundary -- which achievements an
 * authoritative response announces, what advancement each names, how it is titled on screen, and
 * which announcements survive the server's decision.
 *
 * <p>The grant itself needs a real {@code ServerPlayer} and lives in
 * {@code QuestAchievementGameTests}; everything decidable without a server is decided here.
 */
class QuestAchievementAwardTest {

    private static JsonObject response(String clientActionsJson) {
        return JsonParser.parseString("{\"success\":true,\"client_actions\":" + clientActionsJson + "}")
            .getAsJsonObject();
    }

    // ---------------------------------------------------------------- announcements

    @Test
    void anAchievementActionWithAnExplicitKeyIsAnnouncedByThatKey() {
        List<QuestAchievementAward.Announcement> announced = QuestAchievementAward.announced(response(
            "[{\"type\":\"achievement\",\"key\":\"first_harvest\",\"name\":\"First Harvest\","
                + "\"sound\":\"ui.toast.challenge_complete\"}]"));

        assertEquals(1, announced.size());
        assertEquals("first_harvest", announced.get(0).key());
        assertEquals("First Harvest", announced.get(0).title());
    }

    /** A Rails that predates the explicit key sends only the titleized name; it still resolves. */
    @Test
    void anAchievementActionWithoutAKeyRecoversItFromTheName() {
        List<QuestAchievementAward.Announcement> announced = QuestAchievementAward.announced(response(
            "[{\"type\":\"achievement\",\"name\":\"First Harvest\"}]"));

        assertEquals(1, announced.size());
        assertEquals("first_harvest", announced.get(0).key());
    }

    @Test
    void theActionSpellingIsAcceptedAsWellAsTheTypeSpelling() {
        assertEquals(List.of(new QuestAchievementAward.Announcement("first_harvest", "First Harvest")),
            QuestAchievementAward.announced(response(
                "[{\"action\":\"achievement\",\"name\":\"First Harvest\"}]")));
    }

    @Test
    void otherClientActionsAreNotAchievements() {
        assertEquals(List.of(), QuestAchievementAward.announced(response(
            "[{\"type\":\"stat_gain\",\"karma\":3},{\"type\":\"spawn_escort\",\"entity_type\":\"x\"}]")));
    }

    @Test
    void aResponseWithNoClientActionsAnnouncesNothing() {
        assertEquals(List.of(), QuestAchievementAward.announced(
            JsonParser.parseString("{\"success\":true}").getAsJsonObject()));
        assertEquals(List.of(), QuestAchievementAward.announced(response("[]")));
        assertEquals(List.of(), QuestAchievementAward.announced(null));
    }

    @Test
    void theSameAchievementTwiceInOneResponseIsAnnouncedOnce() {
        assertEquals(1, QuestAchievementAward.announced(response(
            "[{\"type\":\"achievement\",\"key\":\"first_harvest\"},"
                + "{\"type\":\"achievement\",\"name\":\"First Harvest\"}]")).size());
    }

    /** The same bound QuestProxyService puts on a client-action array: past it, nothing is read. */
    @Test
    void anOversizedClientActionArrayAnnouncesNothing() {
        JsonArray actions = new JsonArray();
        for (int i = 0; i <= QuestAchievementAward.MAX_CLIENT_ACTIONS; i++) {
            JsonObject action = new JsonObject();
            action.addProperty("type", "achievement");
            action.addProperty("key", "achievement_" + i);
            actions.add(action);
        }
        assertEquals(List.of(), QuestAchievementAward.announced(response(actions.toString())));
    }

    // ---------------------------------------------------------------- keys and ids

    @Test
    void aKeyIsLowerCasedAndReducedToAnAdvancementPath() {
        assertEquals("first_harvest", QuestAchievementAward.sanitize("First Harvest"));
        assertEquals("first_harvest", QuestAchievementAward.sanitize("  first_harvest  "));
        assertEquals("ring_destroyed", QuestAchievementAward.sanitize("Ring Destroyed"));
    }

    @Test
    void aKeyCannotClimbOutOfTheQuestFolder() {
        assertEquals("secret", QuestAchievementAward.sanitize("../secret"));
        assertEquals("a/b", QuestAchievementAward.sanitize("a/./b"));
        assertEquals("", QuestAchievementAward.sanitize("/"));
        assertEquals("", QuestAchievementAward.sanitize(".."));
    }

    @Test
    void aKeyIsBounded() {
        String oversized = "x".repeat(QuestAchievementAward.MAX_KEY_LENGTH + 40);
        assertEquals(QuestAchievementAward.MAX_KEY_LENGTH, QuestAchievementAward.sanitize(oversized).length());
    }

    @Test
    void anAchievementKeyNamesAnAdvancementUnderQuest() {
        ResourceLocation id = QuestAchievementAward.advancementId("first_harvest");
        assertEquals("britannia_mod:quest/first_harvest", String.valueOf(id));
        assertEquals(id, QuestAchievementAward.advancementId("First Harvest"));
    }

    @Test
    void anEmptyKeyNamesNoAdvancement() {
        assertNull(QuestAchievementAward.advancementId(""));
        assertNull(QuestAchievementAward.advancementId(null));
        assertNull(QuestAchievementAward.advancementId(".."));
        assertNull(QuestAchievementAward.advancementId("/"));
        // A key that reduces to SOMETHING still names an advancement -- one that simply is not
        // loaded, which the grant reports as NO_ADVANCEMENT rather than pretending it granted.
        assertEquals("britannia_mod:quest/___",
            String.valueOf(QuestAchievementAward.advancementId("!!!/../")));
    }

    // ---------------------------------------------------------------- naming on screen

    @Test
    void anAchievementIsNamedByItsOwnAdvancementTitle() {
        QuestAchievementAward.Display display = QuestAchievementAward.display("first_harvest", "First Harvest");
        assertEquals("advancements.britannia_mod.quest.first_harvest.title", display.translationKey());
        assertEquals("First Harvest", display.fallback());
    }

    @Test
    void anAchievementWithNoNameStillFallsBackToSomethingReadable() {
        QuestAchievementAward.Display display = QuestAchievementAward.display("first_harvest", "");
        assertEquals("advancements.britannia_mod.quest.first_harvest.title", display.translationKey());
        assertEquals("first_harvest", display.fallback(),
            "the raw key beats showing the translation key itself");
    }

    @Test
    void anAchievementWithNothingUsableHasNoTranslationKeyAtAll() {
        assertEquals(new QuestAchievementAward.Display("", ""), QuestAchievementAward.display(null, null));
        assertEquals(new QuestAchievementAward.Display("", ""), QuestAchievementAward.display("", "  "));
    }

    // ---------------------------------------------------------------- the announcement filter

    @Test
    void onlyAnAlreadyEarnedAdvancementSilencesAnAnnouncement() {
        assertTrue(QuestAchievementAward.announceToPlayer(QuestAchievementAward.Grant.AWARDED));
        assertFalse(QuestAchievementAward.announceToPlayer(QuestAchievementAward.Grant.ALREADY_EARNED));
        // No advancement means no token to decide with, so the pre-M10 behaviour stands.
        assertTrue(QuestAchievementAward.announceToPlayer(QuestAchievementAward.Grant.NO_ADVANCEMENT));
        assertTrue(QuestAchievementAward.announceToPlayer(QuestAchievementAward.Grant.INVALID_KEY));
    }

    @Test
    void filteringRemovesOnlyTheNamedAchievementAndLeavesTheRestOfTheResponseAlone() {
        JsonObject root = JsonParser.parseString(
            "{\"success\":true,\"quest_state_id\":\"9005\",\"granted_items\":[{\"id\":\"g\",\"count\":1}],"
                + "\"client_actions\":["
                + "{\"type\":\"achievement\",\"key\":\"first_harvest\",\"name\":\"First Harvest\"},"
                + "{\"type\":\"achievement\",\"key\":\"other\"},"
                + "{\"type\":\"stat_gain\",\"karma\":3}]}").getAsJsonObject();

        JsonObject filtered = QuestAchievementAward.withoutAnnouncements(root, Set.of("first_harvest"));

        JsonArray kept = filtered.getAsJsonArray("client_actions");
        assertEquals(2, kept.size());
        assertEquals("other", kept.get(0).getAsJsonObject().get("key").getAsString());
        assertEquals("stat_gain", kept.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("9005", filtered.get("quest_state_id").getAsString());
        assertEquals(1, filtered.getAsJsonArray("granted_items").size());

        // The original is untouched: the caller may still be holding it.
        assertEquals(3, root.getAsJsonArray("client_actions").size());
    }

    @Test
    void filteringByANameDerivedKeyMatchesAKeylessAction() {
        JsonObject root = response("[{\"type\":\"achievement\",\"name\":\"First Harvest\"}]");
        assertEquals(0, QuestAchievementAward
            .withoutAnnouncements(root, Set.of("first_harvest"))
            .getAsJsonArray("client_actions").size());
    }
}

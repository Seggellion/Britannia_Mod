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

    /**
     * M11: every advancement under {@code advancement/quest/} parses, and everything it names
     * resolves.
     *
     * <p>The per-file assertions above are about {@code first_harvest} and are worth keeping, but
     * they are a list somebody has to remember to extend. This walks the folder instead, so the
     * next quest advancement is validated the day it is added rather than the day somebody notices
     * its title rendering as its own key.
     *
     * <h2>The one file this cannot hold to the whole rule, and why</h2>
     * {@code ring_destroyed.json} predates this project and carries two defects of its own: its
     * {@code display.title} and {@code display.description} are string literals rather than
     * translatable components, so that advancement is untranslatable; and its icon,
     * {@code britannia_mod:one_ring}, is a registered item with <b>no line in en_us.json</b>, so the
     * advancements screen shows a player the string {@code item.britannia_mod.one_ring}.
     *
     * <p>Both are real and both are recorded here rather than fixed. The ring belongs to another
     * questline, its display name is content somebody owns, and inventing one -- or rewriting
     * another quest's advancement -- is not this milestone's to do. What the exception costs is
     * nothing: the file is named, the two defects are named, and a THIRD advancement cannot join it
     * quietly, because the exception is one filename and not a predicate.
     */
    @Test
    void everyQuestAdvancementParsesAndEverythingItNamesResolves() throws IOException {
        JsonObject lang = read(LANG);
        String itemRegistry = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"),
                StandardCharsets.UTF_8);

        java.util.List<Path> files;
        try (java.util.stream.Stream<Path> walk = Files.list(DATA)) {
            files = walk.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertFalse(files.isEmpty(), "there are no quest advancements at all under " + DATA);

        for (Path file : files) {
            String name = file.getFileName().toString();
            JsonObject advancement = read(file);

            JsonObject criteria = advancement.getAsJsonObject("criteria");
            assertTrue(criteria != null && !criteria.keySet().isEmpty(),
                    name + " has no criteria, so it can never be awarded");
            for (String criterion : criteria.keySet()) {
                String trigger = criteria.getAsJsonObject(criterion).get("trigger").getAsString();
                assertTrue(trigger.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"),
                        name + " criterion " + criterion + " names " + trigger
                                + ", which is not a resource location");
            }

            JsonObject display = advancement.getAsJsonObject("display");
            assertTrue(display != null, name + " has no display block, so nothing can show it");

            // The icon has to be a real item, or the advancements screen draws a missing-texture
            // cube. Checked against the registrations rather than a live registry, which is the
            // same way every other asset contract test in this repository resolves a mod id.
            String icon = display.getAsJsonObject("icon").get("id").getAsString();
            assertTrue(icon.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"),
                    name + " has the icon id " + icon + ", which is not a resource location");
            if (icon.startsWith("britannia_mod:")) {
                String path = icon.substring("britannia_mod:".length());
                assertTrue(itemRegistry.contains("register(\"" + path + "\""),
                        name + " is iconed with " + icon + ", which nothing registers");
                if (!PRE_EXISTING_UNTRANSLATED.contains(name)) {
                    assertTrue(lang.has("item.britannia_mod." + path)
                                    || lang.has("block.britannia_mod." + path),
                            name + " is iconed with " + icon + ", which has no display name; the "
                                    + "advancements screen would show its registry path");
                }
            }

            if (!PRE_EXISTING_UNTRANSLATED.contains(name)) {
                for (String field : new String[]{"title", "description"}) {
                    assertTrue(display.get(field).isJsonObject(),
                            name + ": display." + field + " must be translatable, not a literal");
                    String key = display.getAsJsonObject(field).get("translate").getAsString();
                    assertTrue(lang.has(key), name + ": en_us.json is missing " + key);
                    assertFalse(lang.get(key).getAsString().isBlank(),
                            name + ": " + key + " is blank");
                }
            }

            // The file name is the key the grant looks the advancement up by, so a rename that did
            // not follow the sanitizer would make the advancement unreachable rather than missing.
            String key = name.substring(0, name.length() - ".json".length());
            assertEquals(key, QuestAchievementAward.sanitize(key),
                    name + " is not the sanitized form of its own key, so the grant would look "
                            + "somewhere else for it");
        }
    }

    @Test
    void theOnlyExemptAdvancementIsStillTheOneTheExemptionDescribes() {
        // A guard on the exception. If somebody translates ring_destroyed, or gives the one ring a
        // display name, this says so rather than leaving the exemption -- and its prose -- standing
        // over a file that no longer needs it.
        assertEquals(java.util.Set.of("ring_destroyed.json"), PRE_EXISTING_UNTRANSLATED,
                "the exemption list grew; a new advancement must not join it quietly");
        try {
            JsonObject display = read(DATA.resolve("ring_destroyed.json")).getAsJsonObject("display");
            boolean stillLiteral = display.get("title").isJsonPrimitive()
                    && display.get("description").isJsonPrimitive();
            boolean stillUnnamed = !read(LANG).has("item.britannia_mod.one_ring");
            assertTrue(stillLiteral || stillUnnamed,
                    "ring_destroyed no longer has either defect the exemption is for; remove the "
                            + "exemption and let the walk hold it to the whole rule");
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    /**
     * Advancements that predate this project and carry defects it does not own. One entry, named,
     * with the reasons in the javadoc of the walk above.
     */
    private static final java.util.Set<String> PRE_EXISTING_UNTRANSLATED =
            java.util.Set.of("ring_destroyed.json");
}

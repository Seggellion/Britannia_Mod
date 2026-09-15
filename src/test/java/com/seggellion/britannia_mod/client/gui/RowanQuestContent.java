package com.seggellion.britannia_mod.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The authored Rowan questline content the layout tests and the evidence renderer draw.
 *
 * <p>Two sources, and the difference matters:
 * <ul>
 *   <li>{@link #stage5NodeMetadata()} and {@link #stage5JournalEntry()} read the <b>frozen
 *       contract fixtures</b> at {@code src/test/resources/quest_contract/v1/}. Those files are
 *       M7's, they are covered by a SHA-256 manifest, and nothing here writes to them.</li>
 *   <li>The mixing guide below is <b>transcribed</b> from the acceptance draft's steps 22-26
 *       ({@code docs/projects/rowan-farming-questline/ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md}),
 *       because the mod repository holds no frozen fixture for the stage-4 node -- that content
 *       lives in Rails. It is the authored recipe, not invented content, but it is a transcription
 *       and the report says so.</li>
 * </ul>
 *
 * <p>The guide deliberately has no bucket row. The acceptance draft's step 22 requires the guide to
 * show "hands, ingredients, results, and returned bowls; no mention of the bucket", and
 * {@code QuestMixingGuideLayoutTest} asserts that against this content.
 */
public final class RowanQuestContent {

    private RowanQuestContent() {
    }

    private static final Path PROJECT =
            Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path FIXTURES =
            PROJECT.resolve("src/test/resources/quest_contract/v1");

    public static JsonObject stage5NodeMetadata() {
        return readFixture("node_metadata_stage5.json");
    }

    public static JsonObject stage5JournalEntry() {
        return readFixture("journal_entry_stage5.json");
    }

    public static JsonObject readFixture(String name) {
        try {
            String text = Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read frozen fixture " + name, e);
        }
    }

    // ---------------------------------------------------------------- the mixing guide

    public static final String MIXING_TITLE = "Rowan's mixing guide";

    public static final String MIXING_BODY =
            "Fertilized dirt is mixed by hand, one bowl at a time. Hold the bowl in your main hand "
            + "and what goes into it in your off hand, then Use with nothing in the way.";

    /**
     * The four steps of the fertilized dirt recipe, in order, as the acceptance draft states them.
     * The final step returns both bowls, which is the row the layout has to hold room for.
     */
    public static List<QuestNodePresentation.GuideStep> mixingGuide() {
        return List.of(
                new QuestNodePresentation.GuideStep(
                        "britannia_mod:empty_bowl", "britannia_mod:dirt",
                        "Use with nothing in the way", "britannia_mod:bowl_of_dirt", List.of()),
                new QuestNodePresentation.GuideStep(
                        "britannia_mod:bowl_of_dirt", "britannia_mod:dung",
                        "Use with nothing in the way", "britannia_mod:bowl_of_fertile_dirt", List.of()),
                new QuestNodePresentation.GuideStep(
                        "britannia_mod:empty_bowl", "",
                        "Use on the Water Well, or on still water", "britannia_mod:bowl_of_water",
                        List.of()),
                new QuestNodePresentation.GuideStep(
                        "britannia_mod:bowl_of_fertile_dirt", "britannia_mod:bowl_of_water",
                        "Use with nothing in the way", "britannia_mod:fertilized_dirt",
                        List.of("britannia_mod:empty_bowl", "britannia_mod:empty_bowl")));
    }

    /** Every item id the mixing guide names, for the localization test. */
    public static List<String> mixingGuideItemIds() {
        return List.of(
                "britannia_mod:empty_bowl",
                "britannia_mod:dirt",
                "britannia_mod:bowl_of_dirt",
                "britannia_mod:dung",
                "britannia_mod:bowl_of_fertile_dirt",
                "britannia_mod:bowl_of_water",
                "britannia_mod:fertilized_dirt");
    }

    // ---------------------------------------------------------------- dialogue bodies

    /** Rowan's offer, as the dialogue screen shows it before the quest is accepted. */
    public static final String OFFER_BODY =
            "Good morrow. I am Rowan, and this is my field. If you have a mind to learn the "
            + "growing of things, I will set you five tasks, and you shall keep what you make of "
            + "them. Gather for me, mix for me, plant for me, and you will not leave here hungry.";

    /** What Rowan says while the apprentice is part-way through. */
    public static final String WORKING_BODY =
            "The plot is prepared and the seed is in the ground. Water it, and mind you do not "
            + "leave it too long between one task and the next -- the grass takes back what is not "
            + "tended.";

    /** What Rowan says when everything is done and the reward is waiting. */
    public static final String DONE_BODY =
            "A full basket, and grown by your own hand. Take your pay, and the dirt you mixed is "
            + "yours to keep -- you will want it for the next plot.";

    /** The per-spawner directions hint an administrator can set on Rowan's spawner (M6). */
    public static final String LOCAL_DIRECTIONS =
            "The public plots are along the lane past the barn; the well is behind me.";
}

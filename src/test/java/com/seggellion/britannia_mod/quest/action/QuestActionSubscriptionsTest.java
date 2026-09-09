package com.seggellion.britannia_mod.quest.action;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestObjectiveTriggers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M5: the rule that decides whether a farming action becomes an event at
 * all (protocol section 2.2), driven by the frozen stage-5 journal entry.
 *
 * <p>The subscription is read from the SERVER's journal. Everything asserted here is about what
 * the mod sends, never about what the player is owed: section 2.1 is explicit that Rails
 * re-evaluates every condition, so a match here is a filter and nothing more.
 */
class QuestActionSubscriptionsTest {
    private static final UUID PLANTER = UUID.fromString(QuestActionContractFixtures.PLAYER_UUID);
    private static final UUID SOMEONE_ELSE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOUND_CYCLE = UUID.fromString(QuestActionContractFixtures.CROP_CYCLE_UUID);
    private static final UUID OTHER_CYCLE = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final String BOUND_PLOT = QuestActionContractFixtures.PLOT_KEY;

    @Test
    void theFrozenJournalEntryPublishesTheActionTriggerAndAllFourStepsWithTheirBoundValues() {
        QuestObjectiveTriggers triggers = stage5Triggers();

        assertTrue(triggers.hasActionSubscriptions());
        assertEquals("crop_harvested", triggers.action().triggerKey());
        assertEquals("crop_harvest", triggers.action().action());
        assertEquals("carrot", triggers.action().match().get("crop_id"));
        assertTrue(triggers.action().requirePlanter(), "require_planter is lifted out of match");
        assertEquals(BOUND_PLOT, triggers.action().requireBound().get("plot_key"));
        assertEquals(BOUND_CYCLE.toString(), triggers.action().requireBound().get("crop_cycle_uuid"));

        assertEquals(List.of("plot_hoed", "plot_fertilized", "crop_planted", "crop_watered"),
            triggers.steps().stream().map(QuestObjectiveTriggers.ActionStep::key).toList());
        assertTrue(triggers.steps().stream().allMatch(QuestObjectiveTriggers.ActionStep::done),
            "the frozen entry is at the point where only the harvest is outstanding");
        assertEquals("true", triggers.steps().get(0).match().get("community_plot"),
            "a published boolean condition compares as its text, on both sides");
    }

    @Test
    void theHarvestOfTheBoundCycleByItsPlanterIsReported() {
        List<QuestActionSubscriptions.Match> matches = match(PLANTER,
            QuestAction.CROP_HARVEST, harvest(BOUND_PLOT, "carrot", BOUND_CYCLE, PLANTER));

        assertEquals(1, matches.size(), "exactly one subscription, the advancing trigger");
        assertEquals("crop_harvested", matches.get(0).triggerKey());
        assertEquals("9005", matches.get(0).questStateId());
        assertTrue(matches.get(0).advancing(), "an action_trigger advances the node");
    }

    @Test
    void harvestingWhatSomebodyElsePlantedIsNeverReported() {
        assertTrue(match(SOMEONE_ELSE, QuestAction.CROP_HARVEST,
            harvest(BOUND_PLOT, "carrot", BOUND_CYCLE, PLANTER)).isEmpty(),
            "the harvester is not the planter, so require_planter fails");
    }

    @Test
    void anotherCropAnotherPlotAndAnotherCycleAreAllRefused() {
        assertTrue(match(PLANTER, QuestAction.CROP_HARVEST,
            harvest(BOUND_PLOT, "cabbage", BOUND_CYCLE, PLANTER)).isEmpty(), "wrong crop");
        assertTrue(match(PLANTER, QuestAction.CROP_HARVEST,
            harvest("minecraft:overworld:1:2:3", "carrot", BOUND_CYCLE, PLANTER)).isEmpty(), "wrong plot");
        assertTrue(match(PLANTER, QuestAction.CROP_HARVEST,
            harvest(BOUND_PLOT, "carrot", OTHER_CYCLE, PLANTER)).isEmpty(),
            "the right crop in the right hole, but a later cycle");
    }

    @Test
    void aSubjectMissingAConditionsFieldNeverMatchesBecauseAbsenceIsNotEquality() {
        QuestActionSubject withoutPlanter = QuestActionSubject.builder()
            .text("plot_key", BOUND_PLOT)
            .text("crop_id", "carrot")
            .text("crop_cycle_uuid", BOUND_CYCLE.toString())
            .text("planter_uuid", PLANTER.toString())
            .build();
        assertFalse(match(PLANTER, QuestAction.CROP_HARVEST, withoutPlanter).isEmpty(),
            "the control: with every bound field present it does match");

        QuestActionSubject withoutCycle = QuestActionSubject.builder()
            .text("plot_key", BOUND_PLOT)
            .text("crop_id", "carrot")
            .text("crop_cycle_uuid", BOUND_CYCLE.toString())
            .text("planter_uuid", PLANTER.toString())
            .build();
        assertTrue(QuestActionSubscriptions.matches(PLANTER, List.of(entry(triggersWithoutSteps())),
            QuestAction.CROP_PLANT, withoutCycle).isEmpty(),
            "a different action never matches the harvest trigger");
    }

    @Test
    void aDifferentActionEntirelyIsNotReported() {
        QuestActionSubject dirt = QuestActionSubject.builder().text("item_id", "britannia_mod:dirt").build();
        assertTrue(match(PLANTER, QuestAction.DIRT_GATHER, dirt).isEmpty(),
            "nothing in the stage-5 entry subscribes to dirt gathering");
    }

    @Test
    void anEmptyOrUnloadedJournalReportsNothingRatherThanEverything() {
        QuestActionSubject subject = harvest(BOUND_PLOT, "carrot", BOUND_CYCLE, PLANTER);
        assertTrue(QuestActionSubscriptions.matches(PLANTER, List.of(), QuestAction.CROP_HARVEST, subject).isEmpty());
        assertTrue(QuestActionSubscriptions.matches(PLANTER, null, QuestAction.CROP_HARVEST, subject).isEmpty());
        assertTrue(QuestActionSubscriptions.matches(null, List.of(entry(stage5Triggers())),
            QuestAction.CROP_HARVEST, subject).isEmpty());
    }

    @Test
    void aQuestWithOnlyTheLegacyObserversSubscribesToNoFarmingAction() {
        ClientQuestEntry legacy = new ClientQuestEntry("7", "40", "old", "Rowan", "Legacy", "", "", "accepted",
            new QuestObjectiveTriggers(new QuestObjectiveTriggers.Location("arrived",
                net.minecraft.core.BlockPos.ZERO, net.minecraft.core.BlockPos.ZERO), null, null));

        assertTrue(QuestActionSubscriptions.matches(PLANTER, List.of(legacy), QuestAction.CROP_HARVEST,
            harvest(BOUND_PLOT, "carrot", BOUND_CYCLE, PLANTER)).isEmpty(),
            "location, pickup and destroy quests keep working and cost no action events");
    }

    @Test
    void anOutstandingStepIsPreferredOverOneAlreadyRecorded() {
        QuestObjectiveTriggers triggers = stepsOnly(false);
        List<QuestActionSubscriptions.Match> matches = QuestActionSubscriptions.matches(PLANTER,
            List.of(entry(triggers)), QuestAction.PLOT_HOE, hoe(BOUND_PLOT));

        assertEquals(1, matches.size());
        assertEquals("plot_hoed", matches.get(0).triggerKey());
        assertFalse(matches.get(0).advancing(), "a step records progress, it does not advance the node");
    }

    @Test
    void hoeingAgainAfterEveryStepIsDoneStillReportsSoAReclaimedPlotCanRestart() {
        List<QuestActionSubscriptions.Match> matches = QuestActionSubscriptions.matches(PLANTER,
            List.of(entry(stepsOnly(true))), QuestAction.PLOT_HOE, hoe(BOUND_PLOT));

        assertEquals(1, matches.size(),
            "section 2.1 makes an earlier step restart the sequence; the mod has to send it for that to happen");
        assertEquals("plot_hoed", matches.get(0).triggerKey());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private static List<QuestActionSubscriptions.Match> match(UUID player, QuestAction action,
                                                              QuestActionSubject subject) {
        return QuestActionSubscriptions.matches(player, List.of(entry(stage5Triggers())), action, subject);
    }

    private static QuestObjectiveTriggers stage5Triggers() {
        JsonObject entry = QuestActionContractFixtures.json(QuestActionContractFixtures.JOURNAL_ENTRY_STAGE5);
        return QuestObjectiveTriggers.fromJournalEntry(entry);
    }

    private static QuestObjectiveTriggers triggersWithoutSteps() {
        QuestObjectiveTriggers full = stage5Triggers();
        return new QuestObjectiveTriggers(null, null, null, full.action(), List.of());
    }

    /** The stage-5 steps with only the hoeing step present, so the first-outstanding rule is visible. */
    private static QuestObjectiveTriggers stepsOnly(boolean done) {
        return new QuestObjectiveTriggers(null, null, null, null, List.of(
            new QuestObjectiveTriggers.ActionStep("plot_hoed", "plot_hoe",
                java.util.Map.of("community_plot", "true"), false, java.util.Map.of(), done)));
    }

    private static ClientQuestEntry entry(QuestObjectiveTriggers triggers) {
        return new ClientQuestEntry("9005", "45", "rowan_farming_5", "Rowan",
            "From Soil to Supper (5 of 5)", "", "", "accepted", triggers);
    }

    private static QuestActionSubject harvest(String plotKey, String cropId, UUID cycle, UUID planter) {
        return QuestActionEvents.cropHarvestSubject(plotKey, cropId, cycle, planter, true, 3);
    }

    private static QuestActionSubject hoe(String plotKey) {
        return QuestActionSubject.builder().text("plot_key", plotKey).flag("community_plot", true).build();
    }
}

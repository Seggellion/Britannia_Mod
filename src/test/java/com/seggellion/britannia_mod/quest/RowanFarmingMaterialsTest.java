package com.seggellion.britannia_mod.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What stages four and five actually need, and what a player who already has some of it is told.
 *
 * <p>The subtraction is the whole point. "Existing carried materials may satisfy a requirement; do
 * not force extra collection when the player already has enough" is a rule about the guidance as
 * much as about the hand-in, and a message that sends someone to find what is in their own pack is
 * how people learn to stop reading messages.
 */
class RowanFarmingMaterialsTest {

    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void onlyTheTwoStagesThatSpendMaterialsAskForAny() {
        assertTrue(RowanFarmingMaterials.requirementsFor("rowan_farming_1").isEmpty());
        assertTrue(RowanFarmingMaterials.requirementsFor("rowan_farming_2").isEmpty());
        assertTrue(RowanFarmingMaterials.requirementsFor("rowan_farming_3").isEmpty());
        assertFalse(RowanFarmingMaterials.requirementsFor(RowanQuestlineHooks.MIX_QUEST_KEY).isEmpty());
        assertFalse(RowanFarmingMaterials.requirementsFor(RowanQuestlineHooks.HARVEST_QUEST_KEY).isEmpty());
    }

    @Test
    void aQuestWithNoMaterialsAsksForNothingAndCannotBeToldOtherwise() {
        assertTrue(RowanFarmingMaterials.requirementsFor("some_other_quest").isEmpty());
        assertTrue(RowanFarmingMaterials.requirementsFor(null).isEmpty());
        assertTrue(RowanFarmingMaterials.outstanding("some_other_quest", Map.of()).isEmpty());
        assertFalse(RowanFarmingMaterials.needsDung("some_other_quest"));
    }

    @Test
    void bothRemixStagesNeedFreshDungAndDirt() {
        for (String stage : List.of(RowanQuestlineHooks.MIX_QUEST_KEY, RowanQuestlineHooks.HARVEST_QUEST_KEY)) {
            Map<String, Integer> tally = RowanFarmingMaterials.tally(stage);
            assertEquals(1, tally.get(RowanFarmingMaterials.DUNG), stage);
            assertEquals(1, tally.get(RowanFarmingMaterials.DIRT), stage);
            assertEquals(2, tally.get(RowanFarmingMaterials.EMPTY_BOWL), stage);
            assertTrue(RowanFarmingMaterials.needsDung(stage), stage);
        }
    }

    @Test
    void aPlayerCarryingEverythingIsToldNothing() {
        assertTrue(RowanFarmingMaterials.outstanding(RowanQuestlineHooks.MIX_QUEST_KEY, Map.of(
                RowanFarmingMaterials.DUNG, 1,
                RowanFarmingMaterials.DIRT, 1,
                RowanFarmingMaterials.EMPTY_BOWL, 2,
                RowanFarmingMaterials.WATER_BUCKET, 1)).isEmpty());
    }

    @Test
    void aSpareDungMeansOnlyTheRestIsAskedFor() {
        List<RowanFarmingMaterials.Requirement> outstanding = RowanFarmingMaterials.outstanding(
                RowanQuestlineHooks.HARVEST_QUEST_KEY, Map.of(
                        RowanFarmingMaterials.DUNG, 3,
                        RowanFarmingMaterials.EMPTY_BOWL, 2,
                        RowanFarmingMaterials.WATER_BUCKET, 1));

        assertEquals(1, outstanding.size());
        assertEquals(RowanFarmingMaterials.DIRT, outstanding.get(0).itemId());
        assertEquals(1, outstanding.get(0).count());
    }

    @Test
    void onlyTheShortfallIsReportedWhenAPlayerHasSomeOfWhatIsNeeded() {
        List<RowanFarmingMaterials.Requirement> outstanding = RowanFarmingMaterials.outstanding(
                RowanQuestlineHooks.MIX_QUEST_KEY, Map.of(RowanFarmingMaterials.EMPTY_BOWL, 1));

        assertEquals(1, byId(outstanding, RowanFarmingMaterials.EMPTY_BOWL),
                "one of the two bowls is already in the pack");
        assertEquals(1, byId(outstanding, RowanFarmingMaterials.DUNG));
        assertEquals(1, byId(outstanding, RowanFarmingMaterials.DIRT));
    }

    @Test
    void anEmptyBucketCountsForTheWaterStepBecauseTheWellFillsIt() {
        assertEquals(0, byId(RowanFarmingMaterials.outstanding(RowanQuestlineHooks.MIX_QUEST_KEY,
                        Map.of(RowanFarmingMaterials.BUCKET, 1)), RowanFarmingMaterials.WATER_BUCKET),
                "the step is bring water, and the container is the same object either side of it");
        assertEquals(1, byId(RowanFarmingMaterials.outstanding(RowanQuestlineHooks.MIX_QUEST_KEY,
                Map.of()), RowanFarmingMaterials.WATER_BUCKET));
    }

    @Test
    void anEmptyPackIsToldTheWholeList() {
        assertEquals(4, RowanFarmingMaterials.outstanding(
                RowanQuestlineHooks.HARVEST_QUEST_KEY, Map.of()).size());
        assertEquals(4, RowanFarmingMaterials.outstanding(
                RowanQuestlineHooks.HARVEST_QUEST_KEY, null).size());
    }

    @Test
    void bothRemixStagesBringTheDungScheduleForwardAndTheOthersDoNot() {
        assertTrue(RowanQuestlineHooks.expeditesDung(RowanQuestlineHooks.DUNG_QUEST_KEY));
        assertTrue(RowanQuestlineHooks.expeditesDung(RowanQuestlineHooks.MIX_QUEST_KEY));
        assertTrue(RowanQuestlineHooks.expeditesDung(RowanQuestlineHooks.HARVEST_QUEST_KEY));
        assertFalse(RowanQuestlineHooks.expeditesDung("rowan_farming_2"));
        assertFalse(RowanQuestlineHooks.expeditesDung("rowan_farming_3"));
        assertFalse(RowanQuestlineHooks.expeditesDung(null));
    }

    @Test
    void guidanceRepeatsAtMostOncePerStagePerWindow() {
        long start = 1_000_000L;
        assertTrue(RowanQuestlineHooks.due(PLAYER, RowanQuestlineHooks.MIX_QUEST_KEY, start));
        assertFalse(RowanQuestlineHooks.due(PLAYER, RowanQuestlineHooks.MIX_QUEST_KEY, start + 1_000L),
                "a journal refresh a second later must not repeat the line");
        // A different stage is its own conversation.
        assertTrue(RowanQuestlineHooks.due(PLAYER, RowanQuestlineHooks.HARVEST_QUEST_KEY, start + 1_000L));
        assertTrue(RowanQuestlineHooks.due(PLAYER, RowanQuestlineHooks.MIX_QUEST_KEY,
                start + RowanQuestlineHooks.GUIDANCE_INTERVAL_MILLIS + 1L));
        RowanQuestlineHooks.forgetPlayer(PLAYER);
        assertTrue(RowanQuestlineHooks.due(PLAYER, RowanQuestlineHooks.MIX_QUEST_KEY, start + 2_000L),
                "a player who logged out and back in starts the window fresh");
    }

    private static int byId(List<RowanFarmingMaterials.Requirement> outstanding, String itemId) {
        for (RowanFarmingMaterials.Requirement requirement : outstanding) {
            if (requirement.itemId().equals(itemId)) return requirement.count();
        }
        return 0;
    }
}

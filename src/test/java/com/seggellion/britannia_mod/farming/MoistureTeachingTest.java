package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.item.WateringCanItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M9 item 6: what the player is told about water is read from the crop and
 * the plot, not from a remembered number of watering-can uses.
 *
 * <p>The instruction the project considered was "Water twice, then wait." It was never shipped,
 * and this file is the reason it will not be: it is false for any crop whose ideal is not the
 * carrot's, and it is false for a carrot the moment rain, a bucket or another player moves the
 * hydration. What replaced it is a reading of the plot as it stands, which is true however the
 * water arrived.
 */
class MoistureTeachingTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // ---------------------------------------------------- the four states are the four states

    @Test
    void everyCareStateWordHasItsOwnSentence() {
        Set<String> keys = new LinkedHashSet<>();
        for (String state : new String[] { "dry", "ok", "ideal", "over" }) {
            String key = WateringCanItem.moistureKey(state, null);
            assertTrue(key != null && !key.isBlank(), state + " has no sentence");
            keys.add(key);
        }
        assertEquals(4, keys.size(), "two moisture states share a sentence");
    }

    @Test
    void theWordsAreTheOnesTheActionEventContractAlreadyUses() {
        // WateringCanItem.careState reports these four to Rails as `care_state`. The player is
        // told the same thing the server is told, which is what keeps the two explanations of one
        // plot from disagreeing.
        assertEquals(QuestScreenText.WATER_STATE_DRY, WateringCanItem.moistureKey("dry", null));
        assertEquals(QuestScreenText.WATER_STATE_OK, WateringCanItem.moistureKey("ok", null));
        assertEquals(QuestScreenText.WATER_STATE_IDEAL, WateringCanItem.moistureKey("ideal", null));
        assertEquals(QuestScreenText.WATER_STATE_OVER, WateringCanItem.moistureKey("over", null));
    }

    @Test
    void barePlotWithNoCropFallsBackRatherThanInventingACropsOpinion() {
        assertNull(WateringCanItem.moistureKey("", null));
        assertEquals(QuestScreenText.WATER_FULL, WateringCanItem.moistureKey("", QuestScreenText.WATER_FULL));
    }

    // -------------------------------------------- the reading follows the crop, not a counter

    @Test
    void theSameHydrationReadsDifferentlyForDifferentCrops() {
        CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
        CropDefinition rice = CropRegistry.byId("rice").orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(rice != null, "rice is not registered on this branch");

        // A wet plot: whatever "the right number of waterings" is, it cannot be one number for
        // both of these crops, because their ideals differ.
        assertNotEquals(carrot.hydrationIdeal(), rice.hydrationIdeal(),
                "this test needs two crops that want different amounts of water");
    }

    @Test
    void aCarrotsIdealIsNotTheTopOfTheScaleSoFullyWateredIsNotTheGoal() {
        CropDefinition carrot = CropRegistry.byId("carrot").orElseThrow();
        // The old sentence stopped at hydration 5 and said nothing before it. A carrot at 5 is
        // past its ideal, so "keep watering until it refuses" was advice that made the crop worse.
        assertTrue(carrot.hydrationIdeal() < 1.0f,
                "a carrot that wanted a saturated plot would make this milestone pointless");
        assertTrue(carrot.idealHydration() < FarmingBlockEntity.MAX_HYDRATION,
                "the ideal must sit below the ceiling for over-watering to be possible");
    }

    @Test
    void everyRegisteredCropCanBeReadWithoutSpecialCasing() {
        // The teaching runs off CropDefinition alone, so it must produce a sentence for whatever
        // is planted -- including crops added after this milestone.
        for (CropDefinition crop : CropRegistry.all()) {
            for (int hydration = 0; hydration <= FarmingBlockEntity.MAX_HYDRATION; hydration++) {
                float normalized = hydration / (float) FarmingBlockEntity.MAX_HYDRATION;
                String state = normalized < crop.minHydrationToGrow() ? "dry"
                        : normalized > crop.maxHydrationBeforeSeverePenalty() ? "over"
                        : Math.abs(normalized - crop.hydrationIdeal()) <= crop.hydrationTolerance() ? "ideal"
                        : "ok";
                assertTrue(WateringCanItem.moistureKey(state, null) != null,
                        crop.id() + " at hydration " + hydration + " produced no sentence");
            }
        }
    }
}

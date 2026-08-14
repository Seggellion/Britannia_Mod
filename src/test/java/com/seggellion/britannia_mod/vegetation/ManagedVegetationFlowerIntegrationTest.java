package com.seggellion.britannia_mod.vegetation;

import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationFlowerIntegrationTest {
    @Test
    void everyAuthoritativeSpeciesSupportsAllSevenManagedStages() {
        for (FlowerDefinition definition : FlowerRegistry.initial().definitions().values()) {
            for (int stage = 1; stage <= 7; stage++) {
                long transition = stage == 7 ? ManagedVegetationNode.NO_TRANSITION : 100L + stage;
                ManagedVegetationNode flower = ManagedVegetationNode.regrowing(BlockPos.ZERO, 1L)
                        .flower(ManagedVegetationProfile.FLOWER_ID, definition.id(), stage, transition);

                assertEquals(stage, flower.flowerStage());
                assertEquals(definition.id(), flower.flowerSpeciesId().orElseThrow());
                assertEquals(flower, ManagedVegetationNode.fromTag(flower.toTag()));
            }
        }
    }

    @Test
    void wildFlowerTimingUsesEachExistingGrowthProfile() {
        for (FlowerDefinition definition : FlowerRegistry.initial().definitions().values()) {
            long expected = (long) definition.growthProfile().baseGrowthTicks() * 40L;
            assertEquals(expected, ManagedVegetationManager.flowerStageDelay(definition, 40));
            assertTrue(expected > 0L);
        }
    }
}

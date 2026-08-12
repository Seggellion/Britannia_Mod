package com.seggellion.britannia_mod.vegetation;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodMossIntegrationTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @Test
    void bloodMossPersistsAsAnIndependentManagedLifecycle() {
        ManagedVegetationNode node = ManagedVegetationNode.regrowing(BlockPos.ZERO, 1L)
                .bloodMoss(ManagedVegetationProfile.BLOOD_MOSS_ID);

        assertEquals(ManagedVegetationLifecycle.BLOOD_MOSS, node.lifecycle());
        assertEquals(ManagedVegetationProfile.BLOOD_MOSS_ID, node.vegetationEntryId().orElseThrow());
        assertEquals(ManagedVegetationNode.NO_TRANSITION, node.nextTransitionGameTime());
        assertEquals(node, ManagedVegetationNode.fromTag(node.toTag()));
    }

    @Test
    void sharedSwampRuleUsesTheNeoForgeSwampTagAndDocumentsMangroveDecision() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/wildresource/SwampBiomeRules.java"
        ));
        assertTrue(source.contains("Tags.Biomes.IS_SWAMP"));
        assertTrue(source.contains("includes swamp and mangrove swamp"));
    }

    @Test
    void blockReusesExistingBloodMossItemTextureWithoutFarmingBlockSemantics() throws IOException {
        String model = Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/block/blood_moss.json"
        ));
        String block = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/block/BloodMossBlock.java"
        ));
        assertTrue(model.contains("britannia_mod:item/reagent_blood_moss"));
        assertFalse(block.contains("FarmingBlock"));
    }
}

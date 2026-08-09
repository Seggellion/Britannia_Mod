package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseFarmPlotAssignmentTest {
    private static final ResourceLocation POPPY =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "poppy");
    private static final ResourceLocation CORN =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "corn");

    @Test
    void uninitializedRoundTripsWithoutBecomingCleared() {
        HouseFarmPlotAssignment assignment = HouseFarmPlotAssignment.uninitialized();

        assertEquals(assignment, HouseFarmPlotAssignment.fromTag(assignment.toTag()));
        assertEquals(HouseFarmPlotAssignment.State.UNINITIALIZED, assignment.state());
        assertFalse(assignment.plantId().isPresent());
    }

    @Test
    void assignedFlowerAndCropRoundTripWithCanonicalIdentity() {
        HouseFarmPlotAssignment flower = HouseFarmPlotAssignment.assigned(
                HouseFarmPlotAssignment.Kind.FLOWER, POPPY
        );
        HouseFarmPlotAssignment crop = HouseFarmPlotAssignment.assigned(
                HouseFarmPlotAssignment.Kind.CROP, CORN
        );

        assertEquals(flower, HouseFarmPlotAssignment.fromTag(flower.toTag()));
        assertEquals(crop, HouseFarmPlotAssignment.fromTag(crop.toTag()));
        assertTrue(flower.isAssignedTo(HouseFarmPlotAssignment.Kind.FLOWER, POPPY));
        assertTrue(crop.isAssignedTo(HouseFarmPlotAssignment.Kind.CROP, CORN));
        assertFalse(crop.isAssignedTo(HouseFarmPlotAssignment.Kind.FLOWER, CORN));
    }

    @Test
    void intentionallyClearedRoundTripsAndMissingLegacyTagIsUninitialized() {
        HouseFarmPlotAssignment cleared = HouseFarmPlotAssignment.cleared();

        assertEquals(cleared, HouseFarmPlotAssignment.fromTag(cleared.toTag()));
        assertEquals(
                HouseFarmPlotAssignment.uninitialized(),
                HouseFarmPlotAssignment.fromTag(new CompoundTag())
        );
    }

    @Test
    void corruptAssignedDataFailsSafeToCleared() {
        CompoundTag missingId = new CompoundTag();
        missingId.putString("State", "ASSIGNED");
        missingId.putString("Kind", "CROP");

        CompoundTag invalidId = new CompoundTag();
        invalidId.putString("State", "ASSIGNED");
        invalidId.putString("Kind", "FLOWER");
        invalidId.putString("PlantId", "not a resource id");

        assertEquals(HouseFarmPlotAssignment.cleared(), HouseFarmPlotAssignment.fromTag(missingId));
        assertEquals(HouseFarmPlotAssignment.cleared(), HouseFarmPlotAssignment.fromTag(invalidId));
    }
}

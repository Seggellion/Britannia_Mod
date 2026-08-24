package com.seggellion.britannia_mod.structure;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much land a house asks for, held to what it actually needs.
 *
 * <p>The point of the change is not that the numbers are smaller — it is that every cell now
 * corresponds to something: ground the house stands on, or space the house occupies. The old rule
 * grew a skirt around the footprint and demanded unbroken flat grass and clear air across all of
 * it, which is why a fence post or one step of slope beside the wall refused the whole placement.
 */
class HousePlacementClearanceTest {

    /** A small house at the origin: 9 wide, 8 tall, 9 deep. */
    private static AABB smallHouse() {
        return new AABB(0, 64, 0, 9, 72, 9);
    }

    /** The castle: 34 x 20 x 35. */
    private static AABB castle() {
        return new AABB(0, 64, 0, 34, 84, 35);
    }

    /** The old rule's column count, so the two are comparable rather than merely asserted. */
    private static int previousColumnCount(AABB structureBox) {
        // minX - 1 .. floor(maxX) + 1, and maxX is the exclusive edge, so the skirt reached one
        // block out on the low side and two on the high side.
        int width = (int) (Math.floor(structureBox.maxX) + 1) - ((int) structureBox.minX - 1) + 1;
        int depth = (int) (Math.floor(structureBox.maxZ) + 1) - ((int) structureBox.minZ - 1) + 1;
        return width * depth;
    }

    @Test
    void foundationIsExactlyTheFootprint() {
        assertArrayEquals(new int[] { 0, 8, 0, 8 },
                HousePlacementClearance.foundationColumns(smallHouse()),
                "a small house stands on its own 9x9, and the validator should ask about that and "
                        + "nothing else");
        assertEquals(81, HousePlacementClearance.foundationColumnCount(smallHouse()));
    }

    @Test
    void theGroundLooksAtIsOneBlockBelowTheHouse() {
        assertEquals(63, HousePlacementClearance.foundationY(smallHouse()),
                "the foundation rule is about what the house stands on, not what it stands in");
    }

    @Test
    void theOccupiedVolumeIsExactlyWhatTheStructureWrites() {
        assertArrayEquals(new int[] { 0, 8, 64, 71, 0, 8 },
                HousePlacementClearance.occupiedVolume(smallHouse()),
                "the clear-space rule should cover the cells the template writes into and no more; "
                        + "the extra layer above the roof was padding");
    }

    /**
     * The reduction, stated as a number so "substantially smaller" is a fact rather than a claim.
     */
    @Test
    void theSkirtIsGoneAndTheReductionIsLarge() {
        int smallBefore = previousColumnCount(smallHouse());
        int smallAfter = HousePlacementClearance.foundationColumnCount(smallHouse());
        assertEquals(144, smallBefore, "the old rule asked a 9x9 house for 12x12 of flat ground");
        assertEquals(81, smallAfter);

        int castleBefore = previousColumnCount(castle());
        int castleAfter = HousePlacementClearance.foundationColumnCount(castle());
        assertEquals(1406, castleBefore, "the old rule asked the castle for 37x38 of flat ground");
        assertEquals(1190, castleAfter);

        assertTrue(smallAfter < smallBefore && castleAfter < castleBefore);
        // The important half is not the count but the shape: no cell outside the footprint is
        // consulted at all any more, so nothing beside the wall can refuse a placement.
        assertEquals((int) smallHouse().getXsize() * (int) smallHouse().getZsize(), smallAfter);
        assertEquals((int) castle().getXsize() * (int) castle().getZsize(), castleAfter);
    }

    /**
     * Clearance scales with the house rather than being a constant bolted onto it.
     *
     * <p>Which is what "derive it from the footprint" means: a castle needs castle-sized ground and
     * a cottage needs cottage-sized ground, and the difference between them is the buildings, not a
     * table of tolerances.
     */
    @Test
    void clearanceIsDerivedFromTheFootprintNotAddedToIt() {
        for (AABB box : new AABB[] { smallHouse(), castle(), new AABB(0, 64, 0, 18, 72, 18) }) {
            int[] columns = HousePlacementClearance.foundationColumns(box);
            assertEquals((int) box.minX, columns[0]);
            assertEquals((int) box.maxX - 1, columns[1]);
            assertEquals((int) box.minZ, columns[2]);
            assertEquals((int) box.maxZ - 1, columns[3]);
        }
    }
}

package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.util.HouseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which house a block belongs to, at the sizes the old rule could not handle.
 *
 * <p>{@code HouseUtil.findLot} used to answer this by scanning a 9x5x9 cube for any lot block
 * at all. It now asks which registered region contains the block. The difference is not a
 * bigger number -- it is that there is no number: a door thirty blocks inside a castle resolves
 * exactly as well as one standing on the doorstep, and a lot block one block away in the house
 * next door is not a candidate at all.
 *
 * <p>These exercise the region half in isolation, at real castle and keep dimensions, without a
 * world. The GameTest beside them takes the same path all the way to a key turning in a lock.
 */
class HouseRegionResolutionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void clearRegions() {
        StructureRegionManager.getChunkStructureMap().clear();
    }

    @Test
    void adoorTwentyEightBlocksIntoACastleResolvesTheCastle() {
        BlockPos origin = new BlockPos(1000, 64, -1000);
        UUID castle = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(castle, origin, 34, 20, 35));

        // The castle's own interior doors, at template z = 1, 5, 13 and 28. Three of the four
        // were outside the old cube; all four are inside the castle.
        for (int z : new int[] { 1, 5, 13, 28 }) {
            BlockPos door = origin.offset(17, 1, z);
            StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, door);
            assertNotNull(found, "no region contains the castle door at template z=" + z);
            assertEquals(castle, found.getHouseUuid(), "castle door at z=" + z + " resolved elsewhere");
        }
    }

    @Test
    void aDoorTwoBlocksBackFromTheKeepFrontResolvesTheKeep() {
        BlockPos origin = new BlockPos(-500, 70, 300);
        UUID keep = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(keep, origin, 26, 10, 25));

        // The keep's double door, template (12,1,2) and (13,1,2).
        for (int x : new int[] { 12, 13 }) {
            StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, origin.offset(x, 1, 2));
            assertNotNull(found);
            assertEquals(keep, found.getHouseUuid());
        }
    }

    @Test
    void theVillaAndPatioDoorsThatUsedToBeOutOfReachResolve() {
        BlockPos villaOrigin = new BlockPos(0, 64, 0);
        UUID villa = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(villa, villaOrigin, 13, 10, 13));

        // Both leaves of the villa's entrance, and its interior door on the upper floor.
        for (BlockPos door : new BlockPos[] {
                villaOrigin.offset(2, 1, 6), villaOrigin.offset(3, 1, 6), villaOrigin.offset(6, 4, 5) }) {
            StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, door);
            assertNotNull(found, "villa door at " + door + " resolved nothing");
            assertEquals(villa, found.getHouseUuid());
        }

        BlockPos patioOrigin = new BlockPos(500, 64, 500);
        UUID patio = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(patio, patioOrigin, 18, 8, 18));

        // The patio's deepest door, template (3,1,8) -- eight blocks in from the front wall.
        StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, patioOrigin.offset(3, 1, 8));
        assertNotNull(found);
        assertEquals(patio, found.getHouseUuid());
    }

    @Test
    void aBlockBelongsToTheHouseThatContainsItAndNotToTheNearestOne() {
        // Two small houses built wall to wall. The door is one block inside the left house and
        // one block from the right house's edge; under a proximity rule the wrong one can win.
        BlockPos left = new BlockPos(0, 64, 0);
        BlockPos right = new BlockPos(9, 64, 0);
        UUID leftHouse = UUID.randomUUID();
        UUID rightHouse = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(leftHouse, left, 9, 8, 9));
        StructureRegionManager.registerStructure(house(rightHouse, right, 9, 8, 9));

        StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, left.offset(8, 1, 4));
        assertNotNull(found);
        assertEquals(leftHouse, found.getHouseUuid(),
                "a block on the left house's own boundary was claimed by its neighbour");

        found = HouseUtil.enclosingStructure(Level.OVERWORLD, right.offset(0, 1, 4));
        assertNotNull(found);
        assertEquals(rightHouse, found.getHouseUuid());
    }

    @Test
    void aBlockInsideNoHouseResolvesNothingRatherThanTheNearestHouse() {
        BlockPos origin = new BlockPos(0, 64, 0);
        StructureRegionManager.registerStructure(house(UUID.randomUUID(), origin, 9, 8, 9));

        // One block outside the wall. The old scan would have found this house from here.
        assertNull(HouseUtil.enclosingStructure(Level.OVERWORLD, origin.offset(-1, 1, 4)));
        assertNull(HouseUtil.enclosingStructure(Level.OVERWORLD, origin.offset(4, 1, -1)));
    }

    @Test
    void theBasementBelowAHouseStillBelongsToIt() {
        BlockPos origin = new BlockPos(0, 64, 0);
        UUID house = UUID.randomUUID();
        StructureRegionManager.registerStructure(house(house, origin, 9, 8, 9));

        StructureRecord found = HouseUtil.enclosingStructure(Level.OVERWORLD, origin.offset(4, -5, 4));
        assertNotNull(found, "the region reaches ten blocks down; a basement block must resolve");
        assertEquals(house, found.getHouseUuid());

        assertNull(HouseUtil.enclosingStructure(Level.OVERWORLD, origin.offset(4, -11, 4)),
                "eleven blocks down is past the bottom of the region");
    }

    /* ------------------------------------------------------------------ */

    /** The boxes StructureUtils.makeStructureBoxes produces for a house of this size. */
    private static StructureRecord house(UUID houseUuid, BlockPos origin, int w, int h, int d) {
        AABB structureBox = new AABB(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + w, origin.getY() + h, origin.getZ() + d);
        AABB fullBox = new AABB(
                structureBox.minX, structureBox.minY - 10, structureBox.minZ,
                structureBox.maxX, structureBox.maxY, structureBox.maxZ);
        return new StructureRecord(OWNER, structureBox, fullBox, houseUuid, "test", "TEST", null, 0);
    }
}

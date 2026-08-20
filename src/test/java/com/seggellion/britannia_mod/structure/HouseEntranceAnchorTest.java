package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.util.StructureUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The entrance a house declares, and the placement arithmetic that reads it.
 *
 * <p>Placement used to compute the entrance from the size alone -- front face, horizontal
 * centre. Every structure that shipped honours that because it was authored against it, and
 * none of the three larger houses does: the villa's entrance is a double door part-way down
 * one side, the patio's leaves are a block off centre, the keep's are two blocks back from the
 * front. The offset is now per style.
 *
 * <p>Two things need pinning. The seven existing houses must land exactly where they always
 * did, and a house that declares an entrance somewhere else must land on its own door rather
 * than on the middle of its front wall.
 */
class HouseEntranceAnchorTest {

    @Test
    void everyStyleDeclaresAnEntrance() {
        for (HouseStyle style : HouseStyle.values()) {
            assertNotNull(style.getDoorOffset(), style + " has no entrance");
        }
    }

    @Test
    void theSevenExistingHousesKeepTheEntranceTheyAlwaysHad() {
        // Not a rule the table has to obey -- a regression guard. Before this change the value
        // was computed here, from the size; if any of these moved, an existing house would land
        // somewhere new for players who already own one.
        for (HouseStyle style : HouseStyle.values()) {
            assertEquals(StructureUtils.getDefaultDoorOffset(style.getWidth()), style.getDoorOffset(),
                    style + " no longer places its entrance at front centre");
        }
        assertEquals(new BlockPos(4, 0, 0), HouseStyle.SMALL_BRICK.getDoorOffset());
        assertEquals(new BlockPos(17, 0, 0), HouseStyle.CASTLE.getDoorOffset());
    }

    /**
     * The arithmetic, exercised with an entrance that is nowhere near front centre.
     *
     * <p>(3, 0, 5) is the two-story villa's: its door pair is at template (2,1,6) and (3,1,6),
     * and the convention an offset expresses is (doorX, 0, doorZ - 1) -- which is what
     * (4, 0, 0) means for a small house whose door is at (4, 1, 1). If the placer had kept
     * deriving the anchor from the size it would have used (6, 0, 0), and the villa would land
     * three blocks sideways and five blocks forward of where the player aimed.
     */
    @Test
    void aHouseWithAnOffCentreEntranceLandsOnItsOwnDoor() {
        BlockPos villaEntrance = new BlockPos(3, 0, 5);
        BlockPos villaDoorInTemplate = new BlockPos(3, 1, 6);
        BlockPos doorTarget = new BlockPos(100, 64, -200);

        for (Rotation rotation : Rotation.values()) {
            BlockPos origin = StructureUtils.getAdjustedPosForDoor(
                    doorTarget.above(), rotation, villaEntrance);
            BlockPos placedDoor = origin.offset(
                    StructureUtils.getRotatedDoorOffset(rotation, villaDoorInTemplate));

            // Same relationship a small house's door has to the aim point today: one block on
            // from where the player looked, at head height.
            assertEquals(doorTarget.above(2).relative(rotationForward(rotation), 1), placedDoor,
                    "villa entrance landed wrong under " + rotation);
        }
    }

    @Test
    void theSmallHouseAnchorProducesTheSameRelationship() {
        BlockPos doorTarget = new BlockPos(-40, 70, 12);
        BlockPos smallDoorInTemplate = new BlockPos(4, 1, 1);

        for (Rotation rotation : Rotation.values()) {
            BlockPos origin = StructureUtils.getAdjustedPosForDoor(
                    doorTarget.above(), rotation, HouseStyle.SMALL_BRICK.getDoorOffset());
            BlockPos placedDoor = origin.offset(
                    StructureUtils.getRotatedDoorOffset(rotation, smallDoorInTemplate));

            assertEquals(doorTarget.above(2).relative(rotationForward(rotation), 1), placedDoor,
                    "small house door landed wrong under " + rotation);
        }
    }

    /** Which way a template's +z runs once the structure has been turned. */
    private static net.minecraft.core.Direction rotationForward(Rotation rotation) {
        return rotation.rotate(net.minecraft.core.Direction.SOUTH);
    }
}

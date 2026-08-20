package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.util.StructureUtils;
import net.minecraft.core.BlockPos;

/**
 * One row per house: which structure file it is, how big it is, where its lot block sits, and
 * where its entrance is.
 *
 * <h2>The entrance</h2>
 * Placement aims the house so that its entrance lands where the player was looking. Which
 * block counts as the entrance used to be computed from the size alone -- front face,
 * horizontal centre -- and every structure that shipped happened to honour that, because they
 * were authored against it.
 *
 * <p>The larger houses do not. The two-story villa's entrance is a double door part-way down
 * its west side; the large patio's leaves sit one block off centre; the keep's are two blocks
 * back from the front face. Forcing three finished buildings to move their front door so a
 * formula stays true is the wrong way round: the structure decides where its entrance belongs
 * architecturally, and this table describes it.
 *
 * <p>So the offset is a field. Styles that do put their entrance at front-centre say nothing
 * and get {@link StructureUtils#getDefaultDoorOffset(int)}; styles that do not pass their own.
 * A new house stays one row either way, and no house needs a special case in the placer.
 */
public enum HouseStyle {

    SMALL_WOOD("wooden_house",   HouseSize.SMALL, -2),
    SMALL_STONE("field_stone_house",   HouseSize.SMALL, -2),
    SMALL_WOODPLASTER("wood_and_plaster_house",  HouseSize.SMALL, -2),
    SMALL_COTTAGE("thatched_roof_cottage",    HouseSize.SMALL, -2),
    SMALL_BRICK("small_brick_house",       HouseSize.SMALL, -2),
    SMALL_STONEPLASTER("stone_and_plaster_house",       HouseSize.SMALL, -2),
    CASTLE("castle", HouseSize.CASTLE, -3);


    private final String structureStub;
    private final HouseSize size;
        private final int xOffset;
    private final BlockPos doorOffset;


    /** A house whose entrance is at the front face, horizontally centred. */
    HouseStyle(String structureStub, HouseSize size, int xOffset) {
        this(structureStub, size, xOffset, StructureUtils.getDefaultDoorOffset(size.getWidth()));
    }

    /**
     * A house that puts its entrance somewhere else.
     *
     * @param doorOffset the entrance in un-rotated template space, on the same footing as the
     *                   value the default derives: the block placement aligns to the player's
     *                   aim, which the placer lifts one block and rotates.
     */
    HouseStyle(String structureStub, HouseSize size, int xOffset, BlockPos doorOffset) {
        this.structureStub = structureStub;
        this.size = size;
        this.xOffset = xOffset;
        this.doorOffset = doorOffset;
    }

    public String getStructureFile() {
        return structureStub + ".nbt";
    }

    public HouseSize getSize() {
        return size;
    }

       public int getLotOffsetX() {
        return xOffset;
    }

    /** Where this structure's entrance is, in un-rotated template space. */
    public BlockPos getDoorOffset() {
        return doorOffset;
    }

    public int getWidth()  { return size.getWidth(); }
    public int getHeight() { return size.getHeight(); }
    public int getDepth()  { return size.getDepth(); }
}

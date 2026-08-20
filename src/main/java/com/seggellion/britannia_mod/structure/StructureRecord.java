package com.seggellion.britannia_mod.structure;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

/**
 * Data holder for each structure (house) record.
 */
public class StructureRecord {

    /**
     * Housing Deed Milestone 2: a rotation this record does not know about.
     *
     * <p>Every field below except rotation can be recovered from somewhere else -- the lot
     * block entity, the Rails house row, the style table. Rotation could not be recovered from
     * anywhere, because before Milestone 2 it was written down nowhere: the placement payload
     * carried it as far as {@code StructurePlacer} and then it was gone. The bounding boxes
     * here are its only surviving trace, and they cannot be rebuilt without it.
     *
     * <p>Records built by the pre-Milestone-2 constructors carry this value. They are
     * test fixtures and already hold their boxes, so rotation changes nothing for them; it is
     * only ever wrong to persist such a record as though the rotation were real.
     */
    public static final int ROTATION_UNKNOWN = -1;

    private final UUID   ownerUuid;
    private final UUID   houseUuid;
    private final AABB   structureBox;
    private final AABB   fullBox;
    private final String sizeId;   // e.g. "small"
    private final String styleId;  // e.g. "SMALL_BRICK"
    private final UUID deedId;
    private final int rotationDeg; // 0, 90, 180, 270 -- or ROTATION_UNKNOWN

    /**
     * Which world this house stands in.
     *
     * <p>Regions used to be indexed by chunk alone, which is only an identity if there is one
     * world. Two houses at the same X/Z in different dimensions shared a chunk key and
     * therefore shared each other: a door in the Nether could resolve an Overworld house, and
     * unregistering one could empty the other's entry.
     */
    private final ResourceKey<Level> dimension;

    /** Full constructor */
    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid, String sizeId, String styleId, UUID deedId,
                           int rotationDeg, ResourceKey<Level> dimension) {
        this.ownerUuid    = ownerUuid;
        this.structureBox = structureBox;
        this.fullBox      = fullBox;
        this.houseUuid    = houseUuid;
        this.sizeId       = sizeId;
        this.styleId      = styleId;
        this.deedId       = deedId;
        this.rotationDeg  = rotationDeg;
        this.dimension    = dimension == null ? Level.OVERWORLD : dimension;
    }

    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid, String sizeId, String styleId, UUID deedId,
                           int rotationDeg) {
        this(ownerUuid, structureBox, fullBox, houseUuid, sizeId, styleId, deedId, rotationDeg,
                Level.OVERWORLD);
    }

    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid, String sizeId, String styleId, UUID deedId) {
        this(ownerUuid, structureBox, fullBox, houseUuid, sizeId, styleId, deedId, ROTATION_UNKNOWN);
    }

  public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid, String sizeId, String styleId) {
        this(ownerUuid, structureBox, fullBox, houseUuid, sizeId, styleId, null);
    }

    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid, String sizeId) {
        this(ownerUuid, structureBox, fullBox, houseUuid, sizeId, "unknown", null);
    }

    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid) {
        this(ownerUuid, structureBox, fullBox, houseUuid, "unknown", "unknown", null);
    }

    /* getters … */
    public UUID   getOwnerUuid()   { return ownerUuid;  }
    public UUID   getHouseUuid()   { return houseUuid;  }
    public AABB   getStructureBox(){ return structureBox; }
    public AABB   getFullBox()     { return fullBox;    }
    public String getSizeId()      { return sizeId;     }
    public String getStyleId()     { return styleId;    }
    public UUID getDeedId()        { return deedId; }
    public int getRotationDeg()    { return rotationDeg; }
    public ResourceKey<Level> getDimension() { return dimension; }
}

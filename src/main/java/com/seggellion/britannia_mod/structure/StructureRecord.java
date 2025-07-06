package com.seggellion.britannia_mod.structure;

import net.minecraft.world.phys.AABB;
import java.util.UUID;

/**
 * Data holder for each structure (house) record.
 */
public class StructureRecord {

    private final UUID   ownerUuid;
    private final UUID   houseUuid;
    private final AABB   structureBox;
    private final AABB   fullBox;
    private final String sizeId;   // e.g. "small"
    private final String styleId;  // e.g. "SMALL_BRICK"
    private final UUID deedId;

    /** Full constructor */
    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox,
                           UUID houseUuid, String sizeId, String styleId, UUID deedId) {
        this.ownerUuid    = ownerUuid;
        this.structureBox = structureBox;
        this.fullBox      = fullBox;
        this.houseUuid    = houseUuid;
        this.sizeId       = sizeId;
        this.styleId      = styleId;
        this.deedId = deedId;
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
}

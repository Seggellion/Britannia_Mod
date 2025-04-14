package com.seggellion.britannia_mod.structure;

import net.minecraft.world.phys.AABB;
import java.util.UUID;

/**
 * Data holder for each structure (house) record.
 */
public class StructureRecord {
    private final UUID ownerUuid;
    private final UUID houseUuid; // <- ADD THIS
    private final AABB structureBox; 
    private final AABB fullBox;  

    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox, UUID houseUuid) {
        this.ownerUuid = ownerUuid;
        this.structureBox = structureBox;
        this.fullBox = fullBox;
        this.houseUuid = houseUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

       public UUID getHouseUuid() {
        return houseUuid;
    }

    public AABB getStructureBox() {
        return structureBox;
    }

    public AABB getFullBox() {
        return fullBox;
    }
}

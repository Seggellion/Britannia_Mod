package com.seggellion.britannia_mod.structure;

import net.minecraft.world.phys.AABB;
import java.util.UUID;

/**
 * Data holder for each structure (house) record.
 */
public class StructureRecord {
    private final UUID ownerUuid;
    private final UUID houseUuid;
    private final AABB structureBox; 
    private final AABB fullBox;
    private final String sizeId; 

    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox, UUID houseUuid,  String sizeId) {
        this.ownerUuid = ownerUuid;
        this.structureBox = structureBox;
        this.fullBox = fullBox;
        this.houseUuid = houseUuid;
        this.sizeId = sizeId;
    }

       // Optional: Keep the old constructor if needed
    public StructureRecord(UUID ownerUuid, AABB structureBox, AABB fullBox, UUID houseUuid) {
        this(ownerUuid, structureBox, fullBox, houseUuid, "unknown");
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
    public String getSizeId() { return sizeId; } 
}

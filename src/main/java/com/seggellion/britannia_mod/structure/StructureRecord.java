package com.seggellion.britannia_mod.structure;

import net.minecraft.world.phys.AABB;
import java.util.UUID;

/**
 * Data holder for each structure (house) record.
 */
public class StructureRecord {
    private final UUID ownerUuid;
    private final AABB boundingBox; // This bounding box should already be expanded (Y: -20 to +30)

    public StructureRecord(UUID ownerUuid, AABB boundingBox) {
        this.ownerUuid = ownerUuid;
        this.boundingBox = boundingBox;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public AABB getBoundingBox() {
        return boundingBox;
    }
}

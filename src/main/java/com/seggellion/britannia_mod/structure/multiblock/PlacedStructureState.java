package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;

/** In-memory authoritative placement state. Disk persistence belongs to Milestone 3. */
public record PlacedStructureState(
        FamilyId familyId,
        VariantId variantId,
        Direction facing,
        List<LocalOffset> footprint) {
    public PlacedStructureState {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(facing, "facing");
        footprint = List.copyOf(Objects.requireNonNull(footprint, "footprint"));
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Large structures require a horizontal facing");
        }
        if (footprint.isEmpty() || !footprint.getFirst().equals(LocalOffset.ANCHOR)) {
            throw new IllegalArgumentException("Placed footprint must begin with the anchor offset");
        }
    }
}

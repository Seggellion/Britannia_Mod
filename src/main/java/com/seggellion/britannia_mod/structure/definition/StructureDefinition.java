package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.CollisionProfile;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.Dimensions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.GeometryMode;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.PlacementMode;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.RenderOrigin;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ContentStatus;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.DisplayName;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable raw family and variant definitions; validation is handled separately. */
public final class StructureDefinition {
    private StructureDefinition() {
    }

    public record Family(
            FamilyId id,
            DisplayName displayName,
            Dimensions dimensions,
            List<LocalOffset> footprint,
            LocalOffset anchorOffset,
            PlacementMode placementMode,
            CollisionProfile collisionProfile,
            RenderOrigin renderOrigin,
            VoxelOffset renderOffsetVoxels,
            GeometryMode geometryMode,
            Optional<ClientResource> sharedGeometry,
            Optional<VariantId> defaultVariant,
            ContentStatus contentStatus,
            List<Variant> variants) {
        public Family {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(dimensions, "dimensions");
            footprint = List.copyOf(Objects.requireNonNull(footprint, "footprint"));
            Objects.requireNonNull(anchorOffset, "anchorOffset");
            Objects.requireNonNull(placementMode, "placementMode");
            Objects.requireNonNull(collisionProfile, "collisionProfile");
            Objects.requireNonNull(renderOrigin, "renderOrigin");
            Objects.requireNonNull(renderOffsetVoxels, "renderOffsetVoxels");
            Objects.requireNonNull(geometryMode, "geometryMode");
            sharedGeometry = Objects.requireNonNull(sharedGeometry, "sharedGeometry");
            defaultVariant = Objects.requireNonNull(defaultVariant, "defaultVariant");
            variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        }
    }

    public record Variant(
            VariantId id,
            FamilyId familyId,
            DisplayName displayName,
            Dimensions dimensions,
            List<LocalOffset> footprint,
            PlacementMode placementMode,
            CollisionProfile collisionProfile,
            RenderOrigin renderOrigin,
            VoxelOffset renderOffsetVoxels,
            ClientResource model,
            ClientResource texture,
            int cyclePosition,
            boolean enabled,
            boolean playerFacing,
            ContentStatus contentStatus) {
        public Variant {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(familyId, "familyId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(dimensions, "dimensions");
            footprint = List.copyOf(Objects.requireNonNull(footprint, "footprint"));
            Objects.requireNonNull(placementMode, "placementMode");
            Objects.requireNonNull(collisionProfile, "collisionProfile");
            Objects.requireNonNull(renderOrigin, "renderOrigin");
            Objects.requireNonNull(renderOffsetVoxels, "renderOffsetVoxels");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(texture, "texture");
        }
    }
}

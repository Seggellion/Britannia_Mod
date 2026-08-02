package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Production identity catalogue. It intentionally claims no concrete client resource paths. */
public final class ShrineMonolithDefinitions {
    public static final FamilyId SHRINE = new FamilyId("shrine");
    public static final FamilyId MONOLITH = new FamilyId("monolith");
    public static final Dimensions SHRINE_DIMENSIONS = new Dimensions(2, 1, 2);
    public static final Dimensions MONOLITH_DIMENSIONS = new Dimensions(3, 3, 2);
    public static final VoxelOffset SHRINE_RENDER_OFFSET = new VoxelOffset(0, 0, 0);
    public static final VoxelOffset MONOLITH_RENDER_OFFSET = new VoxelOffset(0, 16, 0);

    private static final ClientResource SHRINE_GEOMETRY = ClientResource.unavailable(
            "shrine_shared_geometry",
            "No approved shrine model path is present in the repository");

    private static final StructureCatalogue CATALOGUE = createCatalogue();

    private ShrineMonolithDefinitions() {
    }

    public static StructureCatalogue catalogue() {
        return CATALOGUE;
    }

    private static StructureCatalogue createCatalogue() {
        return StructureCatalogue.build(List.of(shrineFamily(), monolithFamily()))
                .catalogue()
                .orElseThrow(() -> new IllegalStateException("Built-in shrine/monolith catalogue is invalid"));
    }

    private static Family shrineFamily() {
        List<StructureGeometry.LocalOffset> footprint = StructureGeometry.rectangularFootprint(SHRINE_DIMENSIONS);
        List<Variant> variants = new ArrayList<>();
        String[] names = {
            "Honesty", "Compassion", "Valor", "Justice", "Sacrifice",
            "Honor", "Spirituality", "Humility", "Chaos"
        };
        for (int cyclePosition = 0; cyclePosition < names.length; cyclePosition++) {
            String id = names[cyclePosition].toLowerCase(Locale.ROOT);
            variants.add(new Variant(
                    new VariantId(id),
                    SHRINE,
                    DisplayName.unresolved(id, names[cyclePosition]),
                    SHRINE_DIMENSIONS,
                    footprint,
                    PlacementMode.FLOOR_ORIENTED,
                    CollisionProfile.CELL_BOUNDED,
                    RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                    SHRINE_RENDER_OFFSET,
                    SHRINE_GEOMETRY,
                    ClientResource.unavailable(
                            "shrine_texture_" + id,
                            "Approved logical shrine identity has no verified texture path"),
                    cyclePosition,
                    true,
                    true,
                    ContentStatus.APPROVED));
        }
        return new Family(
                SHRINE,
                DisplayName.unresolved("shrine", "Shrines"),
                SHRINE_DIMENSIONS,
                footprint,
                LocalOffset.ANCHOR,
                PlacementMode.FLOOR_ORIENTED,
                CollisionProfile.CELL_BOUNDED,
                RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                SHRINE_RENDER_OFFSET,
                GeometryMode.SHARED,
                Optional.of(SHRINE_GEOMETRY),
                Optional.of(new VariantId("honesty")),
                ContentStatus.APPROVED,
                variants);
    }

    private static Family monolithFamily() {
        List<StructureGeometry.LocalOffset> footprint = StructureGeometry.rectangularFootprint(MONOLITH_DIMENSIONS);
        Variant diagnostic = new Variant(
                new VariantId("diagnostic_missing_content"),
                MONOLITH,
                DisplayName.unresolved("diagnostic_missing_content", "Monolith content unavailable"),
                MONOLITH_DIMENSIONS,
                footprint,
                PlacementMode.FLOOR_ORIENTED,
                CollisionProfile.CELL_BOUNDED,
                RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                MONOLITH_RENDER_OFFSET,
                ClientResource.unavailable(
                        "monolith_model_unavailable",
                        "No supplied monolith model exists; diagnostic identity is non-player-facing"),
                ClientResource.unavailable(
                        "monolith_texture_unavailable",
                        "No supplied monolith texture exists; diagnostic identity is non-player-facing"),
                0,
                true,
                false,
                ContentStatus.PROVISIONAL);
        return new Family(
                MONOLITH,
                DisplayName.unresolved("monolith", "Monoliths"),
                MONOLITH_DIMENSIONS,
                footprint,
                LocalOffset.ANCHOR,
                PlacementMode.FLOOR_ORIENTED,
                CollisionProfile.CELL_BOUNDED,
                RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                MONOLITH_RENDER_OFFSET,
                GeometryMode.PER_VARIANT,
                Optional.empty(),
                Optional.of(diagnostic.id()),
                ContentStatus.APPROVED,
                List.of(diagnostic));
    }
}

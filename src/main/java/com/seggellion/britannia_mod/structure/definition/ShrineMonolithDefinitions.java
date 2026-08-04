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

/** Production identity catalogue. Shrine client resources are temporary development placeholders. */
public final class ShrineMonolithDefinitions {
    public static final FamilyId SHRINE = new FamilyId("shrine");
    public static final FamilyId MONOLITH = new FamilyId("monolith");
    public static final Dimensions SHRINE_DIMENSIONS = new Dimensions(2, 1, 2);
    public static final Dimensions MONOLITH_DIMENSIONS = new Dimensions(3, 3, 2);
    public static final VoxelOffset SHRINE_RENDER_OFFSET = new VoxelOffset(0, 0, 0);
    public static final VoxelOffset MONOLITH_RENDER_OFFSET = new VoxelOffset(0, 16, 0);

    private static final ClientResource SHRINE_GEOMETRY = ClientResource.available(
            "shrine_shared_geometry", "britannia_mod", "geo/shrine.geo.json");

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
                    DisplayName.resolved(
                            id, "structure.britannia_mod.shrine." + id, names[cyclePosition]),
                    SHRINE_DIMENSIONS,
                    footprint,
                    PlacementMode.FLOOR_ORIENTED,
                    CollisionProfile.CELL_BOUNDED,
                    RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                    SHRINE_RENDER_OFFSET,
                    SHRINE_GEOMETRY,
                    ClientResource.available(
                            "shrine_texture_" + id,
                            "britannia_mod", "textures/block/shrine/" + id + ".png"),
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
                DisplayName.resolved(
                        "diagnostic_missing_content",
                        "structure.britannia_mod.monolith.diagnostic_missing_content",
                        "Diagnostic Monolith (Provisional)"),
                MONOLITH_DIMENSIONS,
                footprint,
                PlacementMode.FLOOR_ORIENTED,
                CollisionProfile.CELL_BOUNDED,
                RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                MONOLITH_RENDER_OFFSET,
                ClientResource.available(
                        "monolith_diagnostic_geometry",
                        "britannia_mod", "geo/monolith_diagnostic.geo.json"),
                ClientResource.available(
                        "monolith_diagnostic_texture",
                        "britannia_mod", "textures/block/monolith/diagnostic_stone.png"),
                0,
                true,
                true,
                ContentStatus.PROVISIONAL);
        Variant alternate = new Variant(
                new VariantId("diagnostic_alternate"),
                MONOLITH,
                DisplayName.resolved(
                        "diagnostic_alternate",
                        "structure.britannia_mod.monolith.diagnostic_alternate",
                        "Diagnostic Monolith Alternate"),
                MONOLITH_DIMENSIONS,
                footprint,
                PlacementMode.FLOOR_ORIENTED,
                CollisionProfile.CELL_BOUNDED,
                RenderOrigin.ANCHOR_LOWER_FRONT_LEFT,
                MONOLITH_RENDER_OFFSET,
                ClientResource.available(
                        "monolith_diagnostic_alternate_geometry",
                        "britannia_mod", "geo/monolith_diagnostic_alternate.geo.json"),
                ClientResource.available(
                        "monolith_diagnostic_alternate_texture",
                        "britannia_mod", "textures/block/monolith/diagnostic_alternate_stone.png"),
                1,
                true,
                true,
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
                List.of(diagnostic, alternate));
    }
}

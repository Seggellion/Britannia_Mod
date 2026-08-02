package com.seggellion.britannia_mod.structure.definition;

import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.Dimensions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ContentStatus;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.DisplayName;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class DefinitionFixtures {
    private DefinitionFixtures() {
    }

    static Family shrine() {
        return ShrineMonolithDefinitions.catalogue().family(ShrineMonolithDefinitions.SHRINE).orElseThrow();
    }

    static Family monolith() {
        return ShrineMonolithDefinitions.catalogue().family(ShrineMonolithDefinitions.MONOLITH).orElseThrow();
    }

    static Family withVariants(Family base, List<Variant> variants) {
        return family(base, base.displayName(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.defaultVariant(), base.contentStatus(), variants);
    }

    static Family withFootprint(Family base, List<LocalOffset> footprint) {
        return family(base, base.displayName(), base.dimensions(), footprint, base.renderOffsetVoxels(),
                base.defaultVariant(), base.contentStatus(), base.variants());
    }

    static Family withAnchor(Family base, LocalOffset anchor) {
        return new Family(
                base.id(), base.displayName(), base.dimensions(), base.footprint(), anchor, base.placementMode(),
                base.collisionProfile(), base.renderOrigin(), base.renderOffsetVoxels(), base.geometryMode(),
                base.sharedGeometry(), base.defaultVariant(), base.contentStatus(), base.variants());
    }

    static Family withDimensions(Family base, Dimensions dimensions) {
        return family(base, base.displayName(), dimensions, base.footprint(), base.renderOffsetVoxels(),
                base.defaultVariant(), base.contentStatus(), base.variants());
    }

    static Family withDefault(Family base, Optional<VariantId> defaultVariant) {
        return family(base, base.displayName(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                defaultVariant, base.contentStatus(), base.variants());
    }

    static Family withStatus(Family base, ContentStatus status) {
        return family(base, base.displayName(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.defaultVariant(), status, base.variants());
    }

    static Family withRenderOffset(Family base, VoxelOffset renderOffset) {
        return family(base, base.displayName(), base.dimensions(), base.footprint(), renderOffset,
                base.defaultVariant(), base.contentStatus(), base.variants());
    }

    static Family withDisplay(Family base, DisplayName displayName) {
        return family(base, displayName, base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.defaultVariant(), base.contentStatus(), base.variants());
    }

    static Variant withId(Variant base, String id) {
        return variant(base, new VariantId(id), base.familyId(), base.dimensions(), base.footprint(),
                base.renderOffsetVoxels(), base.model(), base.texture(), base.cyclePosition(), base.enabled(),
                base.contentStatus());
    }

    static Variant withFamily(Variant base, FamilyId familyId) {
        return variant(base, base.id(), familyId, base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.model(), base.texture(), base.cyclePosition(), base.enabled(), base.contentStatus());
    }

    static Variant withDimensions(Variant base, Dimensions dimensions) {
        return variant(base, base.id(), base.familyId(), dimensions, base.footprint(), base.renderOffsetVoxels(),
                base.model(), base.texture(), base.cyclePosition(), base.enabled(), base.contentStatus());
    }

    static Variant withFootprint(Variant base, List<LocalOffset> footprint) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), footprint, base.renderOffsetVoxels(),
                base.model(), base.texture(), base.cyclePosition(), base.enabled(), base.contentStatus());
    }

    static Variant withModel(Variant base, ClientResource model) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                model, base.texture(), base.cyclePosition(), base.enabled(), base.contentStatus());
    }

    static Variant withTexture(Variant base, ClientResource texture) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.model(), texture, base.cyclePosition(), base.enabled(), base.contentStatus());
    }

    static Variant withCyclePosition(Variant base, int cyclePosition) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.model(), base.texture(), cyclePosition, base.enabled(), base.contentStatus());
    }

    static Variant withEnabled(Variant base, boolean enabled) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.model(), base.texture(), base.cyclePosition(), enabled, base.contentStatus());
    }

    static Variant withStatus(Variant base, ContentStatus status) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), base.footprint(), base.renderOffsetVoxels(),
                base.model(), base.texture(), base.cyclePosition(), base.enabled(), status);
    }

    static Variant withRenderOffset(Variant base, VoxelOffset renderOffset) {
        return variant(base, base.id(), base.familyId(), base.dimensions(), base.footprint(), renderOffset,
                base.model(), base.texture(), base.cyclePosition(), base.enabled(), base.contentStatus());
    }

    static Family twoModelMonolith() {
        Family family = monolith();
        Variant first = family.variants().getFirst();
        Variant second = withCyclePosition(
                withModel(
                        withId(first, "test_second_model"),
                        ClientResource.unavailable("test_second_monolith_model", "Test-only absent model")),
                1);
        List<Variant> variants = new ArrayList<>(family.variants());
        variants.add(second);
        return withVariants(family, variants);
    }

    private static Family family(
            Family base,
            DisplayName displayName,
            Dimensions dimensions,
            List<LocalOffset> footprint,
            VoxelOffset renderOffset,
            Optional<VariantId> defaultVariant,
            ContentStatus status,
            List<Variant> variants) {
        return new Family(
                base.id(), displayName, dimensions, footprint, base.anchorOffset(), base.placementMode(),
                base.collisionProfile(), base.renderOrigin(), renderOffset, base.geometryMode(),
                base.sharedGeometry(), defaultVariant, status, variants);
    }

    private static Variant variant(
            Variant base,
            VariantId id,
            FamilyId familyId,
            Dimensions dimensions,
            List<LocalOffset> footprint,
            VoxelOffset renderOffset,
            ClientResource model,
            ClientResource texture,
            int cyclePosition,
            boolean enabled,
            ContentStatus status) {
        return new Variant(
                id, familyId, base.displayName(), dimensions, footprint, base.placementMode(),
                base.collisionProfile(), base.renderOrigin(), renderOffset, model, texture, cyclePosition,
                enabled, base.playerFacing(), status);
    }
}

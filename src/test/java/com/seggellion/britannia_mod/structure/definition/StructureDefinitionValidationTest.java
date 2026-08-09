package com.seggellion.britannia_mod.structure.definition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.DefinitionDiagnostic.Code;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.Dimensions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceAvailability;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StructureDefinitionValidationTest {
    @Test
    void duplicateFamilyIdsFailWithTypedDiagnostic() {
        var result = StructureCatalogue.build(List.of(DefinitionFixtures.shrine(), DefinitionFixtures.shrine()));
        assertTrue(result.catalogue().isEmpty());
        assertTrue(result.validation().has(Code.DUPLICATE_FAMILY_ID));
    }

    @Test
    void duplicateVariantIdsFailWithTypedDiagnostic() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.add(DefinitionFixtures.withCyclePosition(variants.getFirst(), 99));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.DUPLICATE_VARIANT_ID);
    }

    @Test
    void duplicateCyclePositionsFailWithTypedDiagnostic() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(1, DefinitionFixtures.withCyclePosition(variants.get(1), 0));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.DUPLICATE_CYCLE_POSITION);
    }

    @Test
    void missingDefaultVariantFailsWithTypedDiagnostic() {
        assertDiagnostic(DefinitionFixtures.withDefault(DefinitionFixtures.shrine(), Optional.empty()),
                Code.MISSING_DEFAULT_VARIANT);
    }

    @Test
    void disabledDefaultVariantFailsWithTypedDiagnostic() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(0, DefinitionFixtures.withEnabled(variants.getFirst(), false));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.DISABLED_DEFAULT_VARIANT);
    }

    @Test
    void zeroAndNegativeDimensionsFailWithTypedDiagnostic() {
        assertDiagnostic(DefinitionFixtures.withDimensions(
                DefinitionFixtures.shrine(), new Dimensions(0, -1, 2)), Code.INVALID_DIMENSIONS);
    }

    @Test
    void duplicateFootprintOffsetsAndAnchorFailWithTypedDiagnostics() {
        var shrine = DefinitionFixtures.shrine();
        List<LocalOffset> footprint = new ArrayList<>(shrine.footprint());
        footprint.add(LocalOffset.ANCHOR);
        var invalid = DefinitionFixtures.withFootprint(shrine, footprint);
        assertDiagnostic(invalid, Code.DUPLICATE_OCCUPIED_OFFSET);
        assertDiagnostic(invalid, Code.DUPLICATE_ANCHOR_OFFSET);
    }

    @Test
    void missingAnchorFailsWithTypedDiagnostic() {
        var shrine = DefinitionFixtures.shrine();
        assertDiagnostic(DefinitionFixtures.withFootprint(shrine, shrine.footprint().subList(1, 4)),
                Code.MISSING_ANCHOR_OFFSET);
    }

    @Test
    void nonOriginAnchorFailsWithTypedDiagnostic() {
        assertDiagnostic(DefinitionFixtures.withAnchor(
                DefinitionFixtures.shrine(), new LocalOffset(1, 0, 0)), Code.INVALID_ANCHOR_OFFSET);
    }

    @Test
    void occupiedOffsetOutsideDimensionsFailsWithTypedDiagnostic() {
        var shrine = DefinitionFixtures.shrine();
        List<LocalOffset> footprint = new ArrayList<>(shrine.footprint());
        footprint.set(3, new LocalOffset(2, 0, 1));
        assertDiagnostic(DefinitionFixtures.withFootprint(shrine, footprint),
                Code.OCCUPIED_OFFSET_OUTSIDE_DIMENSIONS);
    }

    @Test
    void occupiedOffsetBeyondPartEncodingFailsWithTypedDiagnostic() {
        var monolith = DefinitionFixtures.monolith();
        List<LocalOffset> footprint = new ArrayList<>(monolith.footprint());
        footprint.set(17, new LocalOffset(3, 2, 1));
        assertDiagnostic(DefinitionFixtures.withFootprint(monolith, footprint),
                Code.OCCUPIED_OFFSET_EXCEEDS_PART_ENCODING);
    }

    @Test
    void dimensionsBeyondPartEncodingFailWithTypedDiagnostic() {
        assertDiagnostic(DefinitionFixtures.withDimensions(
                DefinitionFixtures.monolith(), new Dimensions(4, 3, 2)), Code.DIMENSIONS_EXCEED_PART_ENCODING);
    }

    @Test
    void invalidResourceNamespaceFailsOnlyWhenResourceLocationIsPresent() {
        var shrine = DefinitionFixtures.shrine();
        Variant first = shrine.variants().getFirst();
        ClientResource invalid = new ClientResource(
                "stable_texture_identity",
                Optional.of(new ResourceId("Bad Namespace", "textures/shrine.png")),
                ResourceAvailability.UNAVAILABLE,
                Optional.of("Known location is unavailable"));
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(0, DefinitionFixtures.withTexture(first, invalid));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.INVALID_RESOURCE_NAMESPACE);
        assertFalse(StructureDefinitionValidator.validate(shrine).has(Code.INVALID_RESOURCE_NAMESPACE));
    }

    @Test
    void missingFamilyClassificationFailsWithTypedDiagnostic() {
        assertDiagnostic(DefinitionFixtures.withStatus(DefinitionFixtures.shrine(), null),
                Code.MISSING_CONTENT_STATUS);
    }

    @Test
    void missingVariantClassificationFailsWithTypedDiagnostic() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(0, DefinitionFixtures.withStatus(variants.getFirst(), null));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.MISSING_CONTENT_STATUS);
    }

    @Test
    void shrineVariantAttemptingDifferentGeometryIsRejected() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(0, DefinitionFixtures.withModel(
                variants.getFirst(), ClientResource.unavailable("different_geometry", "Test-only model")));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.SHRINE_GEOMETRY_MISMATCH);
    }

    @Test
    void variantFootprintIncompatibleWithFamilyIsRejected() {
        var monolith = DefinitionFixtures.monolith();
        List<Variant> variants = new ArrayList<>(monolith.variants());
        variants.set(0, DefinitionFixtures.withFootprint(variants.getFirst(), variants.getFirst().footprint().subList(0, 17)));
        var invalid = DefinitionFixtures.withVariants(monolith, variants);
        assertDiagnostic(invalid, Code.VARIANT_FOOTPRINT_MISMATCH);
        assertDiagnostic(invalid, Code.CYCLE_CANDIDATE_INCOMPATIBLE);
    }

    @Test
    void monolithVariantWithWrongRenderOffsetIsRejected() {
        var monolith = DefinitionFixtures.monolith();
        List<Variant> variants = new ArrayList<>(monolith.variants());
        variants.set(0, DefinitionFixtures.withRenderOffset(variants.getFirst(), new VoxelOffset(0, 0, 0)));
        assertDiagnostic(DefinitionFixtures.withVariants(monolith, variants), Code.VARIANT_RENDER_OFFSET_MISMATCH);
    }

    @Test
    void crossFamilyVariantMembershipIsRejected() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(0, DefinitionFixtures.withFamily(variants.getFirst(), new FamilyId("monolith")));
        assertDiagnostic(DefinitionFixtures.withVariants(shrine, variants), Code.CROSS_FAMILY_VARIANT);
    }

    @Test
    void incompatibleEnabledVariantIsExcludedFromCycleCandidates() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        Variant incompatible = DefinitionFixtures.withDimensions(variants.get(1), new Dimensions(1, 1, 1));
        variants.set(1, incompatible);
        var invalid = DefinitionFixtures.withVariants(shrine, variants);
        assertDiagnostic(invalid, Code.CYCLE_CANDIDATE_INCOMPATIBLE);
        assertFalse(StructureVariantCycler.enabledVariants(invalid).contains(incompatible));
    }

    private static void assertDiagnostic(StructureDefinition.Family family, Code code) {
        DefinitionValidation validation = StructureDefinitionValidator.validate(family);
        assertFalse(validation.valid());
        assertTrue(validation.has(code), () -> "Expected " + code + " but got " + validation.diagnostics());
    }
}

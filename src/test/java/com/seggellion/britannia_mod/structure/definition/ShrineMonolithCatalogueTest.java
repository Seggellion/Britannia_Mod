package com.seggellion.britannia_mod.structure.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ContentStatus;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceAvailability;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ShrineMonolithCatalogueTest {
    @Test
    void productionCatalogueContainsOnlyApprovedFamilyIdentities() {
        assertEquals(List.of("shrine", "monolith"), ShrineMonolithDefinitions.catalogue().families().stream()
                .map(family -> family.id().value()).toList());
    }

    @Test
    void shrineCataloguePreservesNineApprovedLogicalIdentities() {
        assertEquals(List.of(
                "honesty", "compassion", "valor", "justice", "sacrifice",
                "honor", "spirituality", "humility", "chaos"),
                DefinitionFixtures.shrine().variants().stream().map(variant -> variant.id().value()).toList());
    }

    @Test
    void shrineVariantsShareExactlyOneGeometryIdentity() {
        var shrine = DefinitionFixtures.shrine();
        Set<ClientResource> models = shrine.variants().stream().map(Variant::model).collect(Collectors.toSet());
        assertEquals(Set.of(shrine.sharedGeometry().orElseThrow()), models);
    }

    @Test
    void shrineVariantsVaryOnlyDisplayAndLogicalTextureIdentity() {
        var shrine = DefinitionFixtures.shrine();
        Variant first = shrine.variants().getFirst();
        assertEquals(9, shrine.variants().stream().map(variant -> variant.texture().logicalIdentity()).distinct().count());
        assertEquals(9, shrine.variants().stream().map(variant -> variant.displayName().logicalIdentity()).distinct().count());
        for (Variant variant : shrine.variants()) {
            assertEquals(first.dimensions(), variant.dimensions());
            assertEquals(first.footprint(), variant.footprint());
            assertEquals(first.model(), variant.model());
            assertEquals(first.placementMode(), variant.placementMode());
            assertEquals(first.collisionProfile(), variant.collisionProfile());
            assertEquals(first.renderOrigin(), variant.renderOrigin());
            assertEquals(first.renderOffsetVoxels(), variant.renderOffsetVoxels());
        }
    }

    @Test
    void productionMonolithPlaceholderIsDiagnosticProvisionalAndNonPlayerFacing() {
        Variant diagnostic = DefinitionFixtures.monolith().variants().getFirst();
        assertEquals("diagnostic_missing_content", diagnostic.id().value());
        assertEquals(ContentStatus.PROVISIONAL, diagnostic.contentStatus());
        assertFalse(diagnostic.playerFacing());
        assertEquals(ResourceAvailability.UNAVAILABLE, diagnostic.model().availability());
        assertEquals(ResourceAvailability.UNAVAILABLE, diagnostic.texture().availability());
    }

    @Test
    void testOnlyMonolithVariantsMayUseDifferentModelsWithSamePlacementContract() {
        var monolith = DefinitionFixtures.twoModelMonolith();
        assertTrue(StructureDefinitionValidator.validate(monolith).valid());
        assertNotEquals(monolith.variants().get(0).model(), monolith.variants().get(1).model());
        assertEquals(monolith.variants().get(0).footprint(), monolith.variants().get(1).footprint());
    }

    @Test
    void enabledVariantOrderIsExplicitAndDeterministic() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> reversed = new ArrayList<>(shrine.variants());
        java.util.Collections.reverse(reversed);
        var reorderedFamily = DefinitionFixtures.withVariants(shrine, reversed);
        assertEquals(shrine.variants().stream().map(Variant::id).toList(),
                StructureVariantCycler.enabledVariants(reorderedFamily).stream().map(Variant::id).toList());
    }

    @Test
    void cycleWrapsFromFinalEnabledVariantToFirst() {
        var shrine = DefinitionFixtures.shrine();
        assertEquals(new VariantId("honesty"),
                StructureVariantCycler.next(shrine, new VariantId("chaos")).orElseThrow().id());
    }

    @Test
    void disabledVariantsAreSkippedByDocumentedCycleContract() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(1, DefinitionFixtures.withEnabled(variants.get(1), false));
        var fixture = DefinitionFixtures.withVariants(shrine, variants);
        assertEquals(new VariantId("valor"),
                StructureVariantCycler.next(fixture, new VariantId("honesty")).orElseThrow().id());
        assertFalse(StructureVariantCycler.enabledVariants(fixture).stream()
                .anyMatch(variant -> variant.id().equals(new VariantId("compassion"))));
    }

    @Test
    void knownDisabledCurrentVariantAdvancesWithoutSelectingIt() {
        var shrine = DefinitionFixtures.shrine();
        List<Variant> variants = new ArrayList<>(shrine.variants());
        variants.set(1, DefinitionFixtures.withEnabled(variants.get(1), false));
        var fixture = DefinitionFixtures.withVariants(shrine, variants);
        assertEquals(new VariantId("valor"),
                StructureVariantCycler.next(fixture, new VariantId("compassion")).orElseThrow().id());
    }

    @Test
    void missingCurrentVariantDoesNotSilentlySubstituteAnotherIdentity() {
        assertTrue(StructureVariantCycler.next(
                DefinitionFixtures.shrine(), new VariantId("missing_saved_identity")).isEmpty());
    }

    @Test
    void missingResourcesPreserveStableFamilyAndVariantIdentity() {
        var catalogue = ShrineMonolithDefinitions.catalogue();
        var honesty = catalogue.variant(ShrineMonolithDefinitions.SHRINE, new VariantId("honesty")).orElseThrow();
        assertEquals("shrine", honesty.familyId().value());
        assertEquals("honesty", honesty.id().value());
        assertEquals("shrine_texture_honesty", honesty.texture().logicalIdentity());
        assertTrue(honesty.texture().location().isEmpty());
    }

    @Test
    void familyScopedLookupRejectsCrossFamilyMembership() {
        assertTrue(ShrineMonolithDefinitions.catalogue()
                .variant(ShrineMonolithDefinitions.MONOLITH, new VariantId("honesty"))
                .isEmpty());
    }

    @Test
    void productionCatalogueIntroducesNoConcreteModelOrTexturePaths() {
        for (var family : ShrineMonolithDefinitions.catalogue().families()) {
            family.sharedGeometry().ifPresent(resource -> assertTrue(resource.location().isEmpty()));
            for (Variant variant : family.variants()) {
                assertTrue(variant.model().location().isEmpty());
                assertTrue(variant.texture().location().isEmpty());
            }
        }
    }
}

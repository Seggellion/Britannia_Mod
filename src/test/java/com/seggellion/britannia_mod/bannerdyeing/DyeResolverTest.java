package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import com.seggellion.britannia_mod.dye.service.DyeResolutionFailure;
import com.seggellion.britannia_mod.dye.service.DyeResolutionOutcome;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.service.ExplainedDyeResolution;
import com.seggellion.britannia_mod.dye.service.FinalTieBreak;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.service.PaletteRejectionReason;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DyeResolverTest {
    private final DyeResolver resolver = new DyeResolver();

    @Test
    void explicitMappingWinsEvenWhenAnotherEntryIsMathematicallyCloser() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#FF0000", List.of("colour_family_red"));
        MaterialPaletteEntry closer = entry("resolver_red", "#FF0000", 0, List.of("colour_family_red"));
        MaterialPaletteEntry explicit = entry("resolver_blue", "#0000FF", 0, List.of("colour_family_blue"));
        RegistrySnapshot snapshot = snapshot(pigment, List.of(closer, explicit),
                Map.of(DyeResolverFixtures.PIGMENT_ID, explicit.id()));

        ExplainedDyeResolution explained = resolver.explain(
                DyeResolverFixtures.PIGMENT_ID, DyeResolverFixtures.MATERIAL_ID, snapshot);
        assertEquals(explicit.id(), explained.outcome().result().orElseThrow().resolvedColourId());
        assertEquals(MatchType.EXPLICIT_MAPPING, explained.outcome().result().orElseThrow().matchType());
        assertTrue(explained.explanation().explicitMappingUsed());
        assertEquals(FinalTieBreak.EXPLICIT_MAPPING, explained.explanation().finalTieBreak());
        assertTrue(explained.outcome().result().orElseThrow().perceptualDistance() > 0.0);
    }

    @Test
    void samePigmentProducesFourDistinctMaterialAwareProductionResults() throws Exception {
        RegistrySnapshot snapshot = DyeResolverFixtures.productionSnapshot();
        PigmentId pigment = PigmentId.parse("britannia_mod:madder_red");
        Map<String, String> expected = Map.of(
                "cotton", "britannia_mod:cotton_red",
                "wool", "britannia_mod:wool_oxblood",
                "linen", "britannia_mod:linen_madder",
                "silk", "britannia_mod:silk_ruby");
        List<String> selected = new ArrayList<>();
        expected.forEach((material, colour) -> {
            var result = resolver.resolve(pigment, FabricMaterialId.parse("britannia_mod:" + material), snapshot)
                    .result().orElseThrow();
            assertEquals(colour, result.resolvedColourId().toString());
            assertEquals(MatchType.EXPLICIT_MAPPING, result.matchType());
            selected.add(result.resolvedColourId().toString());
        });
        assertEquals(4L, selected.stream().distinct().count());
    }

    @Test
    void noOverrideUsesNearestComputedColourAndNaturalCanWinWhenClosest() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of("neutral"));
        MaterialPaletteEntry natural = entry("resolver_natural", "#808080", 0, List.of("natural"));
        MaterialPaletteEntry distant = entry("resolver_distant", "#FF0000", 100, List.of("colour_family_red"));
        RegistrySnapshot snapshot = snapshot(pigment, List.of(natural, distant), Map.of());

        var result = resolver.resolve(DyeResolverFixtures.PIGMENT_ID, DyeResolverFixtures.MATERIAL_ID, snapshot)
                .result().orElseThrow();
        assertEquals(natural.id(), result.resolvedColourId());
        assertEquals(MatchType.NEAREST_COLOUR, result.matchType());
        assertEquals(0.0, result.perceptualDistance());
    }

    @Test
    void naturalLookupUsesDedicatedNaturalMatchType() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of());
        MaterialPaletteEntry natural = entry("resolver_natural", "#D6CDB7", 0, List.of("natural"));
        RegistrySnapshot snapshot = snapshot(pigment, List.of(natural), Map.of());

        var result = resolver.resolveNatural(DyeResolverFixtures.MATERIAL_ID, snapshot).result().orElseThrow();
        assertEquals(natural.id(), result.resolvedColourId());
        assertEquals(MatchType.NATURAL, result.matchType());
        assertEquals(0.0, result.perceptualDistance());
    }

    @Test
    void lowestDistanceIsTheFirstTieStage() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of("colour_family_red"));
        MaterialPaletteEntry closest = entry("resolver_closest", "#808080", 0, List.of());
        MaterialPaletteEntry distantFamily = entry("resolver_family", "#909090", 999,
                List.of("colour_family_red"));
        ExplainedDyeResolution result = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, List.of(closest, distantFamily), Map.of()));
        assertEquals(closest.id(), selected(result));
        assertEquals(FinalTieBreak.LOWEST_DISTANCE, result.explanation().finalTieBreak());
    }

    @Test
    void sharedColourFamilyTagWinsARealDistanceTieButGenericTagsDoNotCount() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080",
                List.of("colour_family_red", "development", "common"));
        MaterialPaletteEntry generic = entry("resolver_generic", "#808080", 999,
                List.of("development", "common"));
        MaterialPaletteEntry family = entry("resolver_family", "#808080", 0,
                List.of("colour_family_red"));
        ExplainedDyeResolution result = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, List.of(generic, family), Map.of()));
        assertEquals(family.id(), selected(result));
        assertEquals(FinalTieBreak.SHARED_COLOUR_FAMILY_TAG, result.explanation().finalTieBreak());
    }

    @Test
    void higherPriorityWinsAfterDistanceAndFamilyTie() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of());
        MaterialPaletteEntry low = entry("resolver_low", "#808080", 1, List.of());
        MaterialPaletteEntry high = entry("resolver_high", "#808080", 9, List.of());
        ExplainedDyeResolution result = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, List.of(low, high), Map.of()));
        assertEquals(high.id(), selected(result));
        assertEquals(FinalTieBreak.HIGHER_PRIORITY, result.explanation().finalTieBreak());
    }

    @Test
    void lexicalStableIdWinsFinalTieIndependentlyOfEntryOrder() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of());
        MaterialPaletteEntry first = entry("resolver_a", "#808080", 1, List.of());
        MaterialPaletteEntry last = entry("resolver_z", "#808080", 1, List.of());
        RegistrySnapshot forward = snapshot(pigment, List.of(last, first), Map.of());
        RegistrySnapshot reverse = snapshot(pigment, List.of(first, last), Map.of());

        ExplainedDyeResolution firstResult = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, forward);
        ExplainedDyeResolution secondResult = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, reverse);
        assertEquals(first.id(), selected(firstResult));
        assertEquals(firstResult, secondResult);
        assertEquals(FinalTieBreak.LEXICOGRAPHIC_ID, firstResult.explanation().finalTieBreak());
    }

    @Test
    void randomizedPaletteOrderDoesNotChangeSelectionOrCandidateExplanationOrder() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of());
        List<MaterialPaletteEntry> base = List.of(
                entry("resolver_a", "#707070", 0, List.of()),
                entry("resolver_b", "#808080", 0, List.of()),
                entry("resolver_c", "#909090", 0, List.of()));
        ExplainedDyeResolution expected = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, base, Map.of()));
        for (int seed = 0; seed < 12; seed++) {
            List<MaterialPaletteEntry> shuffled = new ArrayList<>(base);
            Collections.shuffle(shuffled, new Random(seed));
            assertEquals(expected, resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                    DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, shuffled, Map.of())));
        }
    }

    @Test
    void compatibilityAllowsEmptyRestrictionsAndRequiresOneAllowedTag() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of("plant"));
        MaterialPaletteEntry unrestricted = entry("resolver_unrestricted", "#808080", 0, List.of());
        MaterialPaletteEntry allowed = DyeResolverFixtures.entry("resolver_allowed", "#808080", 10,
                List.of(), List.of("plant", "mineral"), List.of());
        MaterialPaletteEntry denied = DyeResolverFixtures.entry("resolver_denied", "#808080", 100,
                List.of(), List.of("ice"), List.of());
        ExplainedDyeResolution result = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID,
                snapshot(pigment, List.of(unrestricted, allowed, denied), Map.of()));
        assertEquals(allowed.id(), selected(result));
        assertEquals(List.of(denied.id()), result.explanation().rejectedEntries().stream()
                .map(rejection -> rejection.colourId()).toList());
        assertEquals(PaletteRejectionReason.NO_ALLOWED_PIGMENT_TAG,
                result.explanation().rejectedEntries().getFirst().reason());
    }

    @Test
    void excludedTagWinsOverAnAllowedTag() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of("plant", "mundane"));
        MaterialPaletteEntry accepted = entry("resolver_accepted", "#909090", 0, List.of());
        MaterialPaletteEntry rejected = DyeResolverFixtures.entry("resolver_rejected", "#808080", 100,
                List.of(), List.of("plant"), List.of("mundane"));
        ExplainedDyeResolution result = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, List.of(rejected, accepted), Map.of()));
        assertEquals(accepted.id(), selected(result));
        assertEquals(PaletteRejectionReason.EXCLUDED_PIGMENT_TAG,
                result.explanation().rejectedEntries().getFirst().reason());
        assertEquals(List.of("mundane"), result.explanation().rejectedEntries().getFirst().relevantTags());
    }

    @Test
    void noCompatibleCandidateReturnsSafeFailureWithOrderedRejections() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of("mundane"));
        MaterialPaletteEntry z = DyeResolverFixtures.entry("resolver_z", "#808080", 0, List.of(),
                List.of("ice"), List.of());
        MaterialPaletteEntry a = DyeResolverFixtures.entry("resolver_a", "#808080", 0, List.of(),
                List.of("magical"), List.of());
        ExplainedDyeResolution result = resolver.explain(DyeResolverFixtures.PIGMENT_ID,
                DyeResolverFixtures.MATERIAL_ID, snapshot(pigment, List.of(z, a), Map.of()));
        assertFalse(result.outcome().successful());
        assertEquals(DyeResolutionFailure.NO_COMPATIBLE_COLOUR, result.outcome().failure().orElseThrow());
        assertEquals(List.of(a.id(), z.id()), result.explanation().rejectedEntries().stream()
                .map(rejection -> rejection.colourId()).toList());
    }

    @Test
    void lightweightAndExplanatoryPathsAgreeAndExplanationCollectionsAreImmutable() {
        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of());
        MaterialPaletteEntry natural = entry("resolver_natural", "#808080", 0, List.of());
        RegistrySnapshot snapshot = snapshot(pigment, List.of(natural), Map.of());
        DyeResolutionOutcome lightweight = resolver.resolve(
                DyeResolverFixtures.PIGMENT_ID, DyeResolverFixtures.MATERIAL_ID, snapshot);
        ExplainedDyeResolution explained = resolver.explain(
                DyeResolverFixtures.PIGMENT_ID, DyeResolverFixtures.MATERIAL_ID, snapshot);
        assertEquals(lightweight, explained.outcome());
        assertThrows(UnsupportedOperationException.class,
                () -> explained.explanation().compatibleCandidates().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> explained.explanation().rejectedEntries().clear());
    }

    @Test
    void expectedMissingAndOwnershipErrorsAreValuesNotUncheckedExceptions() {
        assertEquals(DyeResolutionFailure.MISSING_PIGMENT,
                resolver.resolve(DyeResolverFixtures.PIGMENT_ID, DyeResolverFixtures.MATERIAL_ID,
                        RegistrySnapshot.empty()).failure().orElseThrow());

        PigmentDefinition pigment = DyeResolverFixtures.pigment("#808080", List.of());
        MaterialPaletteEntry natural = entry("resolver_natural", "#808080", 0, List.of());
        RegistrySnapshot missingMaterial = RegistrySnapshotTestFactory.colourSnapshot(
                Map.of(), Map.of(pigment.id(), pigment), Map.of());
        assertEquals(DyeResolutionFailure.MISSING_MATERIAL,
                resolver.resolve(pigment.id(), DyeResolverFixtures.MATERIAL_ID, missingMaterial)
                        .failure().orElseThrow());

        FabricMaterialDefinition material = DyeResolverFixtures.material(natural.id());
        RegistrySnapshot missingPalette = RegistrySnapshotTestFactory.colourSnapshot(
                Map.of(material.id(), material), Map.of(pigment.id(), pigment), Map.of());
        assertEquals(DyeResolutionFailure.MISSING_PALETTE,
                resolver.resolve(pigment.id(), material.id(), missingPalette).failure().orElseThrow());

        FabricMaterialId other = FabricMaterialId.parse("britannia_mod:other_material");
        MaterialPalette wrongOwner = new MaterialPalette(CoreDataFixtures.SCHEMA, DyeResolverFixtures.PALETTE_ID,
                other, natural.id(), List.of(natural), Map.of());
        RegistrySnapshot mismatch = RegistrySnapshotTestFactory.colourSnapshot(
                Map.of(material.id(), material), Map.of(pigment.id(), pigment),
                Map.of(wrongOwner.id(), wrongOwner));
        assertEquals(DyeResolutionFailure.PALETTE_OWNER_MISMATCH,
                resolver.resolve(pigment.id(), material.id(), mismatch).failure().orElseThrow());

        ResolvedColourId absentNatural = ResolvedColourId.parse("britannia_mod:absent_natural");
        FabricMaterialDefinition wrongNatural = DyeResolverFixtures.material(absentNatural);
        MaterialPalette validPalette = new MaterialPalette(CoreDataFixtures.SCHEMA,
                DyeResolverFixtures.PALETTE_ID, DyeResolverFixtures.MATERIAL_ID, natural.id(),
                List.of(natural), Map.of());
        RegistrySnapshot naturalMismatch = RegistrySnapshotTestFactory.colourSnapshot(
                Map.of(wrongNatural.id(), wrongNatural), Map.of(pigment.id(), pigment),
                Map.of(validPalette.id(), validPalette));
        assertEquals(DyeResolutionFailure.MISSING_NATURAL_COLOUR,
                resolver.resolveNatural(wrongNatural.id(), naturalMismatch).failure().orElseThrow());
    }

    @Test
    void ordinaryBlueCannotUseRestrictedGlacialSilkButIceBlueCan() throws Exception {
        RegistrySnapshot snapshot = DyeResolverFixtures.productionSnapshot();
        FabricMaterialId silk = FabricMaterialId.parse("britannia_mod:silk");
        ExplainedDyeResolution ordinary = resolver.explain(PigmentId.parse("britannia_mod:woad_blue"), silk,
                snapshot);
        ExplainedDyeResolution ice = resolver.explain(PigmentId.parse("britannia_mod:ice_blue"), silk, snapshot);
        assertNotEquals("britannia_mod:silk_glacial", selected(ordinary).toString());
        assertTrue(ordinary.explanation().rejectedEntries().stream()
                .anyMatch(entry -> entry.colourId().toString().equals("britannia_mod:silk_glacial")));
        assertEquals("britannia_mod:silk_glacial", selected(ice).toString());
        assertEquals(MatchType.NEAREST_COLOUR, ice.outcome().result().orElseThrow().matchType());
    }

    @Test
    void resolverAndExplanationTypesRemainCommonSide() throws Exception {
        for (Class<?> type : List.of(DyeResolver.class, DyeResolutionOutcome.class,
                com.seggellion.britannia_mod.dye.service.DyeResolutionExplanation.class,
                com.seggellion.britannia_mod.dye.service.CandidateEvaluation.class)) {
            try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                String pool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(pool.contains("net/minecraft/client/"), type.getName());
                assertFalse(pool.contains("com/mojang/blaze3d/"), type.getName());
            }
        }
    }

    private static ResolvedColourId selected(ExplainedDyeResolution result) {
        return result.outcome().result().orElseThrow().resolvedColourId();
    }

    private static RegistrySnapshot snapshot(
            PigmentDefinition pigment, List<MaterialPaletteEntry> entries,
            Map<PigmentId, ResolvedColourId> overrides) {
        MaterialPalette palette = new MaterialPalette(CoreDataFixtures.SCHEMA, DyeResolverFixtures.PALETTE_ID,
                DyeResolverFixtures.MATERIAL_ID, entries.getFirst().id(), entries, overrides);
        return DyeResolverFixtures.snapshot(pigment, DyeResolverFixtures.material(entries.getFirst().id()), palette);
    }

    private static MaterialPaletteEntry entry(String path, String srgb, int priority, List<String> tags) {
        return DyeResolverFixtures.entry(path, srgb, priority, tags, List.of(), List.of());
    }
}

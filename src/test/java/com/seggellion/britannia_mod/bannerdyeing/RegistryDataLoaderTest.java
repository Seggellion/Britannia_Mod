package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataReloadRegistration;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionRegistry;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionResource;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDataLoader;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryLoadResult;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotPublisher;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.CoreDataFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.RegistryDatasetFixtures;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationIssue;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationReport;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationStage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RegistryDataLoaderTest {
    private final RegistryDataLoader loader = new RegistryDataLoader();

    @Test
    void validRegistrySetLoadsAllSixReadOnlyRegistries() {
        RegistryLoadResult result = load(RegistryDatasetFixtures.valid(), ValidationPolicy.DEVELOPMENT_FAIL_FAST);

        assertTrue(result.published());
        assertFalse(result.report().hasErrors());
        assertEquals(7, result.report().summary().resourcesDiscovered());
        assertEquals(7, result.report().summary().resourcesDecoded());
        assertEquals(1, result.snapshot().banners().activeCount());
        assertEquals(1, result.snapshot().fabricMaterials().activeCount());
        assertEquals(1, result.snapshot().pigments().activeCount());
        assertEquals(1, result.snapshot().materialPalettes().activeCount());
        assertEquals(2, result.snapshot().mounts().activeCount());
        assertEquals(1, result.snapshot().placementProfiles().activeCount());
    }

    @Test
    void lookupPresenceRequireAndDeterministicEnumerationWork() {
        RegistrySnapshot snapshot = load(RegistryDatasetFixtures.valid(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST).snapshot();

        assertTrue(snapshot.banners().contains(CoreDataFixtures.BANNER_ID));
        assertEquals(CoreDataFixtures.bannerDefinition(), snapshot.banners().find(CoreDataFixtures.BANNER_ID).orElseThrow());
        assertEquals(CoreDataFixtures.bannerDefinition(), snapshot.banners().require(CoreDataFixtures.BANNER_ID));
        assertEquals(List.of("britannia_mod:test_brass", "britannia_mod:test_iron"),
                snapshot.mounts().activeEntries().keySet().stream().map(Object::toString).toList());
    }

    @Test
    void registryExposureIsImmutable() {
        RegistrySnapshot snapshot = load(RegistryDatasetFixtures.valid(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST).snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.banners().activeEntries().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.banners().activeDefinitions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.banners().disabledEntries().clear());
    }

    @Test
    void emptyDatasetPublishesAndHasSafeEmptyLookups() {
        RegistryLoadResult result = load(List.of(), ValidationPolicy.DEVELOPMENT_FAIL_FAST);
        BannerDefinitionId missing = BannerDefinitionId.parse("britannia_mod:missing");

        assertTrue(result.published());
        assertFalse(result.snapshot().banners().contains(missing));
        assertTrue(result.snapshot().banners().find(missing).isEmpty());
        NoSuchElementException exception = assertThrows(NoSuchElementException.class,
                () -> result.snapshot().banners().require(missing));
        assertTrue(exception.getMessage().contains("britannia_mod:missing"));
    }

    @Test
    void missingMaterialPaletteIsCrossReferenceError() {
        assertIssue(RegistryDatasetFixtures.missingPalette(), "MISSING_MATERIAL_PALETTE",
                RegistryDatasetFixtures.MATERIAL_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void missingDefaultMountIsReported() {
        assertIssue(RegistryDatasetFixtures.missingDefaultMount(), "MISSING_DEFAULT_MOUNT",
                RegistryDatasetFixtures.BANNER_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void missingSupportedMountIsReportedSeparately() {
        assertIssue(RegistryDatasetFixtures.missingSupportedMount(), "MISSING_SUPPORTED_MOUNT",
                RegistryDatasetFixtures.BANNER_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void missingPlacementProfileIsReported() {
        assertIssue(RegistryDatasetFixtures.missingPlacementProfile(), "MISSING_PLACEMENT_PROFILE",
                RegistryDatasetFixtures.BANNER_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void bannerProfileDimensionMismatchIsReported() {
        assertIssue(RegistryDatasetFixtures.dimensionMismatch(), "PLACEMENT_DIMENSION_MISMATCH",
                RegistryDatasetFixtures.BANNER_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void missingOverridePigmentIsReported() {
        assertIssue(RegistryDatasetFixtures.missingOverridePigment(), "MISSING_OVERRIDE_PIGMENT",
                RegistryDatasetFixtures.PALETTE_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void paletteOwnerMismatchIsReported() {
        RegistryLoadResult result = load(RegistryDatasetFixtures.paletteOwnerMismatch(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST);
        assertTrue(codes(result).contains("MISSING_PALETTE_OWNER"));
        assertTrue(codes(result).contains("PALETTE_MATERIAL_MISMATCH"));
    }

    @Test
    void naturalColourMismatchIsReported() {
        assertIssue(RegistryDatasetFixtures.naturalColourMismatch(), "NATURAL_COLOUR_MISMATCH",
                RegistryDatasetFixtures.MATERIAL_SOURCE, ValidationStage.CROSS_REFERENCE);
    }

    @Test
    void duplicateEmbeddedStableIdRejectsEveryDuplicateSource() {
        RegistryLoadResult result = load(RegistryDatasetFixtures.duplicateBannerId(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST);
        List<ValidationIssue> duplicates = result.report().issues().stream()
                .filter(issue -> issue.code().equals("DUPLICATE_STABLE_ID")).toList();

        assertFalse(result.published());
        assertEquals(2, duplicates.size());
        assertEquals(List.of("britannia_mod:banner_definitions/test_banner.json",
                        "other_pack:banner_definitions/duplicate.json"),
                duplicates.stream().map(issue -> issue.sourceResource().orElseThrow().toString()).toList());
    }

    @Test
    void malformedResourceIsStructuralAndAttributedToExactSource() {
        assertIssue(RegistryDatasetFixtures.malformedBanner(), "MALFORMED_JSON",
                RegistryDatasetFixtures.BANNER_SOURCE, ValidationStage.STRUCTURAL_DECODING);
    }

    @Test
    void unknownSchemaVersionIsStructural() {
        assertIssue(RegistryDatasetFixtures.unknownBannerSchema(), "CODEC_DECODE_FAILED",
                RegistryDatasetFixtures.BANNER_SOURCE, ValidationStage.STRUCTURAL_DECODING);
    }

    @Test
    void missingRequiredFieldIsStructuralAndSourceAttributed() {
        assertIssue(RegistryDatasetFixtures.withMutation(RegistryDomain.BANNER_DEFINITION,
                        RegistryDatasetFixtures.BANNER_SOURCE, json -> json.remove("id")),
                "CODEC_DECODE_FAILED", RegistryDatasetFixtures.BANNER_SOURCE,
                ValidationStage.STRUCTURAL_DECODING);
    }

    @Test
    void invalidDimensionsAreStructuralAndSourceAttributed() {
        assertIssue(RegistryDatasetFixtures.withMutation(RegistryDomain.BANNER_DEFINITION,
                        RegistryDatasetFixtures.BANNER_SOURCE,
                        json -> json.getAsJsonObject("dimensions").addProperty("width_blocks", 0)),
                "CODEC_DECODE_FAILED", RegistryDatasetFixtures.BANNER_SOURCE,
                ValidationStage.STRUCTURAL_DECODING);
    }

    @Test
    void invalidColourIsStructuralAndSourceAttributed() {
        assertIssue(RegistryDatasetFixtures.withMutation(RegistryDomain.PIGMENT,
                        RegistryDatasetFixtures.PIGMENT_SOURCE,
                        json -> json.addProperty("reference_srgb", "#a51c30")),
                "CODEC_DECODE_FAILED", RegistryDatasetFixtures.PIGMENT_SOURCE,
                ValidationStage.STRUCTURAL_DECODING);
    }

    @Test
    void malformedAssetIdentifierIsStructuralAndSourceAttributed() {
        assertIssue(RegistryDatasetFixtures.withMutation(RegistryDomain.BANNER_DEFINITION,
                        RegistryDatasetFixtures.BANNER_SOURCE,
                        json -> json.getAsJsonObject("assets").addProperty("geometry", "Bad Identifier")),
                "CODEC_DECODE_FAILED", RegistryDatasetFixtures.BANNER_SOURCE,
                ValidationStage.STRUCTURAL_DECODING);
    }

    @Test
    void multipleSimultaneousErrorsExposeStructuralAndCrossReferenceStages() {
        RegistryLoadResult result = load(RegistryDatasetFixtures.multipleErrors(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST);

        assertFalse(result.published());
        assertFalse(result.report().structuralIssues().isEmpty());
        assertFalse(result.report().crossReferenceIssues().isEmpty());
        assertTrue(result.report().summary().errors() >= 3);
    }

    @Test
    void developmentFailurePreservesPreviousSnapshot() {
        RegistrySnapshotPublisher publisher = new RegistrySnapshotPublisher();
        RegistryLoadResult valid = apply(RegistryDatasetFixtures.valid(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST, publisher);
        RegistryLoadResult failed = apply(RegistryDatasetFixtures.missingDefaultMount(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST, publisher);

        assertTrue(valid.published());
        assertFalse(failed.published());
        assertSame(valid.snapshot(), failed.snapshot());
        assertSame(valid.snapshot(), publisher.current());
    }

    @Test
    void successfulReloadAtomicallyReplacesOlderSnapshot() {
        RegistrySnapshotPublisher publisher = new RegistrySnapshotPublisher();
        RegistryLoadResult first = apply(RegistryDatasetFixtures.valid(),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST, publisher);
        RegistryLoadResult second = apply(RegistryDatasetFixtures.renamedBannerDisplay("banner.changed"),
                ValidationPolicy.DEVELOPMENT_FAIL_FAST, publisher);

        assertTrue(second.published());
        assertNotSame(first.snapshot(), second.snapshot());
        assertEquals("banner.changed", second.snapshot().banners().require(CoreDataFixtures.BANNER_ID).displayNameKey());
    }

    @Test
    void productionDisablingPropagatesToStableFixedPoint() {
        RegistryLoadResult result = load(RegistryDatasetFixtures.missingOverridePigment(),
                ValidationPolicy.PRODUCTION_DISABLE_INVALID);

        assertTrue(result.published());
        assertEquals(0, result.snapshot().materialPalettes().activeCount());
        assertEquals(0, result.snapshot().fabricMaterials().activeCount());
        assertEquals(0, result.snapshot().banners().activeCount());
        assertEquals(1, result.snapshot().materialPalettes().disabledCount());
        assertEquals(1, result.snapshot().fabricMaterials().disabledCount());
        assertEquals(1, result.snapshot().banners().disabledCount());
        assertTrue(codes(result).containsAll(Set.of(
                "MISSING_OVERRIDE_PIGMENT", "MISSING_MATERIAL_PALETTE", "MISSING_DEFAULT_MATERIAL")));
    }

    @Test
    void productionPublishesOnlyReferenceSafeActiveSubset() {
        RegistrySnapshot snapshot = load(RegistryDatasetFixtures.missingSupportedMount(),
                ValidationPolicy.PRODUCTION_DISABLE_INVALID).snapshot();

        assertEquals(0, snapshot.banners().activeCount());
        assertEquals(1, snapshot.banners().disabledCount());
        snapshot.fabricMaterials().activeDefinitions().forEach(material -> {
            assertTrue(snapshot.materialPalettes().contains(material.paletteId()));
            assertEquals(material.id(), snapshot.materialPalettes().require(material.paletteId()).materialId());
        });
    }

    @Test
    void disabledEntriesCannotBeFoundThroughActiveLookup() {
        RegistrySnapshot snapshot = load(RegistryDatasetFixtures.missingDefaultMount(),
                ValidationPolicy.PRODUCTION_DISABLE_INVALID).snapshot();

        assertTrue(snapshot.banners().find(CoreDataFixtures.BANNER_ID).isEmpty());
        assertFalse(snapshot.banners().contains(CoreDataFixtures.BANNER_ID));
        assertEquals(CoreDataFixtures.BANNER_ID, snapshot.banners().disabledEntries().getFirst().id());
    }

    @Test
    void reportOrderingDoesNotDependOnResourceInputOrder() {
        List<DefinitionResource> forward = RegistryDatasetFixtures.multipleErrors();
        List<DefinitionResource> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        List<String> first = issueKeys(load(forward, ValidationPolicy.DEVELOPMENT_FAIL_FAST));
        List<String> second = issueKeys(load(reverse, ValidationPolicy.DEVELOPMENT_FAIL_FAST));
        assertEquals(first, second);
    }

    @Test
    void embeddedIdIsAuthoritativeRatherThanFilenameDerived() {
        List<DefinitionResource> resources = new ArrayList<>(RegistryDatasetFixtures.valid());
        DefinitionResource banner = resources.removeFirst();
        resources.add(DefinitionResource.text(RegistryDomain.BANNER_DEFINITION,
                "different_namespace:banner_definitions/unrelated_filename.json", banner.contents()));

        RegistryLoadResult result = load(resources, ValidationPolicy.DEVELOPMENT_FAIL_FAST);
        assertTrue(result.published());
        assertTrue(result.snapshot().banners().contains(CoreDataFixtures.BANNER_ID));
    }

    @Test
    void aggregatePublicationNeverExposesMixedSnapshots() throws Exception {
        RegistrySnapshotPublisher publisher = new RegistrySnapshotPublisher();
        apply(RegistryDatasetFixtures.valid(), ValidationPolicy.DEVELOPMENT_FAIL_FAST, publisher);
        AtomicBoolean running = new AtomicBoolean(true);
        Set<String> observations = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> reader = executor.submit(() -> {
                while (running.get()) {
                    RegistrySnapshot snapshot = publisher.current();
                    observations.add(snapshot.banners().require(CoreDataFixtures.BANNER_ID).displayNameKey()
                            + ":" + snapshot.mounts().activeCount());
                }
            });
            apply(RegistryDatasetFixtures.renamedBannerDisplay("banner.changed"),
                    ValidationPolicy.DEVELOPMENT_FAIL_FAST, publisher);
            running.set(false);
            reader.get();
        } finally {
            running.set(false);
            executor.shutdownNow();
        }
        assertTrue(Set.of("banner.britannia_mod.test_banner:2", "banner.changed:2").containsAll(observations));
    }

    @Test
    void registryPipelineCommonClassesDoNotReferenceClientPackages() throws IOException {
        List<Class<?>> classes = List.of(RegistryDataLoader.class, RegistrySnapshot.class,
                RegistrySnapshotPublisher.class, BannerDataRegistries.class, BannerDataReloadRegistration.class,
                DefinitionRegistry.class, ValidationReport.class);
        for (Class<?> commonClass : classes) {
            String resourceName = "/" + commonClass.getName().replace('.', '/') + ".class";
            try (InputStream stream = commonClass.getResourceAsStream(resourceName)) {
                String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(constantPool.contains("net/minecraft/client/"), commonClass.getName());
            }
        }
    }

    private RegistryLoadResult load(List<DefinitionResource> resources, ValidationPolicy policy) {
        return apply(resources, policy, new RegistrySnapshotPublisher());
    }

    private RegistryLoadResult apply(
            List<DefinitionResource> resources,
            ValidationPolicy policy,
            RegistrySnapshotPublisher publisher) {
        return loader.apply(loader.prepare(resources), policy, publisher);
    }

    private void assertIssue(
            List<DefinitionResource> resources, String code, String source, ValidationStage stage) {
        RegistryLoadResult result = load(resources, ValidationPolicy.DEVELOPMENT_FAIL_FAST);
        ValidationIssue issue = result.report().issues().stream()
                .filter(candidate -> candidate.code().equals(code)).findFirst().orElseThrow();
        assertFalse(result.published());
        assertEquals(source, issue.sourceResource().orElseThrow().toString());
        assertEquals(stage, issue.stage());
    }

    private static Set<String> codes(RegistryLoadResult result) {
        return result.report().issues().stream().map(ValidationIssue::code).collect(java.util.stream.Collectors.toSet());
    }

    private static List<String> issueKeys(RegistryLoadResult result) {
        return result.report().issues().stream()
                .map(issue -> issue.severity() + ":" + issue.stage() + ":" + issue.domain() + ":"
                        + issue.definitionId().orElse("") + ":" + issue.sourceResource().map(Object::toString).orElse("")
                        + ":" + issue.code())
                .toList();
    }
}

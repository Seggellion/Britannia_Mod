package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerCatalogueManifestTest {
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void containsExactlyThirtyThreeEntries() {
        assertEquals(33, manifest.banners().size());
    }

    @Test
    void containsExactlyThirtyThreeUniqueStableIds() {
        assertEquals(33, manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet()).size());
    }

    @Test
    void containsExactlyThirtyThreeUniqueIndices() {
        assertEquals(33, manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::index)
                .collect(Collectors.toSet()).size());
    }

    @Test
    void indicesAreContinuousFromOneThroughThirtyThree() {
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 33).boxed().collect(Collectors.toSet()),
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::index).collect(Collectors.toSet()));
    }

    @Test
    void stableIdSetMatchesCanonicalPlaybook() {
        assertEquals(new HashSet<>(ProductionBannerCatalogue.CANONICAL_PATHS),
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id).collect(Collectors.toSet()));
    }

    @Test
    void manifestOrderMatchesCanonicalIndicesAndIds() {
        assertEquals(ProductionBannerCatalogue.CANONICAL_PATHS,
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 33).boxed().toList(),
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::index).toList());
    }

    @Test
    void canonicalGroupCountsMatch() {
        Map<String, Long> counts = manifest.banners().stream().collect(Collectors.groupingBy(
                BannerScaffoldTool.BannerEntry::group, Collectors.counting()));
        assertEquals(Map.of("large", 6L, "medium-wall", 6L, "medium", 8L,
                "small", 6L, "x-small", 7L), counts);
    }

    @Test
    void everySourceReferenceIsPositive() {
        assertTrue(manifest.banners().stream().allMatch(entry -> entry.page() > 0 && entry.row() > 0));
    }

    @Test
    void everyUnnamedEntryRemainsProvisionalAndVisible() {
        List<BannerScaffoldTool.BannerEntry> unnamed = manifest.banners().stream()
                .filter(entry -> entry.sourceLabel() == null).toList();
        assertEquals(14, unnamed.size());
        assertTrue(unnamed.stream().allMatch(entry -> "provisional".equals(entry.nameStatus())));
        assertTrue(unnamed.stream().allMatch(entry -> entry.notes().contains("Name Required")));
    }

    @Test
    void sourceNamedEntriesRetainSourceLabelsWithoutFinalApprovalClaim() {
        List<BannerScaffoldTool.BannerEntry> named = manifest.banners().stream()
                .filter(entry -> "source-named".equals(entry.nameStatus())).toList();
        assertEquals(19, named.size());
        assertTrue(named.stream().allMatch(entry -> entry.sourceLabel() != null));
        assertTrue(named.stream().noneMatch(entry -> entry.notes().toLowerCase().contains("approved")));
    }

    @Test
    void everyInitialDimensionIsProvisionalThroughDefaults() {
        assertEquals(Boolean.TRUE, manifest.defaults().dimensionsProvisional());
        assertTrue(manifest.banners().stream().allMatch(entry -> entry.dimensionsProvisional() == null
                || entry.dimensionsProvisional()));
    }

    @Test
    void noEntryIsComplete() {
        assertEquals("placeholder", manifest.defaults().contentStatus());
        assertTrue(manifest.banners().stream().noneMatch(entry -> "complete".equals(entry.contentStatus())));
    }

    @Test
    void defaultsDoNotMultiplyDesignsByRuntimeCombinations() {
        assertEquals(33, manifest.banners().size());
        assertEquals(2, manifest.defaults().supportedMounts().size());
        assertEquals(2, manifest.defaults().supportedOrientations().size());
        assertEquals("britannia_mod:cotton", manifest.defaults().defaultMaterial());
        assertTrue(manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id).distinct().count() == 33);
    }

    @Test
    void allRequiredInheritedFieldsAreAvailable() {
        assertNotNull(manifest.defaults().fabricBase());
        assertNotNull(manifest.defaults().dyeMask());
        assertNotNull(manifest.defaults().staticOverlay());
        assertEquals(Set.of("large", "medium-wall", "medium", "small", "x-small"),
                manifest.groups().keySet());
        assertFalse(manifest.sharedPlaceholderAssets().isEmpty());
    }
}

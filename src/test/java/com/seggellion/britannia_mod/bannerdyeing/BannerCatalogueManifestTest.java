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
    void containsEveryCanonicalEntry() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, manifest.banners().size());
    }

    @Test
    void containsOnlyUniqueStableIds() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT,
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet()).size());
    }

    @Test
    void containsOnlyUniqueIndices() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT,
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::index)
                .collect(Collectors.toSet()).size());
    }

    @Test
    void indicesAreContinuousFromOneThroughTheDataDerivedTotal() {
        assertEquals(java.util.stream.IntStream.rangeClosed(
                        1, ProductionBannerCatalogue.TARGET_COUNT).boxed().collect(Collectors.toSet()),
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
        assertEquals(java.util.stream.IntStream.rangeClosed(
                        1, ProductionBannerCatalogue.TARGET_COUNT).boxed().toList(),
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::index).toList());
    }

    @Test
    void canonicalGroupCountsMatch() {
        Map<String, Long> counts = manifest.banners().stream().collect(Collectors.groupingBy(
                BannerScaffoldTool.BannerEntry::group, Collectors.counting()));
        assertEquals(Map.of("large", 6L, "medium-wall", 6L, "medium", 8L,
                "small", 6L, "x-small", 9L), counts);
    }

    @Test
    void everySourceReferenceIsPositive() {
        assertTrue(manifest.banners().stream().allMatch(entry -> entry.page() > 0 && entry.row() > 0));
    }

    @Test
    void everyUnnamedEntryRemainsProvisionalAndVisible() {
        List<BannerScaffoldTool.BannerEntry> unnamed = manifest.banners().stream()
                .filter(entry -> entry.sourceLabel() == null).toList();
        assertEquals(13, unnamed.size());
        assertTrue(unnamed.stream().allMatch(entry -> "provisional".equals(entry.nameStatus())));
        assertTrue(unnamed.stream().allMatch(entry -> entry.notes().contains("Name Required")));
    }

    @Test
    void sourceNamedEntriesRetainSourceLabelsAndApprovedNamesAreExplicit() {
        List<BannerScaffoldTool.BannerEntry> named = manifest.banners().stream()
                .filter(entry -> "source-named".equals(entry.nameStatus())).toList();
        assertEquals(22, named.size());
        assertTrue(named.stream().allMatch(entry -> entry.sourceLabel() != null));
        assertEquals(Set.of("road_guard", "pale_road_guard", "red_crosslets",
                        "captains_red_crosslets", "scarlet_court", "verdant_court",
                        "small_curtain", "prosperity_standard", "guardian_standard"),
                named.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.displayNameApproved()))
                .map(BannerScaffoldTool.BannerEntry::id).collect(Collectors.toSet()));
    }

    @Test
    void authoritativeExtraSmallFamilyHasApprovedDimensions() {
        assertEquals(Boolean.TRUE, manifest.defaults().dimensionsProvisional());
        assertEquals(Set.of("road_guard", "pale_road_guard", "red_crosslets",
                        "captains_red_crosslets", "scarlet_court", "verdant_court",
                        "small_curtain", "prosperity_standard", "guardian_standard"),
                manifest.banners().stream()
                .filter(entry -> Boolean.FALSE.equals(entry.dimensionsProvisional()))
                .map(BannerScaffoldTool.BannerEntry::id).collect(Collectors.toSet()));
    }

    @Test
    void extraSmallFamilyAwaitsLiveReview() {
        assertEquals("placeholder", manifest.defaults().contentStatus());
        assertTrue(manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus()))
                .findAny().isEmpty());
        assertEquals(9, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());
    }

    @Test
    void defaultsDoNotMultiplyDesignsByRuntimeCombinations() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, manifest.banners().size());
        assertEquals(2, manifest.defaults().supportedMounts().size());
        assertEquals(2, manifest.defaults().supportedOrientations().size());
        assertEquals("britannia_mod:cotton", manifest.defaults().defaultMaterial());
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT,
                manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id).distinct().count());
    }

    @Test
    void allRequiredInheritedFieldsAreAvailable() {
        assertNotNull(manifest.defaults().baseTexture());
        assertNotNull(manifest.defaults().dyeMask());
        assertEquals(Set.of("base_texture", "dye_mask", "missing"),
                manifest.sharedPlaceholderAssets().keySet());
        assertEquals(Set.of("large", "medium-wall", "medium", "small", "x-small"),
                manifest.groups().keySet());
    }
}

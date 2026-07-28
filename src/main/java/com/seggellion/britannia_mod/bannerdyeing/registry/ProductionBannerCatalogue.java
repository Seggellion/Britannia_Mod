package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Catalogue-specific release boundary; deliberately separate from generic registry loading. */
public final class ProductionBannerCatalogue {
    public static final int TARGET_COUNT = 33;
    public static final List<String> CANONICAL_PATHS = List.of(
            "large_01", "large_02", "large_03", "large_04", "large_05", "large_06",
            "medium_wall_01", "medium_wall_02", "medium_wall_03", "medium_wall_04", "medium_wall_05",
            "joined_wards", "tournament_medium", "ceremonial_tournament", "iron_quarter", "outer_ward",
            "ward_of_serpents", "serpent_guard", "crossroad_guard", "argent_shield",
            "silver_and_gold_pennon", "end_01", "end_02", "pennon_of_silver", "iron_ward",
            "iron_ward_auxiliary", "road_guard", "pale_road_guard", "red_crosslets",
            "captains_red_crosslets", "scarlet_court", "verdant_court", "small_curtain");

    private static final Set<String> CANONICAL_IDS = CANONICAL_PATHS.stream()
            .map(path -> "britannia_mod:" + path)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private ProductionBannerCatalogue() {
    }

    /** Small unit fixtures remain outside this boundary unless they contain a release-catalogue ID. */
    public static boolean isProductionCatalogue(RegistrySnapshot snapshot) {
        return snapshot.banners().activeEntries().keySet().stream()
                .map(Object::toString)
                .anyMatch(CANONICAL_IDS::contains)
                || snapshot.banners().disabledEntries().stream()
                .map(entry -> entry.id().toString())
                .anyMatch(CANONICAL_IDS::contains);
    }

    public static void requireComplete(RegistrySnapshot snapshot) {
        Set<String> active = snapshot.banners().activeEntries().keySet().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> missing = new LinkedHashSet<>(CANONICAL_IDS);
        missing.removeAll(active);
        Set<String> unexpected = new LinkedHashSet<>(active);
        unexpected.removeAll(CANONICAL_IDS);

        if (active.size() != TARGET_COUNT || !missing.isEmpty() || !unexpected.isEmpty()
                || snapshot.banners().disabledCount() != 0) {
            throw new IllegalStateException("Production banner catalogue must expose exactly 33 canonical active "
                    + "definitions and zero disabled definitions; active=" + active.size()
                    + ", disabled=" + snapshot.banners().disabledCount()
                    + ", missing=" + missing + ", unexpected=" + unexpected);
        }
    }

    public static Set<BannerDefinitionId> canonicalIds() {
        return CANONICAL_PATHS.stream()
                .map(path -> BannerDefinitionId.parse("britannia_mod:" + path))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

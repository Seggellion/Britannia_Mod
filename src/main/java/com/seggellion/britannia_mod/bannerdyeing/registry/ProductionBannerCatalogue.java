package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Catalogue-specific release boundary; deliberately separate from generic registry loading. */
public final class ProductionBannerCatalogue {
    public static final List<String> CANONICAL_PATHS = List.of(
            "tournament_curtain", "threefold_chain_standard", "iron_serpent_standard",
            "silver_fleur_curtain", "gilded_trellis_curtain", "gilded_chevron_curtain",
            "verdant_grape_pennon", "silver_rosette_pennon", "four_seals_pennon",
            "twin_spades_pennon", "ankh_pennon",
            "joined_wards", "tournament_medium", "ceremonial_tournament", "iron_quarter", "outer_ward",
            "ward_of_serpents", "serpent_guard", "crossroad_guard", "argent_shield",
            "silver_and_gold_pennon", "star_standard", "ship_standard", "pennon_of_silver", "iron_ward",
            "iron_ward_auxiliary", "road_guard", "pale_road_guard", "red_crosslets",
            "captains_red_crosslets", "scarlet_court", "verdant_court", "small_curtain",
            "prosperity_standard", "guardian_standard");
    public static final int TARGET_COUNT = CANONICAL_PATHS.size();

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
            throw new IllegalStateException("Production banner catalogue must expose exactly " + TARGET_COUNT
                    + " canonical active "
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

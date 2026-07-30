package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MediumGateECloseoutTest {
    private static final List<String> FAMILY = List.of(
            "tournament_medium",
            "ceremonial_tournament",
            "iron_quarter",
            "outer_ward",
            "ward_of_serpents",
            "serpent_guard",
            "crossroad_guard",
            "argent_shield");
    private static final List<String> BATCH_CHECKS = List.of(
            "All eight stable IDs correct",
            "All eight 128 x 128 base textures reviewed",
            "All eight 128 x 128 dye masks reviewed",
            "Natural appearance reviewed for every banner",
            "Dyeable regions recolour correctly",
            "Highlights and shadows remain visible",
            "Alpha edges are clean",
            "No colour bleed or halo",
            "No blurred or unintended texture filtering",
            "No purple or missing-texture fallback",
            "Inventory and hand rendering",
            "Dropped-item rendering",
            "Item-frame rendering",
            "Dye-preview current state",
            "Dye-preview proposed state",
            "Cancel is non-mutating",
            "Apply updates intended regions",
            "Re-dye replaces the prior colour",
            "Unlimited dye tubs remain unlimited",
            "Brass mount behaviour",
            "Iron mount behaviour",
            "Wall-perpendicular placement",
            "North-facing placement",
            "South-facing placement",
            "East-facing placement",
            "West-facing placement",
            "Rotation and mirroring",
            "Mount and model alignment",
            "Save/reload",
            "Relog and client tracking",
            "F3+T resource reload",
            "`/reload` data reload",
            "Break/drop",
            "Support loss",
            "Pick block",
            "Re-placement",
            "Item/preview/placed consistency",
            "All seven pigments represented",
            "All four materials represented",
            "Every unique geometry reviewed",
            "Block-atlas reload",
            "Extra-small and Small family regression",
            "Perpendicular-only orientation enforcement",
            "No parallel Medium placement was exposed");
    private static final List<String> PER_BANNER_CHECKS = List.of(
            "Natural and recoloured appearance",
            "Dyeable regions, highlights, shadows, and alpha edges",
            "Inventory, hand, dropped-item, item-frame, and preview rendering",
            "Brass and iron mounts",
            "Perpendicular placement and all four facings",
            "Save/reload, relog, resource reload, and data reload",
            "Break/drop, support loss, pick block, and re-placement",
            "No parallel placement exposed",
            "No purple fallback");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void mediumFamilyIsCompleteAndParallelMediumWallFamilyRemainsPlaceholder() {
        Set<String> medium = manifest.banners().stream()
                .filter(entry -> "medium".equals(entry.group()))
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(FAMILY), medium);
        assertTrue(manifest.banners().stream()
                .filter(entry -> medium.contains(entry.id()))
                .allMatch(entry -> "complete".equals(entry.contentStatus())));
        assertTrue(manifest.banners().stream()
                .filter(entry -> "medium-wall".equals(entry.group()))
                .allMatch(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())));
        assertEquals(23, manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());
        assertEquals(12, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())).count());
    }

    @Test
    void productOwnerEvidenceMatchesApprovedIntakesAndHasNoUnresolvedResult() throws Exception {
        String batch = Files.readString(Path.of(
                "content/banner-final-intake/MEDIUM_FAMILY_GATE_E_REVIEW.md"));
        assertTrue(batch.contains("Reviewer: Seggellion (Product Owner)"));
        assertTrue(batch.contains(
                "Commit tested: `79474963299603ae73b2efcaf58a9a8614dc881b`"));
        BATCH_CHECKS.forEach(check -> assertTrue(batch.contains("- " + check + ": PASS"), check));
        assertTrue(batch.contains("Batch approval: APPROVED"));
        assertTrue(batch.contains("Gate E: PASS"));
        assertFalse(batch.contains(": FAIL"));
        assertFalse(batch.contains("NOT PERFORMED"));

        for (String id : FAMILY) {
            JsonObject intake = json(Path.of(
                    "content/banner-final-intake/submissions", id, id + ".yml"));
            JsonObject banner = intake.getAsJsonObject("banner");
            JsonObject assets = intake.getAsJsonObject("assets");
            String review = Files.readString(Path.of(
                    "content/banner-final-intake/submissions", id, "GATE_E_REVIEW.md"));
            assertTrue(review.contains("Stable ID: `britannia_mod:" + id + "`"), id);
            assertTrue(review.contains("Display name: "
                    + banner.get("final_display_name").getAsString()), id);
            assertTrue(review.contains("Reviewer: Seggellion (Product Owner)"), id);
            assertTrue(review.contains(
                    "Tested commit: `79474963299603ae73b2efcaf58a9a8614dc881b`"), id);
            assertTrue(review.contains("Base SHA-256: `"
                    + assets.getAsJsonObject("base_texture").get("sha256").getAsString() + "`"), id);
            assertTrue(review.contains("Mask SHA-256: `"
                    + assets.getAsJsonObject("dye_mask").get("sha256").getAsString() + "`"), id);
            assertTrue(review.contains("Geometry: `" + banner.get("geometry_id").getAsString() + "`"), id);
            PER_BANNER_CHECKS.forEach(check ->
                    assertTrue(review.contains("- " + check + ": PASS"), id + ": " + check));
            assertTrue(review.contains("Overall approval: APPROVED"), id);
            assertTrue(review.contains("Gate E result: PASS"), id);
            assertFalse(review.contains(": FAIL"), id);
            assertFalse(review.contains("NOT PERFORMED"), id);
            assertFalse(review.toLowerCase().contains("unresolved"), id);
            assertFalse(review.toLowerCase().contains("pending"), id);
        }
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}

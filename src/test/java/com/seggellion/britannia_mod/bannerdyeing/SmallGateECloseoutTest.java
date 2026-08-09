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

class SmallGateECloseoutTest {
    private static final List<String> FAMILY = List.of(
            "silver_and_gold_pennon",
            "star_standard",
            "ship_standard",
            "pennon_of_silver",
            "iron_ward",
            "iron_ward_auxiliary");
    private static final List<String> BATCH_CHECKS = List.of(
            "All six stable IDs correct",
            "All six 128 x 128 base textures reviewed",
            "All six 128 x 128 dye masks reviewed",
            "Natural appearance reviewed for every banner",
            "Fixed regions remain unchanged",
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
            "Wall-parallel placement",
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
            "/reload data reload",
            "Break/drop",
            "Support loss",
            "Pick block",
            "Re-placement",
            "Item/preview/placed consistency",
            "All seven pigments represented",
            "All four materials represented",
            "Every unique geometry reviewed",
            "Block-atlas reload",
            "Extra-small family regression");
    private static final List<String> PER_BANNER_CHECKS = List.of(
            "128 x 128 fidelity",
            "Natural appearance",
            "Fixed regions",
            "Dyeable regions",
            "Highlights and shadows",
            "Preview",
            "Brass",
            "Iron",
            "Parallel",
            "Perpendicular",
            "Facing and rotation",
            "Save/reload",
            "Break/drop",
            "Pick block and re-placement");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(System.getProperty("britannia.projectDir", "."), BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void smallFamilyIsCompleteAndOnlyLaterFamiliesRemainPlaceholder() {
        Set<String> small = manifest.banners().stream()
                .filter(entry -> "small".equals(entry.group()))
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(FAMILY), small);
        assertTrue(manifest.banners().stream()
                .filter(entry -> small.contains(entry.id()))
                .allMatch(entry -> "complete".equals(entry.contentStatus())));
        assertEquals(35, manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())).count());
    }

    @Test
    void productOwnerEvidenceMatchesApprovedIntakesAndHasNoUnresolvedResult() throws Exception {
        // The banner intake/review documents are excluded from the Patch 18
        // integration line by owner policy (they stay on the banners-dyetub branch).
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.exists(java.nio.file.Path.of(
                        System.getProperty("britannia.projectDir", "."), "content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md")),
                "content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md is not present on this branch (owner exclusion policy)");
        String batch = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md"));
        assertTrue(batch.contains("Reviewer: Seggellion (Product Owner)"));
        assertTrue(batch.contains(
                "Commit tested: `e4f457b132667efc0c9789ee6044c47bedf68bdc`"));
        BATCH_CHECKS.forEach(check -> assertTrue(batch.contains("- " + check + ": PASS"), check));
        assertTrue(batch.contains("Batch approval: APPROVED"));
        assertTrue(batch.contains("Gate E: PASS"));
        assertFalse(batch.contains(": FAIL"));
        assertFalse(batch.contains("NOT PERFORMED"));

        for (String id : FAMILY) {
            JsonObject intake = json(Path.of(System.getProperty("britannia.projectDir", "."),
                    "content/banner-final-intake/submissions", id, id + ".yml"));
            JsonObject banner = intake.getAsJsonObject("banner");
            JsonObject assets = intake.getAsJsonObject("assets");
            String review = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                    "content/banner-final-intake/submissions", id, "GATE_E_REVIEW.md"));
            assertTrue(review.contains("Stable ID: `britannia_mod:" + id + "`"), id);
            assertTrue(review.contains("Display name: "
                    + banner.get("final_display_name").getAsString()), id);
            assertTrue(review.contains("Reviewer: Seggellion (Product Owner)"), id);
            assertTrue(review.contains(
                    "Tested commit: `e4f457b132667efc0c9789ee6044c47bedf68bdc`"), id);
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

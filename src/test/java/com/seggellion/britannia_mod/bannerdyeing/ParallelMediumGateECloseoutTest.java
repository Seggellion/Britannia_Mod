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

class ParallelMediumGateECloseoutTest {
    private static final List<String> FAMILY = List.of(
            "verdant_grape_pennon",
            "silver_rosette_pennon",
            "four_seals_pennon",
            "twin_spades_pennon",
            "ankh_pennon",
            "joined_wards");
    private static final List<String> BATCH_CHECKS = List.of(
            "All six stable IDs correct",
            "All six 128 x 128 base textures reviewed",
            "All six 128 x 128 dye masks reviewed",
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
            "Wall-parallel placement",
            "North-facing placement",
            "South-facing placement",
            "East-facing placement",
            "West-facing placement",
            "Anchor-only rendering and wall offset",
            "No wall overlap or z-fighting",
            "Mount and model alignment",
            "Save/reload",
            "Initial and late client tracking",
            "F3+T resource reload",
            "`/reload` data reload",
            "Break/drop",
            "Support loss",
            "Pick block",
            "Re-placement",
            "Item/preview/placed consistency",
            "Every unique geometry reviewed",
            "Block-atlas reload",
            "Earlier-family regression",
            "Parallel-only orientation enforcement",
            "No perpendicular Parallel Medium placement was exposed");
    private static final List<String> PER_BANNER_CHECKS = List.of(
            "Natural and recoloured appearance",
            "Fixed and dyeable regions, highlights, shadows, and alpha edges",
            "Inventory, hand, dropped-item, item-frame, and preview rendering",
            "Brass and iron mounts",
            "Parallel placement and all four facings",
            "Anchor-only rendering, wall offset, and no z-fighting",
            "Save/reload, tracking, resource reload, and data reload",
            "Break/drop, support loss, pick block, and re-placement",
            "No perpendicular placement exposed",
            "No purple fallback");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void allMediumDefinitionsAreCompleteAndOnlyLargeRemainsPlaceholder() {
        Set<String> parallel = manifest.banners().stream()
                .filter(entry -> "medium-wall".equals(entry.group()))
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(FAMILY), parallel);
        assertTrue(manifest.banners().stream()
                .filter(entry -> parallel.contains(entry.id()))
                .allMatch(entry -> "complete".equals(entry.contentStatus())));
        assertTrue(manifest.banners().stream()
                .filter(entry -> "medium".equals(entry.group()))
                .allMatch(entry -> "complete".equals(entry.contentStatus())));
        assertEquals(29, manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());
        assertEquals(6, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())).count());
    }

    @Test
    void productOwnerEvidenceBindsTheReviewedCommitAssetsAndEveryPassResult() throws Exception {
        String batch = Files.readString(Path.of(
                "content/banner-final-intake/PARALLEL_MEDIUM_GATE_E_REVIEW.md"));
        assertTrue(batch.contains("Reviewer: Seggellion (Product Owner)"));
        assertTrue(batch.contains(
                "Commit tested: `c61d8121d6d1224ea5647bedee8f3d13dd7af933`"));
        BATCH_CHECKS.forEach(check -> assertTrue(batch.contains("- " + check + ": PASS"), check));
        assertTrue(batch.contains("Batch approval: APPROVED"));
        assertTrue(batch.contains("Gate E: PASS"));
        assertFalse(batch.contains(": FAIL"));
        assertFalse(batch.contains("NOT PERFORMED"));

        String runbook = Files.readString(Path.of(
                "docs/banner-dyeing/PARALLEL_MEDIUM_LIVE_REVIEW.md"));
        assertTrue(runbook.contains("GATE E PASS"));
        assertFalse(runbook.contains("NOT PERFORMED"));
        for (String id : FAMILY) {
            assertTrue(runbook.lines()
                    .filter(line -> line.startsWith("| `" + id + "` |"))
                    .allMatch(line -> occurrences(line, "PASS") == 18), id);

            JsonObject intake = json(Path.of(
                    "content/banner-final-intake/submissions", id, id + ".yml"));
            JsonObject banner = intake.getAsJsonObject("banner");
            JsonObject assets = intake.getAsJsonObject("assets");
            String review = Files.readString(Path.of(
                    "content/banner-final-intake/submissions", id, "GATE_E_REVIEW.md"));
            assertTrue(review.contains("Stable ID: `britannia_mod:" + id + "`"), id);
            assertTrue(review.contains("Display name: "
                    + banner.get("final_display_name").getAsString()), id);
            assertTrue(review.contains(
                    "Tested commit: `c61d8121d6d1224ea5647bedee8f3d13dd7af933`"), id);
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
        }
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}

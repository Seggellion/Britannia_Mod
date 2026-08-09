package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParallelLargeGateECloseoutTest {
    private static final String TESTED_COMMIT =
            "598dde33b4f2f322f1ed32c4773b8ab69080eb22";
    private static final List<String> FAMILY = List.of(
            "tournament_curtain",
            "threefold_chain_standard",
            "iron_serpent_standard",
            "silver_fleur_curtain",
            "gilded_trellis_curtain",
            "gilded_chevron_curtain");
    private static final List<String> SUPERSEDED = List.of(
            "large_01", "large_02", "large_03", "large_04", "large_05", "large_06");
    private static final List<String> PRODUCT_OWNER_CHECKS = List.of(
            "Natural artwork",
            "Correct fixed heraldry and ornamentation",
            "Correct dyeable regions",
            "Correct fixed attachment and crossbar pixels",
            "Correct highlight and shadow behavior",
            "Correct transparency and silhouette",
            "Correct 2 x 2 geometry",
            "Correct 2 x 2 footprint",
            "Correct anchor",
            "Parallel wall placement",
            "North facing",
            "South facing",
            "East facing",
            "West facing",
            "Brass mount",
            "Iron mount",
            "Untinted mount rendering",
            "Inventory item",
            "Natural preview",
            "Dyed preview",
            "Placed natural rendering",
            "Placed dyed rendering",
            "Save and reload",
            "Break and drop",
            "Pick block",
            "Re-placement",
            "Resource reload",
            "Data reload",
            "Initial client tracking",
            "Late client tracking",
            "No purple fallback",
            "Overall product-owner approval");
    private static final List<String> PER_DEFINITION_CHECKS = List.of(
            "Natural artwork, fixed heraldry, ornamentation, attachment pixels, highlights, shadows, transparency, and silhouette",
            "Dyeable regions and dyed preview",
            "2 x 2 geometry, footprint, and anchor",
            "Parallel placement across north, south, east, and west facings",
            "Brass and iron mounts remain untinted",
            "Inventory, natural preview, and placed natural rendering",
            "Dyed preview and placed dyed rendering",
            "Save/reload, initial/late tracking, resource reload, and data reload",
            "Break/drop, pick block, and re-placement",
            "No purple fallback");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(System.getProperty("britannia.projectDir", "."), BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void allThirtyFiveDefinitionsAreCompleteAndCanonical() {
        assertEquals(35, manifest.banners().size());
        assertEquals(35, manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "disabled".equals(entry.contentStatus())).count());
        assertEquals(35, manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet()).size());
        assertEquals(35, manifest.banners().stream().map(BannerScaffoldTool.BannerEntry::index)
                .collect(Collectors.toSet()).size());
        assertFalse(manifest.banners().stream().anyMatch(entry -> SUPERSEDED.contains(entry.id())));

        List<BannerScaffoldTool.BannerEntry> large = manifest.banners().stream()
                .filter(entry -> "large".equals(entry.group())).toList();
        assertEquals(FAMILY, large.stream().map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6),
                large.stream().map(BannerScaffoldTool.BannerEntry::index).toList());
        assertTrue(large.stream().allMatch(entry -> entry.widthBlocks() == 2
                && entry.heightBlocks() == 2
                && List.of("wall_parallel").equals(entry.supportedOrientations())
                && List.of("britannia_mod:brass", "britannia_mod:iron")
                        .equals(entry.supportedMounts())
                && "britannia_mod:brass".equals(entry.defaultMount())
                && "britannia_mod:large_parallel".equals(entry.placementProfile())
                && "READY_FOR_INTEGRATION".equals(entry.intakeValidation())));
    }

    @Test
    void productOwnerEvidenceBindsEveryPassToTheExactReviewedCommitAndHashes() throws Exception {
        // The banner intake/review documents are excluded from the Patch 18
        // integration line by owner policy (they stay on the banners-dyetub branch).
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.exists(java.nio.file.Path.of(
                        System.getProperty("britannia.projectDir", "."), "content/banner-final-intake/PARALLEL_LARGE_GATE_E_CLOSEOUT.md")),
                "content/banner-final-intake/PARALLEL_LARGE_GATE_E_CLOSEOUT.md is not present on this branch (owner exclusion policy)");
        String batch = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "content/banner-final-intake/PARALLEL_LARGE_GATE_E_CLOSEOUT.md"));
        assertTrue(batch.contains("Reviewer: Seggellion (Product Owner)"));
        assertTrue(batch.contains("Review date: 2026-07-30"));
        assertTrue(batch.contains("Tested commit: `" + TESTED_COMMIT + "`"));
        PRODUCT_OWNER_CHECKS.forEach(check ->
                assertTrue(batch.contains("- " + check + ": PASS"), check));
        assertTrue(batch.contains("Batch approval: APPROVED"));
        assertTrue(batch.contains("Gate E: PASS"));
        assertTrue(batch.contains("Exceptions observed: none"));
        assertFalse(batch.contains(": FAIL"));
        assertFalse(batch.contains("NOT PERFORMED"));

        String runbook = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "docs/banner-dyeing/PARALLEL_LARGE_LIVE_REVIEW.md"));
        assertTrue(runbook.contains("Status: GATE E PASS"));
        assertTrue(runbook.contains("Tested commit: `" + TESTED_COMMIT + "`"));
        assertTrue(runbook.contains("Overall approval: PASS"));
        assertFalse(runbook.contains("GATE E UNPERFORMED"));
        assertFalse(runbook.contains(": FAIL"));

        for (BannerScaffoldTool.BannerEntry entry : manifest.banners().stream()
                .filter(value -> "large".equals(value.group())).toList()) {
            String id = entry.id();
            assertTrue(runbook.lines()
                    .filter(line -> line.startsWith("| `" + id + "` |"))
                    .allMatch(line -> occurrences(line, "PASS") == 18), id);
            JsonObject intake = json(Path.of(System.getProperty("britannia.projectDir", "."), entry.intakePath()));
            JsonObject banner = intake.getAsJsonObject("banner");
            JsonObject assets = intake.getAsJsonObject("assets");
            String review = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                    "content/banner-final-intake/submissions", id, "GATE_E_REVIEW.md"));
            assertTrue(review.contains("Stable ID: `britannia_mod:" + id + "`"), id);
            assertTrue(review.contains("Tested commit: `" + TESTED_COMMIT + "`"), id);
            assertTrue(review.contains("Base SHA-256: `" + entry.baseTextureSha256() + "`"), id);
            assertTrue(review.contains("Mask SHA-256: `" + entry.dyeMaskSha256() + "`"), id);
            assertTrue(review.contains("Geometry SHA-256: `" + entry.geometrySha256() + "`"), id);
            assertTrue(review.contains("Geometry: `" + banner.get("geometry_id").getAsString() + "`"), id);
            PER_DEFINITION_CHECKS.forEach(check ->
                    assertTrue(review.contains("- " + check + ": PASS"), id + ": " + check));
            assertTrue(review.contains("Overall approval: APPROVED"), id);
            assertTrue(review.contains("Gate E result: PASS"), id);
            assertFalse(review.contains(": FAIL"), id);
            assertEquals(entry.baseTextureSha256(),
                    assets.getAsJsonObject("base_texture").get("sha256").getAsString(), id);
            assertEquals(entry.dyeMaskSha256(),
                    assets.getAsJsonObject("dye_mask").get("sha256").getAsString(), id);
            assertEquals(entry.geometrySha256(),
                    assets.getAsJsonObject("geometry").get("sha256").getAsString(), id);
        }
    }

    @Test
    void approvedLargeAssetsRemainByteIdenticalAndIntakesRemainReady() throws Exception {
        Path root = Path.of(System.getProperty("britannia.projectDir", ".")).toAbsolutePath().normalize();
        Set<String> geometryHashes = new HashSet<>();
        for (BannerScaffoldTool.BannerEntry entry : manifest.banners().stream()
                .filter(value -> "large".equals(value.group())).toList()) {
            JsonObject intake = json(Path.of(System.getProperty("britannia.projectDir", "."), entry.intakePath()));
            FinalContentIntakeValidator.Result validation = FinalContentIntakeValidator.validate(
                    root, Path.of(System.getProperty("britannia.projectDir", "."), entry.intakePath()).toAbsolutePath());
            assertEquals(FinalContentIntakeValidator.Status.READY_FOR_INTEGRATION,
                    validation.status(), entry.id() + ": " + validation.issues());
            assertTrue(validation.issues().isEmpty(), entry.id());

            Path baseSource = Path.of(System.getProperty("britannia.projectDir", "."), intake.getAsJsonObject("assets")
                    .getAsJsonObject("base_texture").get("source_file").getAsString());
            Path maskSource = Path.of(System.getProperty("britannia.projectDir", "."), intake.getAsJsonObject("assets")
                    .getAsJsonObject("dye_mask").get("source_file").getAsString());
            Path geometrySource = Path.of(System.getProperty("britannia.projectDir", "."), intake.getAsJsonObject("assets")
                    .getAsJsonObject("geometry").get("source_file").getAsString());
            Path baseRuntime = texture(entry.baseTexture());
            Path maskRuntime = texture(entry.dyeMask());
            Path geometryRuntime = model(entry.geometry());
            assertEquals(entry.baseTextureSha256(), sha256(baseSource), entry.id());
            assertEquals(entry.baseTextureSha256(), sha256(baseRuntime), entry.id());
            assertEquals(entry.dyeMaskSha256(), sha256(maskSource), entry.id());
            assertEquals(entry.dyeMaskSha256(), sha256(maskRuntime), entry.id());
            assertEquals(entry.geometrySha256(), sha256(geometrySource), entry.id());
            assertEquals(entry.geometrySha256(), sha256(geometryRuntime), entry.id());
            assertTrue(geometryHashes.add(entry.geometrySha256()), entry.id());
        }
        assertEquals(6, geometryHashes.size());
    }

    @Test
    void generatedDefinitionsAndStatusReportAgreeOnFinalTotals() throws Exception {
        Path definitions = Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/resources/data/britannia_mod/banner_definitions");
        try (var paths = Files.list(definitions)) {
            List<Path> files = paths.filter(path -> path.toString().endsWith(".json")).toList();
            assertEquals(35, files.size());
            for (Path file : files) {
                assertEquals("complete", json(file).get("content_status").getAsString(), file.toString());
            }
        }
        String status = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."), BannerScaffoldTool.STATUS_PATH));
        assertTrue(status.contains("Final artwork complete: 35 of 35"));
        assertTrue(status.contains("- placeholder: 0"));
        assertTrue(status.contains("- in_progress: 0"));
        assertTrue(status.contains("- complete: 35"));
        assertTrue(status.contains("- disabled: 0"));
        assertTrue(status.contains("PARALLEL_LARGE_GATE_E_CLOSEOUT.md"));
    }

    private static Path texture(String resource) {
        return Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/assets/britannia_mod/textures",
                resource.substring("britannia_mod:".length()) + ".png");
    }

    private static Path model(String resource) {
        return Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/assets/britannia_mod/models",
                resource.substring("britannia_mod:".length()) + ".json");
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
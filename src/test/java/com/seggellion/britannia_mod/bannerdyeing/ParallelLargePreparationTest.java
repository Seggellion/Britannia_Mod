package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParallelLargePreparationTest {
    private static final List<String> CANDIDATES = List.of(
            "tournament_curtain",
            "threefold_chain_standard",
            "iron_serpent_standard",
            "silver_fleur_curtain",
            "gilded_trellis_curtain",
            "gilded_chevron_curtain");
    private static final List<String> PLACEHOLDERS = List.of(
            "large_01", "large_02", "large_03", "large_04", "large_05", "large_06");
    private static final Path SUBMISSIONS =
            Path.of("content/banner-final-intake/submissions");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void illustratorReportRecordsTheExactReadOnlyLargeSource() {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/large_illustrator_report.json"));
        assertEquals(
                "C:/projects/britannia/raw fiels/tabbard/banner_large.ai",
                report.get("source_file").getAsString());
        assertEquals(35_613_618L, report.get("source_size_bytes").getAsLong());
        assertEquals(
                "64fd720476243a937d155b9ea547de60003ccc83f1c78093098e45517d25379e",
                report.get("source_sha256_before").getAsString());
        assertEquals(report.get("source_sha256_before").getAsString(),
                report.get("source_sha256_after").getAsString());
        assertTrue(report.get("pdf_compatible").getAsBoolean());

        JsonObject document = report.getAsJsonObject("document");
        assertEquals("CMYK", document.get("colour_space").getAsString());
        assertEquals(72, document.get("raster_effects_resolution").getAsInt());
        assertEquals(128, document.get("width").getAsInt());
        assertEquals(128, document.get("height").getAsInt());
        assertEquals(6, document.get("raster_items").getAsInt());
        assertEquals(0, document.get("placed_items").getAsInt());
        assertEquals(0, document.get("text_frames").getAsInt());
        assertEquals(CANDIDATES, strings(document.getAsJsonArray("top_level_layers")));

        JsonArray reconciliation = report.getAsJsonArray("reconciliation");
        assertEquals(CANDIDATES.size(), reconciliation.size());
        for (int index = 0; index < CANDIDATES.size(); index++) {
            JsonObject record = reconciliation.get(index).getAsJsonObject();
            assertEquals(CANDIDATES.get(index), record.get("illustrator_name").getAsString());
            assertEquals("britannia_mod:" + CANDIDATES.get(index),
                    record.get("candidate_stable_id").getAsString());
            assertEquals("britannia_mod:" + PLACEHOLDERS.get(index),
                    record.get("existing_catalogue_id").getAsString());
            assertEquals(index + 1, record.get("catalogue_index").getAsInt());
            assertEquals("MIGRATE_PROVISIONAL_LARGE_ID",
                    record.get("result").getAsString());
            assertTrue(record.get("mapping_evidence").getAsString()
                    .contains("not based on anonymous layer order alone"));
        }
        assertEquals(CANDIDATES.size(), report.getAsJsonArray("exports").size());
    }

    @Test
    void everyDraftPairSatisfiesTheTwoFilePixelContract() throws Exception {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/large_asset_report.json"));
        assertEquals(List.of(128, 128), integers(report.getAsJsonArray("canvas")));
        JsonArray assets = report.getAsJsonArray("assets");
        assertEquals(CANDIDATES.size(), assets.size());

        for (String id : CANDIDATES) {
            JsonObject asset = assets.asList().stream()
                    .map(value -> value.getAsJsonObject())
                    .filter(value -> ("britannia_mod:" + id)
                            .equals(value.get("stable_id").getAsString()))
                    .findFirst().orElseThrow();
            Path basePath = Path.of(asset.get("base_path").getAsString());
            Path maskPath = Path.of(asset.get("mask_path").getAsString());
            BufferedImage base = ImageIO.read(basePath.toFile());
            BufferedImage mask = ImageIO.read(maskPath.toFile());
            assertRgba128(base, id + " base");
            assertRgba128(mask, id + " mask");
            assertEquals(asset.get("base_sha256").getAsString(), sha256(basePath), id);
            assertEquals(asset.get("mask_sha256").getAsString(), sha256(maskPath), id);

            int active = 0;
            int transparent = 0;
            int fixed = 0;
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int basePixel = base.getRGB(x, y);
                    int maskPixel = mask.getRGB(x, y);
                    int baseAlpha = basePixel >>> 24;
                    int maskAlpha = maskPixel >>> 24;
                    assertTrue(maskAlpha <= baseAlpha, id + " at " + x + "," + y);
                    if (maskAlpha > 0) {
                        active++;
                        assertEquals(0xFFFFFF, maskPixel & 0xFFFFFF, id);
                    } else {
                        transparent++;
                        if (baseAlpha > 0) {
                            fixed++;
                        }
                    }
                }
            }
            assertTrue(active > 0, id);
            assertTrue(transparent > 0, id);
            assertTrue(fixed > 0, id);
            assertTrue(asset.get("fixed_upper_band_pixels").getAsInt() > 0, id);
            assertEquals(0, asset.get("mask_alpha_exceeds_base").getAsInt(), id);
            assertEquals(0, asset.get("active_mask_nonwhite_pixels").getAsInt(), id);

            for (String review : List.of(
                    "natural_checkerboard.png",
                    "dye_mask_checkerboard.png",
                    "blue_recolour.png",
                    "alignment.png",
                    "review_sheet.png",
                    "parallel_geometry_footprint.png")) {
                assertTrue(Files.isRegularFile(
                        SUBMISSIONS.resolve(id).resolve("review").resolve(review)),
                        id + ": " + review);
            }
        }
    }

    @Test
    void dimensionPlacementAndGeometryProposalsRemainParallelOnlyAndUnapproved() {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/large_asset_report.json"));
        JsonObject dimensions = report.getAsJsonObject("logical_dimensions_proposal");
        assertEquals(2, dimensions.get("width_blocks").getAsInt());
        assertEquals(2, dimensions.get("height_blocks").getAsInt());
        assertEquals("PENDING", dimensions.get("approval").getAsString());

        JsonObject placement = report.getAsJsonObject("placement_profile_proposal");
        assertEquals("britannia_mod:large_parallel", placement.get("id").getAsString());
        assertEquals(List.of("wall_parallel"),
                strings(placement.getAsJsonArray("orientations")));
        assertFalse(placement.toString().contains("wall_perpendicular"));
        assertEquals("britannia_mod:banner/mount/wall_parallel",
                placement.getAsJsonObject("orientation_mount_geometry")
                        .get("wall_parallel").getAsString());
        assertEquals("BLOCKED_PENDING_READY_INTAKE",
                placement.get("runtime_integration").getAsString());

        JsonArray groups = report.getAsJsonArray("geometry_groups");
        assertEquals(CANDIDATES.size(), groups.size());
        Set<String> ids = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (var value : groups) {
            JsonObject group = value.getAsJsonObject();
            assertEquals("CUSTOM_LARGE_GEOMETRY_REQUIRED",
                    group.get("classification").getAsString());
            assertTrue(ids.add(group.get("resource_id").getAsString()));
            assertTrue(hashes.add(group.get("sha256").getAsString()));
            JsonObject geometry = json(Path.of(group.get("source_file").getAsString()));
            assertEquals("minecraft:translucent", geometry.get("render_type").getAsString());
            assertTrue(geometry.getAsJsonObject("textures").has("base_texture"));
            assertTrue(geometry.getAsJsonObject("textures").has("dye_mask"));
            assertFalse(geometry.toString().contains("static_overlay"));
            assertFalse(geometry.toString().contains("fabric_base"));
        }
    }

    @Test
    void actualValidatorRejectsEachDraftWithoutInventedApprovalOrMigration() {
        Path root = Path.of(".").toAbsolutePath().normalize();
        for (String id : CANDIDATES) {
            Path intake = SUBMISSIONS.resolve(id).resolve(id + ".yml").toAbsolutePath();
            FinalContentIntakeValidator.Result result =
                    FinalContentIntakeValidator.validate(root, intake);
            assertEquals(FinalContentIntakeValidator.Status.INVALID,
                    result.status(), id + ": " + result.issues());
            assertEquals(2, result.pngMetadata().size(), id);
            assertTrue(result.issues().stream().anyMatch(issue -> issue.contains(
                    "banner.stable_id is not present in the live catalogue")), id);
            assertTrue(result.issues().stream().anyMatch(issue -> issue.contains(
                    "approval.status is NOT_APPROVED")), id);
            assertTrue(result.issues().stream().anyMatch(issue -> issue.contains(
                    "provenance.distribution_permission_confirmed must be true")), id);

            JsonObject intakeDocument = json(intake);
            assertEquals("NOT_APPROVED", intakeDocument.getAsJsonObject("approval")
                    .get("status").getAsString(), id);
            assertEquals("", intakeDocument.getAsJsonObject("approval")
                    .get("approved_by").getAsString(), id);
            assertFalse(intakeDocument.getAsJsonObject("provenance")
                    .get("original_art").getAsBoolean(), id);
            assertFalse(intakeDocument.getAsJsonObject("provenance")
                    .get("distribution_permission_confirmed").getAsBoolean(), id);
            assertEquals(List.of("wall_parallel"), strings(intakeDocument
                    .getAsJsonObject("banner")
                    .getAsJsonArray("supported_orientations")), id);
            assertEquals("in_progress",
                    intakeDocument.get("requested_content_status").getAsString(), id);
            assertNoRemovedArchitectureKeys(intakeDocument, id);
        }
    }

    @Test
    void liveCatalogueAndCompletedFamiliesRemainUnchanged() {
        List<BannerScaffoldTool.BannerEntry> large = manifest.banners().stream()
                .filter(entry -> "large".equals(entry.group())).toList();
        assertEquals(PLACEHOLDERS, large.stream()
                .map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), large.stream()
                .map(BannerScaffoldTool.BannerEntry::index).toList());
        assertTrue(large.stream().allMatch(entry -> entry.contentStatus() == null
                || "placeholder".equals(entry.contentStatus())));
        assertEquals(35, manifest.banners().size());
        assertEquals(29, manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus())).count());
        assertEquals(6, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());

        for (String id : CANDIDATES) {
            assertFalse(manifest.banners().stream().anyMatch(entry -> id.equals(entry.id())), id);
            assertFalse(Files.exists(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json")), id);
            assertFalse(Files.exists(Path.of(
                    "src/main/resources/assets/britannia_mod/textures/banner", id)), id);
        }
    }

    private static void assertNoRemovedArchitectureKeys(JsonObject document, String id) {
        String json = document.toString();
        for (String removed : List.of(
                "fabric_base", "static_overlay", "optional_overlay",
                "render_strategy", "\"recipe\"", "\"crafting\"")) {
            assertFalse(json.contains(removed), id + ": " + removed);
        }
    }

    private static void assertRgba128(BufferedImage image, String label) {
        assertNotEquals(null, image, label);
        assertEquals(128, image.getWidth(), label);
        assertEquals(128, image.getHeight(), label);
        assertTrue(image.getColorModel().hasAlpha(), label);
    }

    private static JsonObject json(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(path.toString(), exception);
        }
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(value -> value.getAsString()).toList();
    }

    private static List<Integer> integers(JsonArray values) {
        return values.asList().stream().map(value -> value.getAsInt()).toList();
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

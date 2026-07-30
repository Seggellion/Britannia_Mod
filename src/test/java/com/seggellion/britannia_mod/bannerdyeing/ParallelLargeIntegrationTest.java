package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryFamily;
import com.seggellion.britannia_mod.client.banner.BannerPlacedGeometryPlan;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParallelLargeIntegrationTest {
    private static final List<String> FAMILY = List.of(
            "tournament_curtain",
            "threefold_chain_standard",
            "iron_serpent_standard",
            "silver_fleur_curtain",
            "gilded_trellis_curtain",
            "gilded_chevron_curtain");
    private static final List<String> SUPERSEDED = List.of(
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
    void illustratorReportRecordsTheExactReadOnlyLargeSourceAndMigration() {
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
        assertEquals(FAMILY, strings(document.getAsJsonArray("top_level_layers")));

        JsonArray reconciliation = report.getAsJsonArray("reconciliation");
        assertEquals(FAMILY.size(), reconciliation.size());
        for (int index = 0; index < FAMILY.size(); index++) {
            JsonObject record = reconciliation.get(index).getAsJsonObject();
            assertEquals(FAMILY.get(index), record.get("illustrator_name").getAsString());
            assertEquals("britannia_mod:" + FAMILY.get(index),
                    record.get("candidate_stable_id").getAsString());
            assertEquals("britannia_mod:" + SUPERSEDED.get(index),
                    record.get("existing_catalogue_id").getAsString());
            assertEquals(index + 1, record.get("catalogue_index").getAsInt());
            assertEquals("MIGRATE_PROVISIONAL_LARGE_ID", record.get("result").getAsString());
        }
    }

    @Test
    void canonicalIdsReplaceTheSixPlaceholdersAtUnchangedIndices() {
        List<BannerScaffoldTool.BannerEntry> large = manifest.banners().stream()
                .filter(entry -> "large".equals(entry.group())).toList();
        assertEquals(FAMILY, large.stream().map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6),
                large.stream().map(BannerScaffoldTool.BannerEntry::index).toList());
        assertTrue(large.stream().allMatch(entry -> "source-named".equals(entry.nameStatus())));
        assertTrue(large.stream().allMatch(entry -> Boolean.TRUE.equals(entry.displayNameApproved())));
        assertTrue(large.stream().allMatch(entry -> "in_progress".equals(entry.contentStatus())));
        assertTrue(large.stream().allMatch(entry -> entry.widthBlocks() == 2
                && entry.heightBlocks() == 2
                && Boolean.FALSE.equals(entry.dimensionsProvisional())));
        assertTrue(large.stream().allMatch(entry ->
                List.of("wall_parallel").equals(entry.supportedOrientations())));
        assertFalse(manifest.banners().stream().anyMatch(entry -> SUPERSEDED.contains(entry.id())));
        assertEquals(35, manifest.banners().size());
        assertEquals(29, manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus())).count());
        assertEquals(6, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());

        for (String id : FAMILY) {
            JsonObject definition = json(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json"));
            assertEquals("in_progress", definition.get("content_status").getAsString(), id);
            assertEquals("large", definition.get("catalogue_group").getAsString(), id);
            assertEquals(2, definition.getAsJsonObject("dimensions")
                    .get("width_blocks").getAsInt(), id);
            assertEquals(2, definition.getAsJsonObject("dimensions")
                    .get("height_blocks").getAsInt(), id);
            assertFalse(definition.getAsJsonObject("dimensions")
                    .get("provisional").getAsBoolean(), id);
            assertEquals(List.of("wall_parallel"),
                    strings(definition.getAsJsonArray("supported_orientations")), id);
            assertEquals("britannia_mod:large_parallel",
                    definition.get("placement_profile").getAsString(), id);
            assertFalse(definition.toString().contains("/placeholder/"), id);
        }
        for (String id : SUPERSEDED) {
            assertFalse(Files.exists(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json")), id);
        }
    }

    @Test
    void approvedIntakesAndRuntimePairsSatisfyTheTwoFileContract() throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        JsonObject report = json(Path.of(
                "content/banner-final-intake/large_asset_report.json"));
        JsonArray assets = report.getAsJsonArray("assets");
        assertEquals(FAMILY.size(), assets.size());

        for (String id : FAMILY) {
            Path intake = SUBMISSIONS.resolve(id).resolve(id + ".yml").toAbsolutePath();
            FinalContentIntakeValidator.Result validation =
                    FinalContentIntakeValidator.validate(root, intake);
            assertEquals(FinalContentIntakeValidator.Status.READY_FOR_INTEGRATION,
                    validation.status(), id + ": " + validation.issues());
            assertTrue(validation.issues().isEmpty(), id + ": " + validation.issues());

            JsonObject document = json(intake);
            assertEquals("APPROVED", document.getAsJsonObject("approval")
                    .get("status").getAsString(), id);
            assertEquals("Seggellion", document.getAsJsonObject("approval")
                    .get("approved_by").getAsString(), id);
            assertEquals("2026-07-30", document.getAsJsonObject("approval")
                    .get("approved_date").getAsString(), id);
            assertTrue(document.getAsJsonObject("provenance")
                    .get("original_art").getAsBoolean(), id);
            assertEquals("Seggellion", document.getAsJsonObject("provenance")
                    .get("creator").getAsString(), id);
            assertTrue(document.getAsJsonObject("provenance")
                    .get("distribution_permission_confirmed").getAsBoolean(), id);
            assertFalse(document.getAsJsonObject("provenance")
                    .get("copied_from_reference_art").getAsBoolean(), id);
            assertNoRemovedArchitectureKeys(document, id);

            JsonObject asset = assets.asList().stream()
                    .map(value -> value.getAsJsonObject())
                    .filter(value -> ("britannia_mod:" + id)
                            .equals(value.get("stable_id").getAsString()))
                    .findFirst().orElseThrow();
            Path basePath = Path.of(asset.get("base_path").getAsString());
            Path maskPath = Path.of(asset.get("mask_path").getAsString());
            Path runtime = Path.of(
                    "src/main/resources/assets/britannia_mod/textures/banner", id);
            BufferedImage base = ImageIO.read(basePath.toFile());
            BufferedImage mask = ImageIO.read(maskPath.toFile());
            assertRgba128(base, id + " base");
            assertRgba128(mask, id + " mask");
            assertEquals(asset.get("base_sha256").getAsString(), sha256(basePath), id);
            assertEquals(asset.get("mask_sha256").getAsString(), sha256(maskPath), id);
            assertEquals(sha256(basePath), sha256(runtime.resolve("base_texture.png")), id);
            assertEquals(sha256(maskPath), sha256(runtime.resolve("dye_mask.png")), id);

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
        }
    }

    @Test
    void sixApprovedGeometriesMatchRuntimeAndClientRegistration() throws Exception {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/large_asset_report.json"));
        JsonArray groups = report.getAsJsonArray("geometry_groups");
        assertEquals(FAMILY.size(), groups.size());
        Set<String> resources = new LinkedHashSet<>();
        Set<String> hashes = new HashSet<>();
        for (var value : groups) {
            JsonObject group = value.getAsJsonObject();
            assertEquals("CUSTOM_LARGE_GEOMETRY_REQUIRED",
                    group.get("classification").getAsString());
            String resource = group.get("resource_id").getAsString();
            assertTrue(resources.add(resource));
            assertTrue(hashes.add(group.get("sha256").getAsString()));
            Path runtime = Path.of("src/main/resources/assets/britannia_mod/models",
                    resource.substring("britannia_mod:".length()) + ".json");
            assertEquals(group.get("sha256").getAsString(), sha256(runtime));
            JsonObject geometry = json(runtime);
            assertEquals("minecraft:translucent", geometry.get("render_type").getAsString());
            assertEquals(2, geometry.getAsJsonArray("elements").size());
            assertFalse(geometry.toString().contains("static_overlay"));
            assertFalse(geometry.toString().contains("fabric_base"));
        }
        assertEquals(6, resources.size());

        JsonObject client = json(Path.of(
                "src/main/resources/assets/britannia_mod/banner_client_assets.json"));
        String raw = client.toString();
        resources.forEach(resource -> assertTrue(raw.contains(resource), resource));
        for (String id : FAMILY) {
            assertTrue(raw.contains("britannia_mod:banner/" + id + "/base_texture"), id);
            assertTrue(raw.contains("britannia_mod:banner/" + id + "/dye_mask"), id);
        }
        assertTrue(raw.contains("britannia_mod:banner/mount/wall_parallel"));
    }

    @Test
    void largeParallelProfileUsesTheUntintedSharedMountAcrossFourFacings() {
        JsonObject profile = json(Path.of(
                "src/main/resources/data/britannia_mod/placement_profiles/large_parallel.json"));
        assertEquals("britannia_mod:large_parallel", profile.get("id").getAsString());
        assertEquals(2, profile.getAsJsonObject("dimensions")
                .get("width_blocks").getAsInt());
        assertEquals(2, profile.getAsJsonObject("dimensions")
                .get("height_blocks").getAsInt());
        assertFalse(profile.getAsJsonObject("dimensions").get("provisional").getAsBoolean());
        assertEquals(Set.of("wall_parallel"),
                profile.getAsJsonObject("orientation_mount_geometry").keySet());

        ResourceLocation mount =
                ResourceLocation.parse("britannia_mod:banner/mount/wall_parallel");
        Set<String> corners = new HashSet<>();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PARALLEL,
                    facing,
                    2,
                    2,
                    BannerPlacedGeometryFamily.LARGE,
                    false,
                    Optional.of(mount));
            assertEquals(facing, plan.frontNormal());
            assertEquals(Optional.of(mount), plan.mountGeometry());
            assertNotEquals(plan.topLeft(), plan.topRight());
            assertNotEquals(plan.topLeft(), plan.bottomLeft());
            corners.add(plan.topLeft().toString());
        }
        assertEquals(4, corners.size());

        JsonObject mountModel = json(Path.of(
                "src/main/resources/assets/britannia_mod/models/banner/mount/wall_parallel.json"));
        assertFalse(mountModel.toString().contains("tintindex"));
    }

    @Test
    void provisionalIdsDecodeAndEncodeAsCanonicalWithoutSchemaChanges() {
        for (int index = 0; index < FAMILY.size(); index++) {
            String legacy = "britannia_mod:" + SUPERSEDED.get(index);
            String canonical = "britannia_mod:" + FAMILY.get(index);
            BannerDefinitionId decoded = BannerDefinitionId.CODEC.parse(
                    JsonOps.INSTANCE, JsonParser.parseString("\"" + legacy + "\"")).getOrThrow();
            assertEquals(BannerDefinitionId.parse(canonical), decoded, legacy);
            assertEquals(canonical,
                    BannerDefinitionId.CODEC.encodeStart(JsonOps.INSTANCE, decoded)
                            .getOrThrow().getAsString(), legacy);
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
        assertEquals(4, image.getColorModel().getNumComponents(), label);
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

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

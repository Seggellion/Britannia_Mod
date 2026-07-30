package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

class ParallelMediumAssetPreparationTest {
    private static final List<String> FAMILY = List.of(
            "verdant_grape_pennon",
            "silver_rosette_pennon",
            "four_seals_pennon",
            "twin_spades_pennon",
            "ankh_pennon",
            "joined_wards");
    private static final List<String> SUPERSEDED = List.of(
            "medium_wall_01",
            "medium_wall_02",
            "medium_wall_03",
            "medium_wall_04",
            "medium_wall_05");
    private static final Path SUBMISSIONS =
            Path.of("content/banner-final-intake/submissions");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void illustratorReportReconcilesExactlyTheParallelMediumSource() {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/parallel_medium_illustrator_report.json"));
        assertEquals(
                "C:/projects/britannia/raw fiels/tabbard/banner_medium_wall.ai",
                report.get("source_file").getAsString());
        assertEquals(29_780_859L, report.get("source_size_bytes").getAsLong());
        assertEquals(
                "a6a75becd1793dac7a5b36361c0a33d615846ac4e97502deff794ef5ac337fac",
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
        assertEquals(FAMILY, strings(document.getAsJsonArray("top_level_layers")));

        JsonArray reconciliation = report.getAsJsonArray("reconciliation");
        assertEquals(FAMILY.size(), reconciliation.size());
        assertEquals(5, reconciliation.asList().stream()
                .map(value -> value.getAsJsonObject().get("result").getAsString())
                .filter("MIGRATE_PROVISIONAL_MEDIUM_WALL_ID"::equals).count());
        assertEquals(1, reconciliation.asList().stream()
                .map(value -> value.getAsJsonObject().get("result").getAsString())
                .filter("MATCH_EXISTING_MEDIUM_WALL"::equals).count());
        assertEquals(FAMILY.size(), report.getAsJsonArray("exports").size());
        report.getAsJsonArray("exports").forEach(value -> {
            JsonObject export = value.getAsJsonObject();
            assertTrue(export.get("artwork_extended_outside_artboard").getAsBoolean());
            assertEquals(List.of(128, 128), export.getAsJsonArray("output_canvas")
                    .asList().stream().map(element -> element.getAsInt()).toList());
        });
    }

    @Test
    void canonicalNamesReplaceOnlyTheFiveProvisionalIdsAtExistingIndices() {
        List<BannerScaffoldTool.BannerEntry> parallel = manifest.banners().stream()
                .filter(entry -> "medium-wall".equals(entry.group()))
                .toList();
        assertEquals(FAMILY, parallel.stream()
                .map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(List.of(7, 8, 9, 10, 11, 12), parallel.stream()
                .map(BannerScaffoldTool.BannerEntry::index).toList());
        assertEquals(Set.of("source-named"), parallel.stream()
                .map(BannerScaffoldTool.BannerEntry::nameStatus)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(parallel.stream().allMatch(entry -> entry.contentStatus() == null));
        assertTrue(parallel.stream().allMatch(entry -> entry.sourceLabel() != null));
        assertFalse(manifest.banners().stream()
                .anyMatch(entry -> SUPERSEDED.contains(entry.id())));

        for (String id : FAMILY) {
            JsonObject definition = json(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json"));
            assertEquals("placeholder", definition.get("content_status").getAsString(), id);
            assertEquals("medium-wall", definition.get("catalogue_group").getAsString(), id);
            assertTrue(definition.getAsJsonObject("dimensions")
                    .get("provisional").getAsBoolean(), id);
            assertTrue(definition.getAsJsonObject("assets").toString()
                    .contains("/placeholder/"), id);
        }
        for (String id : SUPERSEDED) {
            assertFalse(Files.exists(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json")), id);
        }
    }

    @Test
    void everyDraftPairSatisfiesTheAlignedTwoFilePixelContract() throws Exception {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/parallel_medium_asset_report.json"));
        JsonArray assets = report.getAsJsonArray("assets");
        assertEquals(FAMILY.size(), assets.size());
        for (String id : FAMILY) {
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
                    "parallel_geometry_mount.png")) {
                assertTrue(Files.isRegularFile(
                        SUBMISSIONS.resolve(id).resolve("review").resolve(review)),
                        id + ": " + review);
            }
        }
    }

    @Test
    void actualIntakeValidatorReportsNotReadyWithoutInvalidFindings() {
        Path root = Path.of(".").toAbsolutePath().normalize();
        for (String id : FAMILY) {
            Path intake = SUBMISSIONS.resolve(id).resolve(id + ".yml").toAbsolutePath();
            FinalContentIntakeValidator.Result result =
                    FinalContentIntakeValidator.validate(root, intake);
            assertEquals(FinalContentIntakeValidator.Status.NOT_READY,
                    result.status(), id + ": " + result.issues());
            assertTrue(result.issues().stream()
                    .noneMatch(issue -> issue.startsWith("INVALID:")),
                    id + ": " + result.issues());
            assertEquals(2, result.pngMetadata().size(), id);

            JsonObject document = json(intake);
            assertEquals("NOT_APPROVED", document.getAsJsonObject("approval")
                    .get("status").getAsString(), id);
            assertEquals("in_progress",
                    document.get("requested_content_status").getAsString(), id);
            assertEquals(List.of("wall_parallel"), strings(document
                    .getAsJsonObject("banner")
                    .getAsJsonArray("supported_orientations")), id);
            assertNoRemovedArchitectureKeys(document, id);
        }
    }

    @Test
    void fiveProposedGeometryGroupsPreserveTheFullCanvasUvBasis() {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/parallel_medium_asset_report.json"));
        JsonArray groups = report.getAsJsonArray("geometry_groups");
        assertEquals(5, groups.size());
        assertEquals(5, groups.asList().stream()
                .map(value -> value.getAsJsonObject().get("sha256").getAsString())
                .collect(java.util.stream.Collectors.toSet()).size());

        Set<String> covered = new LinkedHashSet<>();
        for (var value : groups) {
            JsonObject group = value.getAsJsonObject();
            assertFalse(group.get("existing_medium_geometry_reuse").getAsBoolean());
            strings(group.getAsJsonArray("members")).forEach(covered::add);
            JsonObject geometry = json(Path.of(group.get("source_file").getAsString()));
            assertEquals("minecraft:block/block", geometry.get("parent").getAsString());
            assertEquals("minecraft:translucent", geometry.get("render_type").getAsString());
            assertEquals(Set.of("base_texture", "dye_mask", "particle"),
                    geometry.getAsJsonObject("textures").keySet());
            assertEquals(2, geometry.getAsJsonArray("elements").size());
            JsonArray uv = geometry.getAsJsonArray("elements").get(0).getAsJsonObject()
                    .getAsJsonObject("faces").getAsJsonObject("north")
                    .getAsJsonArray("uv");
            uv.forEach(coordinate -> {
                assertTrue(coordinate.getAsDouble() >= 0.0);
                assertTrue(coordinate.getAsDouble() <= 16.0);
            });
            assertEquals(1, geometry.getAsJsonArray("elements").get(1).getAsJsonObject()
                    .getAsJsonObject("faces").getAsJsonObject("north")
                    .get("tintindex").getAsInt());
        }
        assertEquals(Set.copyOf(FAMILY), covered);
        JsonObject first = groups.get(0).getAsJsonObject();
        assertEquals(List.of("verdant_grape_pennon", "silver_rosette_pennon"),
                strings(first.getAsJsonArray("members")));
        assertEquals("REUSE_SHARED_MEDIUM_WALL_GEOMETRY",
                first.get("classification").getAsString());
    }

    @Test
    void proposedParallelPlacementUsesOnlyTheUntintedParallelMountAcrossFourFacings() {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/parallel_medium_asset_report.json"));
        JsonObject profile = report.getAsJsonObject("placement_profile_proposal");
        assertEquals("britannia_mod:medium_parallel", profile.get("id").getAsString());
        assertEquals(List.of("wall_parallel"),
                strings(profile.getAsJsonArray("orientations")));
        assertEquals(Set.of("wall_parallel"),
                profile.getAsJsonObject("orientation_mount_geometry").keySet());
        assertEquals("britannia_mod:banner/mount/wall_parallel",
                profile.getAsJsonObject("orientation_mount_geometry")
                        .get("wall_parallel").getAsString());
        assertEquals(1, profile.getAsJsonObject("dimensions")
                .get("width_blocks").getAsInt());
        assertEquals(2, profile.getAsJsonObject("dimensions")
                .get("height_blocks").getAsInt());

        ResourceLocation mount =
                ResourceLocation.parse("britannia_mod:banner/mount/wall_parallel");
        Set<String> corners = new HashSet<>();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BannerPlacedGeometryPlan plan = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PARALLEL,
                    facing,
                    1,
                    2,
                    BannerPlacedGeometryFamily.MEDIUM,
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
        assertEquals("britannia_mod:banner/mount/brass",
                mountModel.getAsJsonObject("textures").get("mount").getAsString());
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/britannia_mod/banner_mounts/parallel.json")));
    }

    @Test
    void draftFinalAssetsAreNotIntegratedIntoRuntime() {
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/britannia_mod/placement_profiles/medium_parallel.json")));
        for (String id : FAMILY) {
            assertFalse(Files.exists(Path.of(
                    "src/main/resources/assets/britannia_mod/textures/banner", id)),
                    id);
        }
        JsonObject client = json(Path.of(
                "src/main/resources/assets/britannia_mod/banner_client_assets.json"));
        String raw = client.toString();
        for (String id : FAMILY) {
            assertFalse(raw.contains("banner/" + id + "/base_texture"), id);
            assertFalse(raw.contains("banner/" + id + "/dye_mask"), id);
        }
        assertFalse(raw.contains("medium_parallel"));
    }

    private static void assertRgba128(BufferedImage image, String label) {
        assertEquals(128, image.getWidth(), label);
        assertEquals(128, image.getHeight(), label);
        assertTrue(image.getColorModel().hasAlpha(), label);
        assertEquals(4, image.getColorModel().getNumComponents(), label);
        assertEquals(8, image.getColorModel().getComponentSize(0), label);
    }

    private static void assertNoRemovedArchitectureKeys(JsonObject value, String id) {
        String raw = value.toString();
        for (String forbidden : List.of(
                "fabric_base",
                "static_overlay",
                "optional_overlay",
                "render_strategy",
                "recipe",
                "npc",
                "shop",
                "economy")) {
            assertFalse(raw.contains(forbidden), id + ": " + forbidden);
        }
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream()
                .map(element -> element.getAsString()).toList();
    }

    private static JsonObject json(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Could not read " + path, exception);
        }
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(path)));
    }
}

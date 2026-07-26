package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator.Status;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinalContentIntakeValidatorTest {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @TempDir
    Path temporaryDirectory;

    @Test
    void validApprovedIntakeIsReadyAndReportsPngMetadata() throws Exception {
        Fixture fixture = seed();
        var result = validate(fixture);
        assertEquals(Status.READY_FOR_INTEGRATION, result.status(), result.issues().toString());
        assertEquals("britannia_mod:road_guard", result.stableId());
        assertEquals(3, result.pngMetadata().size());
        assertTrue(result.pngMetadata().stream().allMatch(metadata ->
                metadata.width() == 8 && metadata.height() == 8
                        && metadata.bitDepth() == 8 && metadata.colourType() == 6
                        && metadata.alphaChannel()));
    }

    @Test
    void notApprovedIntakeIsNotReady() throws Exception {
        Fixture fixture = seed();
        fixture.document().getAsJsonObject("approval").addProperty("status", "NOT_APPROVED");
        fixture.document().getAsJsonObject("approval").addProperty("approved_by", "");
        fixture.document().getAsJsonObject("approval").addProperty("approved_date", "");
        var result = validate(fixture);
        assertEquals(Status.NOT_READY, result.status());
        assertIssue(result, "approval.status is NOT_APPROVED");
    }

    @Test
    void missingStableIdIsNotReadyAndUnknownStableIdIsInvalid() throws Exception {
        Fixture missing = seed();
        missing.document().getAsJsonObject("banner").add("stable_id", JsonNull.INSTANCE);
        assertEquals(Status.NOT_READY, validate(missing).status());

        Fixture unknown = seed();
        unknown.document().getAsJsonObject("banner")
                .addProperty("stable_id", "britannia_mod:not_in_catalogue");
        assertEquals(Status.INVALID, validate(unknown).status());
    }

    @Test
    void invalidDimensionsOrientationAndMountAreRejected() throws Exception {
        Fixture dimensions = seed();
        dimensions.document().getAsJsonObject("banner").addProperty("width_blocks", 4);
        assertEquals(Status.INVALID, validate(dimensions).status());

        Fixture orientation = seed();
        orientation.document().getAsJsonObject("banner")
                .getAsJsonArray("supported_orientations").add("floor");
        assertEquals(Status.INVALID, validate(orientation).status());

        Fixture mount = seed();
        mount.document().getAsJsonObject("banner")
                .getAsJsonArray("supported_mounts").add("britannia_mod:gold");
        assertEquals(Status.INVALID, validate(mount).status());
    }

    @Test
    void defaultMountMustBeAllowedAndSupported() throws Exception {
        Fixture fixture = seed();
        fixture.document().getAsJsonObject("banner")
                .getAsJsonArray("supported_mounts").remove(1);
        fixture.document().getAsJsonObject("banner")
                .addProperty("default_mount", "britannia_mod:iron");
        var result = validate(fixture);
        assertEquals(Status.INVALID, result.status());
        assertIssue(result, "default_mount must be included");
    }

    @Test
    void missingAssetInvalidPngAndMissingAlphaAreRejected() throws Exception {
        Fixture missing = seed();
        missing.document().getAsJsonObject("assets").getAsJsonObject("fabric_base")
                .addProperty("source_file", "owner/missing.png");
        assertEquals(Status.INVALID, validate(missing).status());

        Fixture invalidPng = seed();
        Path invalidPath = invalidPng.root().resolve("owner/fabric.png");
        Files.writeString(invalidPath, "not png", StandardCharsets.UTF_8);
        invalidPng.document().getAsJsonObject("assets").getAsJsonObject("fabric_base")
                .addProperty("sha256", sha256(invalidPath));
        assertEquals(Status.INVALID, validate(invalidPng).status());

        Fixture missingAlpha = seed();
        Path rgb = missingAlpha.root().resolve("owner/fabric.png");
        writePng(rgb, BufferedImage.TYPE_INT_RGB);
        missingAlpha.document().getAsJsonObject("assets").getAsJsonObject("fabric_base")
                .addProperty("sha256", sha256(rgb));
        var result = validate(missingAlpha);
        assertEquals(Status.INVALID, result.status());
        assertIssue(result, "true-colour RGBA");
    }

    @Test
    void incompleteProvenanceAndPermissionRemainNotReady() throws Exception {
        Fixture original = seed();
        original.document().getAsJsonObject("provenance").addProperty("original_art", false);
        assertEquals(Status.NOT_READY, validate(original).status());

        Fixture permission = seed();
        permission.document().getAsJsonObject("provenance")
                .addProperty("distribution_permission_confirmed", false);
        assertEquals(Status.NOT_READY, validate(permission).status());
    }

    @Test
    void copiedReferenceArtAndAmbiguousLayerMappingAreInvalid() throws Exception {
        Fixture copied = seed();
        copied.document().getAsJsonObject("provenance")
                .addProperty("copied_from_reference_art", true);
        assertEquals(Status.INVALID, validate(copied).status());

        Fixture mapping = seed();
        JsonObject assets = mapping.document().getAsJsonObject("assets");
        String fabric = assets.getAsJsonObject("fabric_base").get("resource_id").getAsString();
        assets.getAsJsonObject("dye_mask").addProperty("resource_id", fabric);
        var result = validate(mapping);
        assertEquals(Status.INVALID, result.status());
        assertIssue(result, "resource IDs must be distinct");
    }

    @Test
    void manualVerificationCannotBeClaimedWithoutTesterAndDate() throws Exception {
        Fixture fixture = seed();
        fixture.document().getAsJsonObject("manual_verification").addProperty("performed", true);
        fixture.document().getAsJsonObject("manual_verification").addProperty("tester", "");
        fixture.document().getAsJsonObject("manual_verification").addProperty("date", "");
        var result = validate(fixture);
        assertEquals(Status.NOT_READY, result.status());
        assertIssue(result, "manual_verification.tester is required");
        assertIssue(result, "manual_verification.date is required");
    }

    @Test
    void reportIsDeterministicAndValidationMutatesNoCatalogueOrGeneratedFile() throws Exception {
        Fixture fixture = seed();
        Path manifest = fixture.root().resolve("content/banner_catalogue.yml");
        Path definition = fixture.root().resolve(
                "src/main/resources/data/britannia_mod/banner_definitions/road_guard.json");
        byte[] manifestBefore = Files.readAllBytes(manifest);
        byte[] definitionBefore = Files.readAllBytes(definition);

        var first = validate(fixture);
        var second = validate(fixture);

        assertEquals(first, second);
        assertEquals(HexFormat.of().formatHex(manifestBefore),
                HexFormat.of().formatHex(Files.readAllBytes(manifest)));
        assertEquals(HexFormat.of().formatHex(definitionBefore),
                HexFormat.of().formatHex(Files.readAllBytes(definition)));
        assertTrue(Files.readString(definition).contains("\"content_status\": \"placeholder\""));
    }

    private Fixture seed() throws Exception {
        Path root = temporaryDirectory.resolve("repository-" + java.util.UUID.randomUUID());
        Path manifest = root.resolve("content/banner_catalogue.yml");
        Files.createDirectories(manifest.getParent());
        Files.copy(Path.of("content/banner_catalogue.yml"), manifest);

        Path definition = root.resolve(
                "src/main/resources/data/britannia_mod/banner_definitions/road_guard.json");
        Files.createDirectories(definition.getParent());
        Files.writeString(definition,
                "{\n  \"id\": \"britannia_mod:road_guard\",\n"
                        + "  \"content_status\": \"placeholder\"\n}\n",
                StandardCharsets.UTF_8);

        Path owner = root.resolve("owner");
        Files.createDirectories(owner);
        Path fabric = owner.resolve("fabric.png");
        Path mask = owner.resolve("mask.png");
        Path overlay = owner.resolve("overlay.png");
        writePng(fabric, BufferedImage.TYPE_INT_ARGB);
        writePng(mask, BufferedImage.TYPE_INT_ARGB);
        writePng(overlay, BufferedImage.TYPE_INT_ARGB);
        Path geometry = owner.resolve("geometry.json");
        Files.writeString(geometry, "{\"parent\":\"minecraft:block/block\"}\n", StandardCharsets.UTF_8);

        JsonObject document = JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "approval": {
                    "status": "APPROVED",
                    "approved_by": "Product Owner",
                    "approved_date": "2026-07-26",
                    "notes": "Approved test fixture"
                  },
                  "banner": {
                    "stable_id": "britannia_mod:road_guard",
                    "final_display_name": "Approved Test Banner",
                    "width_blocks": 1,
                    "height_blocks": 1,
                    "supported_orientations": ["wall_parallel", "wall_perpendicular"],
                    "supported_mounts": ["britannia_mod:brass", "britannia_mod:iron"],
                    "default_mount": "britannia_mod:brass",
                    "placement_profile_id": "britannia_mod:placeholder_x_small",
                    "geometry_id": "britannia_mod:banner/final/test_geometry",
                    "geometry_convention": "Approved one-block planar geometry"
                  },
                  "localization": {
                    "language": "en_us",
                    "value": "Approved Test Banner"
                  },
                  "assets": {
                    "fabric_base": {
                      "resource_id": "britannia_mod:banner/final/test_fabric",
                      "source_file": "owner/fabric.png",
                      "sha256": ""
                    },
                    "dye_mask": {
                      "resource_id": "britannia_mod:banner/final/test_mask",
                      "source_file": "owner/mask.png",
                      "sha256": ""
                    },
                    "static_overlay": {
                      "resource_id": "britannia_mod:banner/final/test_overlay",
                      "source_file": "owner/overlay.png",
                      "sha256": ""
                    },
                    "geometry": {
                      "shared_geometry_approved": false,
                      "source_file": "owner/geometry.json",
                      "sha256": ""
                    }
                  },
                  "references": {
                    "authoring_source_file": null,
                    "preview_image_file": null
                  },
                  "provenance": {
                    "original_art": true,
                    "creator": "Project Artist",
                    "creation_method": "Original digital pixel art",
                    "source_project_file": "artist-project/banner-source.aseprite",
                    "distribution_permission_confirmed": true,
                    "copied_from_reference_art": false
                  },
                  "manual_verification": {
                    "performed": false,
                    "tester": "",
                    "date": "",
                    "notes": ""
                  },
                  "requested_content_status": "in_progress"
                }
                """).getAsJsonObject();
        JsonObject assets = document.getAsJsonObject("assets");
        assets.getAsJsonObject("fabric_base").addProperty("sha256", sha256(fabric));
        assets.getAsJsonObject("dye_mask").addProperty("sha256", sha256(mask));
        assets.getAsJsonObject("static_overlay").addProperty("sha256", sha256(overlay));
        assets.getAsJsonObject("geometry").addProperty("sha256", sha256(geometry));
        return new Fixture(root, root.resolve("content/intake.yml"), document);
    }

    private FinalContentIntakeValidator.Result validate(Fixture fixture) throws Exception {
        Files.createDirectories(fixture.path().getParent());
        Files.writeString(fixture.path(), GSON.toJson(fixture.document()) + "\n", StandardCharsets.UTF_8);
        return FinalContentIntakeValidator.validate(fixture.root(), fixture.path());
    }

    private static void writePng(Path path, int type) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, type);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = type == BufferedImage.TYPE_INT_ARGB && (x == 0 || y == 0) ? 0 : 255;
                image.setRGB(x, y, alpha << 24 | 0x00A0A0A0);
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void assertIssue(FinalContentIntakeValidator.Result result, String text) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains(text)),
                result.issues().toString());
    }

    private record Fixture(Path root, Path path, JsonObject document) {
    }
}

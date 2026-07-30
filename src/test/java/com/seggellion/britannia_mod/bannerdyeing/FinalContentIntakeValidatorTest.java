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
    private static final int FINAL_TEXTURE_SIZE = 128;
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @TempDir
    Path temporaryDirectory;

    @Test
    void validApprovedTwoFileIntakeIsReadyAndAllowsFullColourBase() throws Exception {
        Fixture fixture = seed();
        var result = validate(fixture);
        assertEquals(Status.READY_FOR_INTEGRATION, result.status(), result.issues().toString());
        assertEquals("britannia_mod:road_guard", result.stableId());
        assertEquals(2, result.pngMetadata().size());
        assertTrue(result.pngMetadata().stream().allMatch(metadata ->
                metadata.width() == FINAL_TEXTURE_SIZE && metadata.height() == FINAL_TEXTURE_SIZE
                        && metadata.bitDepth() == 8 && metadata.colourType() == 6
                        && metadata.alphaChannel()));
    }

    @Test
    void approvedFinalTexturesMustBeExactly128By128() throws Exception {
        Fixture baseWrongSize = seed();
        replaceBase(baseWrongSize, 64, 128, (x, y) ->
                (x == 0 || y == 0 ? 0 : 255) << 24 | 0x806040);
        assertIssue(validate(baseWrongSize), "final base texture must be exactly 128x128 RGBA");

        Fixture maskWrongSize = seed();
        replaceMask(maskWrongSize, 128, 64, (x, y) ->
                (x == 0 || y == 0 ? 0 : 255) << 24 | 0x808080);
        assertIssue(validate(maskWrongSize), "final dye mask must be exactly 128x128 RGBA");
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
    void invalidDimensionsOrientationMountAndDefaultMountAreRejected() throws Exception {
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

        Fixture defaultMount = seed();
        defaultMount.document().getAsJsonObject("banner")
                .getAsJsonArray("supported_mounts").remove(1);
        defaultMount.document().getAsJsonObject("banner")
                .addProperty("default_mount", "britannia_mod:iron");
        assertIssue(validate(defaultMount), "default_mount must be included");
    }

    @Test
    void largeIntakeRequiresExactlyWallParallelOrientation() throws Exception {
        Fixture parallel = seed();
        JsonObject parallelBanner = parallel.document().getAsJsonObject("banner");
        parallelBanner.addProperty("stable_id", "britannia_mod:tournament_curtain");
        parallelBanner.getAsJsonArray("supported_orientations").remove(1);
        assertEquals(Status.READY_FOR_INTEGRATION, validate(parallel).status());

        Fixture perpendicular = seed();
        JsonObject perpendicularBanner = perpendicular.document().getAsJsonObject("banner");
        perpendicularBanner.addProperty("stable_id", "britannia_mod:threefold_chain_standard");
        perpendicularBanner.getAsJsonArray("supported_orientations").remove(0);
        assertIssue(validate(perpendicular),
                "large banners support wall_parallel orientation only");

        Fixture both = seed();
        both.document().getAsJsonObject("banner")
                .addProperty("stable_id", "britannia_mod:iron_serpent_standard");
        assertIssue(validate(both),
                "large banners support wall_parallel orientation only");
    }

    @Test
    void missingAssetInvalidPngAndMissingAlphaAreRejected() throws Exception {
        Fixture missing = seed();
        missing.document().getAsJsonObject("assets").getAsJsonObject("base_texture")
                .addProperty("source_file", "owner/missing.png");
        assertEquals(Status.INVALID, validate(missing).status());

        Fixture invalidPng = seed();
        Path invalidPath = invalidPng.root().resolve("owner/base.png");
        Files.writeString(invalidPath, "not png", StandardCharsets.UTF_8);
        invalidPng.document().getAsJsonObject("assets").getAsJsonObject("base_texture")
                .addProperty("sha256", sha256(invalidPath));
        assertEquals(Status.INVALID, validate(invalidPng).status());

        Fixture missingAlpha = seed();
        Path rgb = missingAlpha.root().resolve("owner/base.png");
        writeSolidPng(rgb, BufferedImage.TYPE_INT_RGB, 0xFF336699);
        missingAlpha.document().getAsJsonObject("assets").getAsJsonObject("base_texture")
                .addProperty("sha256", sha256(rgb));
        assertIssue(validate(missingAlpha), "true-colour RGBA");
    }

    @Test
    void dyeMaskRequiresTransparentActiveAndStrictGrayscalePixels() throws Exception {
        Fixture opaque = seed();
        replaceMask(opaque, (x, y) -> 0xFF808080);
        assertIssue(validate(opaque), "at least one fully transparent pixel");

        Fixture transparent = seed();
        replaceMask(transparent, (x, y) -> 0x00000000);
        assertIssue(validate(transparent), "at least one active pixel");

        Fixture coloured = seed();
        replaceMask(coloured, (x, y) -> x == 0 ? 0x00000000 : 0xFF806080);
        assertIssue(validate(coloured), "active RGB must be grayscale");

        Fixture outsideOpaqueBase = seed();
        replaceMask(outsideOpaqueBase, (x, y) ->
                x == 0 && y == 0 ? 0x80808080 : x == 1 ? 0x00000000 : 0xFF808080);
        assertIssue(validate(outsideOpaqueBase), "must not exceed base_texture alpha");
    }

    @Test
    void maskMayFollowPartiallyTransparentBaseEdgesWithoutExceedingThem() throws Exception {
        Fixture partialEdge = seed();
        replaceBase(partialEdge, (x, y) -> {
            int alpha = x == 0 || y == 0 ? 0 : x == 1 ? 128 : 255;
            return alpha << 24 | 0x806040;
        });
        replaceMask(partialEdge, (x, y) -> {
            int alpha = x == 0 || y == 0 ? 0 : x == 1 ? 128 : 255;
            return alpha << 24 | 0xFFFFFF;
        });
        assertEquals(Status.READY_FOR_INTEGRATION, validate(partialEdge).status());
    }

    @Test
    void incompleteProvenancePermissionAndFalseManualClaimAreNotReady() throws Exception {
        Fixture original = seed();
        original.document().getAsJsonObject("provenance").addProperty("original_art", false);
        assertEquals(Status.NOT_READY, validate(original).status());

        Fixture permission = seed();
        permission.document().getAsJsonObject("provenance")
                .addProperty("distribution_permission_confirmed", false);
        assertEquals(Status.NOT_READY, validate(permission).status());

        Fixture manual = seed();
        manual.document().getAsJsonObject("manual_verification").addProperty("performed", true);
        manual.document().getAsJsonObject("manual_verification").addProperty("tester", "");
        manual.document().getAsJsonObject("manual_verification").addProperty("date", "");
        var result = validate(manual);
        assertEquals(Status.NOT_READY, result.status());
        assertIssue(result, "manual_verification.tester is required");
        assertIssue(result, "manual_verification.date is required");
    }

    @Test
    void copiedReferenceArtAndAmbiguousTwoFileMappingAreInvalid() throws Exception {
        Fixture copied = seed();
        copied.document().getAsJsonObject("provenance")
                .addProperty("copied_from_reference_art", true);
        assertEquals(Status.INVALID, validate(copied).status());

        Fixture mapping = seed();
        JsonObject assets = mapping.document().getAsJsonObject("assets");
        String base = assets.getAsJsonObject("base_texture").get("resource_id").getAsString();
        assets.getAsJsonObject("dye_mask").addProperty("resource_id", base);
        assertIssue(validate(mapping), "resource IDs must be distinct");
    }

    @Test
    void removedAssetKeysAreRejectedWithTwoFileMigrationMessage() throws Exception {
        for (String removed : java.util.List.of("fabric_base", "static_overlay")) {
            Fixture fixture = seed();
            fixture.document().getAsJsonObject("assets").add(removed, new JsonObject());
            var result = validate(fixture);
            assertEquals(Status.INVALID, result.status());
            assertIssue(result, "assets." + removed + " is removed");
            assertIssue(result, "base_texture + dye_mask");
        }
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
        Path base = owner.resolve("base.png");
        Path mask = owner.resolve("mask.png");
        writeBasePng(base);
        writeMaskPng(mask);
        Path geometry = owner.resolve("geometry.json");
        Files.writeString(geometry, "{\"parent\":\"minecraft:block/block\"}\n", StandardCharsets.UTF_8);

        JsonObject document = JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "approval": {
                    "status": "APPROVED",
                    "approved_by": "Product Owner",
                    "approved_date": "2026-07-27",
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
                    "base_texture": {
                      "resource_id": "britannia_mod:banner/final/test_base",
                      "source_file": "owner/base.png",
                      "sha256": ""
                    },
                    "dye_mask": {
                      "resource_id": "britannia_mod:banner/final/test_mask",
                      "source_file": "owner/mask.png",
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
        assets.getAsJsonObject("base_texture").addProperty("sha256", sha256(base));
        assets.getAsJsonObject("dye_mask").addProperty("sha256", sha256(mask));
        assets.getAsJsonObject("geometry").addProperty("sha256", sha256(geometry));
        return new Fixture(root, root.resolve("content/intake.yml"), document);
    }

    private FinalContentIntakeValidator.Result validate(Fixture fixture) throws Exception {
        Files.createDirectories(fixture.path().getParent());
        Files.writeString(fixture.path(), GSON.toJson(fixture.document()) + "\n", StandardCharsets.UTF_8);
        return FinalContentIntakeValidator.validate(fixture.root(), fixture.path());
    }

    private static void writeBasePng(Path path) throws Exception {
        BufferedImage image = new BufferedImage(
                FINAL_TEXTURE_SIZE, FINAL_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = x == 0 || y == 0 ? 0 : 255;
                image.setRGB(x, y, alpha << 24 | ((40 + x % 16 * 12) << 16)
                        | ((70 + y % 16 * 8) << 8) | 190);
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }

    private static void writeMaskPng(Path path) throws Exception {
        writePixels(path, (x, y) -> {
            int alpha = x == 0 || y == 0 ? 0 : x == 1 ? 128 : 255;
            int shade = 48 + (x + y) % 20 * 8;
            return alpha << 24 | shade << 16 | shade << 8 | shade;
        });
    }

    private static void writeSolidPng(Path path, int type, int pixel) throws Exception {
        BufferedImage image = new BufferedImage(FINAL_TEXTURE_SIZE, FINAL_TEXTURE_SIZE, type);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, pixel);
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
    }

    private static void replaceMask(Fixture fixture, Pixel pixel) throws Exception {
        replaceMask(fixture, FINAL_TEXTURE_SIZE, FINAL_TEXTURE_SIZE, pixel);
    }

    private static void replaceMask(Fixture fixture, int width, int height, Pixel pixel) throws Exception {
        Path mask = fixture.root().resolve("owner/mask.png");
        writePixels(mask, width, height, pixel);
        fixture.document().getAsJsonObject("assets").getAsJsonObject("dye_mask")
                .addProperty("sha256", sha256(mask));
    }

    private static void replaceBase(Fixture fixture, Pixel pixel) throws Exception {
        replaceBase(fixture, FINAL_TEXTURE_SIZE, FINAL_TEXTURE_SIZE, pixel);
    }

    private static void replaceBase(Fixture fixture, int width, int height, Pixel pixel) throws Exception {
        Path base = fixture.root().resolve("owner/base.png");
        writePixels(base, width, height, pixel);
        fixture.document().getAsJsonObject("assets").getAsJsonObject("base_texture")
                .addProperty("sha256", sha256(base));
    }

    private static void writePixels(Path path, Pixel pixel) throws Exception {
        writePixels(path, FINAL_TEXTURE_SIZE, FINAL_TEXTURE_SIZE, pixel);
    }

    private static void writePixels(Path path, int width, int height, Pixel pixel) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, pixel.argb(x, y));
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

    @FunctionalInterface
    private interface Pixel {
        int argb(int x, int y);
    }

    private record Fixture(Path root, Path path, JsonObject document) {
    }
}

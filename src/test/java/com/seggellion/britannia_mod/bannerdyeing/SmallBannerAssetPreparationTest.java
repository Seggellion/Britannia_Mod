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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SmallBannerAssetPreparationTest {
    private static final List<String> FAMILY = List.of(
            "silver_and_gold_pennon",
            "end_01",
            "end_02",
            "pennon_of_silver",
            "iron_ward",
            "iron_ward_auxiliary");
    private static final Set<String> EXTRA_SMALL = Set.of(
            "road_guard",
            "pale_road_guard",
            "red_crosslets",
            "captains_red_crosslets",
            "scarlet_court",
            "verdant_court",
            "small_curtain",
            "prosperity_standard",
            "guardian_standard");
    private static final Path SUBMISSIONS =
            Path.of("content/banner-final-intake/submissions");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void familyIdentityAndRuntimeStateRemainSafelyUnintegrated() {
        List<BannerScaffoldTool.BannerEntry> small = manifest.banners().stream()
                .filter(entry -> "small".equals(entry.group()))
                .toList();
        assertEquals(FAMILY, small.stream().map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(List.of(21, 22, 23, 24, 25, 26),
                small.stream().map(BannerScaffoldTool.BannerEntry::index).toList());
        assertEquals(6, new HashSet<>(FAMILY).size());
        small.forEach(entry -> {
            assertTrue(entry.contentStatus() == null
                    || "placeholder".equals(entry.contentStatus()), entry.id());
            assertEquals("small", entry.group(), entry.id());
        });

        Set<String> complete = manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus()))
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet());
        assertEquals(EXTRA_SMALL, complete);
        assertTrue(complete.contains("small_curtain"));
        assertFalse(manifest.banners().stream()
                .anyMatch(entry -> "x_small_unnamed_01".equals(entry.id())));
        assertEquals(26, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus()))
                .count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus()))
                .count());
        assertEquals(35, manifest.banners().size());
    }

    @Test
    void everyDraftUsesTheActualValidatorAndIsNotReadyRatherThanInvalid() {
        for (String id : FAMILY) {
            Path intake = intake(id);
            FinalContentIntakeValidator.Result result =
                    FinalContentIntakeValidator.validate(Path.of("."), intake);
            assertEquals(FinalContentIntakeValidator.Status.NOT_READY,
                    result.status(), id + ": " + result.issues());
            assertTrue(result.issues().stream().noneMatch(
                    issue -> issue.startsWith("INVALID:")), id + ": " + result.issues());
            assertTrue(result.issues().contains(
                    "MISSING: approval.status is NOT_APPROVED"), id);
            assertTrue(result.issues().contains(
                    "MISSING: provenance.distribution_permission_confirmed must be true"), id);
            assertEquals(2, result.pngMetadata().size(), id);

            JsonObject document = json(intake);
            assertEquals("NOT_APPROVED",
                    document.getAsJsonObject("approval").get("status").getAsString(), id);
            assertEquals("in_progress",
                    document.get("requested_content_status").getAsString(), id);
            assertEquals("small",
                    document.getAsJsonObject("banner").get("family").getAsString(), id);
            assertNoRemovedArchitectureKeys(document, id);
        }
    }

    @Test
    void everyExportSatisfiesTheAlignedRgbaMaskContract() throws Exception {
        for (String id : FAMILY) {
            JsonObject assets = json(intake(id)).getAsJsonObject("assets");
            Path basePath = Path.of(assets.getAsJsonObject("base_texture")
                    .get("source_file").getAsString());
            Path maskPath = Path.of(assets.getAsJsonObject("dye_mask")
                    .get("source_file").getAsString());
            BufferedImage base = ImageIO.read(basePath.toFile());
            BufferedImage mask = ImageIO.read(maskPath.toFile());

            assertRgba128(base, id + " base");
            assertRgba128(mask, id + " mask");
            assertEquals(assets.getAsJsonObject("base_texture").get("sha256").getAsString(),
                    sha256(basePath), id);
            assertEquals(assets.getAsJsonObject("dye_mask").get("sha256").getAsString(),
                    sha256(maskPath), id);

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

            for (String review : List.of(
                    "natural_checkerboard.png",
                    "dye_mask_checkerboard.png",
                    "blue_recolour.png",
                    "alignment.png",
                    "review_sheet.png")) {
                assertTrue(Files.isRegularFile(SUBMISSIONS.resolve(id).resolve("review")
                        .resolve(review)), id + ": " + review);
            }
        }
    }

    @Test
    void proposedGeometryGroupsMatchTheAuthoredSilhouetteEvidence() throws Exception {
        Map<String, String> geometryIds = FAMILY.stream().collect(Collectors.toMap(
                id -> id,
                id -> json(intake(id)).getAsJsonObject("banner")
                        .get("geometry_id").getAsString()));
        assertEquals(geometryIds.get("silver_and_gold_pennon"),
                geometryIds.get("pennon_of_silver"));
        assertEquals(5, new HashSet<>(geometryIds.values()).size());

        Set<String> geometryHashes = new HashSet<>();
        for (String id : FAMILY) {
            JsonObject intake = json(intake(id));
            JsonObject source = intake.getAsJsonObject("assets").getAsJsonObject("geometry");
            Path path = Path.of(source.get("source_file").getAsString());
            assertTrue(Files.isRegularFile(path), id);
            assertEquals(source.get("sha256").getAsString(), sha256(path), id);

            JsonObject model = json(path);
            assertEquals("minecraft:block/block", model.get("parent").getAsString(), id);
            assertEquals("minecraft:translucent", model.get("render_type").getAsString(), id);
            JsonArray elements = model.getAsJsonArray("elements");
            assertEquals(2, elements.size(), id);
            assertEquals(Set.of("base_texture", "dye_mask", "particle"),
                    model.getAsJsonObject("textures").keySet(), id);
            assertEquals(1, elements.get(1).getAsJsonObject().getAsJsonObject("faces")
                    .getAsJsonObject("north").get("tintindex").getAsInt(), id);
            geometryHashes.add(source.get("sha256").getAsString());
        }
        assertEquals(5, geometryHashes.size());
    }

    @Test
    void proposedSmallPlacementReusesOrientationMountsAndAllFourFacings() throws Exception {
        ResourceLocation parallelId =
                ResourceLocation.parse("britannia_mod:banner/mount/wall_parallel");
        ResourceLocation perpendicularId =
                ResourceLocation.parse("britannia_mod:banner/mount/wall_perpendicular");
        assertNotEquals(Files.readString(model("mount/wall_parallel")),
                Files.readString(model("mount/wall_perpendicular")));

        for (String id : FAMILY) {
            JsonObject banner = json(intake(id)).getAsJsonObject("banner");
            assertEquals(List.of("wall_parallel", "wall_perpendicular"),
                    strings(banner.getAsJsonArray("supported_orientations")), id);
            assertEquals(List.of("britannia_mod:brass", "britannia_mod:iron"),
                    strings(banner.getAsJsonArray("supported_mounts")), id);
            assertEquals("britannia_mod:brass", banner.get("default_mount").getAsString(), id);
            assertEquals("britannia_mod:small",
                    banner.get("placement_profile_id").getAsString(), id);
        }

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BannerPlacedGeometryPlan parallel = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PARALLEL, facing, 1, 1,
                    BannerPlacedGeometryFamily.SMALL, false, Optional.of(parallelId));
            BannerPlacedGeometryPlan perpendicular = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PERPENDICULAR, facing, 1, 1,
                    BannerPlacedGeometryFamily.SMALL, false, Optional.of(perpendicularId));
            assertEquals(Optional.of(parallelId), parallel.mountGeometry());
            assertEquals(Optional.of(perpendicularId), perpendicular.mountGeometry());
            assertNotEquals(parallel.mountTopLeft(), perpendicular.mountTopLeft());
        }
    }

    @Test
    void noDraftAssetWasAdoptedIntoRuntimeBeforeApproval() {
        for (String id : FAMILY) {
            Path runtime = Path.of(
                    "src/main/resources/assets/britannia_mod/textures/banner", id);
            assertFalse(Files.exists(runtime), id);
            JsonObject definition = json(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json"));
            assertEquals("placeholder", definition.get("content_status").getAsString(), id);
            JsonObject assets = definition.getAsJsonObject("assets");
            assertTrue(assets.get("base_texture").getAsString().contains("/placeholder/"), id);
            assertTrue(assets.get("dye_mask").getAsString().contains("/placeholder/"), id);
            assertEquals(Set.of("geometry", "base_texture", "dye_mask"),
                    assets.keySet(), id);
        }
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
        return values.asList().stream().map(element -> element.getAsString()).toList();
    }

    private static Path intake(String id) {
        return SUBMISSIONS.resolve(id).resolve(id + ".yml");
    }

    private static Path model(String path) {
        return Path.of("src/main/resources/assets/britannia_mod/models/banner",
                path + ".json");
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
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;
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
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtraSmallBannerFamilyIntegrationTest {
    private static final List<String> FAMILY = List.of(
            "road_guard",
            "pale_road_guard",
            "red_crosslets",
            "captains_red_crosslets",
            "scarlet_court",
            "verdant_court",
            "small_curtain",
            "prosperity_standard",
            "guardian_standard");
    private static final Set<String> ROAD_GUARD_STYLE = Set.of(
            "road_guard",
            "pale_road_guard",
            "red_crosslets",
            "captains_red_crosslets",
            "scarlet_court",
            "verdant_court",
            "prosperity_standard",
            "guardian_standard");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void load() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(Path.of(System.getProperty("britannia.projectDir", "."), BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void authoritativeFamilyExistsExactlyOnceAndLegacyIdIsInactive() {
        List<BannerScaffoldTool.BannerEntry> family = manifest.banners().stream()
                .filter(entry -> FAMILY.contains(entry.id())).toList();
        assertEquals(FAMILY.size(), family.size());
        assertEquals(new HashSet<>(FAMILY), family.stream()
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertFalse(ProductionBannerCatalogue.CANONICAL_PATHS.contains("x_small_unnamed_01"));
        assertEquals(Set.of("prosperity_standard", "guardian_standard"),
                family.stream().filter(entry -> entry.index() >= 34)
                        .map(BannerScaffoldTool.BannerEntry::id)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void familyCatalogueContractIsCompleteAfterGateE() {
        for (BannerScaffoldTool.BannerEntry entry : manifest.banners()) {
            if (!FAMILY.contains(entry.id())) {
                continue;
            }
            assertEquals("x-small", entry.group(), entry.id());
            assertEquals("complete", entry.contentStatus(), entry.id());
            assertEquals(1, entry.widthBlocks(), entry.id());
            assertEquals(1, entry.heightBlocks(), entry.id());
            assertEquals(Boolean.FALSE, entry.dimensionsProvisional(), entry.id());
            // Owner ruling 2026-08-24: the non-wall families (x-small, small, medium) hang
            // perpendicular; only medium-wall and large lie parallel against the wall.
            assertEquals(List.of("wall_perpendicular"),
                    entry.supportedOrientations(), entry.id());
            assertEquals(List.of("britannia_mod:brass", "britannia_mod:iron"),
                    entry.supportedMounts(), entry.id());
            assertEquals("britannia_mod:brass", entry.defaultMount(), entry.id());
            assertEquals("britannia_mod:extra_small", entry.placementProfile(), entry.id());
            assertTrue(Boolean.TRUE.equals(entry.displayNameApproved()), entry.id());
        }
        assertEquals(new HashSet<>(FAMILY), manifest.banners().stream()
                .filter(entry -> "x-small".equals(entry.group()))
                .filter(entry -> "complete".equals(entry.contentStatus()))
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void eightDefinitionsShareRoadGuardGeometryAndCurtainIsDistinct() throws Exception {
        for (BannerScaffoldTool.BannerEntry entry : manifest.banners()) {
            if (ROAD_GUARD_STYLE.contains(entry.id())) {
                assertEquals("britannia_mod:banner/road_guard/geometry", entry.geometry(), entry.id());
            }
        }
        BannerScaffoldTool.BannerEntry curtain = manifest.banners().stream()
                .filter(entry -> entry.id().equals("small_curtain")).findFirst().orElseThrow();
        assertEquals("britannia_mod:banner/small_curtain/geometry", curtain.geometry());
        assertNotEquals(Files.readString(model("road_guard/geometry")),
                Files.readString(model("small_curtain/geometry")));
    }

    @Test
    void everyApprovedFamilyIntakeIsReadyForIntegration() {
        for (String id : FAMILY) {
            Path path = Path.of(System.getProperty("britannia.projectDir", "."),"content/banner-final-intake/submissions", id, id + ".yml");
            var result = FinalContentIntakeValidator.validate(Path.of(System.getProperty("britannia.projectDir", ".")), path);
            assertEquals(FinalContentIntakeValidator.Status.READY_FOR_INTEGRATION,
                    result.status(), id + ": " + result.issues());
        }
    }

    @Test
    void everyAssetIsAlignedRgba128AndRuntimeHashMatchesIntake() throws Exception {
        for (String id : FAMILY) {
            Path intakeBase = intake(id, "base_texture.png");
            Path intakeMask = intake(id, "dye_mask.png");
            Path runtimeBase = runtime(id, "base_texture.png");
            Path runtimeMask = runtime(id, "dye_mask.png");
            assertArrayEquals(Files.readAllBytes(intakeBase), Files.readAllBytes(runtimeBase), id);
            assertArrayEquals(Files.readAllBytes(intakeMask), Files.readAllBytes(runtimeMask), id);

            BufferedImage base = ImageIO.read(intakeBase.toFile());
            BufferedImage mask = ImageIO.read(intakeMask.toFile());
            assertEquals(128, base.getWidth(), id);
            assertEquals(128, base.getHeight(), id);
            assertEquals(128, mask.getWidth(), id);
            assertEquals(128, mask.getHeight(), id);
            assertTrue(base.getColorModel().hasAlpha(), id);
            assertTrue(mask.getColorModel().hasAlpha(), id);

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
        }
    }

    @Test
    void orientationMountSelectionIsSharedDistinctAndFourFacingSafe() throws Exception {
        JsonObject profile = JsonParser.parseString(Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/resources/data/britannia_mod/placement_profiles/extra_small.json")))
                .getAsJsonObject();
        JsonObject mounts = profile.getAsJsonObject("orientation_mount_geometry");
        // The extra-small profile is perpendicular-only after the owner's orientation ruling;
        // its key set must exactly equal the family's supported orientations.
        assertEquals(java.util.Set.of("wall_perpendicular"), mounts.keySet());
        ResourceLocation perpendicularId = ResourceLocation.parse(mounts.get("wall_perpendicular").getAsString());
        // The parallel mount belongs to the wall families now, and stays a distinct model.
        ResourceLocation parallelId = ResourceLocation.parse("britannia_mod:banner/mount/wall_parallel");
        assertNotEquals(parallelId, perpendicularId);
        assertNotEquals(Files.readString(model("mount/wall_parallel")),
                Files.readString(model("mount/wall_perpendicular")));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BannerPlacedGeometryPlan perpendicular = BannerPlacedGeometryPlan.create(
                    BannerOrientation.WALL_PERPENDICULAR, facing, 1, 1,
                    BannerPlacedGeometryFamily.X_SMALL, false, Optional.of(perpendicularId));
            assertEquals(Optional.of(perpendicularId), perpendicular.mountGeometry());
            assertEquals(1.125, perpendicular.mountTopRight().distanceTo(
                    perpendicular.mountTopLeft()), 1.0e-12);
        }
    }

    @Test
    void clientIndexDeduplicatesFamilyAndMountGeometry() throws Exception {
        JsonObject index = JsonParser.parseString(Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/resources/assets/britannia_mod/banner_client_assets.json"))).getAsJsonObject();
        List<String> geometries = index.getAsJsonArray("geometry_models").asList().stream()
                .map(element -> element.getAsString()).toList();
        assertEquals(1, geometries.stream()
                .filter("britannia_mod:banner/road_guard/geometry"::equals).count());
        assertEquals(1, geometries.stream()
                .filter("britannia_mod:banner/small_curtain/geometry"::equals).count());
        assertEquals(1, geometries.stream()
                .filter("britannia_mod:banner/mount/wall_parallel"::equals).count());
        assertEquals(1, geometries.stream()
                .filter("britannia_mod:banner/mount/wall_perpendicular"::equals).count());
    }

    @Test
    void catalogueHashesMatchRuntimeAssets() throws Exception {
        for (BannerScaffoldTool.BannerEntry entry : manifest.banners()) {
            if (!FAMILY.contains(entry.id())) {
                continue;
            }
            assertEquals(entry.baseTextureSha256(), sha256(runtime(entry.id(), "base_texture.png")), entry.id());
            assertEquals(entry.dyeMaskSha256(), sha256(runtime(entry.id(), "dye_mask.png")), entry.id());
            Path geometry = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/assets/britannia_mod/models",
                    entry.geometry().substring("britannia_mod:".length()) + ".json");
            assertEquals(entry.geometrySha256(), sha256(geometry), entry.id());
        }
    }

    private static Path intake(String id, String file) {
        return Path.of(System.getProperty("britannia.projectDir", "."),"content/banner-final-intake/submissions", id, file);
    }

    private static Path runtime(String id, String file) {
        return Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/assets/britannia_mod/textures/banner", id, file);
    }

    private static Path model(String path) {
        return Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/assets/britannia_mod/models/banner", path + ".json");
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}

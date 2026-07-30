package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PerpendicularMediumIntegrationTest {
    private static final List<String> MEDIUM = List.of(
            "tournament_medium",
            "ceremonial_tournament",
            "iron_quarter",
            "outer_ward",
            "ward_of_serpents",
            "serpent_guard",
            "crossroad_guard",
            "argent_shield");
    private static final Set<String> MEDIUM_WALL = Set.of(
            "medium_wall_01",
            "medium_wall_02",
            "medium_wall_03",
            "medium_wall_04",
            "medium_wall_05",
            "joined_wards");
    private static final Path INTAKE =
            Path.of("content/banner-final-intake/submissions");

    @Test
    void illustratorReportReconcilesExactlyTheExistingPerpendicularFamily() throws Exception {
        JsonObject report = json(Path.of(
                "content/banner-final-intake/medium_illustrator_report.json"));
        assertEquals(
                "C:/projects/britannia/raw fiels/tabbard/banner_medium.ai",
                report.get("source_file").getAsString());
        assertEquals(47_417_154L, report.get("source_size_bytes").getAsLong());
        assertEquals(
                "5a219e6e276884e6b7173ec5c6608c8a85b53dcbdeb768b0f2c19ead0423d34d",
                report.get("source_sha256_before").getAsString());
        assertEquals(
                report.get("source_sha256_before").getAsString(),
                report.get("source_sha256_after").getAsString());
        assertTrue(report.get("pdf_compatible").getAsBoolean());

        JsonObject document = report.getAsJsonObject("document");
        assertEquals(128, document.get("width").getAsInt());
        assertEquals(128, document.get("height").getAsInt());
        assertEquals("CMYK", document.get("colour_space").getAsString());
        assertEquals(72, document.get("raster_effects_resolution").getAsInt());
        assertEquals(0, document.get("placed_items").getAsInt());

        Set<String> layers = new LinkedHashSet<>();
        document.getAsJsonArray("top_level_layers")
                .forEach(layer -> layers.add(layer.getAsString()));
        assertEquals(Set.copyOf(MEDIUM), layers);
        assertEquals(MEDIUM.size(), report.getAsJsonArray("exports").size());
    }

    @Test
    void everyApprovedPairSatisfiesTheTwoFilePixelContract() throws Exception {
        for (String id : MEDIUM) {
            BufferedImage base = ImageIO.read(INTAKE.resolve(id).resolve("base_texture.png").toFile());
            BufferedImage mask = ImageIO.read(INTAKE.resolve(id).resolve("dye_mask.png").toFile());
            assertEquals(128, base.getWidth(), id);
            assertEquals(128, base.getHeight(), id);
            assertTrue(base.getColorModel().hasAlpha(), id);
            assertEquals(128, mask.getWidth(), id);
            assertEquals(128, mask.getHeight(), id);
            assertTrue(mask.getColorModel().hasAlpha(), id);

            int active = 0;
            int transparent = 0;
            int fixed = 0;
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int baseArgb = base.getRGB(x, y);
                    int maskArgb = mask.getRGB(x, y);
                    int baseAlpha = baseArgb >>> 24;
                    int maskAlpha = maskArgb >>> 24;
                    assertTrue(maskAlpha <= baseAlpha, id + " at " + x + "," + y);
                    if (maskAlpha == 0) {
                        transparent++;
                        if (baseAlpha > 0) {
                            fixed++;
                        }
                    } else {
                        active++;
                        assertEquals(0x00ffffff, maskArgb & 0x00ffffff, id);
                    }
                }
            }
            assertTrue(active > 0, id);
            assertTrue(transparent > 0, id);
            assertTrue(fixed > 0, id);
        }
    }

    @Test
    void actualValidatorAcceptsEveryApprovedIntake() {
        Path root = Path.of(".").toAbsolutePath().normalize();
        for (String id : MEDIUM) {
            FinalContentIntakeValidator.Result result = FinalContentIntakeValidator.validate(
                    root, INTAKE.resolve(id).resolve(id + ".yml").toAbsolutePath());
            assertEquals(FinalContentIntakeValidator.Status.READY_FOR_INTEGRATION, result.status(), id);
            assertTrue(result.issues().stream().noneMatch(issue -> issue.startsWith("INVALID:")), id);
            assertEquals(2, result.pngMetadata().size(), id);
        }
    }

    @Test
    void runtimeCatalogueIntegratesPerpendicularFamilyAndLeavesParallelFamilyUntouched() throws Exception {
        JsonObject catalogue = json(Path.of("content/banner_catalogue.yml"));
        JsonArray banners = catalogue.getAsJsonArray("banners");
        Set<String> medium = new LinkedHashSet<>();
        Set<String> mediumWall = new LinkedHashSet<>();
        for (var value : banners) {
            JsonObject banner = value.getAsJsonObject();
            String group = banner.get("group").getAsString();
            if ("medium".equals(group)) {
                String id = banner.get("id").getAsString();
                medium.add(id);
                assertEquals("complete", banner.get("content_status").getAsString(), id);
                assertFalse(banner.get("dimensions_provisional").getAsBoolean(), id);
                assertEquals(List.of("wall_perpendicular"), banner.getAsJsonArray("supported_orientations")
                        .asList().stream().map(value2 -> value2.getAsString()).toList(), id);
                assertEquals("britannia_mod:medium_perpendicular", banner.get("placement_profile").getAsString(), id);
            } else if ("medium-wall".equals(group)) {
                mediumWall.add(banner.get("id").getAsString());
                assertFalse(banner.has("content_status"), banner.toString());
            }
        }
        assertEquals(Set.copyOf(MEDIUM), medium);
        assertEquals(MEDIUM_WALL, mediumWall);

        JsonObject profile = json(Path.of(
                "src/main/resources/data/britannia_mod/placement_profiles/medium_perpendicular.json"));
        JsonObject mounts = profile.getAsJsonObject("orientation_mount_geometry");
        assertEquals(Set.of("wall_perpendicular"), mounts.keySet());
        assertEquals("britannia_mod:banner/mount/wall_perpendicular",
                mounts.get("wall_perpendicular").getAsString());

        for (String id : MEDIUM) {
            JsonObject definition = json(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json"));
            assertEquals("complete", definition.get("content_status").getAsString());
            assertEquals("britannia_mod:medium_perpendicular", definition.get("placement_profile").getAsString());
            assertEquals(List.of("wall_perpendicular"), definition.getAsJsonArray("supported_orientations")
                    .asList().stream().map(value -> value.getAsString()).toList());
            JsonObject assets = definition.getAsJsonObject("assets");
            assertEquals("britannia_mod:banner/" + id + "/base_texture",
                    assets.get("base_texture").getAsString());
            assertEquals("britannia_mod:banner/" + id + "/dye_mask",
                    assets.get("dye_mask").getAsString());
            Path runtime = Path.of("src/main/resources/assets/britannia_mod/textures/banner", id);
            assertEquals(-1L, Files.mismatch(INTAKE.resolve(id).resolve("base_texture.png"),
                    runtime.resolve("base_texture.png")), id);
            assertEquals(-1L, Files.mismatch(INTAKE.resolve(id).resolve("dye_mask.png"),
                    runtime.resolve("dye_mask.png")), id);
        }
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}

package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import com.seggellion.britannia_mod.tools.FinalContentIntakeValidator;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtraSmallGateECloseoutTest {
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
    private static final List<String> BATCH_CHECKS = List.of(
            "All nine stable IDs correct",
            "All nine 128 x 128 base textures reviewed",
            "All nine 128 x 128 dye masks reviewed",
            "Natural appearance reviewed for every banner",
            "Fixed regions remain unchanged",
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
            "Wall-perpendicular placement",
            "North-facing placement",
            "South-facing placement",
            "East-facing placement",
            "West-facing placement",
            "Rotation and mirroring",
            "Mount and model alignment",
            "Save/reload",
            "Relog and client tracking",
            "F3+T resource reload",
            "/reload data reload",
            "Break/drop",
            "Support loss",
            "Pick block",
            "Re-placement",
            "Item/preview/placed consistency",
            "All seven pigments represented",
            "All four materials represented",
            "Every unique geometry reviewed",
            "Block-atlas reload");
    private static final List<String> PER_BANNER_CHECKS = List.of(
            "128 x 128 fidelity",
            "Natural appearance",
            "Fixed regions",
            "Dyeable regions",
            "Highlights and shadows",
            "Preview",
            "Brass",
            "Iron",
            "Parallel",
            "Perpendicular",
            "Facing and rotation",
            "Save/reload",
            "Break/drop",
            "Pick block and re-placement");
    private static BannerScaffoldTool.Manifest manifest;

    @BeforeAll
    static void loadManifest() throws Exception {
        manifest = BannerScaffoldTool.readAndValidateManifest(Path.of(BannerScaffoldTool.MANIFEST_PATH));
    }

    @Test
    void exactlyTheApprovedNineAreCompleteAndTheOtherTwentySixRemainPlaceholder() {
        Set<String> complete = manifest.banners().stream()
                .filter(entry -> "complete".equals(entry.contentStatus()))
                .map(BannerScaffoldTool.BannerEntry::id)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(FAMILY), complete);
        assertEquals(26, manifest.banners().stream()
                .filter(entry -> entry.contentStatus() == null
                        || "placeholder".equals(entry.contentStatus())).count());
        assertEquals(0, manifest.banners().stream()
                .filter(entry -> "in_progress".equals(entry.contentStatus())).count());
        assertEquals(35, manifest.banners().size());
        assertTrue(complete.contains("small_curtain"));
        assertFalse(manifest.banners().stream()
                .anyMatch(entry -> "x_small_unnamed_01".equals(entry.id())));
    }

    @Test
    void everyCompleteDefinitionHasReadyApprovedIntakeAndOnlyFinalTwoFileAssets() throws Exception {
        for (String id : FAMILY) {
            Path intakePath = intake(id, id + ".yml");
            var result = FinalContentIntakeValidator.validate(Path.of("."), intakePath);
            assertEquals(FinalContentIntakeValidator.Status.READY_FOR_INTEGRATION,
                    result.status(), id + ": " + result.issues());

            JsonObject intake = json(intakePath);
            assertEquals("APPROVED", intake.getAsJsonObject("approval").get("status").getAsString(), id);
            JsonObject provenance = intake.getAsJsonObject("provenance");
            assertTrue(provenance.get("original_art").getAsBoolean(), id);
            assertTrue(provenance.get("distribution_permission_confirmed").getAsBoolean(), id);
            assertFalse(provenance.get("creator").getAsString().isBlank(), id);
            assertFalse(provenance.get("creation_method").getAsString().isBlank(), id);

            JsonObject definition = json(Path.of(
                    "src/main/resources/data/britannia_mod/banner_definitions", id + ".json"));
            assertEquals("complete", definition.get("content_status").getAsString(), id);
            JsonObject assets = definition.getAsJsonObject("assets");
            assertEquals(Set.of("geometry", "base_texture", "dye_mask"), assets.keySet(), id);
            assertFalse(assets.get("base_texture").getAsString().contains("placeholder"), id);
            assertFalse(assets.get("dye_mask").getAsString().contains("placeholder"), id);
            String raw = definition.toString();
            assertFalse(raw.contains("fabric_base"), id);
            assertFalse(raw.contains("static_overlay"), id);
            assertFalse(raw.toLowerCase().contains("recipe"), id);
            assertFalse(raw.toLowerCase().contains("craft"), id);
        }
    }

    @Test
    void productOwnerEvidenceIsCompleteTrackedAndHasNoUnresolvedCurrentItem() throws Exception {
        String batch = Files.readString(Path.of(
                "content/banner-final-intake/EXTRA_SMALL_GATE_E_REVIEW.md"));
        assertTrue(batch.contains("Reviewer: Product Owner"));
        assertTrue(batch.contains(
                "Commit tested: `bf68e4b0025f1aed9a905904a669a09f39e06d31`"));
        BATCH_CHECKS.forEach(check -> assertTrue(batch.contains("- " + check + ": PASS"), check));
        assertTrue(batch.contains("Batch approval: APPROVED"));
        assertTrue(batch.contains("Gate E: PASS"));

        for (String id : FAMILY) {
            JsonObject intake = json(intake(id, id + ".yml"));
            JsonObject banner = intake.getAsJsonObject("banner");
            JsonObject assets = intake.getAsJsonObject("assets");
            String review = Files.readString(intake(id, "GATE_E_REVIEW.md"));
            String marker = "## 2026-07-28 authoritative 128 x 128 family review";
            int markerOffset = review.indexOf(marker);
            assertTrue(markerOffset >= 0, id);
            String current = review.substring(markerOffset);
            assertTrue(current.contains("Stable ID: `britannia_mod:" + id + "`"), id);
            assertTrue(current.contains("Display name: "
                    + banner.get("final_display_name").getAsString()), id);
            assertTrue(current.contains("Reviewer: Product Owner"), id);
            assertTrue(current.contains(
                    "Tested commit: `bf68e4b0025f1aed9a905904a669a09f39e06d31`"), id);
            assertTrue(current.contains("Base SHA-256: `"
                    + assets.getAsJsonObject("base_texture").get("sha256").getAsString() + "`"), id);
            assertTrue(current.contains("Mask SHA-256: `"
                    + assets.getAsJsonObject("dye_mask").get("sha256").getAsString() + "`"), id);
            assertTrue(current.contains("Texture dimensions: 128 x 128, 8-bit RGBA"), id);
            assertTrue(current.contains("Geometry: `" + banner.get("geometry_id").getAsString() + "`"), id);
            PER_BANNER_CHECKS.forEach(check ->
                    assertTrue(current.contains("- " + check + ": PASS"), id + ": " + check));
            assertTrue(current.contains("Overall approval: APPROVED"), id);
            assertTrue(current.contains("Gate E result: PASS"), id);
            assertFalse(current.contains(": FAIL"), id);
            assertFalse(current.contains("NOT PERFORMED"), id);
            assertFalse(current.toLowerCase().contains("unresolved"), id);
            assertFalse(current.toLowerCase().contains("pending"), id);
        }
    }

    @Test
    void allEighteenAssetsMatchIntakeAndRuntimeAtRgba128() throws Exception {
        for (String id : FAMILY) {
            JsonObject assets = json(intake(id, id + ".yml")).getAsJsonObject("assets");
            for (String file : List.of("base_texture", "dye_mask")) {
                Path source = intake(id, file + ".png");
                Path runtime = Path.of("src/main/resources/assets/britannia_mod/textures/banner",
                        id, file + ".png");
                byte[] sourceBytes = Files.readAllBytes(source);
                byte[] runtimeBytes = Files.readAllBytes(runtime);
                assertArrayEquals(sourceBytes, runtimeBytes, id + ": " + file);
                assertEquals(assets.getAsJsonObject(file).get("sha256").getAsString(),
                        sha256(sourceBytes), id + ": " + file);
                BufferedImage image = ImageIO.read(source.toFile());
                assertEquals(128, image.getWidth(), id + ": " + file);
                assertEquals(128, image.getHeight(), id + ": " + file);
                assertTrue(image.getColorModel().hasAlpha(), id + ": " + file);
                assertEquals(4, image.getColorModel().getNumComponents(), id + ": " + file);
                assertEquals(8, image.getColorModel().getComponentSize(0), id + ": " + file);
            }
        }
    }

    private static Path intake(String id, String name) {
        return Path.of("content/banner-final-intake/submissions", id, name);
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

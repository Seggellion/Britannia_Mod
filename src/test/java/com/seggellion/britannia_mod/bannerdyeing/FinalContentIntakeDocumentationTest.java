package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FinalContentIntakeDocumentationTest {
    private static final Path GUIDE = Path.of("docs/banner-dyeing/FINAL_CONTENT_INTAKE.md");
    private static final Path CHECKLIST =
            Path.of("docs/banner-dyeing/FINAL_CONTENT_REVIEW_CHECKLIST.md");
    private static final Path README = Path.of("content/banner-final-intake/README.md");
    private static final Path GENERIC =
            Path.of("content/banner-final-intake/intake.example.yml");
    private static final Path BATCH =
            Path.of("content/banner-final-intake/extra_small_batch.example.yml");
    private static final Path SMALL_BATCH =
            Path.of("content/banner-final-intake/small_batch.example.yml");
    private static final List<String> EXTRA_SMALL_IDS = List.of(
            "britannia_mod:road_guard",
            "britannia_mod:pale_road_guard",
            "britannia_mod:red_crosslets",
            "britannia_mod:captains_red_crosslets",
            "britannia_mod:scarlet_court",
            "britannia_mod:verdant_court",
            "britannia_mod:small_curtain",
            "britannia_mod:prosperity_standard",
            "britannia_mod:guardian_standard");
    private static final List<String> SMALL_IDS = List.of(
            "britannia_mod:silver_and_gold_pennon",
            "britannia_mod:star_standard",
            "britannia_mod:ship_standard",
            "britannia_mod:pennon_of_silver",
            "britannia_mod:iron_ward",
            "britannia_mod:iron_ward_auxiliary");

    @Test
    void intakeGuideChecklistReadmeAndTemplatesExist() {
        assertTrue(Files.isRegularFile(GUIDE));
        assertTrue(Files.isRegularFile(CHECKLIST));
        assertTrue(Files.isRegularFile(README));
        assertTrue(Files.isRegularFile(GENERIC));
        assertTrue(Files.isRegularFile(BATCH));
        assertTrue(Files.isRegularFile(SMALL_BATCH));
    }

    @Test
    void guideDefinesTheSingleTwoFileSelectiveRecolourContract() throws Exception {
        String guide = Files.readString(GUIDE);
        for (String heading : List.of(
                "## 1. Purpose", "## 2. Stable identity", "## 3. Required owner decisions",
                "## 4. Required visual assets", "## 5. Base-texture rules", "## 6. Dye-mask rules",
                "## 7. Fixed artwork and the two-file limitation", "## 8. Mount and material rules",
                "## 9. File and composition requirements", "## 10. Original-art provenance",
                "## 11. Approval states", "## 12. Content statuses and render state",
                "## 13. Existing-world consequences", "## 14. Integration workflow",
                "## 15. Crafting boundary")) {
            assertTrue(guide.contains(heading), heading);
        }
        assertTrue(guide.contains("it may not invent"));
        assertTrue(guide.contains("x_small_unnamed_01"));
        assertTrue(guide.contains("small_curtain"));
        assertTrue(guide.contains("Existing 16 x 16 diagnostic placeholders"));
        assertTrue(guide.contains("exactly 128 x 128 pixels"));
        assertTrue(guide.contains("mask alpha exceed base alpha"));
        assertTrue(guide.contains("Rendering remains resolution-independent"));
        assertTrue(guide.contains("not automatic approval"));
        assertTrue(guide.contains("source_pigment_id is present"));
        assertTrue(guide.contains("output.rgb = B.rgb × (1 - a) + T.rgb × a"));
        assertTrue(guide.contains("Banner crafting: not approved"));
        assertTrue(guide.contains("Crafting requirements: not applicable"));
    }

    @Test
    void checklistCoversTwoFilesAutomatedBoundariesAndManualMatrices() throws Exception {
        String checklist = Files.readString(CHECKLIST);
        for (String required : List.of(
                "Stable ID verified", "Final display name approved", "base_texture.png", "dye_mask.png",
                "exactly 128 x 128 RGBA", "Mask alpha never exceeds aligned base alpha",
                "Asset-to-ID mapping is unambiguous", "Manifest validates", "Base remains untinted",
                "Natural/default state omits the mask pass", "Brass remains untinted", "Iron remains untinted",
                "Inventory", "First-person hand", "Third-person hand", "Dropped item", "Item frame",
                "Dye-preview current", "Dye-preview proposed", "cotton", "wool", "linen", "silk",
                "North", "South", "East", "West", "Save/reload", "Relog", "Late client tracking",
                "Resource reload", "Data reload", "Anchor break", "Child break, if applicable",
                "Pick block", "Re-place recovered item",
                "Product owner reviewed final natural and recoloured appearance",
                "Crafting requirements are not applicable")) {
            assertTrue(checklist.contains(required), required);
        }
    }

    @Test
    void readmeAndTemplatesStateTheApproved128PixelStandard() throws Exception {
        String readme = Files.readString(README);
        String generic = Files.readString(GENERIC);
        String batch = Files.readString(BATCH);
        String smallBatch = Files.readString(SMALL_BATCH);
        for (String text : List.of(readme, generic, batch, smallBatch)) {
            assertTrue(text.contains("128 x 128"));
        }
        assertTrue(readme.contains("Existing 16 x 16 diagnostic placeholders"));
        assertTrue(readme.contains("runtime rendering remains independent of texture resolution"));
    }

    @Test
    void genericTemplateIsJsonCompatibleYamlAndExactlyTwoFileUnapproved() throws Exception {
        String raw = Files.readString(GENERIC);
        JsonObject intake = JsonParser.parseString(raw).getAsJsonObject();
        assertEquals(1, intake.get("schema_version").getAsInt());
        assertEquals("NOT_APPROVED",
                intake.getAsJsonObject("approval").get("status").getAsString());
        assertEquals("britannia_mod:example_banner",
                intake.getAsJsonObject("banner").get("stable_id").getAsString());
        assertEquals("placeholder", intake.get("requested_content_status").getAsString());
        assertFalse(intake.getAsJsonObject("provenance").get("original_art").getAsBoolean());
        assertEquals(Set.of("base_texture", "dye_mask", "geometry"),
                intake.getAsJsonObject("assets").keySet());
        assertNoRemovedOrStrategyKeys(raw);
        assertNoForbiddenIntakeKeys(intake);
    }

    @Test
    void extraSmallTemplateContainsEveryStableIdExactlyOnceAndNoneApproved() throws Exception {
        String raw = Files.readString(BATCH);
        JsonObject batch = JsonParser.parseString(raw).getAsJsonObject();
        assertEquals("NOT_APPROVED", batch.get("template_status").getAsString());
        var records = batch.getAsJsonArray("records");
        assertEquals(9, records.size());
        List<String> ids = records.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(record -> record.getAsJsonObject("banner").get("stable_id").getAsString())
                .toList();
        assertEquals(EXTRA_SMALL_IDS, ids);
        assertEquals(9, ids.stream().distinct().count());
        for (JsonElement element : records) {
            JsonObject record = element.getAsJsonObject();
            assertEquals("NOT_APPROVED",
                    record.getAsJsonObject("approval").get("status").getAsString());
            assertEquals("INFORMATIONAL_NOT_INTAKE_APPROVAL",
                    record.getAsJsonObject("current_catalogue_state")
                            .get("approval_meaning").getAsString());
            assertEquals("complete",
                    record.getAsJsonObject("current_catalogue_state")
                            .get("content_status").getAsString());
            assertEquals("placeholder", record.get("requested_content_status").getAsString());
            assertEquals(Set.of("base_texture", "dye_mask", "geometry"),
                    record.getAsJsonObject("assets").keySet());
            assertNoForbiddenIntakeKeys(record);
        }
        assertNoRemovedOrStrategyKeys(raw);
        EXTRA_SMALL_IDS.forEach(id -> assertEquals(1, occurrences(raw, "\"" + id + "\""), id));
    }

    @Test
    void smallTemplateContainsTheSixCatalogueMembersOnceAndNoGuessedApproval() throws Exception {
        String raw = Files.readString(SMALL_BATCH);
        JsonObject batch = JsonParser.parseString(raw).getAsJsonObject();
        assertEquals("NOT_APPROVED", batch.get("template_status").getAsString());
        var records = batch.getAsJsonArray("records");
        assertEquals(6, records.size());
        List<String> ids = records.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(record -> record.getAsJsonObject("banner").get("stable_id").getAsString())
                .toList();
        assertEquals(SMALL_IDS, ids);
        assertEquals(6, ids.stream().distinct().count());
        for (JsonElement element : records) {
            JsonObject record = element.getAsJsonObject();
            JsonObject approval = record.getAsJsonObject("approval");
            JsonObject banner = record.getAsJsonObject("banner");
            JsonObject provisional = record.getAsJsonObject("current_provisional_state");
            assertEquals("NOT_APPROVED", approval.get("status").getAsString());
            assertTrue(approval.get("approved_by").getAsString().isEmpty());
            assertTrue(approval.get("approved_date").getAsString().isEmpty());
            assertTrue(banner.get("final_display_name").getAsString().isEmpty());
            assertTrue(banner.get("width_blocks").isJsonNull());
            assertTrue(banner.get("height_blocks").isJsonNull());
            assertTrue(banner.getAsJsonArray("supported_orientations").isEmpty());
            assertTrue(banner.getAsJsonArray("supported_mounts").isEmpty());
            assertTrue(banner.get("default_mount").isJsonNull());
            assertTrue(banner.get("placement_profile_id").isJsonNull());
            assertTrue(banner.get("geometry_id").isJsonNull());
            assertTrue(provisional.get("dimensions_provisional").getAsBoolean());
            assertEquals("placeholder", provisional.get("content_status").getAsString());
            assertEquals("INFORMATIONAL_NOT_INTAKE_APPROVAL",
                    provisional.get("approval_meaning").getAsString());
            assertEquals("in_progress", record.get("requested_content_status").getAsString());
            assertFalse(record.getAsJsonObject("provenance").get("original_art").getAsBoolean());
            assertFalse(record.getAsJsonObject("provenance")
                    .get("distribution_permission_confirmed").getAsBoolean());
            assertEquals(Set.of("base_texture", "dye_mask", "geometry"),
                    record.getAsJsonObject("assets").keySet());
            assertNoForbiddenIntakeKeys(record);
        }
        assertNoRemovedOrStrategyKeys(raw);
        assertFalse(raw.contains("\"overlay\""));
        SMALL_IDS.forEach(id -> assertEquals(1, occurrences(raw, "\"" + id + "\""), id));
    }

    @Test
    void templatesAreOutsideRuntimeResourcesAndRegistryDoesNotScanThem() throws Exception {
        assertFalse(GENERIC.normalize().startsWith(Path.of("src/main/resources")));
        assertFalse(BATCH.normalize().startsWith(Path.of("src/main/resources")));
        assertFalse(SMALL_BATCH.normalize().startsWith(Path.of("src/main/resources")));
        Set<String> runtimeFolders = java.util.Arrays.stream(
                        com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain.values())
                .map(com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain::folder)
                .collect(Collectors.toSet());
        assertFalse(runtimeFolders.contains("banner-final-intake"));
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(source).contains("content/banner-final-intake"), source.toString());
            }
        }
    }

    @Test
    void productionCatalogueKeepsApprovedExtraSmallAndSmallComplete() throws Exception {
        BannerScaffoldTool.Manifest manifest =
                BannerScaffoldTool.readAndValidateManifest(Path.of(BannerScaffoldTool.MANIFEST_PATH));
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, manifest.banners().size());
        assertEquals("placeholder", manifest.defaults().contentStatus());
        assertEquals(java.util.stream.Stream.concat(SMALL_IDS.stream(), EXTRA_SMALL_IDS.stream())
                        .map(id -> id.substring("britannia_mod:".length())).toList(),
                manifest.banners().stream()
                        .filter(banner -> "complete".equals(banner.contentStatus()))
                        .map(BannerScaffoldTool.BannerEntry::id).toList());
        assertEquals(0, manifest.banners().stream()
                .filter(banner -> "in_progress".equals(banner.contentStatus())).count());
        var production = DyeResolverFixtures.productionSnapshot();
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, production.banners().activeCount());
        assertEquals(20, production.banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.PLACEHOLDER).count());
        assertEquals(0, production.banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.IN_PROGRESS).count());
        assertEquals(15, production.banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.COMPLETE).count());
    }

    private static void assertNoRemovedOrStrategyKeys(String raw) {
        assertFalse(raw.contains("\"fabric_base\""));
        assertFalse(raw.contains("\"static_overlay\""));
        assertFalse(raw.contains("\"strategy\""));
        assertFalse(raw.contains("\"render_strategy\""));
        assertFalse(raw.contains("\"optional_overlay\""));
        assertFalse(raw.contains("\"legacy_overlay\""));
    }

    private static void assertNoForbiddenIntakeKeys(JsonElement element) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                assertFalse(key.contains("recipe"), entry.getKey());
                assertFalse(key.contains("craft"), entry.getKey());
                assertFalse(key.contains("pattern"), entry.getKey());
                assertFalse(key.contains("price"), entry.getKey());
                assertFalse(key.contains("npc"), entry.getKey());
                assertFalse(key.contains("shop"), entry.getKey());
                assertFalse(key.contains("nbt"), entry.getKey());
                assertFalse(key.contains("source_pigment"), entry.getKey());
                assertNoForbiddenIntakeKeys(entry.getValue());
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(FinalContentIntakeDocumentationTest::assertNoForbiddenIntakeKeys);
        }
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}

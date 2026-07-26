package com.seggellion.britannia_mod.bannerdyeing;

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
    private static final Path GUIDE =
            Path.of("docs/banner-dyeing/FINAL_CONTENT_INTAKE.md");
    private static final Path CHECKLIST =
            Path.of("docs/banner-dyeing/FINAL_CONTENT_REVIEW_CHECKLIST.md");
    private static final Path GENERIC =
            Path.of("content/banner-final-intake/intake.example.yml");
    private static final Path BATCH =
            Path.of("content/banner-final-intake/extra_small_batch.example.yml");
    private static final List<String> EXTRA_SMALL_IDS = List.of(
            "britannia_mod:road_guard",
            "britannia_mod:pale_road_guard",
            "britannia_mod:red_crosslets",
            "britannia_mod:captains_red_crosslets",
            "britannia_mod:scarlet_court",
            "britannia_mod:verdant_court",
            "britannia_mod:x_small_unnamed_01");

    @Test
    void intakeGuideChecklistReadmeAndTemplatesExist() {
        assertTrue(Files.isRegularFile(GUIDE));
        assertTrue(Files.isRegularFile(CHECKLIST));
        assertTrue(Files.isRegularFile(Path.of("content/banner-final-intake/README.md")));
        assertTrue(Files.isRegularFile(GENERIC));
        assertTrue(Files.isRegularFile(BATCH));
    }

    @Test
    void guideDefinesRequiredContractWithoutTreatingRuntimeCapabilitiesAsApproval() throws Exception {
        String guide = Files.readString(GUIDE);
        for (String heading : List.of(
                "## 1. Purpose", "## 2. Stable identity", "## 3. Required owner decisions",
                "## 4. Required visual assets", "## 5. Fabric-base rules", "## 6. Dye-mask rules",
                "## 7. Static-overlay rules", "## 8. Mount rules", "## 9. File requirements",
                "## 10. Original-art provenance", "## 11. Approval states", "## 12. Content statuses",
                "## 13. Existing-world consequences", "## 14. Integration workflow",
                "## 15. Crafting boundary")) {
            assertTrue(guide.contains(heading), heading);
        }
        assertTrue(guide.contains("Codex must not invent"));
        assertTrue(guide.contains("x_small_unnamed_01"));
        assertTrue(guide.contains("Current diagnostic placeholder textures happen to be 16×16"));
        assertTrue(guide.contains("not automatic approval"));
        assertTrue(guide.contains("Banner crafting: not approved"));
        assertTrue(guide.contains("Crafting requirements: not applicable"));
    }

    @Test
    void checklistContainsEveryRequiredAutomatedAndManualBoundary() throws Exception {
        String checklist = Files.readString(CHECKLIST);
        for (String required : List.of(
                "Stable ID verified", "Final display name approved", "Fabric base supplied",
                "Asset-to-ID mapping unambiguous", "Manifest validates", "Fabric tint test passes",
                "Overlay remains untinted", "Brass remains untinted", "Iron remains untinted",
                "Inventory", "First-person hand", "Third-person hand", "Dropped item", "Item frame",
                "Dye-preview current", "Dye-preview proposed", "Cotton", "Wool", "Linen", "Silk",
                "Wall parallel", "Wall perpendicular", "Save/reload", "Relog", "Late client tracking",
                "Resource reload", "Data reload", "Anchor break", "Child break, if applicable",
                "Pick block", "Re-place recovered item", "Product owner reviewed final appearance",
                "Crafting: not applicable — product-disabled")) {
            assertTrue(checklist.contains(required), required);
        }
    }

    @Test
    void genericTemplateIsJsonCompatibleYamlAndObviouslyUnapproved() throws Exception {
        JsonObject intake = JsonParser.parseString(Files.readString(GENERIC)).getAsJsonObject();
        assertEquals(1, intake.get("schema_version").getAsInt());
        assertEquals("NOT_APPROVED",
                intake.getAsJsonObject("approval").get("status").getAsString());
        assertEquals("britannia_mod:example_banner",
                intake.getAsJsonObject("banner").get("stable_id").getAsString());
        assertEquals("placeholder", intake.get("requested_content_status").getAsString());
        assertFalse(intake.getAsJsonObject("provenance").get("original_art").getAsBoolean());
        assertNoForbiddenIntakeKeys(intake);
    }

    @Test
    void extraSmallTemplateContainsEveryStableIdExactlyOnceAndNoneApproved() throws Exception {
        JsonObject batch = JsonParser.parseString(Files.readString(BATCH)).getAsJsonObject();
        assertEquals("NOT_APPROVED", batch.get("template_status").getAsString());
        var records = batch.getAsJsonArray("records");
        assertEquals(7, records.size());
        List<String> ids = records.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(record -> record.getAsJsonObject("banner").get("stable_id").getAsString())
                .toList();
        assertEquals(EXTRA_SMALL_IDS, ids);
        assertEquals(7, ids.stream().distinct().count());
        for (JsonElement element : records) {
            JsonObject record = element.getAsJsonObject();
            assertEquals("NOT_APPROVED",
                    record.getAsJsonObject("approval").get("status").getAsString());
            assertEquals("PROVISIONAL",
                    record.getAsJsonObject("current_provisional_state")
                            .get("approval_meaning").getAsString());
            assertEquals("placeholder", record.get("requested_content_status").getAsString());
            assertNoForbiddenIntakeKeys(record);
        }
        String raw = Files.readString(BATCH);
        EXTRA_SMALL_IDS.forEach(id -> assertEquals(1, occurrences(raw, "\"" + id + "\""), id));
    }

    @Test
    void templatesAreOutsideRuntimeResourcesAndRegistryDoesNotScanThem() throws Exception {
        assertFalse(GENERIC.normalize().startsWith(Path.of("src/main/resources")));
        assertFalse(BATCH.normalize().startsWith(Path.of("src/main/resources")));
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
    void productionCatalogueAndDefinitionsRemainUnchangedPlaceholders() throws Exception {
        BannerScaffoldTool.Manifest manifest =
                BannerScaffoldTool.readAndValidateManifest(Path.of(BannerScaffoldTool.MANIFEST_PATH));
        assertEquals(33, manifest.banners().size());
        assertEquals("placeholder", manifest.defaults().contentStatus());
        assertTrue(manifest.banners().stream().allMatch(
                banner -> banner.contentStatus() == null || "placeholder".equals(banner.contentStatus())));
        var production = DyeResolverFixtures.productionSnapshot();
        assertEquals(33, production.banners().activeCount());
        assertTrue(production.banners().activeDefinitions().stream()
                .allMatch(definition -> definition.contentStatus() == BannerContentStatus.PLACEHOLDER));
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

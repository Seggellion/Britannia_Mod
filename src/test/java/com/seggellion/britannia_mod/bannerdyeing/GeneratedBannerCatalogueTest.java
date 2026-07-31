package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionResource;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionDyeContent;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDataLoader;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryLoadResult;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotPublisher;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.tools.BannerScaffoldTool;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeneratedBannerCatalogueTest {
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/britannia_mod");
    private static RegistryLoadResult result;

    @BeforeAll
    static void loadProductionDataThroughMilestoneThreePipeline() throws Exception {
        List<DefinitionResource> resources = new ArrayList<>();
        for (RegistryDomain domain : RegistryDomain.values()) {
            Path folder = DATA_ROOT.resolve(domain.folder());
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (var paths = Files.list(folder)) {
                for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    resources.add(DefinitionResource.text(domain,
                            "britannia_mod:" + domain.folder() + "/" + path.getFileName(), Files.readString(path)));
                }
            }
        }
        RegistryDataLoader loader = new RegistryDataLoader();
        result = loader.apply(loader.prepare(resources), ValidationPolicy.DEVELOPMENT_FAIL_FAST,
                new RegistrySnapshotPublisher());
    }

    @Test
    void allCanonicalDefinitionsDecodeThroughMilestoneTwoCodec() throws Exception {
        Path folder = DATA_ROOT.resolve("banner_definitions");
        int decoded = 0;
        try (var paths = Files.list(folder)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                assertTrue(BannerDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(path))).result().isPresent(), path.toString());
                decoded++;
            }
        }
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, decoded);
    }

    @Test
    void allSupportingDefinitionsDecodeThroughMilestoneTwoCodecs() throws Exception {
        assertEquals(4, decodeFolder("fabric_materials", FabricMaterialDefinition.CODEC));
        assertEquals(4, decodeFolder("material_palettes", MaterialPalette.CODEC));
        assertEquals(7, decodeFolder("pigments", PigmentDefinition.CODEC));
        assertEquals(2, decodeFolder("banner_mounts", MountDefinition.CODEC));
        assertEquals(10, decodeFolder("placement_profiles", PlacementProfile.CODEC));
    }

    @Test
    void completeProductionDatasetPassesDevelopmentValidation() {
        assertTrue(result.published(), result.report().issues().toString());
        assertEquals(0, result.report().summary().errors());
    }

    @Test
    void everyCanonicalBannerIsActiveAndNoneDisabled() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, result.snapshot().banners().activeCount());
        assertEquals(0, result.snapshot().banners().disabledCount());
        ProductionBannerCatalogue.requireComplete(result.snapshot());
    }

    @Test
    void everyBannerHasExactlyOneLocalizationEntry() throws Exception {
        String raw = Files.readString(Path.of(BannerScaffoldTool.LOCALIZATION_PATH));
        JsonObject language = JsonParser.parseString(raw).getAsJsonObject();
        long generated = language.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("banner.britannia_mod.")).count();
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, generated);
        result.snapshot().banners().activeDefinitions().forEach(definition -> {
            assertTrue(language.has(definition.displayNameKey()), definition.displayNameKey());
            assertEquals(1, raw.split(java.util.regex.Pattern.quote("\"" + definition.displayNameKey() + "\""), -1)
                    .length - 1, definition.displayNameKey());
        });
    }

    @Test
    void everyEmittedAssetIdentifierMapsToADeclaredRuntimeFile() {
        result.snapshot().banners().activeDefinitions().forEach(definition -> {
            assertTrue(Files.isRegularFile(modelPath(definition.assets().geometry())));
            assertTrue(Files.isRegularFile(texturePath(definition.assets().baseTexture())));
            assertTrue(Files.isRegularFile(texturePath(definition.assets().dyeMask())));
        });
    }

    @Test
    void activeDefinitionsReferenceTheProductionSizeProfiles() {
        Set<String> profiles = result.snapshot().banners().activeDefinitions().stream()
                .map(definition -> definition.placementProfile().toString()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("britannia_mod:large_parallel", "britannia_mod:medium_parallel",
                "britannia_mod:small",
                "britannia_mod:extra_small", "britannia_mod:medium_perpendicular"), profiles);
    }

    @Test
    void cottonPaletteBrassAndIronCrossReferencesAreActive() {
        assertEquals(4, result.snapshot().fabricMaterials().activeCount());
        assertEquals(4, result.snapshot().materialPalettes().activeCount());
        assertEquals(7, result.snapshot().pigments().activeCount());
        assertEquals(2, result.snapshot().mounts().activeCount());
        assertEquals(10, result.snapshot().placementProfiles().activeCount());
        assertTrue(result.snapshot().fabricMaterials().activeEntries().keySet().stream()
                .anyMatch(id -> id.toString().equals("britannia_mod:cotton")));
        assertEquals(Set.of("britannia_mod:brass", "britannia_mod:iron"),
                result.snapshot().mounts().activeEntries().keySet().stream().map(Object::toString)
                        .collect(java.util.stream.Collectors.toSet()));
        ProductionDyeContent.requireComplete(result.snapshot());
    }

    @Test
    void allBannersRemainActiveAndDefaultToCottonWithDevelopmentDyeContent() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, result.snapshot().banners().activeCount());
        assertEquals(0, result.snapshot().banners().disabledCount());
        assertTrue(result.snapshot().banners().activeDefinitions().stream()
                .allMatch(banner -> banner.defaultMaterial().toString().equals("britannia_mod:cotton")));
    }

    @Test
    void developmentPaletteAndPigmentCountsAreExactAndAuthored() {
        Map<String, Integer> entries = result.snapshot().materialPalettes().activeDefinitions().stream()
                .collect(java.util.stream.Collectors.toMap(
                        palette -> palette.materialId().toString(), palette -> palette.entries().size()));
        assertEquals(Map.of(
                "britannia_mod:cotton", 8,
                "britannia_mod:wool", 8,
                "britannia_mod:linen", 8,
                "britannia_mod:silk", 9), entries);
        assertEquals(4, result.snapshot().materialPalettes().activeDefinitions().stream()
                .mapToInt(palette -> palette.pigmentOverrides().size()).sum());
        assertEquals(1, result.snapshot().materialPalettes().activeDefinitions().stream()
                .flatMap(palette -> palette.entries().stream())
                .filter(entry -> !entry.allowedPigmentTags().isEmpty()
                        || !entry.excludedPigmentTags().isEmpty()).count());
    }

    @Test
    void everyMaterialColourAndPigmentHasLocalization() throws Exception {
        JsonObject language = JsonParser.parseString(Files.readString(Path.of(BannerScaffoldTool.LOCALIZATION_PATH)))
                .getAsJsonObject();
        result.snapshot().fabricMaterials().activeDefinitions().forEach(material ->
                assertTrue(language.has(material.displayNameKey()), material.displayNameKey()));
        result.snapshot().materialPalettes().activeDefinitions().stream()
                .flatMap(palette -> palette.entries().stream()).forEach(entry ->
                        assertTrue(language.has(entry.displayNameKey()), entry.displayNameKey()));
        result.snapshot().pigments().activeDefinitions().forEach(pigment ->
                assertTrue(language.has(pigment.displayNameKey()), pigment.displayNameKey()));
    }

    @Test
    void registryEnumerationIsDocumentedLexicalOrder() {
        List<String> actual = result.snapshot().banners().activeDefinitions().stream()
                .map(definition -> definition.id().toString()).toList();
        List<String> sorted = actual.stream().sorted().toList();
        assertEquals(sorted, actual);
        assertEquals(new HashSet<>(ProductionBannerCatalogue.CANONICAL_PATHS), actual.stream()
                .map(id -> id.substring("britannia_mod:".length())).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void allAuthoritativeBannerFamiliesAreComplete() {
        List<BannerDefinition> complete = result.snapshot().banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.COMPLETE).toList();
        assertEquals(Set.of("britannia_mod:road_guard", "britannia_mod:pale_road_guard",
                        "britannia_mod:red_crosslets", "britannia_mod:captains_red_crosslets",
                        "britannia_mod:scarlet_court", "britannia_mod:verdant_court",
                        "britannia_mod:small_curtain", "britannia_mod:prosperity_standard",
                        "britannia_mod:guardian_standard",
                        "britannia_mod:verdant_grape_pennon", "britannia_mod:silver_rosette_pennon",
                        "britannia_mod:four_seals_pennon", "britannia_mod:twin_spades_pennon",
                        "britannia_mod:ankh_pennon", "britannia_mod:joined_wards",
                        "britannia_mod:tournament_medium", "britannia_mod:ceremonial_tournament",
                        "britannia_mod:iron_quarter", "britannia_mod:outer_ward",
                        "britannia_mod:ward_of_serpents", "britannia_mod:serpent_guard",
                        "britannia_mod:crossroad_guard", "britannia_mod:argent_shield",
                        "britannia_mod:silver_and_gold_pennon", "britannia_mod:star_standard",
                        "britannia_mod:ship_standard", "britannia_mod:pennon_of_silver",
                        "britannia_mod:iron_ward", "britannia_mod:iron_ward_auxiliary",
                        "britannia_mod:tournament_curtain", "britannia_mod:threefold_chain_standard",
                        "britannia_mod:iron_serpent_standard", "britannia_mod:silver_fleur_curtain",
                        "britannia_mod:gilded_trellis_curtain",
                        "britannia_mod:gilded_chevron_curtain"),
                complete.stream().map(definition -> definition.id().toString())
                        .collect(java.util.stream.Collectors.toSet()));
        BannerDefinition roadGuard = result.snapshot().banners().activeDefinitions().stream()
                .filter(definition -> definition.id().toString().equals("britannia_mod:road_guard"))
                .findFirst().orElseThrow();
        assertFalse(roadGuard.dimensions().provisional());
        assertEquals("britannia_mod:banner/road_guard/geometry", roadGuard.assets().geometry().toString());
        assertEquals("britannia_mod:banner/road_guard/base_texture", roadGuard.assets().baseTexture().toString());
        assertEquals("britannia_mod:banner/road_guard/dye_mask", roadGuard.assets().dyeMask().toString());
        assertEquals(0, result.snapshot().banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.IN_PROGRESS).count());
        assertEquals(0, result.snapshot().banners().activeDefinitions().stream()
                .filter(definition -> definition.contentStatus() == BannerContentStatus.PLACEHOLDER).count());
        assertEquals(0, result.snapshot().banners().activeDefinitions().stream()
                .filter(definition -> definition.dimensions().provisional()).count());
    }

    @Test
    void smallCurtainReplacesTheUnnamedDefinitionAtCatalogueIndexThirtyThree() throws Exception {
        Path definitions = DATA_ROOT.resolve("banner_definitions");
        assertTrue(Files.isRegularFile(definitions.resolve("small_curtain.json")));
        assertFalse(Files.exists(definitions.resolve("x_small_unnamed_01.json")));

        BannerDefinition smallCurtain = result.snapshot().banners().activeDefinitions().stream()
                .filter(definition -> definition.id().toString().equals("britannia_mod:small_curtain"))
                .findFirst().orElseThrow();
        assertEquals("banner.britannia_mod.small_curtain", smallCurtain.displayNameKey());
        assertEquals(BannerContentStatus.COMPLETE, smallCurtain.contentStatus());

        JsonObject catalogue = JsonParser.parseString(Files.readString(Path.of(
                "content/banner_catalogue.yml"))).getAsJsonObject();
        JsonObject indexThirtyThree = null;
        for (var element : catalogue.getAsJsonArray("banners")) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("index").getAsInt() == 33) {
                indexThirtyThree = entry;
            }
        }
        assertEquals("small_curtain", indexThirtyThree.get("id").getAsString());

        JsonObject language = JsonParser.parseString(Files.readString(Path.of(
                BannerScaffoldTool.LOCALIZATION_PATH))).getAsJsonObject();
        assertEquals("Small Curtain", language.get("banner.britannia_mod.small_curtain").getAsString());
        assertFalse(language.has("banner.britannia_mod.x_small_unnamed_01"));
    }

    @Test
    void statusReportListsEveryProvisionalEntryAndGateBFacts() throws Exception {
        String status = Files.readString(Path.of(BannerScaffoldTool.STATUS_PATH));
        BannerScaffoldTool.Manifest manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
        List<String> provisional = manifest.banners().stream()
                .filter(entry -> "provisional".equals(entry.nameStatus()))
                .map(BannerScaffoldTool.BannerEntry::id).toList();
        assertEquals(0, provisional.size());
        provisional.forEach(id -> assertTrue(status.contains("`" + id + "`"), id));
        assertTrue(status.contains("Catalogue target: data-derived from the canonical manifest"));
        assertTrue(status.contains("Stable identity set approved at Gate B: yes"));
        assertTrue(status.contains("Banner crafting implemented: no"));
        assertTrue(status.contains("Admin acquisition implemented: yes"));
        assertTrue(status.contains("Survival acquisition implemented: no"));
        assertTrue(status.contains("NPC/shop distribution implemented: no"));
        assertTrue(status.contains("Final display names approved: 35 of "
                + ProductionBannerCatalogue.TARGET_COUNT));
        assertTrue(status.contains("Final dimensions approved: 35 of "
                + ProductionBannerCatalogue.TARGET_COUNT));
        assertTrue(status.contains("Final artwork complete: 35 of "
                + ProductionBannerCatalogue.TARGET_COUNT));
        assertTrue(status.contains("- placeholder: 0"));
        assertTrue(status.contains("- in_progress: 0"));
        assertTrue(status.contains("- complete: 35"));
        assertTrue(status.contains("- disabled: 0"));
        assertTrue(status.contains("## Extra-small family integration"));
        assertTrue(status.contains("Intake validation: `READY_FOR_INTEGRATION`"));
        assertTrue(status.contains("Manual review: Gate E PASS for all nine authoritative hashes"));
        assertTrue(status.contains("Prosperity Standard and Guardian Standard"));
        assertTrue(status.contains("## Small-family integration"));
        assertTrue(status.contains("Manual review: Gate E PASS for all six authoritative hashes"));
        assertTrue(status.contains("## Perpendicular medium integration"));
        assertTrue(status.contains("## Parallel Medium integration"));
        assertTrue(status.contains("PARALLEL_MEDIUM_GATE_E_REVIEW.md"));
        assertTrue(status.contains("## Parallel Large integration"));
        assertTrue(status.contains("PARALLEL_LARGE_GATE_E_CLOSEOUT.md"));
        assertTrue(status.contains("Manual review: Gate E PASS for all eight authoritative hashes"));
        assertTrue(status.contains("MEDIUM_FAMILY_GATE_E_REVIEW.md"));
    }

    @Test
    void placeholderPngsAreDeterministicSixteenPixelDiagnosticAssets() throws Exception {
        Path folder = Path.of("src/main/resources/assets/britannia_mod/textures/banner/placeholder");
        try (var paths = Files.list(folder)) {
            List<Path> pngs = paths.filter(path -> path.toString().endsWith(".png")).sorted().toList();
            assertEquals(3, pngs.size());
            for (Path png : pngs) {
                BufferedImage image = ImageIO.read(png.toFile());
                assertEquals(16, image.getWidth(), png.toString());
                assertEquals(16, image.getHeight(), png.toString());
            }
        }
    }

    @Test
    void generatedResourcesRemainCommonDataWithoutClientClasses() throws Exception {
        String tool = Files.readString(Path.of(
                "tools/scaffold/com/seggellion/britannia_mod/tools/BannerScaffoldTool.java"));
        String catalogue = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/bannerdyeing/registry/ProductionBannerCatalogue.java"));
        assertFalse(tool.contains("net.minecraft.client"));
        assertFalse(catalogue.contains("net.minecraft.client"));
        assertFalse(tool.contains("DeferredRegister"));
        assertFalse(tool.contains("DyeResolver"));
        assertFalse(tool.contains("OKLab"));
    }

    private static JsonObject json(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(DATA_ROOT.resolve(relative))).getAsJsonObject();
    }

    private static <T> int decodeFolder(String folder, com.mojang.serialization.Codec<T> codec) throws Exception {
        int decoded = 0;
        try (var paths = Files.list(DATA_ROOT.resolve(folder))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path)))
                        .result().isPresent(), path.toString());
                decoded++;
            }
        }
        return decoded;
    }

    private static Path modelPath(net.minecraft.resources.ResourceLocation id) {
        return Path.of("src/main/resources/assets", id.getNamespace(), "models", id.getPath() + ".json");
    }

    private static Path texturePath(net.minecraft.resources.ResourceLocation id) {
        return Path.of("src/main/resources/assets", id.getNamespace(), "textures", id.getPath() + ".png");
    }
}

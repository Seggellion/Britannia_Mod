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
    void allThirtyThreeDefinitionsDecodeThroughMilestoneTwoCodec() throws Exception {
        Path folder = DATA_ROOT.resolve("banner_definitions");
        int decoded = 0;
        try (var paths = Files.list(folder)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                assertTrue(BannerDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(path))).result().isPresent(), path.toString());
                decoded++;
            }
        }
        assertEquals(33, decoded);
    }

    @Test
    void allSupportingDefinitionsDecodeThroughMilestoneTwoCodecs() throws Exception {
        assertEquals(4, decodeFolder("fabric_materials", FabricMaterialDefinition.CODEC));
        assertEquals(4, decodeFolder("material_palettes", MaterialPalette.CODEC));
        assertEquals(7, decodeFolder("pigments", PigmentDefinition.CODEC));
        assertEquals(2, decodeFolder("banner_mounts", MountDefinition.CODEC));
        assertEquals(5, decodeFolder("placement_profiles", PlacementProfile.CODEC));
    }

    @Test
    void completeProductionDatasetPassesDevelopmentValidation() {
        assertTrue(result.published(), result.report().issues().toString());
        assertEquals(0, result.report().summary().errors());
    }

    @Test
    void exactlyThirtyThreeBannersAreActiveAndNoneDisabled() {
        assertEquals(33, result.snapshot().banners().activeCount());
        assertEquals(0, result.snapshot().banners().disabledCount());
        ProductionBannerCatalogue.requireComplete(result.snapshot());
    }

    @Test
    void everyBannerHasExactlyOneLocalizationEntry() throws Exception {
        String raw = Files.readString(Path.of(BannerScaffoldTool.LOCALIZATION_PATH));
        JsonObject language = JsonParser.parseString(raw).getAsJsonObject();
        long generated = language.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("banner.britannia_mod.")).count();
        assertEquals(33, generated);
        result.snapshot().banners().activeDefinitions().forEach(definition -> {
            assertTrue(language.has(definition.displayNameKey()), definition.displayNameKey());
            assertEquals(1, raw.split(java.util.regex.Pattern.quote("\"" + definition.displayNameKey() + "\""), -1)
                    .length - 1, definition.displayNameKey());
        });
    }

    @Test
    void everyEmittedAssetIdentifierMapsToDeclaredPlaceholderFile() {
        result.snapshot().banners().activeDefinitions().forEach(definition -> {
            assertTrue(Files.isRegularFile(modelPath(definition.assets().geometry())));
            assertTrue(Files.isRegularFile(texturePath(definition.assets().baseTexture())));
            assertTrue(Files.isRegularFile(texturePath(definition.assets().dyeMask())));
        });
    }

    @Test
    void allFiveSizeFamilyProfilesAreReferenced() {
        Set<String> profiles = result.snapshot().banners().activeDefinitions().stream()
                .map(definition -> definition.placementProfile().toString()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("britannia_mod:placeholder_large", "britannia_mod:placeholder_medium_wall",
                "britannia_mod:placeholder_medium", "britannia_mod:placeholder_small",
                "britannia_mod:placeholder_x_small"), profiles);
    }

    @Test
    void cottonPaletteBrassAndIronCrossReferencesAreActive() {
        assertEquals(4, result.snapshot().fabricMaterials().activeCount());
        assertEquals(4, result.snapshot().materialPalettes().activeCount());
        assertEquals(7, result.snapshot().pigments().activeCount());
        assertEquals(2, result.snapshot().mounts().activeCount());
        assertEquals(5, result.snapshot().placementProfiles().activeCount());
        assertTrue(result.snapshot().fabricMaterials().activeEntries().keySet().stream()
                .anyMatch(id -> id.toString().equals("britannia_mod:cotton")));
        assertEquals(Set.of("britannia_mod:brass", "britannia_mod:iron"),
                result.snapshot().mounts().activeEntries().keySet().stream().map(Object::toString)
                        .collect(java.util.stream.Collectors.toSet()));
        ProductionDyeContent.requireComplete(result.snapshot());
    }

    @Test
    void allBannersRemainActiveAndDefaultToCottonWithDevelopmentDyeContent() {
        assertEquals(33, result.snapshot().banners().activeCount());
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
    void everyDefinitionRemainsPlaceholderWithProvisionalDimensions() {
        assertTrue(result.snapshot().banners().activeDefinitions().stream()
                .allMatch(definition -> definition.contentStatus() == BannerContentStatus.PLACEHOLDER));
        assertTrue(result.snapshot().banners().activeDefinitions().stream()
                .allMatch(definition -> definition.dimensions().provisional()));
    }

    @Test
    void statusReportListsEveryProvisionalEntryAndGateBFacts() throws Exception {
        String status = Files.readString(Path.of(BannerScaffoldTool.STATUS_PATH));
        BannerScaffoldTool.Manifest manifest = BannerScaffoldTool.readAndValidateManifest(
                Path.of(BannerScaffoldTool.MANIFEST_PATH));
        List<String> provisional = manifest.banners().stream()
                .filter(entry -> "provisional".equals(entry.nameStatus()))
                .map(BannerScaffoldTool.BannerEntry::id).toList();
        assertEquals(14, provisional.size());
        provisional.forEach(id -> assertTrue(status.contains("`" + id + "`"), id));
        assertTrue(status.contains("Catalogue target: exactly 33"));
        assertTrue(status.contains("Stable identity set approved at Gate B: yes"));
        assertTrue(status.contains("Banner crafting implemented: no"));
        assertTrue(status.contains("Admin acquisition implemented: yes"));
        assertTrue(status.contains("Survival acquisition implemented: no"));
        assertTrue(status.contains("NPC/shop distribution implemented: no"));
        assertTrue(status.contains("Final display names approved: no"));
        assertTrue(status.contains("Final dimensions approved: no"));
        assertTrue(status.contains("Final artwork complete: no"));
        assertTrue(status.contains("retained `Tournament Medium`"));
        assertTrue(status.contains("`Pennon of Silver` as the canonical scaffold labels"));
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

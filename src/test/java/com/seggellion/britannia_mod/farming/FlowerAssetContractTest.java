package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerAssetContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");
    private static final String SHARED_PARENT = "britannia_mod:block/flowers/shared/multi_plane";
    private static final List<FlowerContent> CONTENT = List.of(
            content("poppy"),
            content("snowdrop"),
            content("lily"),
            content("foxglove"),
            content("campion"),
            content("hyacinth"),
            content("orfluer")
    );

    @Test
    void registryMappingsCoverSevenSpeciesAndFourteenFlowerItems() throws IOException {
        FlowerRegistry registry = FlowerRegistry.initial();
        String itemRegistrySource = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"),
                StandardCharsets.UTF_8
        );
        assertEquals(7, CONTENT.size());
        assertEquals(7, registry.definitions().size());

        Set<ResourceLocation> itemIds = new HashSet<>();
        Set<ResourceLocation> seedIds = new HashSet<>();
        for (FlowerContent content : CONTENT) {
            FlowerDefinition definition = registry.byId(content.speciesId()).orElseThrow();
            assertEquals(content.flowerId(), definition.harvestedItemId());
            assertEquals(content.seedId(), definition.seedItemId());
            assertEquals(definition, registry.bySeedItemId(content.seedId()).orElseThrow());
            assertEquals(definition, registry.byHarvestedItemId(content.flowerId()).orElseThrow());
            assertTrue(itemRegistrySource.contains(
                    content.constantName() + " = harvestedFlowerItem(\"" + content.path() + "\")"
            ));
            assertTrue(itemRegistrySource.contains(
                    content.constantName() + "_SEEDS = flowerContentItem(\"" + content.path() + "_seeds\")"
            ));
            assertTrue(itemIds.add(content.flowerId()));
            assertTrue(itemIds.add(content.seedId()));
            assertTrue(seedIds.add(content.seedId()));
        }
        assertEquals(14, itemIds.size());
        assertEquals(7, seedIds.size());
    }

    @Test
    void localizationCreativePlacementAndGroupingTagsCoverEveryItem() throws IOException {
        JsonObject language = readJson(ASSETS.resolve("lang/en_us.json"));
        String creativeSource = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"),
                StandardCharsets.UTF_8
        );
        String seedSection = between(creativeSource, "FARMING_SEEDS = List.of(", "FARMING_PRODUCE = List.of(");
        String produceSection = creativeSource.substring(creativeSource.indexOf("FARMING_PRODUCE = List.of("));

        Set<String> expectedFlowers = new HashSet<>();
        Set<String> expectedSeeds = new HashSet<>();
        for (FlowerContent content : CONTENT) {
            assertTrue(language.has(languageKey(content.flowerId())), content.flowerId().toString());
            assertTrue(language.has(languageKey(content.seedId())), content.seedId().toString());
            assertTrue(seedSection.contains("ItemRegistry." + content.constantName() + "_SEEDS"));
            assertTrue(produceSection.contains("ItemRegistry." + content.constantName()));
            expectedFlowers.add(content.flowerId().toString());
            expectedSeeds.add(content.seedId().toString());
        }

        assertEquals(expectedFlowers, tagValues("flowers.json"));
        assertEquals(expectedSeeds, tagValues("flower_seeds.json"));
    }

    @Test
    void allLogicalStageAndItemModelsResolveThroughSharedMultiPlaneGeometry() throws IOException {
        Path flowerModels = ASSETS.resolve("models/block/flowers");
        assertEquals(49, countMatching(flowerModels, ".json"));
        assertEquals(0, countMatching(flowerModels, "_base.json"));
        assertEquals(0, countMatching(flowerModels, "_dye_mask.json"));

        JsonObject parent = readJson(resolveModel(SHARED_PARENT));
        assertEquals("minecraft:cutout", parent.get("render_type").getAsString());
        assertTrue(parent.getAsJsonArray("elements").size() >= 4);

        for (FlowerContent content : CONTENT) {
            for (int stage = 1; stage <= 7; stage++) {
                String baseTexture = "britannia_mod:block/flowers/" + content.path() + "/stage_" + stage + "_base_texture";
                String maskTexture = "britannia_mod:block/flowers/" + content.path() + "/stage_" + stage + "_dye_mask";
                JsonObject model = readJson(model(content.path(), stage));
                assertEquals(SHARED_PARENT, model.get("parent").getAsString());
                assertTrue(Files.isRegularFile(resolveModel(model.get("parent").getAsString())));
                JsonObject textures = model.getAsJsonObject("textures");
                assertEquals(Set.of("flower", "dye_mask", "particle"), textures.keySet());
                assertEquals(baseTexture, textures.get("flower").getAsString());
                assertEquals(maskTexture, textures.get("dye_mask").getAsString());
                assertEquals(baseTexture, textures.get("particle").getAsString());
                Set<String> referenced = Set.of(baseTexture, maskTexture);
                assertEquals(2, referenced.size());
                assertTrue(referenced.stream().allMatch(texture -> Files.isRegularFile(resolveTexture(texture))));
            }

            assertItemModel(content.path());
            assertItemModel(content.path() + "_seeds");
        }
    }

    @Test
    void allNinetyEightInWorldPngsHonorTheAlignedBaseMaskContract() throws IOException {
        Path flowerTextures = ASSETS.resolve("textures/block/flowers");
        assertEquals(49, countMatching(flowerTextures, "_base_texture.png"));
        assertEquals(49, countMatching(flowerTextures, "_dye_mask.png"));
        String manifest = Files.readString(PROJECT.resolve("FLOWER_ASSET_PLACEHOLDER_MANIFEST.md"));
        Set<Integer> configuredColors = new HashSet<>();
        FlowerRegistry.initial().colors().values().forEach(color -> configuredColors.add(color.color().tintValue()));

        for (FlowerContent content : CONTENT) {
            for (int stage = 1; stage <= 7; stage++) {
                Path basePath = texture(content.path(), stage, "base");
                Path maskPath = texture(content.path(), stage, "dye_mask");
                BufferedImage base = ImageIO.read(basePath.toFile());
                BufferedImage mask = ImageIO.read(maskPath.toFile());
                assertNotNull(base, basePath.toString());
                assertNotNull(mask, maskPath.toString());
                assertEquals(128, base.getWidth());
                assertEquals(128, base.getHeight());
                assertEquals(base.getWidth(), mask.getWidth());
                assertEquals(base.getHeight(), mask.getHeight());

                boolean transparentBase = false;
                boolean transparentMask = false;
                int visibleMask = 0;
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        int baseArgb = base.getRGB(x, y);
                        int maskArgb = mask.getRGB(x, y);
                        int baseAlpha = baseArgb >>> 24;
                        int maskAlpha = maskArgb >>> 24;
                        transparentBase |= baseAlpha == 0;
                        transparentMask |= maskAlpha == 0;
                        assertFalse(baseAlpha != 0 && maskAlpha != 0,
                                "Base/mask alpha overlap at " + content.path() + " stage " + stage + " (" + x + "," + y + ")");
                        if (maskAlpha != 0) {
                            visibleMask++;
                            int rgb = maskArgb & 0xFFFFFF;
                            int red = (rgb >>> 16) & 0xFF;
                            int green = (rgb >>> 8) & 0xFF;
                            int blue = rgb & 0xFF;
                            assertEquals(red, green);
                            assertEquals(green, blue);
                            assertFalse(configuredColors.contains(rgb));
                        }
                    }
                }
                assertTrue(transparentBase);
                assertTrue(transparentMask);
                if (stage <= 2) {
                    assertEquals(0, visibleMask);
                    assertTrue(manifest.contains("| `britannia_mod:" + content.path() + "` | " + stage));
                    assertTrue(manifest.contains("Yes - no bloom at this stage"));
                } else {
                    assertTrue(visibleMask > 0);
                }
                assertTrue(manifest.contains(PROJECT.relativize(basePath).toString().replace('\\', '/')));
                assertTrue(manifest.contains(PROJECT.relativize(maskPath).toString().replace('\\', '/')));
                assertFalse(isMissingTexturePattern(base));
                assertFalse(isMissingTexturePattern(mask));
            }
        }
    }

    @Test
    void manifestAndHashLedgerIntentionallyCoverEveryGeneratedPlaceholder() throws IOException {
        String manifest = Files.readString(PROJECT.resolve("FLOWER_ASSET_PLACEHOLDER_MANIFEST.md"));
        assertTrue(manifest.contains("All current flower assets are technical placeholders and are not final owner-approved artwork."));
        assertTrue(manifest.contains("Do not add a third in-world texture."));
        assertTrue(manifest.contains("Do not bake a species colour into dye_mask."));
        assertTrue(manifest.contains("Do not overwrite approved replacement artwork with the placeholder generator."));
        assertTrue(manifest.contains("| Species registry ID | Stage | model_path | base_texture_path | dye_mask_texture_path |"));

        JsonObject ledger = readJson(PROJECT.resolve("tools/flower_placeholder_hashes.json"));
        JsonObject files = ledger.getAsJsonObject("files");
        assertEquals(182, files.size());
        assertTrue(files.has("src/main/resources/assets/britannia_mod/models/item/skinning_knife.json"));
        assertTrue(files.has("src/main/resources/assets/britannia_mod/textures/item/skinning_knife.png"));
        assertTrue(files.has("src/main/resources/data/britannia_mod/tags/item/skinning_knives.json"));
        assertFalse(files.has("src/main/resources/data/britannia_mod/tags/items/skinning_knives.json"));
        for (String relative : files.keySet()) {
            Path path = PROJECT.resolve(relative);
            assertTrue(Files.isRegularFile(path), relative);
            assertEquals(files.get(relative).getAsString(), sha256(path), relative);
        }
    }

    private static FlowerContent content(String path) {
        return new FlowerContent(
                path,
                path.toUpperCase(java.util.Locale.ROOT),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", path),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", path),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", path + "_seeds")
        );
    }

    private static void assertItemModel(String itemPath) throws IOException {
        Path modelPath = ASSETS.resolve("models/item/" + itemPath + ".json");
        JsonObject model = readJson(modelPath);
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertEquals("britannia_mod:item/flowers/" + itemPath, texture);
        assertTrue(Files.isRegularFile(resolveTexture(texture)));
        assertNotNull(ImageIO.read(resolveTexture(texture).toFile()));
    }

    private static Set<String> tagValues(String file) throws IOException {
        JsonArray values = readJson(RESOURCES.resolve("data/britannia_mod/tags/items/" + file)).getAsJsonArray("values");
        Set<String> result = new HashSet<>();
        values.forEach(value -> assertTrue(result.add(value.getAsString())));
        return result;
    }

    private static Path model(String species, int stage) {
        return ASSETS.resolve("models/block/flowers/" + species + "/stage_" + stage + ".json");
    }

    private static Path texture(String species, int stage, String pass) {
        String suffix = pass.equals("base") ? "base_texture" : "dye_mask";
        return ASSETS.resolve("textures/block/flowers/" + species + "/stage_" + stage + "_" + suffix + ".png");
    }

    private static String languageKey(ResourceLocation id) {
        return "item." + id.getNamespace() + "." + id.getPath();
    }

    private static Path resolveModel(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return RESOURCES.resolve("assets").resolve(location.getNamespace()).resolve("models")
                .resolve(location.getPath() + ".json");
    }

    private static Path resolveTexture(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return RESOURCES.resolve("assets").resolve(location.getNamespace()).resolve("textures")
                .resolve(location.getPath() + ".png");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static long countMatching(Path root, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("stage_"))
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .count();
        }
    }

    private static boolean isMissingTexturePattern(BufferedImage image) {
        Set<Integer> opaqueColors = new HashSet<>();
        boolean fullyOpaque = true;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                fullyOpaque &= (argb >>> 24) == 255;
                if ((argb >>> 24) != 0) {
                    opaqueColors.add(argb & 0xFFFFFF);
                }
            }
        }
        return fullyOpaque && !opaqueColors.isEmpty()
                && opaqueColors.stream().allMatch(color -> color == 0x000000 || color == 0xF800F8 || color == 0xFF00FF);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static String sha256(Path path) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }

    private record FlowerContent(
            String path,
            String constantName,
            ResourceLocation speciesId,
            ResourceLocation flowerId,
            ResourceLocation seedId
    ) {
    }
}

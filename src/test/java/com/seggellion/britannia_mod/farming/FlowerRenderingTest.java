package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.client.renderer.FlowerVisualModels;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerRenderingTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final float EPSILON = 0.0000001F;

    @Test
    void allSevenSpeciesAndStagesResolveExactlyFortyNineCanonicalModels() {
        assertEquals(7, FlowerVisualModels.supportedSpecies().size());
        assertEquals(49, FlowerVisualModels.allModelLocations().size());
        assertEquals(49, FlowerVisualModels.allModelLocations().stream().distinct().count());

        for (ResourceLocation species : FlowerVisualModels.supportedSpecies()) {
            for (int stage = 1; stage <= 7; stage++) {
                FlowerVisualModels.StageModel model = FlowerVisualModels.stageModel(species, stage);
                String prefix = "block/flowers/" + species.getPath() + "/stage_" + stage;
                assertEquals(prefix, model.canonicalId().getPath());
                assertEquals(prefix + "_base_texture", model.baseTextureId().getPath());
                assertEquals(prefix + "_dye_mask", model.dyeMaskTextureId().getPath());
            }
        }
    }

    @Test
    void exactStageSelectionIncludesNormalAndMasteryPoppyModels() {
        for (ResourceLocation species : FlowerVisualModels.supportedSpecies()) {
            for (int stage = 1; stage <= 7; stage++) {
                FlowerVisualModels.RenderPlan plan = FlowerVisualModels.resolve(species, stage, 0x123456);
                assertEquals(species, plan.visualSpecies());
                assertEquals(stage, plan.visualStage());
                assertFalse(plan.fallbackSpecies());
                assertFalse(plan.fallbackStage());
            }
        }
        assertTrue(FlowerVisualModels.resolve(FlowerRegistry.POPPY, 6, 0x123456)
                .model().canonicalId().getPath().endsWith("stage_6"));
        assertTrue(FlowerVisualModels.resolve(FlowerRegistry.POPPY, 7, 0x123456)
                .model().canonicalId().getPath().endsWith("stage_7"));
    }

    @Test
    void invalidStageAndUnknownSpeciesFallbacksAreDeterministicAndPure() {
        FlowerVisualModels.RenderPlan low = FlowerVisualModels.resolve(FlowerRegistry.LILY, -9, 0x123456);
        FlowerVisualModels.RenderPlan high = FlowerVisualModels.resolve(FlowerRegistry.LILY, 42, 0x123456);
        assertEquals(1, low.visualStage());
        assertEquals(7, high.visualStage());
        assertTrue(low.fallbackStage());
        assertTrue(high.fallbackStage());

        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("britannia_mod", "future_flower");
        FlowerVisualModels.RenderPlan first = FlowerVisualModels.resolve(unknown, 5, 0x123456);
        FlowerVisualModels.RenderPlan second = FlowerVisualModels.resolve(unknown, 5, 0x123456);
        assertEquals(first, second);
        assertEquals(unknown, first.savedSpecies());
        assertEquals(FlowerRegistry.POPPY, first.visualSpecies());
        assertEquals(1, first.visualStage());
        assertEquals(FlowerVisualModels.Tint.WHITE, first.dyeMaskTint());
        assertTrue(first.fallbackSpecies());
    }

    @Test
    void colorConversionPreservesEveryEightBitChannelAndOpaqueAlpha() {
        for (int rgb : new int[]{0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x123456}) {
            assertChannels(rgb, FlowerVisualModels.Tint.fromRgb(rgb));
        }
        for (FlowerColorDefinition color : FlowerRegistry.initial().colors().values()) {
            assertChannels(color.color().tintValue(), FlowerVisualModels.Tint.fromRgb(color.color().tintValue()));
        }

        FlowerVisualModels.RenderPlan arbitrarySavedTint = FlowerVisualModels.resolve(
                FlowerRegistry.POPPY, 4, 0x123456
        );
        assertChannels(0x123456, arbitrarySavedTint.dyeMaskTint());
        assertFalse(arbitrarySavedTint.fallbackTint());
        assertEquals(FlowerVisualModels.Tint.WHITE, arbitrarySavedTint.baseTint());
    }

    @Test
    void invalidNumericTintUsesSpeciesFallbackWithoutRewritingInputs() {
        FlowerDefinition poppy = FlowerRegistry.initial().byId(FlowerRegistry.POPPY).orElseThrow();
        int expected = FlowerRegistry.initial().fallbackColor(poppy).tintValue();
        FlowerVisualModels.RenderPlan invalid = FlowerVisualModels.resolve(FlowerRegistry.POPPY, 4, -1);
        assertEquals(-1, invalid.savedTint());
        assertEquals(expected, invalid.visualTint());
        assertChannels(expected, invalid.dyeMaskTint());
        assertTrue(invalid.fallbackTint());
    }

    @Test
    void canonicalStageAssetGraphUsesOneModelAndExactlyTwoImageTextures() throws IOException {
        Path models = PROJECT.resolve("src/main/resources/assets/britannia_mod/models");
        Path flowerModels = models.resolve("block/flowers");
        Path sharedModel = models.resolve("block/flowers/shared/multi_plane.json");
        assertTrue(Files.isRegularFile(sharedModel));
        JsonObject shared = json(sharedModel);
        shared.getAsJsonArray("elements").forEach(element ->
                element.getAsJsonObject().getAsJsonObject("faces").entrySet().forEach(face ->
                        assertEquals(0, face.getValue().getAsJsonObject().get("tintindex").getAsInt())));
        Set<Path> resolvedStageModels = new HashSet<>();
        for (ResourceLocation species : FlowerVisualModels.supportedSpecies()) {
            for (int stage = 1; stage <= 7; stage++) {
                FlowerVisualModels.StageModel stageModel = FlowerVisualModels.stageModel(species, stage);
                Path modelPath = models.resolve(stageModel.canonicalId().getPath() + ".json").normalize();
                assertTrue(resolvedStageModels.add(modelPath));
                JsonObject model = json(modelPath);
                String baseTexture = "britannia_mod:block/flowers/" + species.getPath()
                        + "/stage_" + stage + "_base_texture";
                String maskTexture = "britannia_mod:block/flowers/" + species.getPath()
                        + "/stage_" + stage + "_dye_mask";
                assertEquals("britannia_mod:block/flowers/shared/multi_plane", model.get("parent").getAsString());
                JsonObject textures = model.getAsJsonObject("textures");
                assertEquals(Set.of("flower", "dye_mask", "particle"), textures.keySet());
                assertEquals(baseTexture, textures.get("flower").getAsString());
                assertEquals(maskTexture, textures.get("dye_mask").getAsString());
                assertEquals(baseTexture, textures.get("particle").getAsString());
                assertEquals(2, Set.of(
                        textures.get("flower").getAsString(),
                        textures.get("dye_mask").getAsString()
                ).size());
            }
        }

        Set<Path> actualStageModels = new HashSet<>();
        try (Stream<Path> paths = Files.walk(flowerModels)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(flowerModels.resolve("shared")))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> actualStageModels.add(path.normalize()));
        }
        assertEquals(resolvedStageModels, actualStageModels);
        assertEquals(49, actualStageModels.size());
        assertTrue(actualStageModels.stream().noneMatch(path ->
                path.getFileName().toString().endsWith("_base.json")
                        || path.getFileName().toString().endsWith("_dye_mask.json")));
    }

    @Test
    void renderPlanAndRendererEnforceTwoCutoutPassesWithOneTransform() throws IOException {
        assertEquals(List.of(FlowerVisualModels.Pass.BASE, FlowerVisualModels.Pass.DYE_MASK),
                FlowerVisualModels.passOrder());
        String renderer = source("client/renderer/FlowerBlockEntityRenderer.java");
        int base = renderer.indexOf("plan.baseTint(), packedLight, packedOverlay");
        int mask = renderer.indexOf("plan.dyeMaskTint(), packedLight, packedOverlay");
        assertTrue(base >= 0 && mask > base);
        assertEquals(2, count(renderer, "renderPass(minecraft"));
        assertEquals(2, count(renderer, "resolved.model(),"));
        assertEquals(1, count(renderer, "resolveModel(modelManager, plan.model()"));
        assertTrue(renderer.contains("RenderType.entityCutout(textureFile(resolved.assets().dyeMaskTextureId()))"));
        assertTrue(renderer.contains("new TextureRemappingVertexConsumer("));
        assertTrue(renderer.contains("delegate.setUv(relativeU, relativeV)"));
        assertFalse(renderer.contains("dyeMaskModel"));
        assertEquals(1, count(renderer, "bufferSource.getBuffer(RenderType.cutout())"));
        assertEquals(1, count(renderer, "bufferSource.getBuffer(RenderType.entityCutout("));
        assertTrue(renderer.indexOf("bufferSource.getBuffer(RenderType.cutout())")
                < renderer.indexOf("bufferSource.getBuffer(RenderType.entityCutout("));
        assertEquals(1, count(renderer, "poseStack.pushPose()"));
        assertEquals(1, count(renderer, "poseStack.popPose()"));
        assertEquals(1, count(renderer, "poseStack.mulPose("));
        assertFalse(renderer.contains("RenderType.translucent"));
    }

    @Test
    void modelRegistrationReloadAndDedicatedServerBoundariesStayClientOnly() throws IOException {
        String setup = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/ClientModSetup.java"), StandardCharsets.UTF_8);
        String renderer = source("client/renderer/FlowerBlockEntityRenderer.java");
        String resolver = source("client/renderer/FlowerVisualModels.java");
        assertTrue(setup.contains("value = Dist.CLIENT"));
        assertTrue(setup.contains("FlowerVisualModels.allModelLocations()"));
        assertEquals(1, count(setup, "BlockEntityRegistry.FLOWER_BLOCK_BE.get(), FlowerBlockEntityRenderer::new"));
        assertTrue(setup.contains("FlowerVisualModels.onModelsReloaded()"));
        assertTrue(setup.contains("FlowerBlockEntityRenderer.onModelsReloaded()"));
        assertTrue(renderer.contains("modelManager.getModel("));
        assertFalse(renderer.contains("static final BakedModel"));
        assertFalse(resolver.contains("ModelPair"));
        assertFalse(resolver.contains("WeightedFlowerColorSelector"));
        assertFalse(renderer.contains("WeightedFlowerColorSelector"));

        for (Path common : commonFlowerSources()) {
            String text = Files.readString(common, StandardCharsets.UTF_8);
            assertFalse(text.contains("net.minecraft.client"), common.toString());
            assertFalse(text.contains("FlowerBlockEntityRenderer"), common.toString());
            assertFalse(text.contains("FlowerVisualModels"), common.toString());
        }
    }

    @Test
    void synchronizedObserversDeriveIdenticalModelsTintsAndFallbacks() {
        FlowerVisualModels.RenderPlan first = FlowerVisualModels.resolve(FlowerRegistry.HYACINTH, 7, 0x374E82);
        FlowerVisualModels.RenderPlan second = FlowerVisualModels.resolve(FlowerRegistry.HYACINTH, 7, 0x374E82);
        assertEquals(first, second);
        assertEquals(first.model(), second.model());
        assertEquals(first.dyeMaskTint(), second.dyeMaskTint());

        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("britannia_mod", "missing");
        assertEquals(
                FlowerVisualModels.resolve(unknown, 99, 0xABCDEF),
                FlowerVisualModels.resolve(unknown, 99, 0xABCDEF)
        );
    }

    private static void assertChannels(int rgb, FlowerVisualModels.Tint tint) {
        assertEquals(((rgb >> 16) & 0xFF) / 255.0F, tint.red(), EPSILON);
        assertEquals(((rgb >> 8) & 0xFF) / 255.0F, tint.green(), EPSILON);
        assertEquals((rgb & 0xFF) / 255.0F, tint.blue(), EPSILON);
        assertEquals(1.0F, tint.alpha(), EPSILON);
    }

    private static JsonObject json(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path.toString());
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static List<Path> commonFlowerSources() {
        return List.of(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/block/FlowerBlock.java"),
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/block/entity/FlowerBlockEntity.java"),
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/registry/BlockEntityRegistry.java"),
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/farming/FlowerPersistentState.java")
        );
    }

    private static int count(String text, String needle) {
        int matches = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            matches++;
            index += needle.length();
        }
        return matches;
    }
}

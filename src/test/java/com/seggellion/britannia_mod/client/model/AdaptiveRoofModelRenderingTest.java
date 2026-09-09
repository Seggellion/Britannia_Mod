package com.seggellion.britannia_mod.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AdaptiveRoofModelRenderingTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", "."));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    @Test
    void acquiredGeometryIsAStandardsCompliantLowerHalfWithoutAnInternalTopFace() {
        assertEquals(0.0F, AdaptiveRoofBakedModel.MIN_PIXEL);
        assertEquals(8.0F, AdaptiveRoofBakedModel.LOWER_HALF_MAX_Y_PIXEL);
        assertEquals(16.0F, AdaptiveRoofBakedModel.MAX_PIXEL);
        assertEquals(Set.of(
                        Direction.DOWN,
                        Direction.NORTH,
                        Direction.SOUTH,
                        Direction.WEST,
                        Direction.EAST),
                Set.copyOf(AdaptiveRoofBakedModel.ACQUIRED_FACES));
        assertFalse(AdaptiveRoofBakedModel.ACQUIRED_FACES.contains(Direction.UP));
    }

    @Test
    void faceBakeryProducesMatchingWindingBoundsNormalsAndSprite() {
        try (TestSprite testSprite = new TestSprite()) {
            for (Direction face : AdaptiveRoofBakedModel.ACQUIRED_FACES) {
                BakedQuad quad = AdaptiveRoofBakedModel.bakeFace(face, testSprite.sprite());

                assertEquals(face, quad.getDirection());
                assertEquals(face, FaceBakery.calculateFacing(quad.getVertices()));
                assertSame(testSprite.sprite(), quad.getSprite());
                assertTrue(quad.isShade());
                assertTrue(quad.hasAmbientOcclusion());
                assertFaceBoundsAndNormals(quad, face);
            }
        }
    }

    @Test
    void wrapperLeavesTheOriginalModelUntouchedWithoutAcquiredModelData() {
        List<BakedQuad> originalQuads = new ArrayList<>();
        AdaptiveRoofBakedModel model = new AdaptiveRoofBakedModel(stubModel(originalQuads));

        List<BakedQuad> actual = model.getQuads(
                null, Direction.DOWN, RandomSource.create(1L), ModelData.EMPTY, null);

        assertSame(originalQuads, actual);
    }

    @Test
    void blockEntityModelDataUsesTheImmutableTextureId() {
        assertTrue(AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY.test(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/stone")));
        assertFalse(AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY.test(null));
    }

    @Test
    void acquiredModelDataResolvesTheRequestedSpriteAndCachesTheBakedQuad() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                "britannia_mod", "block/structure/plaster_wood_foundation");
        try (TestSprite testSprite = new TestSprite()) {
            List<ResourceLocation> resolvedTextures = new ArrayList<>();
            AdaptiveRoofBakedModel model = new AdaptiveRoofBakedModel(
                    stubModel(List.of()),
                    location -> {
                        resolvedTextures.add(location);
                        return testSprite.sprite();
                    });
            ModelData modelData = ModelData.of(
                    AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY, texture);

            BakedQuad first = model.getQuads(
                    null, Direction.NORTH, RandomSource.create(2L), modelData, null).getFirst();
            BakedQuad second = model.getQuads(
                    null, Direction.NORTH, RandomSource.create(3L), modelData, null).getFirst();

            assertEquals(List.of(texture), resolvedTextures);
            assertSame(first, second);
            assertSame(testSprite.sprite(), first.getSprite());
        }
    }

    @Test
    void sandstoneBrickUsesExistingModelAssetsAndClearingRemovesEveryAcquiredFace() throws Exception {
        Path assets = PROJECT.resolve("src/main/resources/assets/britannia_mod");
        String canonical = "britannia_mod:block/structure/sandstone/custom_sandstone_brick_0";
        try (var reader = Files.newBufferedReader(assets.resolve("models/item/custom_sandstone_brick.json"))) {
            assertEquals(canonical, JsonParser.parseReader(reader).getAsJsonObject().get("parent").getAsString());
        }
        // The base item chooses variant zero; the existing alternate/top-row family must
        // continue to resolve too. Load real PNG pixels rather than a dummy fallback sprite.
        for (String row : List.of("", "top_")) {
            for (int variant = 0; variant < 4; variant++) {
                String path = "block/structure/sandstone/custom_sandstone_brick_" + row + variant;
                ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
                try (var reader = Files.newBufferedReader(assets.resolve("models/" + path + ".json"))) {
                    assertEquals(texture.toString(), JsonParser.parseReader(reader).getAsJsonObject()
                            .getAsJsonObject("textures").get("all").getAsString());
                }
                try (var input = Files.newInputStream(assets.resolve("textures/" + path + ".png"));
                     TestSprite sprite = new TestSprite(texture, NativeImage.read(input))) {
                    List<ResourceLocation> resolved = new ArrayList<>();
                    List<BakedQuad> original = List.of();
                    AdaptiveRoofBakedModel model = new AdaptiveRoofBakedModel(stubModel(original), id -> {
                        resolved.add(id);
                        assertEquals(texture, id);
                        return sprite.sprite();
                    });
                    ModelData applied = ModelData.of(AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY, texture);
                    for (Direction face : AdaptiveRoofBakedModel.ACQUIRED_FACES) {
                        List<BakedQuad> quads = model.getQuads(null, face, RandomSource.create(1), applied, null);
                        assertEquals(1, quads.size());
                        assertSame(sprite.sprite(), quads.getFirst().getSprite());
                        assertEquals(texture, quads.getFirst().getSprite().contents().name());
                        assertSame(original, model.getQuads(null, face, RandomSource.create(1), ModelData.EMPTY, null));
                    }
                    assertEquals(List.of(texture), resolved);
                }
            }
        }
    }

    @Test
    void typeBasedRuleWrapsTopOnlyTerrainModelsButNeverInventoryOrForeignModels()
            throws Exception {
        TopOnlySlabBlock adaptiveRoof = new TopOnlySlabBlock(BlockBehaviour.Properties.of());
        ResourceLocation futureRoof = ResourceLocation.fromNamespaceAndPath(
                "britannia_mod", "future_top_only_roof");

        assertTrue(AdaptiveRoofClientModels.isAdaptiveRoofBlockModel(
                new ModelResourceLocation(
                        futureRoof, "type=top,supports_lantern=true,waterlogged=false"),
                ignored -> adaptiveRoof));
        assertFalse(AdaptiveRoofClientModels.isAdaptiveRoofBlockModel(
                ModelResourceLocation.inventory(futureRoof),
                ignored -> {
                    throw new AssertionError("inventory model performed a block lookup");
                }));
        assertFalse(AdaptiveRoofClientModels.isAdaptiveRoofBlockModel(
                new ModelResourceLocation(
                        ResourceLocation.fromNamespaceAndPath("other_mod", "roof"), "normal"),
                ignored -> adaptiveRoof));
        assertFalse(AdaptiveRoofClientModels.isAdaptiveRoofBlockModel(
                new ModelResourceLocation(
                        ResourceLocation.fromNamespaceAndPath("britannia_mod", "stone"), "normal"),
                ignored -> null));

        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/client/model/AdaptiveRoofClientModels.java"));
        assertTrue(source.contains("instanceof TopOnlySlabBlock"));
        assertFalse(source.contains("ADAPTIVE_ROOF_IDS"));
    }

    @Test
    void acquiredStateUsesAFullOcclusionShapeForTerrainCullingAndLighting() {
        TopOnlySlabBlock block = new TopOnlySlabBlock(BlockBehaviour.Properties.of());
        BlockState topOnly = block.defaultBlockState();
        BlockState acquired = topOnly.setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);

        assertEquals(0.5D, topOnly.getOcclusionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().minY);
        assertEquals(0.0D, acquired.getOcclusionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().minY);
        assertEquals(1.0D, acquired.getOcclusionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().maxY);
    }

    @Test
    void manualBlockEntityRenderPathAndFullbrightWorkaroundsAreAbsent() throws Exception {
        Path main = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
        String setup = Files.readString(main.resolve("ClientModSetup.java"));
        String entity = Files.readString(
                main.resolve("block/structure/AdaptiveRoofBlockEntity.java"));
        String model = Files.readString(
                main.resolve("client/model/AdaptiveRoofBakedModel.java"));

        assertFalse(Files.exists(
                main.resolve("block/renderer/AdaptiveRoofRenderer.java")));
        assertFalse(setup.contains("registerBlockEntityRenderer(BlockEntityRegistry.ADAPTIVE_ROOF"));
        assertTrue(entity.contains("ModelData.of(BOTTOM_TEXTURE_MODEL_PROPERTY, bottomTexture)"));
        assertTrue(entity.contains("requestModelDataUpdate()"));
        assertTrue(model.contains("FaceBakery"));
        assertFalse(model.contains("LevelRenderer.getLightColor"));
        assertFalse(model.contains("15728880"));
        assertFalse(model.contains("FULL_BRIGHT"));
    }

    private static BakedModel stubModel(List<BakedQuad> quads) {
        return new BakedModel() {
            @Override
            public List<BakedQuad> getQuads(
                    BlockState state, Direction side, RandomSource random) {
                return quads;
            }

            @Override
            public boolean useAmbientOcclusion() {
                return true;
            }

            @Override
            public boolean isGui3d() {
                return true;
            }

            @Override
            public boolean usesBlockLight() {
                return true;
            }

            @Override
            public boolean isCustomRenderer() {
                return false;
            }

            @Override
            public TextureAtlasSprite getParticleIcon() {
                return null;
            }

            @Override
            public ItemOverrides getOverrides() {
                return ItemOverrides.EMPTY;
            }
        };
    }

    private static void assertFaceBoundsAndNormals(BakedQuad quad, Direction face) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        int expectedNormal = packedNormal(face);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            assertEquals(expectedNormal, vertices[offset + 7] & 0x00FF_FFFF);
        }

        assertEquals(face == Direction.EAST ? 1.0F : 0.0F, minX);
        assertEquals(0.0F, minY);
        assertEquals(face == Direction.SOUTH ? 1.0F : 0.0F, minZ);
        assertEquals(face == Direction.WEST ? 0.0F : 1.0F, maxX);
        assertEquals(face == Direction.DOWN ? 0.0F : 0.5F, maxY);
        assertEquals(face == Direction.NORTH ? 0.0F : 1.0F, maxZ);
    }

    private static int packedNormal(Direction face) {
        int x = ((byte) (face.getStepX() * 127)) & 0xFF;
        int y = ((byte) (face.getStepY() * 127)) & 0xFF;
        int z = ((byte) (face.getStepZ() * 127)) & 0xFF;
        return x | (y << 8) | (z << 16);
    }

    private static final class TestSprite implements AutoCloseable {
        private final SpriteContents contents;
        private final TextureAtlasSprite sprite;

        private TestSprite() {
            this(ResourceLocation.withDefaultNamespace("block/test"), new NativeImage(16, 16, true));
        }

        private TestSprite(ResourceLocation texture, NativeImage image) {
            contents = new SpriteContents(
                    texture,
                    new FrameSize(image.getWidth(), image.getHeight()),
                    image,
                    ResourceMetadata.EMPTY);
            sprite = new TextureAtlasSprite(
                    TextureAtlas.LOCATION_BLOCKS, contents, image.getWidth(), image.getHeight(), 0, 0) { };
        }

        private TextureAtlasSprite sprite() {
            return sprite;
        }

        @Override
        public void close() {
            contents.close();
        }
    }
}

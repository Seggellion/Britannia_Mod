package com.seggellion.britannia_mod.client.model;

import com.mojang.math.Transformation;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Adds the acquired lower half to a roof as ordinary solid terrain quads. */
public final class AdaptiveRoofBakedModel extends BakedModelWrapper<BakedModel> {
    static final float MIN_PIXEL = 0.0F;
    static final float MAX_PIXEL = 16.0F;
    static final float LOWER_HALF_MAX_Y_PIXEL = 8.0F;
    static final List<Direction> ACQUIRED_FACES = List.of(
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST);

    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final SimpleModelState IDENTITY =
            new SimpleModelState(Transformation.identity());

    /** Recreated with this baked-model instance on every resource reload. */
    private final Map<ResourceLocation, Map<Direction, BakedQuad>> quadCache =
            new ConcurrentHashMap<>();
    private final Function<ResourceLocation, TextureAtlasSprite> spriteResolver;

    public AdaptiveRoofBakedModel(BakedModel originalModel) {
        this(originalModel, texture -> Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(texture));
    }

    AdaptiveRoofBakedModel(
            BakedModel originalModel,
            Function<ResourceLocation, TextureAtlasSprite> spriteResolver) {
        super(originalModel);
        this.spriteResolver = spriteResolver;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType) {
        List<BakedQuad> original = originalModel.getQuads(
                state, side, random, modelData, renderType);
        ResourceLocation texture = modelData.get(
                AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY);

        // The upper face would be internal against the existing top-slab model. Quads are
        // returned in their directional cull buckets so chunk rendering applies normal block
        // face culling, neighbor light sampling, directional shade, and ambient occlusion.
        if (texture == null || side == null || !ACQUIRED_FACES.contains(side)) {
            return original;
        }

        BakedQuad acquired = quadCache
                .computeIfAbsent(texture, this::bakeLowerHalf)
                .get(side);
        if (original.isEmpty()) {
            return List.of(acquired);
        }

        List<BakedQuad> combined = new ArrayList<>(original.size() + 1);
        combined.addAll(original);
        combined.add(acquired);
        return List.copyOf(combined);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ModelData modelData) {
        ModelData resolved = originalModel.getModelData(level, pos, state, modelData);
        ResourceLocation texture = modelData.get(
                AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY);
        if (texture != null && !resolved.has(
                AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY)) {
            return resolved.derive()
                    .with(AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY, texture)
                    .build();
        }
        return resolved;
    }

    private Map<Direction, BakedQuad> bakeLowerHalf(ResourceLocation texture) {
        TextureAtlasSprite sprite = spriteResolver.apply(texture);
        Map<Direction, BakedQuad> quads = new EnumMap<>(Direction.class);
        for (Direction face : ACQUIRED_FACES) {
            quads.put(face, bakeFace(face, sprite));
        }
        return Map.copyOf(quads);
    }

    static BakedQuad bakeFace(Direction face, TextureAtlasSprite sprite) {
        float[] uv = face == Direction.DOWN
                ? new float[]{0.0F, 0.0F, 16.0F, 16.0F}
                : new float[]{0.0F, 8.0F, 16.0F, 16.0F};
        BlockElementFace elementFace = new BlockElementFace(
                null, -1, "", new BlockFaceUV(uv, 0));
        return FACE_BAKERY.bakeQuad(
                new Vector3f(MIN_PIXEL, MIN_PIXEL, MIN_PIXEL),
                new Vector3f(MAX_PIXEL, LOWER_HALF_MAX_Y_PIXEL, MAX_PIXEL),
                elementFace,
                sprite,
                face,
                IDENTITY,
                null,
                true);
    }
}

package com.seggellion.britannia_mod.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies the structure envelope to the owner's complete rotated model after baking.
 *
 * <p>The source's actual rotated bounds are 48 x 44.815764 x 27.531494 voxels. Transforming
 * final vertices (instead of individual cube bounds) preserves every pivot and angle while fitting
 * the required one-block depth and moving the true lowest rotated vertex onto the ground.
 */
public final class MarketStallNormalizedModel implements BakedModel {
    static final float DEPTH_MIN = -0.71875F;
    static final float DEPTH_SCALE = 0.5811526F;
    static final float GROUND_OFFSET = 0.8009853F;

    private final BakedModel original;

    public MarketStallNormalizedModel(BakedModel original) {
        this.original = original;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            @NotNull RandomSource random,
            @NotNull ModelData data,
            @Nullable RenderType renderType) {
        return normalize(original.getQuads(state, side, random, data, renderType), state);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(
            @Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random) {
        return normalize(original.getQuads(state, side, random), state);
    }

    private List<BakedQuad> normalize(List<BakedQuad> source, @Nullable BlockState state) {
        if (source.isEmpty()) {
            return source;
        }
        Direction facing = state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        List<BakedQuad> normalized = new ArrayList<>(source.size());
        for (BakedQuad quad : source) {
            normalized.add(normalizeQuad(quad, facing));
        }
        return normalized;
    }

    private BakedQuad normalizeQuad(BakedQuad quad, Direction facing) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]) + GROUND_OFFSET;
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            switch (facing) {
                case EAST -> x = 1.0F - normalizeDepth(1.0F - x);
                case SOUTH -> z = 1.0F - normalizeDepth(1.0F - z);
                case WEST -> x = normalizeDepth(x);
                default -> z = normalizeDepth(z);
            }
            vertices[offset] = Float.floatToRawIntBits(x);
            vertices[offset + 1] = Float.floatToRawIntBits(y);
            vertices[offset + 2] = Float.floatToRawIntBits(z);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(),
                quad.getSprite(), quad.isShade());
    }

    private static float normalizeDepth(float value) {
        return (value - DEPTH_MIN) * DEPTH_SCALE;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(
            @NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
        return original.getRenderTypes(state, random, data);
    }

    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemOverrides getOverrides() { return original.getOverrides(); }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
}

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

/** Uniformly scales baked decorative quads without violating vanilla JSON element limits. */
public final class DecorativeScaledModel implements BakedModel {
    private final BakedModel original;
    private final float scale;
    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;

    public DecorativeScaledModel(
            BakedModel original, float scale, float pivotX, float pivotY, float pivotZ) {
        this(original, scale, pivotX, pivotY, pivotZ, 0.0F, 0.0F, 0.0F);
    }

    public DecorativeScaledModel(
            BakedModel original,
            float scale,
            float pivotX,
            float pivotY,
            float pivotZ,
            float offsetX,
            float offsetY,
            float offsetZ) {
        this.original = original;
        this.scale = scale;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            @NotNull RandomSource random,
            @NotNull ModelData data,
            @Nullable RenderType renderType) {
        return scale(original.getQuads(state, side, random, data, renderType), state);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(
            @Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random) {
        return scale(original.getQuads(state, side, random), state);
    }

    private List<BakedQuad> scale(List<BakedQuad> source, @Nullable BlockState state) {
        if (source.isEmpty()) {
            return source;
        }
        float[] pivot = rotatedPivot(state);
        List<BakedQuad> scaled = new ArrayList<>(source.size());
        for (BakedQuad quad : source) {
            scaled.add(scaleQuad(quad, pivot[0], pivot[1], pivot[2]));
        }
        return scaled;
    }

    private float[] rotatedPivot(@Nullable BlockState state) {
        if (state == null || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return new float[] {pivotX, pivotY, pivotZ};
        }
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case SOUTH -> new float[] {1.0F - pivotX, pivotY, 1.0F - pivotZ};
            case WEST -> new float[] {pivotZ, pivotY, 1.0F - pivotX};
            case EAST -> new float[] {1.0F - pivotZ, pivotY, pivotX};
            default -> new float[] {pivotX, pivotY, pivotZ};
        };
    }

    private BakedQuad scaleQuad(BakedQuad quad, float px, float py, float pz) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            vertices[offset] = Float.floatToRawIntBits(px + (x - px) * scale + offsetX);
            vertices[offset + 1] = Float.floatToRawIntBits(py + (y - py) * scale + offsetY);
            vertices[offset + 2] = Float.floatToRawIntBits(pz + (z - pz) * scale + offsetZ);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(),
                quad.getSprite(), quad.isShade());
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

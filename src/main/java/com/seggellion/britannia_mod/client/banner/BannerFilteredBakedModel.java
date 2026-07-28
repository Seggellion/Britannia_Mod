package com.seggellion.britannia_mod.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** Generic view of one shared geometry model, selecting either its complete-base or dye-mask quads. */
final class BannerFilteredBakedModel implements BakedModel {
    enum Selection {
        BASE_TEXTURE,
        DYE_MASK
    }

    private final BakedModel original;
    private final Selection selection;
    private final TextureAtlasSprite targetSprite;

    BannerFilteredBakedModel(
            BakedModel original,
            Selection selection,
            TextureAtlasSprite targetSprite) {
        this.original = java.util.Objects.requireNonNull(original, "original");
        this.selection = java.util.Objects.requireNonNull(selection, "selection");
        this.targetSprite = java.util.Objects.requireNonNull(targetSprite, "targetSprite");
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return filter(original.getQuads(state, side, random));
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData data,
            @Nullable RenderType renderType) {
        return filter(original.getQuads(state, side, random, data, renderType));
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return original.getRenderTypes(stack, fabulous);
    }

    private List<BakedQuad> filter(List<BakedQuad> quads) {
        return quads.stream()
                .filter(quad -> selection == Selection.DYE_MASK
                        ? quad.getTintIndex() == BannerRenderLayer.DYE_MASK_TINT_INDEX
                        : quad.getTintIndex() != BannerRenderLayer.DYE_MASK_TINT_INDEX)
                .map(this::retexture)
                .toList();
    }

    private BakedQuad retexture(BakedQuad quad) {
        TextureAtlasSprite source = quad.getSprite();
        if (source.contents().name().equals(targetSprite.contents().name())) {
            return quad;
        }
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float sourceU = Float.intBitsToFloat(vertices[offset + 4]);
            float sourceV = Float.intBitsToFloat(vertices[offset + 5]);
            float relativeU = (sourceU - source.getU0()) / (source.getU1() - source.getU0());
            float relativeV = (sourceV - source.getV0()) / (source.getV1() - source.getV0());
            float targetU = targetSprite.getU0() + relativeU * (targetSprite.getU1() - targetSprite.getU0());
            float targetV = targetSprite.getV0() + relativeV * (targetSprite.getV1() - targetSprite.getV0());
            vertices[offset + 4] = Float.floatToRawIntBits(targetU);
            vertices[offset + 5] = Float.floatToRawIntBits(targetV);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(),
                targetSprite, quad.isShade());
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        original.applyTransform(context, poseStack, leftHand);
        return this;
    }

    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}

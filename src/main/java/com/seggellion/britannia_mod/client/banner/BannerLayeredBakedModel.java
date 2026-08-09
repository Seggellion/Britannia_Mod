package com.seggellion.britannia_mod.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;

/** A resolved immutable list of framework-owned baked passes for one render key. */
final class BannerLayeredBakedModel implements BakedModel {
    private final BakedModel transformModel;
    private final List<BakedModel> passes;

    BannerLayeredBakedModel(BakedModel transformModel, List<BakedModel> passes) {
        this.transformModel = transformModel;
        this.passes = List.copyOf(passes);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        return passes;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        transformModel.applyTransform(context, poseStack, leftHand);
        return this;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        List<BakedQuad> quads = new ArrayList<>();
        for (BakedModel pass : passes) {
            quads.addAll(pass.getQuads(state, side, random));
        }
        return List.copyOf(quads);
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return transformModel.isGui3d(); }
    @Override public boolean usesBlockLight() { return transformModel.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return passes.getFirst().getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return transformModel.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}

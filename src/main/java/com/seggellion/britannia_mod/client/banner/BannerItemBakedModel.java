package com.seggellion.britannia_mod.client.banner;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The one dynamic baked model installed for the one shared banner item. */
final class BannerItemBakedModel implements BakedModel {
    private final BakedModel original;
    private final ItemOverrides overrides = new ItemOverrides() {
        @Override
        public BakedModel resolve(
                BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                @Nullable LivingEntity entity, int seed) {
            return BannerRenderCache.resolve(stack);
        }
    };

    BannerItemBakedModel(BakedModel original) {
        this.original = original;
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return original.getQuads(state, side, random);
    }
    @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        original.applyTransform(context, poseStack, leftHand);
        return this;
    }
    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return overrides; }
}

package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.block.entity.ManagedFlowerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Renders managed wild flowers at their own block position using the canonical flower assets. */
@OnlyIn(Dist.CLIENT)
public final class ManagedFlowerBlockEntityRenderer implements BlockEntityRenderer<ManagedFlowerBlockEntity> {
    public ManagedFlowerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            ManagedFlowerBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        ResourceLocation species = entity.speciesId().orElse(null);
        if (species == null) {
            return;
        }
        FlowerBlockEntityRenderer.renderVisual(
                entity.getBlockPos(), entity.getBlockState(), species, entity.growthStage(),
                entity.visualTint(), 0.0D, poseStack, bufferSource, packedLight, packedOverlay
        );
    }
}

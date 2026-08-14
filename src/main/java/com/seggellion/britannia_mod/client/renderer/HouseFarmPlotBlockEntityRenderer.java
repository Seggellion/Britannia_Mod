package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Reuses ordinary crop/flower models at the house plot's measured 14/16 soil surface. */
public final class HouseFarmPlotBlockEntityRenderer implements BlockEntityRenderer<HouseFarmPlotBlockEntity> {
    public static final double SOIL_SURFACE_Y = 14.0D / 16.0D;
    public static final double NON_TALL_CROP_RENDER_Y = 15.0D / 16.0D;

    public HouseFarmPlotBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            HouseFarmPlotBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (entity.flowerState().isPresent()) {
            FlowerBlockEntityRenderer.renderFlower(
                    entity, poseStack, bufferSource, packedLight, packedOverlay, SOIL_SURFACE_Y
            );
            return;
        }
        FarmingBlockEntityRenderer.renderCrop(
                entity, poseStack, bufferSource, packedLight, packedOverlay, NON_TALL_CROP_RENDER_Y
        );
    }

    @Override
    public AABB getRenderBoundingBox(HouseFarmPlotBlockEntity entity) {
        return new AABB(entity.getBlockPos()).expandTowards(0.0D, 4.0D, 0.0D).inflate(0.25D);
    }
}

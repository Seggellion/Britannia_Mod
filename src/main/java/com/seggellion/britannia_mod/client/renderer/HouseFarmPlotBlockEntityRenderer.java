package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
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
        int surfaceLight = sampleSurfaceLight(entity, packedLight);
        if (entity.flowerState().isPresent()) {
            FlowerBlockEntityRenderer.renderFlower(
                    entity, poseStack, bufferSource, surfaceLight, packedOverlay, SOIL_SURFACE_Y
            );
            return;
        }
        FarmingBlockEntityRenderer.renderCrop(
                entity, poseStack, bufferSource, surfaceLight, packedOverlay, NON_TALL_CROP_RENDER_Y
        );
    }

    /**
     * The plot block encloses its block entity, so the dispatcher-supplied light is sampled inside
     * opaque geometry and can be zero. Plants are rendered above the soil and must use the exposed
     * light at that position. Preserve either channel when the fallback is brighter (for example,
     * nearby block light at night).
     */
    private static int sampleSurfaceLight(HouseFarmPlotBlockEntity entity, int fallbackLight) {
        var level = entity.getLevel();
        var surfacePos = entity.getBlockPos().above();
        if (level == null || !level.hasChunkAt(surfacePos)) {
            return fallbackLight;
        }
        int sampledLight = LevelRenderer.getLightColor(level, surfacePos);
        return LightTexture.pack(
                Math.max(LightTexture.block(fallbackLight), LightTexture.block(sampledLight)),
                Math.max(LightTexture.sky(fallbackLight), LightTexture.sky(sampledLight))
        );
    }

    @Override
    public AABB getRenderBoundingBox(HouseFarmPlotBlockEntity entity) {
        return new AABB(entity.getBlockPos()).expandTowards(0.0D, 4.0D, 0.0D).inflate(0.25D);
    }
}

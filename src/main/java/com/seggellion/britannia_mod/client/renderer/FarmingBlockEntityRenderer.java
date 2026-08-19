package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.CropVisualModels;
import com.seggellion.britannia_mod.farming.CropVisualRotation;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FarmingBlockEntityRenderer implements BlockEntityRenderer<FarmingBlockEntity> {
    private static final double CROP_RENDER_Y_OFFSET = 1.0D;
    private static final double TALL_CROP_BASE_RENDER_Y_OFFSET = 0.0D;

    public FarmingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FarmingBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderCrop(entity, poseStack, bufferSource, packedLight, packedOverlay, CROP_RENDER_Y_OFFSET);
    }

    /** Reuses the canonical crop model at a surface-specific non-tall planting anchor. */
    public static void renderCrop(
            FarmingBlockEntity entity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            double nonTallRenderYOffset
    ) {
        if (!entity.hasCrop()) {
            return;
        }

        CropDefinition crop = CropRegistry.byId(entity.getPlantedCropId()).orElse(null);
        if (!CropVisualRotation.isEnabledFor(crop) || !hasRenderableCropModel(crop, entity)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(ModelResourceLocation.standalone(cropModelLocation(crop, entity)));
        BlockState renderState = BlockRegistry.FARMING_BLOCK.get().defaultBlockState();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());

        poseStack.pushPose();
        poseStack.translate(0.5D, renderYOffset(crop, nonTallRenderYOffset), 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                CropVisualRotation.yawFor(entity.getBlockPos(), crop) + entity.visualRotationDegrees()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                renderState,
                model,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay
        );
        poseStack.popPose();
    }

    public static boolean hasRenderableCropModel(CropDefinition crop, FarmingBlockEntity entity) {
        return crop != null && hasModelResource(cropModelLocation(crop, entity));
    }

    public static boolean hasRenderableCropModel(CropDefinition crop, int growthStage) {
        return crop != null && hasModelResource(cropModelLocation(crop, growthStage));
    }

    public static ResourceLocation cropModelLocation(CropDefinition crop, FarmingBlockEntity entity) {
        return CropVisualModels.modelLocation(crop, entity.getGrowthStage(), entity.getStoredSeed());
    }

    public static ResourceLocation cropModelLocation(CropDefinition crop, int growthStage) {
        return CropVisualModels.modelLocation(crop, growthStage);
    }

    private static boolean hasModelResource(ResourceLocation modelLocation) {
        String path = "/assets/" + modelLocation.getNamespace() + "/models/" + modelLocation.getPath() + ".json";
        return FarmingBlockEntityRenderer.class.getResource(path) != null;
    }

    private static double renderYOffset(CropDefinition crop, double nonTallRenderYOffset) {
        double soilAnchor = crop != null && crop.tallCrop() ? TALL_CROP_BASE_RENDER_Y_OFFSET : nonTallRenderYOffset;
        return soilAnchor + CropVisualModels.modelAnchorYOffset(crop);
    }

    @Override
    public AABB getRenderBoundingBox(FarmingBlockEntity entity) {
        CropDefinition crop = CropRegistry.byId(entity.getPlantedCropId()).orElse(null);
        double height = renderBoundsHeight(crop);
        double horizontalPadding = renderBoundsHorizontalPadding(crop);
        return new AABB(entity.getBlockPos())
                .expandTowards(0.0D, height - 1.0D, 0.0D)
                .inflate(horizontalPadding, 0.25D, horizontalPadding);
    }

    /** The grape arbor spans a block past its plot on either side and must not cull with it. */
    private static double renderBoundsHorizontalPadding(CropDefinition crop) {
        return crop != null && "grapes".equals(crop.id()) ? 1.25D : 0.25D;
    }

    private static double renderBoundsHeight(CropDefinition crop) {
        if (crop == null) {
            return 1.0D;
        }
        if ("grapes".equals(crop.id())) {
            // Checked before tallCrop: the arbor tops out two blocks above its already-lifted
            // anchor, which is a block higher than its three-block occupancy footprint.
            return 4.0D;
        }
        if (crop.tallCrop()) {
            return Math.max(1, crop.maxHeight());
        }
        if (crop.supportRequirement().name().contains("TRELLIS")) {
            return 3.0D;
        }
        return 1.0D;
    }
}

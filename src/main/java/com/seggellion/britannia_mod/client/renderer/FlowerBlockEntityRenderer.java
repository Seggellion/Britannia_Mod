package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import com.seggellion.britannia_mod.farming.CropVisualRotation;
import com.seggellion.britannia_mod.farming.FlowerPersistentState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Renders one untinted base baked model followed by one stored-colour dye-mask model. */
@OnlyIn(Dist.CLIENT)
public final class FlowerBlockEntityRenderer implements BlockEntityRenderer<FlowerBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double FLOWER_RENDER_Y_OFFSET = 1.0D;
    private static final Set<ModelResourceLocation> REPORTED_MISSING_MODELS = ConcurrentHashMap.newKeySet();

    public FlowerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            FlowerBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        FlowerPersistentState state = entity.flowerState().orElse(null);
        if (state == null) {
            return;
        }

        FlowerVisualModels.RenderPlan plan = FlowerVisualModels.resolve(
                state.speciesId(), state.growthStage(), state.color().tintValue()
        );
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager modelManager = minecraft.getModelManager();
        FlowerVisualModels.ModelPair fallback = FlowerVisualModels.fallbackPair();
        BakedModel baseModel = resolveModel(modelManager, plan.models().baseModel(), fallback.baseModel());
        BakedModel dyeMaskModel = resolveModel(modelManager, plan.models().dyeMaskModel(), fallback.dyeMaskModel());
        if (baseModel == null && dyeMaskModel == null) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());
        BlockState renderState = entity.getBlockState();
        poseStack.pushPose();
        poseStack.translate(0.5D, FLOWER_RENDER_Y_OFFSET, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(CropVisualRotation.yawFor(
                entity.getBlockPos(), plan.visualSpecies().toString()
        )));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        if (baseModel != null) {
            renderPass(minecraft, poseStack, consumer, renderState, baseModel,
                    plan.baseTint(), packedLight, packedOverlay);
        }
        if (dyeMaskModel != null) {
            renderPass(minecraft, poseStack, consumer, renderState, dyeMaskModel,
                    plan.dyeMaskTint(), packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void renderPass(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer consumer,
            BlockState renderState,
            BakedModel model,
            FlowerVisualModels.Tint tint,
            int packedLight,
            int packedOverlay
    ) {
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                renderState,
                model,
                tint.red(),
                tint.green(),
                tint.blue(),
                packedLight,
                packedOverlay
        );
    }

    @Nullable
    private static BakedModel resolveModel(
            ModelManager modelManager,
            ModelResourceLocation requested,
            ModelResourceLocation fallback
    ) {
        BakedModel model = modelManager.getModel(requested);
        BakedModel missing = modelManager.getMissingModel();
        if (model != missing) {
            return model;
        }
        warnMissingOnce(requested, fallback);
        BakedModel fallbackModel = modelManager.getModel(fallback);
        return fallbackModel == missing ? null : fallbackModel;
    }

    private static void warnMissingOnce(ModelResourceLocation requested, ModelResourceLocation fallback) {
        if (REPORTED_MISSING_MODELS.add(requested)) {
            LOGGER.warn("[flower rendering] Missing baked model {}; using {} when available", requested, fallback);
        }
    }

    public static void onModelsReloaded() {
        REPORTED_MISSING_MODELS.clear();
    }

    @Override
    public AABB getRenderBoundingBox(FlowerBlockEntity entity) {
        return new AABB(entity.getBlockPos()).expandTowards(0.0D, 1.0D, 0.0D).inflate(0.25D);
    }
}

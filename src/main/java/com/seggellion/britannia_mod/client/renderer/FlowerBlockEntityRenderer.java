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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Renders one canonical stage model's geometry with its base and dye-mask textures. */
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

        renderVisual(
                entity.getBlockPos(), entity.getBlockState(), state.speciesId(), state.growthStage(),
                state.color().tintValue(), FLOWER_RENDER_Y_OFFSET, poseStack, bufferSource,
                packedLight, packedOverlay
        );
    }

    /** Shared renderer used by farm flowers and independent managed wild flowers. */
    public static void renderVisual(
            net.minecraft.core.BlockPos position,
            BlockState renderState,
            ResourceLocation speciesId,
            int growthStage,
            int tint,
            double yOffset,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        FlowerVisualModels.RenderPlan plan = FlowerVisualModels.resolve(
                speciesId, growthStage, tint
        );
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager modelManager = minecraft.getModelManager();
        ResolvedStageModel resolved = resolveModel(modelManager, plan.model(), FlowerVisualModels.fallbackModel());
        if (resolved == null) {
            return;
        }

        TextureAtlasSprite baseSprite = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(resolved.assets().baseTextureId());
        poseStack.pushPose();
        poseStack.translate(0.5D, yOffset, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(CropVisualRotation.yawFor(
                position, plan.visualSpecies().toString()
        )));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        renderPass(minecraft, poseStack, bufferSource.getBuffer(RenderType.cutout()), renderState, resolved.model(),
                plan.baseTint(), packedLight, packedOverlay);
        VertexConsumer dyeMaskConsumer = new TextureRemappingVertexConsumer(
                bufferSource.getBuffer(RenderType.entityTranslucent(textureFile(resolved.assets().dyeMaskTextureId()))),
                baseSprite,
                plan.dyeMaskTint().alpha()
        );
        renderPass(minecraft, poseStack, dyeMaskConsumer, renderState, resolved.model(),
                plan.dyeMaskTint(), packedLight, packedOverlay);
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
    private static ResolvedStageModel resolveModel(
            ModelManager modelManager,
            FlowerVisualModels.StageModel requested,
            FlowerVisualModels.StageModel fallback
    ) {
        BakedModel model = modelManager.getModel(requested.canonicalModel());
        BakedModel missing = modelManager.getMissingModel();
        if (model != missing) {
            return new ResolvedStageModel(model, requested);
        }
        warnMissingOnce(requested.canonicalModel(), fallback.canonicalModel());
        BakedModel fallbackModel = modelManager.getModel(fallback.canonicalModel());
        return fallbackModel == missing ? null : new ResolvedStageModel(fallbackModel, fallback);
    }

    private static ResourceLocation textureFile(ResourceLocation textureId) {
        return ResourceLocation.fromNamespaceAndPath(
                textureId.getNamespace(), "textures/" + textureId.getPath() + ".png"
        );
    }

    private static void warnMissingOnce(ModelResourceLocation requested, ModelResourceLocation fallback) {
        if (REPORTED_MISSING_MODELS.add(requested)) {
            LOGGER.warn("[flower rendering] Missing baked model {}; using {} when available", requested, fallback);
        }
    }

    public static void onModelsReloaded() {
        REPORTED_MISSING_MODELS.clear();
    }

    private record ResolvedStageModel(BakedModel model, FlowerVisualModels.StageModel assets) {
    }

    /** Reuses the canonical baked quads while translating their atlas UVs to the paired dye mask. */
    private static final class TextureRemappingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final TextureAtlasSprite source;
        private final float opacity;

        private TextureRemappingVertexConsumer(
                VertexConsumer delegate,
                TextureAtlasSprite source,
                float opacity
        ) {
            this.delegate = delegate;
            this.source = source;
            this.opacity = opacity;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * opacity));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            float sourceWidth = source.getU1() - source.getU0();
            float sourceHeight = source.getV1() - source.getV0();
            float relativeU = sourceWidth == 0.0F ? 0.0F : (u - source.getU0()) / sourceWidth;
            float relativeV = sourceHeight == 0.0F ? 0.0F : (v - source.getV0()) / sourceHeight;
            delegate.setUv(relativeU, relativeV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    @Override
    public AABB getRenderBoundingBox(FlowerBlockEntity entity) {
        return new AABB(entity.getBlockPos()).expandTowards(0.0D, 1.0D, 0.0D).inflate(0.25D);
    }
}

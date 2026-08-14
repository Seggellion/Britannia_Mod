package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.block.entity.MoongateBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Y-axis billboard renderer for the animated vertical city-moongate layers. */
public final class MoongateBlockEntityRenderer implements BlockEntityRenderer<MoongateBlockEntity> {
    public static final ModelResourceLocation BILLBOARD_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/moongate_billboard"));

    public MoongateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            MoongateBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(BILLBOARD_MODEL);
        if (model == minecraft.getModelManager().getMissingModel()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.scale(1.2F, 1.2F, 1.2F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-minecraft.gameRenderer.getMainCamera().getYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                BlockRegistry.MOONGATE_BLOCK.get().defaultBlockState(),
                model,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MoongateBlockEntity entity) {
        return new AABB(entity.getBlockPos()).expandTowards(0.0D, 1.0D, 0.0D).inflate(0.5D);
    }
}

package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.block.entity.ThreeHeightLightBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.BritanniaMod;


public class ThreeHeightLightRenderer implements BlockEntityRenderer<ThreeHeightLightBlockEntity> {

    public ThreeHeightLightRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ThreeHeightLightBlockEntity entity,
                       float partialTicks,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {

        poseStack.pushPose();

        // raise model by 16 voxels (1 block)
        poseStack.translate(0.0D, 1.0D, 0.0D);

        BlockState state = entity.getBlockState();
        Minecraft mc = Minecraft.getInstance();

        // get the baked model for the current block state (respects facing etc.)
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());

        mc.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                state,
                model,
                1.0F, 1.0F, 1.0F,
                packedLight,
                packedOverlay
        );

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ThreeHeightLightBlockEntity be) {
        return true;
    }
}
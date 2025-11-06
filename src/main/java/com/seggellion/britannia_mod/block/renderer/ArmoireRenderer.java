package com.seggellion.britannia_mod.block.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.block.entity.ArmoireBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

public class ArmoireRenderer implements BlockEntityRenderer<ArmoireBlockEntity> {

    public ArmoireRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ArmoireBlockEntity entity,
                       float partialTicks,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       int packedOverlay) {

        BlockState state = entity.getBlockState();

        poseStack.pushPose();

        // ✅ Lift the baked model upward 12 px (0.75 blocks)
        poseStack.translate(0.0D, 0.75D, 0.0D);

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        PoseStack.Pose pose = poseStack.last();

        // Draw the model in-world with vanilla tint & light
        mc.getBlockRenderer().getModelRenderer().renderModel(
                pose,
                consumer,
                state,
                model,
                1.0F, 1.0F, 1.0F,     // RGB multipliers
                packedLight,
                packedOverlay
        );

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ArmoireBlockEntity entity) {
        return true;
    }
}

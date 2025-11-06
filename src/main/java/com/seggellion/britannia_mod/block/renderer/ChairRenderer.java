package com.seggellion.britannia_mod.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.seggellion.britannia_mod.block.nudgeable.block_entities.ChairBlockEntity;


public class ChairRenderer implements BlockEntityRenderer<ChairBlockEntity> {
    @Override
    public void render(
        ChairBlockEntity blockEntity,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();

        // Apply nudge offset
        Vec3 offset = blockEntity.getOffset(); // ← from your existing system
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);

        // Render the block model
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
            state,
            poseStack,
            buffer,
            packedLight,
            packedOverlay
        );

        poseStack.popPose();
    }
}

package com.seggellion.britannia_mod.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.math.Axis;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;


import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public class HouseSignRenderer implements BlockEntityRenderer<HouseSignBlockEntity> {

    private final Font font;

    public HouseSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(HouseSignBlockEntity signEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        BlockPos posBelow = signEntity.getBlockPos().below();
        Level level = signEntity.getLevel();

        if (level == null) return;

        BlockEntity be = level.getBlockEntity(posBelow);
        if (!(be instanceof HouseLotBlockEntity lot)) return;

        String name = lot.getHouseName();
        if (name == null || name.trim().isEmpty()) {
            name = "Unnamed House";
        }

        // Translate to center
        poseStack.pushPose();
        poseStack.translate(0.5, 1.2, 0.5); // center above the block
        Direction facing = signEntity.getBlockState().getValue(HouseSignBlock.FACING);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

//        poseStack.mulPose(Axis.YP.rotationDegrees(-signEntity.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot()));
        poseStack.scale(0.01f, -0.01f, 0.01f); // tiny scale

        // Draw text
        float x = -font.width(name) / 2f;
        font.drawInBatch(name, x, 0, 0xFFFFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);

        poseStack.popPose();
    }
}

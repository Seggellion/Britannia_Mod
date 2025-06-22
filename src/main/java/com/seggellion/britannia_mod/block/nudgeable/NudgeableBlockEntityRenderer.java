package com.seggellion.britannia_mod.block.nudgeable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

public class NudgeableBlockEntityRenderer implements BlockEntityRenderer<NudgeableBlockEntity> {

    public NudgeableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NudgeableBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        Vec3 offset = entity.getOffset();
        BlockState blockState = entity.getBlockState();
        BlockPos pos = entity.getBlockPos();

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(blockState);

        RandomSource random = RandomSource.create();
        random.setSeed(blockState.getSeed(pos));

        ModelData modelData = ModelData.EMPTY;
        if (entity.getLevel() != null) {
            modelData = model.getModelData(entity.getLevel(), pos, blockState, ModelData.EMPTY);
        }

        dispatcher.getModelRenderer().tesselateBlock(
                entity.getLevel(),
                model,
                blockState,
                pos,
                poseStack,
                bufferSource.getBuffer(RenderType.solid()),
                false,
                random,
                blockState.getSeed(pos),
                packedOverlay,
                modelData,
                null
        );

        poseStack.popPose();
    }
}
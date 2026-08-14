package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.grabbyhands.blockentity.GrabbyPlacedItemBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import com.mojang.math.Axis;

/**
 * Draws whatever item the host is holding, using that item's own model.
 *
 * <p>Rendering the real stack rather than a second art registry is what keeps the host generic: an
 * item enrolled tomorrow looks correct today, and a model change to an item is picked up
 * automatically.
 *
 * <p>The stack is laid flat, as an object resting on a surface rather than standing upright, and
 * rotated to the block's facing so a player can orient what they set down.
 */
public class GrabbyPlacedItemRenderer implements BlockEntityRenderer<GrabbyPlacedItemBlockEntity> {
    private static final float LYING_HEIGHT = 0.09F;
    private static final float SCALE = 0.5F;

    private final ItemRenderer itemRenderer;

    public GrabbyPlacedItemRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            GrabbyPlacedItemBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {

        ItemStack payload = blockEntity.grabbyPayload();
        if (payload.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, LYING_HEIGHT, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facingDegrees(blockEntity)));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(SCALE, SCALE, SCALE);

        itemRenderer.renderStatic(
                payload,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                // A stable seed keeps a randomised model from flickering between frames.
                (int) blockEntity.getBlockPos().asLong());

        poseStack.popPose();
    }

    private static float facingDegrees(GrabbyPlacedItemBlockEntity blockEntity) {
        var state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
        return facing.toYRot();
    }
}

package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Draws merchandise from the authoritative root block entity. */
@OnlyIn(Dist.CLIENT)
public final class DisplayCaseBlockEntityRenderer
        implements BlockEntityRenderer<DisplayCaseBlockEntity> {
    /** The owner-authored base top is model Y=16, exactly one block above the root origin. */
    public static final double DISPLAY_SURFACE_Y = 16.0D / 16.0D;
    /** A block model fits inside the frame's model Y=16..22 display volume. */
    public static final float BLOCK_MERCHANDISE_SCALE = 6.0F / 16.0F;
    public static final float ITEM_MERCHANDISE_SCALE = 0.45F;
    public static final double ITEM_MODEL_CENTER_Y = DISPLAY_SURFACE_Y + 0.22D;

    private final BlockRenderDispatcher blockRenderer;
    private final ItemRenderer itemRenderer;

    public DisplayCaseBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            DisplayCaseBlockEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        BlockState rootState = entity.getBlockState();
        if (!(rootState.getBlock() instanceof DisplayCaseBlock displayCase)
                || !displayCase.isRoot(rootState)) {
            return;
        }

        int surfaceLight = packedLight;
        if (entity.getLevel() != null) {
            surfaceLight = LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos().above());
        }

        ItemStack merchandise = entity.displayedItem();
        if (merchandise.isEmpty()) {
            return;
        }

        Direction facing = rootState.getValue(DecorativeMultiblockBlock.FACING);
        if (merchandise.getItem() instanceof BlockItem blockItem) {
            renderBlockMerchandise(
                    blockItem.getBlock().defaultBlockState(), facing, poseStack,
                    bufferSource, surfaceLight, packedOverlay);
        } else {
            renderItemMerchandise(
                    merchandise, facing, entity, poseStack, bufferSource, surfaceLight);
        }
    }

    private void renderBlockMerchandise(
            BlockState merchandise,
            Direction facing,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, DISPLAY_SURFACE_Y, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.scale(
                BLOCK_MERCHANDISE_SCALE,
                BLOCK_MERCHANDISE_SCALE,
                BLOCK_MERCHANDISE_SCALE);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        blockRenderer.renderSingleBlock(
                merchandise, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private void renderItemMerchandise(
            ItemStack merchandise,
            Direction facing,
            DisplayCaseBlockEntity entity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.5D, ITEM_MODEL_CENTER_Y, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-15.0F));
        poseStack.scale(
                ITEM_MERCHANDISE_SCALE,
                ITEM_MERCHANDISE_SCALE,
                ITEM_MERCHANDISE_SCALE);
        itemRenderer.renderStatic(
                merchandise,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.getLevel(),
                (int) entity.getBlockPos().asLong());
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DisplayCaseBlockEntity entity) {
        BlockPos root = entity.getBlockPos();
        return new AABB(
                root.getX(), root.getY(), root.getZ(),
                root.getX() + 1.0D, root.getY() + 2.0D, root.getZ() + 1.0D);
    }
}

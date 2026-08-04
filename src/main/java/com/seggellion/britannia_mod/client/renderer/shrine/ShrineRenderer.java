package com.seggellion.britannia_mod.client.renderer.shrine;

import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.ShrineRenderTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** One display-only renderer registered solely for the authoritative anchor entity. */
public final class ShrineRenderer extends GeoBlockRenderer<LargeStructureAnchorBlockEntity> {
    public ShrineRenderer(BlockEntityRendererProvider.Context context) {
        super(new ShrineGeoModel());
    }

    @Override
    public AABB getRenderBoundingBox(LargeStructureAnchorBlockEntity anchor) {
        return anchor.getRenderBoundingBox();
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            LargeStructureAnchorBlockEntity anchor,
            BakedGeoModel model,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int color) {
        if (!isReRender) {
            poseStack.translate(0.0, ShrineRenderTransform.renderOffsetBlocks(anchor), 0.0);
        }
        super.preRender(poseStack, anchor, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, color);
    }
}

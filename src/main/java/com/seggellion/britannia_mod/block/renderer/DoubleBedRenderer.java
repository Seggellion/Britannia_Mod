package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.block.entity.DoubleBedBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

public class DoubleBedRenderer implements BlockEntityRenderer<DoubleBedBlockEntity> {

    private final ModelPart headModel;
    private final ModelPart footModel;

    public DoubleBedRenderer(BlockEntityRendererProvider.Context ctx) {
        // vanilla bed geometry: two roots, baked once
        this.headModel = ctx.bakeLayer(ModelLayers.BED_HEAD);
        this.footModel = ctx.bakeLayer(ModelLayers.BED_FOOT);
    }

    @Override
    public void render(DoubleBedBlockEntity be, float partialTicks,
                       PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {

        var state = be.getBlockState();
        Direction dir = state.getValue(BedBlock.FACING);
        boolean isHead = state.getValue(BedBlock.PART) == BedPart.HEAD;

        /* ----- pose stack ----- */
        pose.pushPose();
        pose.translate(0.0, 0.5625, 0.0);                         // lift mattress
        pose.mulPose(Axis.YP.rotationDegrees(-dir.toYRot()));     // rotate to facing

        /* ----- texture & buffer ----- */
        ResourceLocation tex = getBedTexture(be.getColor());
        VertexConsumer vc    = buffer.getBuffer(RenderType.entitySolid(tex));

        (isHead ? headModel : footModel).render(pose, vc, packedLight, packedOverlay);

        pose.popPose();
    }

    /* vanilla texture path generator */
    private static ResourceLocation getBedTexture(DyeColor color) {
        // If you really want the vanilla quilt tint:
        // return ResourceLocation.fromNamespaceAndPath("minecraft",
        //        "textures/entity/bed/" + color.getName() + ".png");

        // Otherwise point to **your** texture (recommended):
        return ResourceLocation.fromNamespaceAndPath("britannia_mod",
                "textures/entity/bed/double_bed.png");
    }
}

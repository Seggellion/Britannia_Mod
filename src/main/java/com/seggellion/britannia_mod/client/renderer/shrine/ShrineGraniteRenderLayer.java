package com.seggellion.britannia_mod.client.renderer.shrine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.render.ShrineRimMaterialSelection;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/** Renders only the shrine's exterior rim with the owner-supplied granite material. */
public final class ShrineGraniteRenderLayer extends GeoRenderLayer<LargeStructureAnchorBlockEntity> {
    static final String SURFACE_BONE = "shrine_surface";
    static final String GRANITE_BONE = "granite_rim";

    public ShrineGraniteRenderLayer(GeoRenderer<LargeStructureAnchorBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            LargeStructureAnchorBlockEntity anchor,
            BakedGeoModel model,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay) {
        GeoBone surface = model.getBone(SURFACE_BONE).orElse(null);
        GeoBone granite = model.getBone(GRANITE_BONE).orElse(null);
        if (surface == null || granite == null) {
            return;
        }

        boolean surfaceWasHidden = surface.isHidden();
        boolean graniteWasHidden = granite.isHidden();
        try {
            surface.setHidden(true);
            granite.setHidden(false);
            var selected = ShrineRimMaterialSelection.textureFor(anchor);
            ResourceLocation graniteTexture = ResourceLocation.fromNamespaceAndPath(
                    selected.namespace(), selected.path());
            RenderType graniteRenderType = RenderType.entityCutoutNoCull(graniteTexture);
            VertexConsumer graniteBuffer = bufferSource.getBuffer(graniteRenderType);
            getRenderer().reRender(model, poseStack, bufferSource, anchor,
                    graniteRenderType, graniteBuffer, partialTick, packedLight, packedOverlay,
                    Color.WHITE.argbInt());
        } finally {
            surface.setHidden(surfaceWasHidden);
            granite.setHidden(graniteWasHidden);
        }
    }
}

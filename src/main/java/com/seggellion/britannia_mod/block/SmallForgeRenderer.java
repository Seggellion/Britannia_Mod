package com.seggellion.britannia_mod.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SmallForgeRenderer extends GeoBlockRenderer<SmallForgeBlockEntity> {
    public SmallForgeRenderer(BlockEntityRendererProvider.Context context) {
        super(new SmallForgeModel());
    }

    @Override
    public void render(SmallForgeBlockEntity entity, float partialTicks, PoseStack stack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        int fullBright = LightTexture.pack(15, 15); 

        super.render(entity, partialTicks, stack, bufferSource, fullBright, packedOverlay);
    }
}
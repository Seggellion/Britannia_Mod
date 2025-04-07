package com.seggellion.britannia_mod.block;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;


import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LargeForgeRenderer extends GeoBlockRenderer<LargeForgeBlockEntity> {
    public LargeForgeRenderer(BlockEntityRendererProvider.Context context) {
        super(new LargeForgeModel());
    }

    @Override
    public void render(LargeForgeBlockEntity entity, float partialTicks, PoseStack stack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        int fullBright = LightTexture.pack(15, 15); 

        super.render(entity, partialTicks, stack, bufferSource, fullBright, packedOverlay);
    }
}
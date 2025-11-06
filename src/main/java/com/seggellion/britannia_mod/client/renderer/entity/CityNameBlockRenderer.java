package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.seggellion.britannia_mod.util.HasCityName;

/**
 * Reusable name renderer for any spawn block that implements getCityName()
 */
public class CityNameBlockRenderer<T extends BlockEntity & HasCityName>
        implements BlockEntityRenderer<T> {

    private final Font font;

    public CityNameBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(T be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        String name = be.getCityName();
        if (name == null || name.isBlank()) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.25, 0.5);   // centered above block
        poseStack.scale(0.01f, -0.01f, 0.01f); // scale text down & flip Y

        var matrix = poseStack.last().pose();
        float x = -font.width(name) / 2f;

        font.drawInBatch(
            name, x, 0,
            0xFFFFFF, false,
            matrix, buffer,
            Font.DisplayMode.NORMAL, 0,
            packedLight
        );

        poseStack.popPose();
    }

    /** Interface to ensure entity has a getCityName() method */
    public interface HasCityName {
        String getCityName();
    }
}

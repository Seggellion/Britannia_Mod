package com.seggellion.britannia_mod.block;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PillarRenderer extends GeoBlockRenderer<PillarBlockEntity> {
            private static final Logger LOGGER = LogUtils.getLogger();

    public PillarRenderer(BlockEntityRendererProvider.Context context) {

        super(new PillarModel());
    }

protected void applyRotations(PoseStack poseStack, PillarBlockEntity animatable, float ageInTicks, float rotationYaw, float partialTick) {
    Direction direction = animatable.getFacing();
    float yRot = switch (direction) {
        case SOUTH -> 180f;
        case WEST -> 90f;
        case EAST -> -90f;
        default -> 0f;
    };

    poseStack.translate(0.5, 0, 0.5); // Center the model
    poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
    poseStack.translate(-0.5, 0, -0.5); // Un-center
}


@Override
public void render(PillarBlockEntity entity, float partialTicks, PoseStack stack,
                   MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
    super.render(entity, partialTicks, stack, bufferSource, packedLight, packedOverlay);
}


}

package com.seggellion.britannia_mod.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.client.model.item.OrderShieldModel;
import com.seggellion.britannia_mod.item.OrderShieldItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;


public class OrderShieldRenderer extends GeoItemRenderer<OrderShieldItem> {

    public OrderShieldRenderer() {
        super(new OrderShieldModel());
    }

@Override
public void preRender(PoseStack poseStack, OrderShieldItem item, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int color) {
    super.preRender(poseStack, item, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, color);

    Minecraft minecraft = Minecraft.getInstance();

    // Ensure we only adjust poseStack for first-person and third-person contexts
    if (minecraft.player != null 
        && minecraft.player.isUsingItem() 
        && minecraft.player.getUseItem().getItem() instanceof OrderShieldItem 
        && renderPerspective != ItemDisplayContext.GUI) {


     boolean isRightHand = minecraft.player.getUsedItemHand().name().equals("MAIN_HAND");
        boolean leftHandHasShield = minecraft.player.getOffhandItem().getItem() instanceof OrderShieldItem;
        boolean rightHandHasShield = minecraft.player.getMainHandItem().getItem() instanceof OrderShieldItem;

        // Prioritize animating the left hand if both hands have shields
        if (leftHandHasShield && rightHandHasShield) {
            if (isRightHand) {
                return; // Skip animation for the right hand
            }
        }




 if (renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            // Adjust positioning for first-person perspective
            if (renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
                 poseStack.translate(-0.5, 0.1, -0.1); 
            } else {
                poseStack.translate(0.5, 0.1, -0.1);             
            }
        } else if (renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            // Adjust positioning for third-person perspective
            if (renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                poseStack.translate(0.0, 0, 0.2); // Adjust third-person right hand
                poseStack.mulPose(Axis.ZP.rotationDegrees(-10)); // Slight tilt inward
                poseStack.mulPose(Axis.YP.rotationDegrees(-60));
                //poseStack.scale(1.2f, 1.2f, 1.2f); // Slight scaling to match visual size
            } else {
                poseStack.translate(0.0, 0, 0.2); // Adjust third-person left hand
                poseStack.mulPose(Axis.ZP.rotationDegrees(10)); // Slight tilt inward
                poseStack.mulPose(Axis.YP.rotationDegrees(60));
                //poseStack.scale(1.2f, 1.2f, 1.2f); // Slight scaling to match visual size
            }
        }

        // Translate towards the center of the screen based on hand
        //if (isRightHand) {
        //    poseStack.translate(-0.5, 0.5, -0.4); // Move left hand closer to center
        //} else {
        //    poseStack.translate(0.5, 0.1, -0.1);  // Refined positioning
        //}

        
    }
}



    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Call the parent method to handle rendering
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    }
}

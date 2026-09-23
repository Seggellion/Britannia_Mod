package com.seggellion.britannia_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.seggellion.britannia_mod.blessed.BlessedItemLifecycleMetadata;
import com.seggellion.britannia_mod.item.StarfarersMedallionItem;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Draws the ordinary item model on the player's upper torso, following the body pose. */
@OnlyIn(Dist.CLIENT)
public final class StarfarersMedallionLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float WORN_SCALE = 0.35F;

    private final ItemRenderer itemRenderer;

    public StarfarersMedallionLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            ItemRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!shouldRender(stack, player.getUUID())) {
            return;
        }

        poseStack.pushPose();
        try {
            getParentModel().body.translateAndRotate(poseStack);
            // ModelPart coordinates are in sixteenths of a block. Keep the medallion
            // ahead of the jacket layer and below the neck, without changing its
            // inventory/display-case model or adding a second texture/render path.
            poseStack.translate(0.0D, 0.22D, -0.18D);
            poseStack.scale(WORN_SCALE, WORN_SCALE, WORN_SCALE);
            // PlayerModel coordinates invert the item's vertical axis. Roll only the
            // worn render so the bail and cord sit above the medallion face.
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffers, player.level(),
                    player.getId());
        } finally {
            poseStack.popPose();
        }
    }

    /** Client-side fail-closed guard; server-side equip validation remains authoritative. */
    public static boolean shouldRender(ItemStack stack, UUID wearer) {
        return stack.getItem() instanceof StarfarersMedallionItem
                && BlessedItemLifecycleMetadata.ownerOf(stack)
                        .filter(wearer::equals).isPresent();
    }
}

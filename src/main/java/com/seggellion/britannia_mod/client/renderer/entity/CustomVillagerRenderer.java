package com.seggellion.britannia_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.seggellion.britannia_mod.villager.BlacksmithProfessions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public class CustomVillagerRenderer extends MobRenderer<Villager, HumanoidModel<Villager>> {

    private static final ResourceLocation JOURNEYMAN_BLACKSMITH_TEXTURE = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/entity/journeyman_blacksmith.png");

    private static final ResourceLocation DEFAULT_VILLAGER_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/villager/villager.png");
    private static final ResourceLocation DEFAULT_PLAYER_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/ari.png");


    public CustomVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Villager villager) {
        if (villager.getVillagerData().getProfession() == BlacksmithProfessions.JOURNEYMAN_BLACKSMITH.get()) {
            return DEFAULT_PLAYER_TEXTURE;
        }
        // Provide a default villager texture instead of calling super
        return DEFAULT_VILLAGER_TEXTURE;
    }

    @Override
    protected void scale(Villager villager, PoseStack poseStack, float partialTick) {
        // Optionally scale the model if needed
        super.scale(villager, poseStack, partialTick);
    }
}

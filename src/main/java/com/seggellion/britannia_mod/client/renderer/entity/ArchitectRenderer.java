package com.seggellion.britannia_mod.client.renderer;

import com.seggellion.britannia_mod.entity.ArchitectEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;

public class ArchitectRenderer extends MobRenderer<ArchitectEntity, HumanoidModel<ArchitectEntity>> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/villager/villager.png");

    public ArchitectRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);

        HumanoidModel<ArchitectEntity> innerArmor = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidModel<ArchitectEntity> outerArmor = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        ModelManager modelManager = ctx.getModelManager();

        this.addLayer(new HumanoidArmorLayer<>(
            this,
            innerArmor,
            outerArmor,
            modelManager
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(ArchitectEntity entity) {
        return TEXTURE;
    }
}

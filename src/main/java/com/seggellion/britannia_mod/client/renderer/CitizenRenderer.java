package com.seggellion.britannia_mod.client.renderer;

import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.client.model.CitizenGeoModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CitizenRenderer extends GeoEntityRenderer<CitizenEntity> {

    public CitizenRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CitizenGeoModel());

    }
}
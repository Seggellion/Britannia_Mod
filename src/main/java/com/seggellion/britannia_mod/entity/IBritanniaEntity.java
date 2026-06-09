package com.seggellion.britannia_mod.entity;

import software.bernie.geckolib.animatable.GeoAnimatable;

public interface IBritanniaEntity extends GeoAnimatable {
    String getEntityName();

    default float getTargetAlpha() {
        return 1.0F; 
    }

}
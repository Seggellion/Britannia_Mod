// OrderShieldModel.java
package com.seggellion.britannia_mod.client.model.item;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.OrderShieldItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class OrderShieldModel extends GeoModel<OrderShieldItem> {

    @Override
    public ResourceLocation getModelResource(OrderShieldItem object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID,"geo/order_shield.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(OrderShieldItem object) {
        return ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "textures/item/order_shield.png");
    }

    @Override
    public ResourceLocation getAnimationResource(OrderShieldItem object) {
        // Return null if you don't have animations
        return null;
    }
}

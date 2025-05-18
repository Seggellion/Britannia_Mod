package com.seggellion.britannia_mod.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.BakedModel;
import com.seggellion.britannia_mod.client.model.ThinWallBakedModel;

import java.util.Map;

/** Registered in BritanniaMod with:  modEventBus.register(new ThinWallClient()); */
public final class ThinWallClient {

    /** Runs **after** all models are baked, with a mutable registry. */
    @SubscribeEvent
    public void onModifyBaking(ModelEvent.ModifyBakingResult evt) {

        Map<ModelResourceLocation, BakedModel> models = evt.getModels();   // ← mutable

        for (Map.Entry<ModelResourceLocation, BakedModel> e : models.entrySet()) {
            ModelResourceLocation mrl = e.getKey();
            ResourceLocation      id  = mrl.id();        // record accessor

            if (!"britannia_mod".equals(id.getNamespace())) continue;
            if (!id.getPath().startsWith("stone_wall_"))   continue;

            models.put(mrl, new ThinWallBakedModel(e.getValue()));
        }
    }
}
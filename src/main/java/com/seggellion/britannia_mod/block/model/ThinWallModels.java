package com.seggellion.britannia_mod.client.model;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.resources.ResourceLocation;


import java.util.Map;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
/**
 * Client-side cache for extra ThinWall model variants.
 */
public class ThinWallModels {

    private static BakedModel STAIR_FILL;
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called during model bake phase to cache additional models.
     */

@SubscribeEvent
public static void onModifyBakingResults(ModelEvent.ModifyBakingResult event) {
    Map<ModelResourceLocation, BakedModel> models = event.getModels();

    for (Map.Entry<ModelResourceLocation, BakedModel> entry : models.entrySet()) {
        ModelResourceLocation modelId = entry.getKey();
        ResourceLocation id = modelId.id();

        if (id.getNamespace().equals("britannia_mod") && id.getPath().contains("thin_wall")) {
            if (id.getPath().equals("block/structure/stone_wall/thin_wall_stair_fill")) {
                setStairFillModel(entry.getValue()); // Cache the original model
            } else {
                BakedModel original = entry.getValue();
                BakedModel wrapped = new ThinWallBakedModel(original);
                models.put(modelId, wrapped); // ✅ Safe to write here
            }
        }
    }
}


public static void setStairFillModel(BakedModel model) {
    STAIR_FILL = model;
}



    public static BakedModel stairFill() {
        return STAIR_FILL;
    }
}
    
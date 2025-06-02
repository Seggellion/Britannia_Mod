package com.seggellion.britannia_mod.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

public class StoneFloorGeometryLoader implements IGeometryLoader<StoneFloorModelLoader> {
    public static final StoneFloorGeometryLoader INSTANCE = new StoneFloorGeometryLoader();

    @Override
    public StoneFloorModelLoader read(JsonObject jsonObject, JsonDeserializationContext context) {
        return StoneFloorModelLoader.INSTANCE;
    }
}

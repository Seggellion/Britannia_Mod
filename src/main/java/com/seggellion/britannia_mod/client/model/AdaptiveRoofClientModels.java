package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Installs the terrain-model wrapper on every registered top-only roof slab variant. */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = BritanniaMod.MODID)
public final class AdaptiveRoofClientModels {
    private AdaptiveRoofClientModels() {
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) ->
                isAdaptiveRoofBlockModel(location)
                                && !(model instanceof AdaptiveRoofBakedModel)
                        ? new AdaptiveRoofBakedModel(model)
                        : model);
    }

    static boolean isAdaptiveRoofBlockModel(ModelResourceLocation location) {
        return isAdaptiveRoofBlockModel(location, BuiltInRegistries.BLOCK::get);
    }

    static boolean isAdaptiveRoofBlockModel(
            ModelResourceLocation location,
            Function<ResourceLocation, Block> blockLookup) {
        ResourceLocation id = location.id();
        return BritanniaMod.MODID.equals(id.getNamespace())
                && !"inventory".equals(location.variant())
                && blockLookup.apply(id) instanceof TopOnlySlabBlock;
    }
}

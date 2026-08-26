package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import java.util.Set;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Installs the terrain-model wrapper on every registered top-only roof slab variant. */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = BritanniaMod.MODID)
public final class AdaptiveRoofClientModels {
    static final Set<String> ADAPTIVE_ROOF_IDS = Set.of(
            "tile_roof_flat",
            "cedar_roof_flat",
            "slate_roof_flat",
            "slate_roof_1_flat",
            "slate_roof_2_flat",
            "thatch_roof_flat");

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
        ResourceLocation id = location.id();
        return BritanniaMod.MODID.equals(id.getNamespace())
                && ADAPTIVE_ROOF_IDS.contains(id.getPath())
                && !"inventory".equals(location.variant());
    }
}

package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Installs the column model over every {@code crate_stack} cell variant.
 *
 * <p>The same seam {@code AdaptiveRoofClientModels} uses. The blockstate file supplies an empty
 * placeholder model per {@code part}; this replaces each with a wrapper that composes the real crate
 * models at bake time.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = BritanniaMod.MODID)
public final class CrateStackClientModels {

    static final String CRATE_STACK_ID = "crate_stack";

    private CrateStackClientModels() {
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) ->
                isCrateStackBlockModel(location) && !(model instanceof CrateStackBakedModel)
                        ? new CrateStackBakedModel(model)
                        : model);
    }

    static boolean isCrateStackBlockModel(ModelResourceLocation location) {
        ResourceLocation id = location.id();
        return BritanniaMod.MODID.equals(id.getNamespace())
                && CRATE_STACK_ID.equals(id.getPath())
                && !"inventory".equals(location.variant());
    }
}

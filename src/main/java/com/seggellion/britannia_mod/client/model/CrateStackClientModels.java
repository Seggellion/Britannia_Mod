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
 *
 * <p>The large crate is wrapped too, for the opposite reason: it draws nothing of its own that this
 * changes, but it has to draw the bottom of any column standing on its lid, which is physically inside
 * a cell the large crate owns.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = BritanniaMod.MODID)
public final class CrateStackClientModels {

    static final String CRATE_STACK_ID = "crate_stack";

    /** The one crate that carries columns instead of joining them. */
    static final String FOUNDATION_ID = "large_crate";

    private CrateStackClientModels() {
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            if (isCrateStackBlockModel(location) && !(model instanceof CrateStackBakedModel)) {
                return new CrateStackBakedModel(model);
            }
            // A large crate has to draw the part of any column resting on its lid that hangs into
            // the cell above it, because that cell is one the large crate owns.
            if (isFoundationBlockModel(location) && !(model instanceof CrateFoundationBakedModel)) {
                return new CrateFoundationBakedModel(model);
            }
            return model;
        });
    }

    static boolean isFoundationBlockModel(ModelResourceLocation location) {
        ResourceLocation id = location.id();
        return BritanniaMod.MODID.equals(id.getNamespace())
                && FOUNDATION_ID.equals(id.getPath())
                && !"inventory".equals(location.variant());
    }

    static boolean isCrateStackBlockModel(ModelResourceLocation location) {
        ResourceLocation id = location.id();
        return BritanniaMod.MODID.equals(id.getNamespace())
                && CRATE_STACK_ID.equals(id.getPath())
                && !"inventory".equals(location.variant());
    }
}

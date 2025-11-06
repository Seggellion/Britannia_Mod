package com.seggellion.britannia_mod.client.model;

import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Client-side cache for ThinWall helper meshes.
 *  – caches stair-gap filler
 *  – caches corner plug
 *  – wraps every other thin-wall model with ThinWallBakedModel
 */
public final class ThinWallModels {

    private static final Logger LOGGER = LogUtils.getLogger();

    /* helper caches ---------------------------------------------------- */
    private static BakedModel STAIR_FILL;
    private static BakedModel CORNER_PLUG;

    /* helper file locations (relative path in the model-map) ----------- */
    private static final String STAIR_PATH  = "block/structure/thin_wall_stair_fill";
    private static final String CORNER_PATH = "block/structure/thin_wall_corner_fill";

    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onModifyBakingResults(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        for (Map.Entry<ModelResourceLocation, BakedModel> e : models.entrySet()) {

            ModelResourceLocation mrl = e.getKey();
            ResourceLocation id       = mrl.id();            // namespace:path
            if (!"britannia_mod".equals(id.getNamespace()))  // only care about our mod
                continue;

            String path = id.getPath();                      // e.g. block/structure/stone_wall/stone_wall_top_south

            /* —— 1.  pick up the two helper meshes -------------------- */
            if (path.equals(STAIR_PATH)) {                   // stair-gap filler
                STAIR_FILL = e.getValue();
                continue;
            }
            if (path.equals(CORNER_PATH)) {                  // corner plug
                CORNER_PLUG = e.getValue();
                continue;
            }

            /* —— 2. wrap every other thin-wall model ------------------ */
            if (path.contains("thin_wall")) {                // all regular variants
                BakedModel original = e.getValue();
                models.put(mrl, new ThinWallBakedModel(original));
            }
        }
    }

    /* getters ---------------------------------------------------------- */
    public static BakedModel stairFill()  { return STAIR_FILL;  }
    public static BakedModel cornerFill() { return CORNER_PLUG; }
}

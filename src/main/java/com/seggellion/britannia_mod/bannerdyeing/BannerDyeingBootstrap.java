package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.banner.BannerFeature;
import com.seggellion.britannia_mod.dye.DyeFeature;

/**
 * Side-effect-free common bootstrap. Gameplay registration is intentionally deferred.
 */
public final class BannerDyeingBootstrap {
    private BannerDyeingBootstrap() {
    }

    public static void bootstrapCommon() {
        BannerFeature.bootstrapCommon();
        DyeFeature.bootstrapCommon();
    }
}

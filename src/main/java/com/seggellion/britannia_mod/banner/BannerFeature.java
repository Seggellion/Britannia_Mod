package com.seggellion.britannia_mod.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BannerFeature {
    public static final Logger CONTENT_VALIDATION_LOGGER =
            LoggerFactory.getLogger(BritanniaMod.MODID + ".banner.content_validation");

    private BannerFeature() {
    }

    public static void bootstrapCommon() {
        Objects.requireNonNull(BannerDyeingConstants.BRASS_MOUNT_ID);
        Objects.requireNonNull(BannerDyeingConstants.IRON_MOUNT_ID);
    }
}

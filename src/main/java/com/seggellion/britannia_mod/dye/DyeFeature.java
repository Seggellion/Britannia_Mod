package com.seggellion.britannia_mod.dye;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DyeFeature {
    public static final Logger CONTENT_VALIDATION_LOGGER =
            LoggerFactory.getLogger(BritanniaMod.MODID + ".dye.content_validation");

    private DyeFeature() {
    }

    public static void bootstrapCommon() {
        Objects.requireNonNull(BannerDyeingConstants.DEFAULT_COTTON_MATERIAL_ID);
    }
}

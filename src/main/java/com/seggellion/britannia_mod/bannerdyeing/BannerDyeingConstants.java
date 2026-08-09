package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;

public final class BannerDyeingConstants {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final FabricMaterialId DEFAULT_COTTON_MATERIAL_ID =
            FabricMaterialId.of(BritanniaMod.MODID, "cotton");
    public static final MountId BRASS_MOUNT_ID = MountId.of(BritanniaMod.MODID, "brass");
    public static final MountId IRON_MOUNT_ID = MountId.of(BritanniaMod.MODID, "iron");
    public static final BannerOrientation WALL_PARALLEL = BannerOrientation.WALL_PARALLEL;
    public static final BannerOrientation WALL_PERPENDICULAR = BannerOrientation.WALL_PERPENDICULAR;

    private BannerDyeingConstants() {
    }
}

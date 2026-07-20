package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;

public enum BannerOrientation {
    WALL_PARALLEL("wall_parallel"),
    WALL_PERPENDICULAR("wall_perpendicular");

    public static final Codec<BannerOrientation> CODEC = Codec.STRING
            .comapFlatMap(BannerOrientation::decode, BannerOrientation::serializedName)
            .stable();

    private final String serializedName;

    BannerOrientation(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static DataResult<BannerOrientation> decode(String value) {
        return Arrays.stream(values())
                .filter(orientation -> orientation.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown banner orientation: " + value));
    }
}

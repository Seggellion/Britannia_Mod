package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;

public enum BannerContentStatus {
    PLACEHOLDER("placeholder"),
    IN_PROGRESS("in_progress"),
    COMPLETE("complete");

    public static final Codec<BannerContentStatus> CODEC = Codec.STRING
            .comapFlatMap(BannerContentStatus::decode, BannerContentStatus::serializedName)
            .stable();

    private final String serializedName;

    BannerContentStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<BannerContentStatus> decode(String value) {
        return Arrays.stream(values())
                .filter(status -> status.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown banner content status: " + value));
    }
}

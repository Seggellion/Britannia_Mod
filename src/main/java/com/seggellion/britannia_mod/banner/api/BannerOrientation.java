package com.seggellion.britannia_mod.banner.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.StringRepresentable;

public enum BannerOrientation implements StringRepresentable {
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

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Stable placement-selection order, independent of definition list order. */
    public static List<BannerOrientation> orderedSupported(List<BannerOrientation> supported) {
        return supported.stream().distinct()
                .sorted(Comparator.comparingInt(BannerOrientation::selectionIndex))
                .toList();
    }

    private int selectionIndex() {
        return switch (this) {
            case WALL_PARALLEL -> 0;
            case WALL_PERPENDICULAR -> 1;
        };
    }

    public static DataResult<BannerOrientation> decode(String value) {
        return Arrays.stream(values())
                .filter(orientation -> orientation.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown banner orientation: " + value));
    }
}

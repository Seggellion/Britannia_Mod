package com.seggellion.britannia_mod.dye.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;

public enum MatchType {
    EXPLICIT_MAPPING("explicit_mapping"),
    NEAREST_COLOUR("nearest_colour"),
    NATURAL("natural");

    public static final Codec<MatchType> CODEC = Codec.STRING
            .comapFlatMap(MatchType::decode, MatchType::serializedName)
            .stable();

    private final String serializedName;

    MatchType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<MatchType> decode(String value) {
        return Arrays.stream(values())
                .filter(matchType -> matchType.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown dye match type: " + value));
    }
}

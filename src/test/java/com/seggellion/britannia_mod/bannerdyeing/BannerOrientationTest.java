package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class BannerOrientationTest {
    static Stream<Arguments> orientations() {
        return Stream.of(
                Arguments.of(BannerOrientation.WALL_PARALLEL, "wall_parallel"),
                Arguments.of(BannerOrientation.WALL_PERPENDICULAR, "wall_perpendicular"));
    }

    @ParameterizedTest(name = "{1} round-trips")
    @MethodSource("orientations")
    void stableNameRoundTrip(BannerOrientation orientation, String stableName) {
        JsonElement encoded = BannerOrientation.CODEC.encodeStart(JsonOps.INSTANCE, orientation).getOrThrow();
        BannerOrientation decoded = BannerOrientation.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(stableName, orientation.serializedName());
        assertEquals(stableName, encoded.getAsString());
        assertEquals(orientation, decoded);
    }

    @Test
    void unknownStableNameIsRejected() {
        DataResult<BannerOrientation> result = BannerOrientation.CODEC.parse(
                JsonOps.INSTANCE, new JsonPrimitive("ceiling"));

        assertTrue(result.error().isPresent());
    }
}

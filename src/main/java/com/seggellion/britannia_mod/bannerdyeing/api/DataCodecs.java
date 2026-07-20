package com.seggellion.britannia_mod.bannerdyeing.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import java.util.List;
import java.util.Objects;

/** Shared structural codecs for the banner and material-aware dyeing data contracts. */
public final class DataCodecs {
    public static final Codec<Integer> CURRENT_SCHEMA_VERSION = Codec.INT.validate(version ->
            version == BannerDyeingConstants.CURRENT_SCHEMA_VERSION
                    ? DataResult.success(version)
                    : DataResult.error(() -> "Unsupported schema_version " + version
                            + "; expected " + BannerDyeingConstants.CURRENT_SCHEMA_VERSION));
    public static final Codec<String> NON_BLANK_STRING = Codec.STRING.validate(value ->
            value.isBlank()
                    ? DataResult.error(() -> "Value must not be blank")
                    : DataResult.success(value));
    /** Canonical colours use an uppercase, six-digit {@code #RRGGBB} string. */
    public static final Codec<String> CANONICAL_SRGB = Codec.STRING.validate(value ->
            value.matches("#[0-9A-F]{6}")
                    ? DataResult.success(value)
                    : DataResult.error(() -> "sRGB colour must use canonical uppercase #RRGGBB format: " + value));
    public static final Codec<Double> FINITE_DOUBLE = Codec.DOUBLE.validate(value ->
            Double.isFinite(value)
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Number must be finite: " + value));
    public static final Codec<Double> NON_NEGATIVE_FINITE_DOUBLE = FINITE_DOUBLE.validate(value ->
            value >= 0.0
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Number must not be negative: " + value));
    public static final Codec<List<Double>> OKLAB_COMPONENTS = FINITE_DOUBLE.listOf(3, 3);
    public static final Codec<List<String>> TAGS = NON_BLANK_STRING.listOf(0, 128);

    private DataCodecs() {
    }

    public static void requireCurrentSchema(int schemaVersion) {
        if (schemaVersion != BannerDyeingConstants.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema_version " + schemaVersion
                    + "; expected " + BannerDyeingConstants.CURRENT_SCHEMA_VERSION);
        }
    }

    public static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static String requireCanonicalSrgb(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!value.matches("#[0-9A-F]{6}")) {
            throw new IllegalArgumentException(fieldName + " must use canonical uppercase #RRGGBB format");
        }
        return value;
    }

    public static List<Double> requireOklab(List<Double> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.size() != 3) {
            throw new IllegalArgumentException(fieldName + " must contain exactly three components");
        }
        if (value.stream().anyMatch(component -> component == null || !Double.isFinite(component))) {
            throw new IllegalArgumentException(fieldName + " components must be finite");
        }
        return List.copyOf(value);
    }
}

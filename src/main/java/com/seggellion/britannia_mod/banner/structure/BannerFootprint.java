package com.seggellion.britannia_mod.banner.structure;

import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable rectangular occupancy derived only from a definition's declared dimensions. */
public record BannerFootprint(int width, int height, List<BannerLocalOffset> offsets) {
    public enum Failure {
        NONE,
        UNSUPPORTED_WIDTH,
        UNSUPPORTED_HEIGHT,
        MALFORMED
    }

    public record Result(BannerFootprint footprint, Failure failure) {
        public static Result success(BannerFootprint footprint) {
            return new Result(Objects.requireNonNull(footprint), Failure.NONE);
        }

        public static Result failure(Failure failure) {
            return new Result(null, Objects.requireNonNull(failure));
        }

        public boolean successful() {
            return footprint != null && failure == Failure.NONE;
        }
    }

    public BannerFootprint {
        offsets = List.copyOf(Objects.requireNonNull(offsets, "offsets"));
        if (width < 1 || width > 3 || height < 1 || height > 2 || offsets.size() != width * height
                || !offsets.contains(BannerLocalOffset.ANCHOR)) {
            throw new IllegalArgumentException("Malformed banner footprint");
        }
        List<BannerLocalOffset> expected = rectangularOffsets(width, height);
        if (!offsets.equals(expected)) {
            throw new IllegalArgumentException("Footprint must be a complete deterministic rectangle");
        }
    }

    public static Result fromDimensions(BannerDimensions dimensions) {
        Objects.requireNonNull(dimensions, "dimensions");
        if (dimensions.widthBlocks() < 1 || dimensions.widthBlocks() > 3) {
            return Result.failure(Failure.UNSUPPORTED_WIDTH);
        }
        if (dimensions.heightBlocks() < 1 || dimensions.heightBlocks() > 2) {
            return Result.failure(Failure.UNSUPPORTED_HEIGHT);
        }
        try {
            return Result.success(new BannerFootprint(dimensions.widthBlocks(), dimensions.heightBlocks(),
                    rectangularOffsets(dimensions.widthBlocks(), dimensions.heightBlocks())));
        } catch (IllegalArgumentException exception) {
            return Result.failure(Failure.MALFORMED);
        }
    }

    private static List<BannerLocalOffset> rectangularOffsets(int width, int height) {
        List<BannerLocalOffset> result = new ArrayList<>(width * height);
        for (int vertical = 0; vertical < height; vertical++) {
            for (int horizontal = 0; horizontal < width; horizontal++) {
                result.add(new BannerLocalOffset(horizontal, vertical));
            }
        }
        return List.copyOf(result);
    }
}

package com.seggellion.britannia_mod.banner.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Versioned anchor-owned placement state; facing remains authoritative in the anchor block state. */
public record BannerPlacedStructure(
        int schemaVersion,
        BannerOrientation orientation,
        int width,
        int height,
        List<BannerLocalOffset> occupiedOffsets) {
    private static final Codec<Decoded> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(Decoded::schemaVersion),
            BannerOrientation.CODEC.fieldOf("orientation").forGetter(Decoded::orientation),
            Codec.intRange(1, 3).fieldOf("width").forGetter(Decoded::width),
            Codec.intRange(1, 2).fieldOf("height").forGetter(Decoded::height),
            BannerLocalOffset.CODEC.listOf(1, 6).fieldOf("occupied_offsets").forGetter(Decoded::occupiedOffsets)
    ).apply(instance, Decoded::new));
    public static final Codec<BannerPlacedStructure> CODEC = RAW_CODEC.flatXmap(
            BannerPlacedStructure::decode,
            value -> DataResult.success(Decoded.from(value)));

    public BannerPlacedStructure {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(orientation, "orientation");
        occupiedOffsets = List.copyOf(Objects.requireNonNull(occupiedOffsets, "occupiedOffsets"));
        validate(orientation, width, height, occupiedOffsets);
    }

    public static BannerPlacedStructure fromFootprint(BannerFootprint footprint) {
        return fromFootprint(BannerOrientation.WALL_PARALLEL, footprint);
    }

    public static BannerPlacedStructure fromFootprint(
            BannerOrientation orientation, BannerFootprint footprint) {
        return new BannerPlacedStructure(BannerDyeingConstants.CURRENT_SCHEMA_VERSION,
                orientation, footprint.width(), footprint.height(), footprint.offsets());
    }

    /** Migration value for Milestone 10 saves that have no placement record. */
    public static BannerPlacedStructure legacyOneCell() {
        return new BannerPlacedStructure(BannerDyeingConstants.CURRENT_SCHEMA_VERSION,
                BannerOrientation.WALL_PARALLEL, 1, 1, List.of(BannerLocalOffset.ANCHOR));
    }

    public boolean contains(BannerLocalOffset offset) {
        return occupiedOffsets.contains(offset);
    }

    private static DataResult<BannerPlacedStructure> decode(Decoded decoded) {
        try {
            return DataResult.success(new BannerPlacedStructure(decoded.schemaVersion, decoded.orientation,
                    decoded.width, decoded.height, decoded.occupiedOffsets));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validate(
            BannerOrientation orientation, int width, int height, List<BannerLocalOffset> offsets) {
        if (width < 1 || width > 3 || height < 1 || height > 2 || offsets.size() != width * height
                || !offsets.contains(BannerLocalOffset.ANCHOR) || new HashSet<>(offsets).size() != offsets.size()) {
            throw new IllegalArgumentException("Placed structure is not a complete supported rectangle");
        }
        BannerFootprint.Result expected = BannerFootprint.fromDimensions(
                new com.seggellion.britannia_mod.banner.data.BannerDimensions(width, height, false));
        if (!expected.successful() || !offsets.equals(expected.footprint().offsets())) {
            throw new IllegalArgumentException("Occupied offsets are not in deterministic row-major order");
        }
    }

    private record Decoded(
            int schemaVersion,
            BannerOrientation orientation,
            int width,
            int height,
            List<BannerLocalOffset> occupiedOffsets) {
        private static Decoded from(BannerPlacedStructure value) {
            return new Decoded(value.schemaVersion, value.orientation, value.width, value.height,
                    value.occupiedOffsets);
        }
    }
}

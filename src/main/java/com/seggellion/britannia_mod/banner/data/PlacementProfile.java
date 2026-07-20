package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import java.util.Objects;

/** Minimal declared occupancy and support boundary; placement transforms remain deferred. */
public record PlacementProfile(
        int schemaVersion,
        PlacementProfileId id,
        BannerDimensions dimensions,
        boolean requiresWallSupport) {
    public static final Codec<PlacementProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(PlacementProfile::schemaVersion),
            PlacementProfileId.CODEC.fieldOf("id").forGetter(PlacementProfile::id),
            BannerDimensions.CODEC.fieldOf("dimensions").forGetter(PlacementProfile::dimensions),
            Codec.BOOL.fieldOf("requires_wall_support").forGetter(PlacementProfile::requiresWallSupport)
    ).apply(instance, PlacementProfile::new));

    public PlacementProfile {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimensions, "dimensions");
    }
}

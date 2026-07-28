package com.seggellion.britannia_mod.banner.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Declared occupancy, support boundary, and optional family-wide orientation mount selection. */
public record PlacementProfile(
        int schemaVersion,
        PlacementProfileId id,
        BannerDimensions dimensions,
        boolean requiresWallSupport,
        Map<BannerOrientation, ResourceLocation> orientationMountGeometry) {
    public static final Codec<PlacementProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(PlacementProfile::schemaVersion),
            PlacementProfileId.CODEC.fieldOf("id").forGetter(PlacementProfile::id),
            BannerDimensions.CODEC.fieldOf("dimensions").forGetter(PlacementProfile::dimensions),
            Codec.BOOL.fieldOf("requires_wall_support").forGetter(PlacementProfile::requiresWallSupport),
            Codec.unboundedMap(BannerOrientation.CODEC, ResourceLocation.CODEC)
                    .optionalFieldOf("orientation_mount_geometry", Map.of())
                    .forGetter(PlacementProfile::orientationMountGeometry)
    ).apply(instance, PlacementProfile::new));

    public PlacementProfile {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimensions, "dimensions");
        orientationMountGeometry = Map.copyOf(Objects.requireNonNull(
                orientationMountGeometry, "orientationMountGeometry"));
        if (!orientationMountGeometry.isEmpty()
                && !orientationMountGeometry.keySet().equals(java.util.Set.of(
                        BannerOrientation.WALL_PARALLEL, BannerOrientation.WALL_PERPENDICULAR))) {
            throw new IllegalArgumentException(
                    "orientationMountGeometry must define both wall orientations or neither");
        }
        if (orientationMountGeometry.size() == 2
                && orientationMountGeometry.get(BannerOrientation.WALL_PARALLEL)
                .equals(orientationMountGeometry.get(BannerOrientation.WALL_PERPENDICULAR))) {
            throw new IllegalArgumentException("Parallel and perpendicular mount geometry must be distinct");
        }
    }

    public PlacementProfile(
            int schemaVersion,
            PlacementProfileId id,
            BannerDimensions dimensions,
            boolean requiresWallSupport) {
        this(schemaVersion, id, dimensions, requiresWallSupport, Map.of());
    }
}

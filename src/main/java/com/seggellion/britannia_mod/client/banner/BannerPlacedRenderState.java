package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Immutable anchor snapshot prepared from synchronized block-entity and display state. */
public record BannerPlacedRenderState(
        BannerAppearanceState appearance,
        BannerOrientation orientation,
        Direction facing,
        int persistedWidth,
        int persistedHeight,
        List<BannerLocalOffset> occupiedOffsets,
        Direction spanAxis,
        Direction verticalAxis,
        BannerAnchorConvention anchorConvention,
        AABB renderBounds,
        List<BlockPos> lightingSamplePositions,
        BannerPlacedGeometryFamily geometryFamily,
        BannerPlacedRenderFailure failure,
        String diagnosticId,
        long resourceGeneration) {
    public BannerPlacedRenderState {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(facing, "facing");
        occupiedOffsets = List.copyOf(Objects.requireNonNull(occupiedOffsets, "occupiedOffsets"));
        Objects.requireNonNull(spanAxis, "spanAxis");
        Objects.requireNonNull(verticalAxis, "verticalAxis");
        Objects.requireNonNull(anchorConvention, "anchorConvention");
        Objects.requireNonNull(renderBounds, "renderBounds");
        lightingSamplePositions = List.copyOf(Objects.requireNonNull(
                lightingSamplePositions, "lightingSamplePositions"));
        Objects.requireNonNull(geometryFamily, "geometryFamily");
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
    }

    public boolean fallback() {
        return failure != BannerPlacedRenderFailure.NONE || appearance.fallback();
    }

    public BannerPlacedRenderKey key() {
        return new BannerPlacedRenderKey(appearance.key(), orientation, facing,
                persistedWidth, persistedHeight, appearance.geometry(), appearance.mountId(),
                appearance.dataGeneration(), resourceGeneration, failure, diagnosticId);
    }
}

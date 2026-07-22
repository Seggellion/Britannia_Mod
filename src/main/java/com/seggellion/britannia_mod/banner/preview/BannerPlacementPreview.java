package com.seggellion.britannia_mod.banner.preview;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record BannerPlacementPreview(
        BannerPlacementPreviewStatus status,
        BlockPos anchorPosition,
        Direction facing,
        BannerOrientation orientation,
        BannerDimensions dimensions,
        MountId mountId,
        List<BannerPlacementPreviewCell> cells,
        List<BlockPos> requiredSupportPositions,
        List<BlockPos> invalidSupportPositions,
        Optional<BlockPos> firstFailurePosition) {
    public BannerPlacementPreview {
        java.util.Objects.requireNonNull(status, "status");
        anchorPosition = anchorPosition.immutable();
        java.util.Objects.requireNonNull(facing, "facing");
        java.util.Objects.requireNonNull(orientation, "orientation");
        java.util.Objects.requireNonNull(dimensions, "dimensions");
        java.util.Objects.requireNonNull(mountId, "mountId");
        cells = List.copyOf(cells);
        requiredSupportPositions = requiredSupportPositions.stream().map(BlockPos::immutable).toList();
        invalidSupportPositions = invalidSupportPositions.stream().map(BlockPos::immutable).toList();
        firstFailurePosition = firstFailurePosition.map(BlockPos::immutable);
    }

    public boolean locallyPlaceable() {
        return status == BannerPlacementPreviewStatus.VALID
                || status == BannerPlacementPreviewStatus.UNKNOWN_SERVER_PROTECTION;
    }
}

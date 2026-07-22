package com.seggellion.britannia_mod.banner.preview;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDefinition;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Client-independent immutable view-model planner. It performs no mutation and grants no authority. */
public final class BannerPlacementPreviewPlanner {
    private BannerPlacementPreviewPlanner() {
    }

    public static Optional<BannerPlacementPreview> plan(
            BannerRenderDefinition definition,
            BannerInstanceState bannerState,
            BannerOrientation selectedOrientation,
            BlockPos clickedPosition,
            Direction clickedFace,
            BannerPlacementPreviewWorld world) {
        if (definition == null || bannerState == null || selectedOrientation == null || world == null) {
            return Optional.empty();
        }
        BlockPos anchor = clickedPosition.relative(clickedFace).immutable();
        if (!definition.id().equals(bannerState.bannerDefinitionId())) {
            return Optional.empty();
        }
        if (!clickedFace.getAxis().isHorizontal()) {
            return Optional.of(empty(BannerPlacementPreviewStatus.UNSUPPORTED_FACE, anchor, clickedFace,
                    selectedOrientation, definition, bannerState));
        }
        if (!definition.supportedOrientations().contains(selectedOrientation)) {
            return Optional.of(empty(BannerPlacementPreviewStatus.UNSUPPORTED_ORIENTATION, anchor, clickedFace,
                    selectedOrientation, definition, bannerState));
        }
        if (!definition.supportedMounts().contains(bannerState.mountId())) {
            return Optional.of(empty(BannerPlacementPreviewStatus.UNSUPPORTED_MOUNT, anchor, clickedFace,
                    selectedOrientation, definition, bannerState));
        }
        BannerFootprint.Result footprintResult = BannerFootprint.fromDimensions(definition.dimensions());
        if (!footprintResult.successful()) {
            return Optional.empty();
        }
        BannerFootprint footprint = footprintResult.footprint();
        List<BlockPos> supports = BannerStructureTransform.requiredSupportPositions(
                anchor, clickedFace, selectedOrientation, footprint.offsets());
        List<BannerPlacementPreviewCell> cells = new ArrayList<>();
        BannerPlacementPreviewStatus firstFailure = null;
        BlockPos firstFailurePosition = null;

        for (BannerLocalOffset offset : footprint.offsets()) {
            BlockPos position = BannerStructureTransform.worldPosition(
                    anchor, clickedFace, selectedOrientation, offset);
            boolean blocked = false;
            if (!world.inWorldBounds(position)) {
                if (firstFailure == null) {
                    firstFailure = BannerPlacementPreviewStatus.WORLD_BOUNDS;
                    firstFailurePosition = position;
                }
            } else if (!world.chunkLoaded(position)) {
                if (firstFailure == null) {
                    firstFailure = BannerPlacementPreviewStatus.REQUIRED_CHUNK_UNAVAILABLE;
                    firstFailurePosition = position;
                }
            } else if (!world.replaceable(position)) {
                blocked = true;
                if (firstFailure == null) {
                    firstFailure = BannerPlacementPreviewStatus.BLOCKED_CELL;
                    firstFailurePosition = position;
                }
            }
            cells.add(new BannerPlacementPreviewCell(offset, position,
                    offset.isAnchor() ? BannerCellRole.ANCHOR : BannerCellRole.PART, blocked));
        }

        List<BlockPos> invalidSupports = new ArrayList<>();
        for (BlockPos support : supports) {
            if (!world.inWorldBounds(support)) {
                if (firstFailure == null) {
                    firstFailure = BannerPlacementPreviewStatus.WORLD_BOUNDS;
                    firstFailurePosition = support;
                }
            } else if (!world.chunkLoaded(support)) {
                if (firstFailure == null) {
                    firstFailure = BannerPlacementPreviewStatus.REQUIRED_CHUNK_UNAVAILABLE;
                    firstFailurePosition = support;
                }
            } else if (!world.validWallSupport(support, clickedFace)) {
                invalidSupports.add(support);
                if (firstFailure == null) {
                    firstFailure = BannerPlacementPreviewStatus.INVALID_SUPPORT;
                    firstFailurePosition = support;
                }
            }
        }

        BannerPlacementPreviewStatus status = firstFailure != null ? firstFailure
                : world.serverProtectionKnownAllowed()
                        ? BannerPlacementPreviewStatus.VALID
                        : BannerPlacementPreviewStatus.UNKNOWN_SERVER_PROTECTION;
        return Optional.of(new BannerPlacementPreview(status, anchor, clickedFace, selectedOrientation,
                definition.dimensions(), bannerState.mountId(), cells, supports, invalidSupports,
                Optional.ofNullable(firstFailurePosition)));
    }

    private static BannerPlacementPreview empty(
            BannerPlacementPreviewStatus status, BlockPos anchor, Direction facing,
            BannerOrientation orientation, BannerRenderDefinition definition, BannerInstanceState state) {
        return new BannerPlacementPreview(status, anchor, facing, orientation, definition.dimensions(),
                state.mountId(), List.of(), List.of(), List.of(), Optional.empty());
    }
}

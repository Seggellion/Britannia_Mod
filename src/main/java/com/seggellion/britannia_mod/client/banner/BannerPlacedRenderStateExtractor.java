package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Pure client-side view extraction. It never validates gameplay or writes entity/world state. */
public final class BannerPlacedRenderStateExtractor {
    private BannerPlacedRenderStateExtractor() {
    }

    public static BannerPlacedRenderState extract(
            BannerBlockEntity entity,
            ClientBannerRenderPublication publication,
            BannerAssetAvailability assets,
            long resourceGeneration) {
        var state = entity.bannerState().orElse(null);
        BannerAppearanceState appearance = BannerAppearanceResolver.resolve(
                state, publication, assets, resourceGeneration);
        boolean validAnchorFacing = hasValidAnchorFacing(entity);
        Direction facing = anchorFacing(entity);
        BannerPlacedStructure structure = entity.placedStructure().orElse(null);
        BannerOrientation orientation = structure == null
                ? BannerOrientation.WALL_PARALLEL : structure.orientation();
        int width = structure == null ? 1 : structure.width();
        int height = structure == null ? 1 : structure.height();
        List<BannerLocalOffset> offsets = structure == null
                ? List.of(BannerLocalOffset.ANCHOR) : structure.occupiedOffsets();

        BannerPlacedRenderFailure failure = BannerPlacedRenderFailure.NONE;
        String diagnosticId = "";
        BannerPlacedGeometryFamily family = appearance.geometry()
                .flatMap(geometry -> BannerPlacedGeometryFamily.from(
                        geometry, appearance.definitionDimensions().orElse(null)))
                .orElse(BannerPlacedGeometryFamily.SMALL);
        if (entity.structurallyInvalid()) {
            failure = BannerPlacedRenderFailure.STRUCTURALLY_INVALID_STATE;
            diagnosticId = "structurally_invalid";
        } else if (state == null) {
            failure = BannerPlacedRenderFailure.MISSING_BANNER_STATE;
            diagnosticId = "banner_instance_state";
        } else if (structure == null) {
            failure = BannerPlacedRenderFailure.MISSING_PLACED_STRUCTURE;
            diagnosticId = "placed_structure";
        } else if (!validAnchorFacing) {
            failure = BannerPlacedRenderFailure.INVALID_ANCHOR_FACING;
            diagnosticId = "anchor_facing";
        } else if (appearance.fallback()) {
            failure = BannerPlacedRenderFailure.APPEARANCE_FALLBACK;
            diagnosticId = appearance.failure() + ":" + appearance.diagnosticId();
        } else if (appearance.geometry().flatMap(geometry -> BannerPlacedGeometryFamily.from(
                geometry, appearance.definitionDimensions().orElse(null))).isEmpty()) {
            failure = BannerPlacedRenderFailure.UNSUPPORTED_GEOMETRY_FAMILY;
            diagnosticId = appearance.geometry().map(Object::toString).orElse("missing");
        } else if (appearance.definitionDimensions().isEmpty()
                || appearance.definitionDimensions().orElseThrow().widthBlocks() != width
                || appearance.definitionDimensions().orElseThrow().heightBlocks() != height
                || !family.supports(width, height)) {
            failure = BannerPlacedRenderFailure.GEOMETRY_FOOTPRINT_MISMATCH;
            diagnosticId = appearance.geometry().orElseThrow() + ":" + width + "x" + height;
        }

        Direction resolvedFacing = facing;
        List<BlockPos> lightPositions = offsets.stream()
                .map(offset -> BannerStructureTransform.worldPosition(
                        entity.getBlockPos(), resolvedFacing, orientation, offset))
                .toList();
        return new BannerPlacedRenderState(appearance, orientation, facing, width, height, offsets,
                BannerStructureTransform.spanAxis(facing, orientation),
                BannerStructureTransform.verticalAxis(), BannerAnchorConvention.TOP_LEFT_OR_TOP_INNER,
                BannerPlacedRenderBounds.from(entity.getBlockPos(), facing, entity.placedStructure()),
                lightPositions, family, failure, diagnosticId, resourceGeneration);
    }

    static Direction anchorFacing(BannerBlockEntity entity) {
        BlockState state = entity.getBlockState();
        if (state.hasProperty(BannerBlock.FACING)) {
            Direction facing = state.getValue(BannerBlock.FACING);
            if (facing.getAxis().isHorizontal()) {
                return facing;
            }
        }
        return Direction.NORTH;
    }

    private static boolean hasValidAnchorFacing(BannerBlockEntity entity) {
        BlockState state = entity.getBlockState();
        return state.hasProperty(BannerBlock.FACING)
                && state.getValue(BannerBlock.FACING).getAxis().isHorizontal();
    }
}

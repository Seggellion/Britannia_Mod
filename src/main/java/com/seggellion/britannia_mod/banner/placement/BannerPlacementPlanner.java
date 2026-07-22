package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.banner.structure.BannerStructureCell;
import com.seggellion.britannia_mod.banner.structure.BannerStructureTransform;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionRegistry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Ordered, mutation-free construction of a complete rectangular wall-parallel placement transaction. */
public final class BannerPlacementPlanner {
    private BannerPlacementPlanner() {
    }

    public static BannerPlacementPlanningResult plan(
            BannerItem item,
            ItemStack stack,
            RegistrySnapshot snapshot,
            boolean registryAvailable,
            BlockPos clickedPos,
            Direction clickedFace,
            BannerBlock bannerBlock,
            BannerPartBlock partBlock,
            BannerPlacementWorld world) {
        if (stack.getItem() != item) return fail(BannerPlacementFailure.INVALID_ITEM);
        BannerInstanceState state = item.stateAccess().read(stack).orElse(null);
        if (state == null) return fail(BannerPlacementFailure.UNCONFIGURED_BANNER);
        if (!registryAvailable) return fail(BannerPlacementFailure.REGISTRY_UNAVAILABLE);

        BannerDefinition definition = snapshot.banners().find(state.bannerDefinitionId()).orElse(null);
        if (definition == null) {
            return fail(missingOrDisabled(snapshot.banners(), state.bannerDefinitionId(),
                    BannerPlacementFailure.DEFINITION_MISSING, BannerPlacementFailure.DEFINITION_DISABLED));
        }
        if (!snapshot.fabricMaterials().contains(state.materialId())) {
            return fail(missingOrDisabled(snapshot.fabricMaterials(), state.materialId(),
                    BannerPlacementFailure.MATERIAL_MISSING, BannerPlacementFailure.MATERIAL_DISABLED));
        }
        var material = snapshot.fabricMaterials().require(state.materialId());
        var palette = snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (palette == null) return fail(BannerPlacementFailure.PALETTE_MISSING);
        if (palette.entries().stream().noneMatch(entry -> entry.id().equals(state.resolvedColourId()))) {
            return fail(BannerPlacementFailure.COLOUR_MISSING);
        }
        if (state.sourcePigmentId().filter(id -> !snapshot.pigments().contains(id)).isPresent()) {
            var pigment = state.sourcePigmentId().orElseThrow();
            return fail(missingOrDisabled(snapshot.pigments(), pigment,
                    BannerPlacementFailure.PIGMENT_MISSING, BannerPlacementFailure.PIGMENT_DISABLED));
        }
        if (!snapshot.mounts().contains(state.mountId())) {
            return fail(missingOrDisabled(snapshot.mounts(), state.mountId(),
                    BannerPlacementFailure.MOUNT_MISSING, BannerPlacementFailure.MOUNT_DISABLED));
        }
        if (!definition.supportedMounts().contains(state.mountId())) {
            return fail(BannerPlacementFailure.UNSUPPORTED_MOUNT);
        }
        if (!definition.supportedOrientations().contains(BannerOrientation.WALL_PARALLEL)) {
            return fail(BannerPlacementFailure.UNSUPPORTED_ORIENTATION);
        }

        BannerFootprint.Result footprintResult = BannerFootprint.fromDimensions(definition.dimensions());
        if (!footprintResult.successful()) {
            return fail(switch (footprintResult.failure()) {
                case UNSUPPORTED_WIDTH -> BannerPlacementFailure.UNSUPPORTED_WIDTH;
                case UNSUPPORTED_HEIGHT -> BannerPlacementFailure.UNSUPPORTED_HEIGHT;
                default -> BannerPlacementFailure.MALFORMED_FOOTPRINT;
            });
        }
        BannerFootprint footprint = footprintResult.footprint();
        PlacementProfile profile = snapshot.placementProfiles().find(definition.placementProfile()).orElse(null);
        if (profile == null || !profile.requiresWallSupport()
                || profile.dimensions().widthBlocks() < footprint.width()
                || profile.dimensions().heightBlocks() < footprint.height()) {
            return fail(BannerPlacementFailure.UNSUPPORTED_PLACEMENT_PROFILE);
        }
        if (!clickedFace.getAxis().isHorizontal()) return fail(BannerPlacementFailure.INVALID_CLICKED_FACE);

        BlockPos anchorPos = clickedPos.relative(clickedFace).immutable();
        BlockState anchorState = bannerBlock.defaultBlockState().setValue(BannerBlock.FACING, clickedFace);
        BannerPlacedStructure placedStructure = BannerPlacedStructure.fromFootprint(footprint);
        List<BannerStructureCell> cells = new ArrayList<>(footprint.offsets().size());
        List<BlockPos> supports = new ArrayList<>(footprint.width());

        for (BannerLocalOffset offset : footprint.offsets()) {
            BlockPos worldPos = BannerStructureTransform.worldPosition(anchorPos, clickedFace, offset);
            BlockState placedState;
            try {
                placedState = offset.isAnchor() ? anchorState : partBlock.stateFor(clickedFace, offset);
            } catch (RuntimeException exception) {
                return fail(BannerPlacementFailure.PART_STATE_ENCODING_FAILURE);
            }
            cells.add(new BannerStructureCell(offset, worldPos,
                    offset.isAnchor() ? BannerCellRole.ANCHOR : BannerCellRole.PART,
                    world.blockState(worldPos), placedState));
            if (offset.vertical() == 0) {
                supports.add(worldPos.relative(clickedFace.getOpposite()).immutable());
            }
        }

        for (BannerStructureCell cell : cells) {
            if (!world.inWorldBounds(cell.worldPosition())) return fail(BannerPlacementFailure.WORLD_BOUND_FAILURE);
        }
        for (BannerStructureCell cell : cells) {
            if (!world.chunkLoaded(cell.worldPosition())) return fail(BannerPlacementFailure.REQUIRED_CHUNK_UNLOADED);
        }
        for (BlockPos support : supports) {
            if (!world.chunkLoaded(support)) return fail(BannerPlacementFailure.REQUIRED_CHUNK_UNLOADED);
        }
        for (BannerStructureCell cell : cells) {
            if (!world.targetReplaceable(cell.worldPosition())) return fail(BannerPlacementFailure.TARGET_OCCUPIED);
            if (world.unrelatedBannerCell(cell.worldPosition())) return fail(BannerPlacementFailure.UNRELATED_BANNER_CELL);
        }
        for (BlockPos support : supports) {
            if (!world.validWallSupport(support, clickedFace)) {
                return fail(BannerPlacementFailure.INVALID_WALL_SUPPORT);
            }
        }
        for (BannerStructureCell cell : cells) {
            if (!world.placementAllowed(cell.worldPosition(), clickedFace, stack)) {
                return fail(BannerPlacementFailure.PROTECTED_PLACEMENT);
            }
        }
        if (!world.canCreateBannerBlockEntity(anchorState)) {
            return fail(BannerPlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        }
        for (BannerStructureCell cell : cells) {
            if (cell.role() == BannerCellRole.PART && !world.canEncodePart(cell.placedState())) {
                return fail(BannerPlacementFailure.PART_STATE_ENCODING_FAILURE);
            }
        }
        if (!world.canAcceptState(state)) return fail(BannerPlacementFailure.STATE_TRANSFER_FAILURE);

        return BannerPlacementPlanningResult.success(new BannerPlacementPlan(
                anchorPos, clickedFace, BannerOrientation.WALL_PARALLEL, anchorState,
                state, placedStructure, cells, supports));
    }

    private static BannerPlacementPlanningResult fail(BannerPlacementFailure failure) {
        return BannerPlacementPlanningResult.failure(failure);
    }

    private static <I, T> BannerPlacementFailure missingOrDisabled(
            DefinitionRegistry<I, T> registry, I id,
            BannerPlacementFailure missing, BannerPlacementFailure disabled) {
        boolean isDisabled = registry.disabledEntries().stream().map(DefinitionEntry::id).anyMatch(id::equals);
        return isDisabled ? disabled : missing;
    }
}

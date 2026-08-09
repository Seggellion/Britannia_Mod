package com.seggellion.britannia_mod.structure.placement;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureDefinitionValidator;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.definition.StructureTransform;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.WorldPosition;
import com.seggellion.britannia_mod.structure.item.ConfiguredStructureItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Ordered, mutation-free construction of a complete configured large-structure transaction. */
public final class ShrinePlacementPlanner {
    private ShrinePlacementPlanner() {
    }

    /** Production path: configured item state is decoded and validated before any world query. */
    public static ShrinePlacementPlanningResult plan(
            ConfiguredStructureItem item,
            ItemStack stack,
            BlockPos clickedPosition,
            Direction clickedFace,
            Direction outwardFacing,
            LargeStructureAnchorBlock anchorBlock,
            LargeStructurePartBlock partBlock,
            ShrinePlacementWorld world) {
        return plan(item, stack, clickedPosition, clickedFace, outwardFacing,
                anchorBlock, partBlock, world, ShrineMonolithDefinitions.catalogue());
    }

    static ShrinePlacementPlanningResult plan(
            ConfiguredStructureItem item,
            ItemStack stack,
            BlockPos clickedPosition,
            Direction clickedFace,
            Direction outwardFacing,
            LargeStructureAnchorBlock anchorBlock,
            LargeStructurePartBlock partBlock,
            ShrinePlacementWorld world,
            StructureCatalogue catalogue) {
        var validation = item.stateAccess().validateForPlacement(stack, catalogue);
        if (!validation.valid()) {
            return fail(switch (validation.status()) {
                case INVALID_ITEM -> ShrinePlacementFailure.INVALID_ITEM;
                case UNSUPPORTED_FAMILY -> ShrinePlacementFailure.UNSUPPORTED_FAMILY;
                case FAMILY_MISSING -> ShrinePlacementFailure.FAMILY_MISSING;
                case VARIANT_MISSING -> ShrinePlacementFailure.VARIANT_MISSING;
                case VARIANT_DISABLED -> ShrinePlacementFailure.VARIANT_DISABLED;
                case INCOMPATIBLE_VARIANT -> ShrinePlacementFailure.INCOMPATIBLE_VARIANT;
                case VALID -> throw new IllegalStateException("Valid item state must be present");
            });
        }
        var state = validation.state().orElseThrow();
        return plan(item, stack, state.familyId(), state.variantId(), clickedPosition, clickedFace,
                outwardFacing, anchorBlock, partBlock, world, catalogue);
    }

    public static ShrinePlacementPlanningResult plan(
            ConfiguredStructureItem item,
            ItemStack stack,
            FamilyId familyId,
            VariantId variantId,
            BlockPos clickedPosition,
            Direction clickedFace,
            Direction outwardFacing,
            LargeStructureAnchorBlock anchorBlock,
            LargeStructurePartBlock partBlock,
            ShrinePlacementWorld world) {
        return plan(item, stack, familyId, variantId, clickedPosition, clickedFace, outwardFacing,
                anchorBlock, partBlock, world, ShrineMonolithDefinitions.catalogue());
    }

    static ShrinePlacementPlanningResult plan(
            ConfiguredStructureItem item,
            ItemStack stack,
            FamilyId familyId,
            VariantId variantId,
            BlockPos clickedPosition,
            Direction clickedFace,
            Direction outwardFacing,
            LargeStructureAnchorBlock anchorBlock,
            LargeStructurePartBlock partBlock,
            ShrinePlacementWorld world,
            StructureCatalogue catalogue) {
        if (stack.getItem() != item) {
            return fail(ShrinePlacementFailure.INVALID_ITEM);
        }
        if (!familyId.equals(item.familyId())) {
            return fail(ShrinePlacementFailure.UNSUPPORTED_FAMILY);
        }
        Family family = catalogue.family(familyId).orElse(null);
        if (family == null) {
            return fail(ShrinePlacementFailure.FAMILY_MISSING);
        }
        Variant variant = catalogue.variant(familyId, variantId).orElse(null);
        if (variant == null) {
            return fail(ShrinePlacementFailure.VARIANT_MISSING);
        }
        if (!variant.enabled()) {
            return fail(ShrinePlacementFailure.VARIANT_DISABLED);
        }
        if (!variant.playerFacing()
                || !StructureDefinitionValidator.compatible(family, variant)
                || family.footprint().size() != item.expectedCells()) {
            return fail(ShrinePlacementFailure.INCOMPATIBLE_VARIANT);
        }
        if (clickedFace != Direction.UP) {
            return fail(ShrinePlacementFailure.INVALID_CLICKED_FACE);
        }
        if (!outwardFacing.getAxis().isHorizontal()) {
            return fail(ShrinePlacementFailure.INVALID_FACING);
        }

        BlockPos anchorPosition = clickedPosition.relative(Direction.UP).immutable();
        BlockState anchorState = anchorBlock.defaultBlockState()
                .setValue(LargeStructureAnchorBlock.FACING, outwardFacing);
        List<CellProjection> projections = new ArrayList<>(family.footprint().size());
        for (LocalOffset offset : family.footprint()) {
            BlockPos worldPosition = worldPosition(anchorPosition, outwardFacing, offset);
            BlockState placedState;
            try {
                placedState = offset.equals(LocalOffset.ANCHOR)
                        ? anchorState
                        : partBlock.stateFor(outwardFacing, offset);
            } catch (RuntimeException exception) {
                return fail(ShrinePlacementFailure.PART_STATE_ENCODING_FAILURE);
            }
            projections.add(new CellProjection(
                    offset,
                    worldPosition,
                    offset.equals(LocalOffset.ANCHOR) ? StructureCellRole.ANCHOR : StructureCellRole.PART,
                    placedState));
        }
        if (new HashSet<>(projections.stream().map(CellProjection::offset).toList()).size()
                    != item.expectedCells()
                || new HashSet<>(projections.stream().map(CellProjection::worldPosition).toList()).size()
                    != item.expectedCells()) {
            return fail(ShrinePlacementFailure.INCOMPATIBLE_VARIANT);
        }

        for (CellProjection cell : projections) {
            if (!world.inWorldBounds(cell.worldPosition())) {
                return fail(ShrinePlacementFailure.WORLD_BOUND_FAILURE);
            }
        }
        for (CellProjection cell : projections) {
            if (!world.chunkLoaded(cell.worldPosition())) {
                return fail(ShrinePlacementFailure.REQUIRED_CHUNK_UNLOADED);
            }
        }

        for (CellProjection cell : projections) {
            if (!world.targetReplaceable(cell.worldPosition())) {
                return fail(ShrinePlacementFailure.TARGET_OCCUPIED);
            }
        }
        for (CellProjection cell : projections) {
            if (world.unrelatedLargeStructureCell(cell.worldPosition())) {
                return fail(ShrinePlacementFailure.UNRELATED_STRUCTURE_CELL);
            }
        }

        List<BlockPos> authorizedPositions = new ArrayList<>(projections.size());
        boolean allAuthorized = true;
        for (CellProjection cell : projections) {
            boolean authorized = world.placementAllowed(cell.worldPosition(), outwardFacing, stack);
            allAuthorized &= authorized;
            if (authorized) {
                authorizedPositions.add(cell.worldPosition());
            }
        }
        if (!allAuthorized) {
            return fail(ShrinePlacementFailure.PROTECTED_PLACEMENT);
        }
        for (CellProjection cell : projections) {
            if (cell.role() == StructureCellRole.PART && !world.canEncodePart(cell.placedState())) {
                return fail(ShrinePlacementFailure.PART_STATE_ENCODING_FAILURE);
            }
        }
        if (!world.canCreateAnchorBlockEntity(anchorState)) {
            return fail(ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        }

        PlacedStructureState state = new PlacedStructureState(
                family.id(), variant.id(), outwardFacing, family.footprint());
        if (!world.canInitializeAnchor(anchorState, state)) {
            return fail(ShrinePlacementFailure.ANCHOR_INITIALIZATION_FAILURE);
        }
        List<StructureCell> cells = projections.stream()
                .map(cell -> new StructureCell(
                        cell.offset(),
                        cell.worldPosition(),
                        cell.role(),
                        world.blockState(cell.worldPosition()),
                        cell.placedState()))
                .toList();
        List<ChunkPos> requiredChunks = cells.stream()
                .map(cell -> new ChunkPos(cell.worldPosition()))
                .distinct()
                .toList();
        return ShrinePlacementPlanningResult.success(
                new ShrinePlacementPlan(
                        anchorPosition,
                        anchorState,
                        state,
                        cells,
                        requiredChunks,
                        authorizedPositions,
                        true));
    }

    public static BlockPos worldPosition(
            BlockPos anchorPosition, Direction facing, LocalOffset offset) {
        WorldPosition transformed = StructureTransform.worldPosition(
                LargeStructurePartBlock.worldPosition(anchorPosition),
                LargeStructurePartBlock.horizontalFacing(facing),
                offset);
        return new BlockPos(transformed.x(), transformed.y(), transformed.z());
    }

    private static ShrinePlacementPlanningResult fail(ShrinePlacementFailure failure) {
        return ShrinePlacementPlanningResult.failure(failure);
    }

    private record CellProjection(
            LocalOffset offset,
            BlockPos worldPosition,
            StructureCellRole role,
            BlockState placedState) {
    }
}

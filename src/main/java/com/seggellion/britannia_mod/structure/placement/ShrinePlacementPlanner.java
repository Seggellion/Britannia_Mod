package com.seggellion.britannia_mod.structure.placement;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.definition.StructureTransform;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.WorldPosition;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.multiblock.StructureCell;
import com.seggellion.britannia_mod.structure.multiblock.StructureCellRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Ordered, mutation-free construction of the complete diagnostic shrine transaction. */
public final class ShrinePlacementPlanner {
    private ShrinePlacementPlanner() {
    }

    public static ShrinePlacementPlanningResult plan(
            ShrineItem item,
            ItemStack stack,
            FamilyId familyId,
            VariantId variantId,
            BlockPos clickedPosition,
            Direction clickedFace,
            Direction outwardFacing,
            LargeStructureAnchorBlock anchorBlock,
            LargeStructurePartBlock partBlock,
            ShrinePlacementWorld world) {
        if (stack.getItem() != item) {
            return fail(ShrinePlacementFailure.INVALID_ITEM);
        }
        if (!familyId.equals(ShrineMonolithDefinitions.SHRINE)) {
            return fail(ShrinePlacementFailure.UNSUPPORTED_FAMILY);
        }
        Family family = ShrineMonolithDefinitions.catalogue().family(familyId).orElse(null);
        if (family == null) {
            return fail(ShrinePlacementFailure.FAMILY_MISSING);
        }
        Variant variant = ShrineMonolithDefinitions.catalogue().variant(familyId, variantId).orElse(null);
        if (variant == null) {
            return fail(ShrinePlacementFailure.VARIANT_MISSING);
        }
        if (!variant.enabled() || !variant.playerFacing()) {
            return fail(ShrinePlacementFailure.VARIANT_DISABLED);
        }
        if (!variant.familyId().equals(family.id())
                || !variant.dimensions().equals(family.dimensions())
                || !variant.footprint().equals(family.footprint())
                || family.footprint().size() != 4) {
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

        List<StructureCell> cells = projections.stream()
                .map(cell -> new StructureCell(
                        cell.offset(),
                        cell.worldPosition(),
                        cell.role(),
                        world.blockState(cell.worldPosition()),
                        cell.placedState()))
                .toList();
        for (StructureCell cell : cells) {
            if (world.unrelatedLargeStructureCell(cell.worldPosition())) {
                return fail(ShrinePlacementFailure.UNRELATED_STRUCTURE_CELL);
            }
            if (!world.targetReplaceable(cell.worldPosition())) {
                return fail(ShrinePlacementFailure.TARGET_OCCUPIED);
            }
        }
        for (StructureCell cell : cells) {
            if (!world.placementAllowed(cell.worldPosition(), outwardFacing, stack)) {
                return fail(ShrinePlacementFailure.PROTECTED_PLACEMENT);
            }
        }
        if (!world.canCreateAnchorBlockEntity(anchorState)) {
            return fail(ShrinePlacementFailure.BLOCK_ENTITY_CREATION_FAILURE);
        }
        for (StructureCell cell : cells) {
            if (cell.role() == StructureCellRole.PART && !world.canEncodePart(cell.placedState())) {
                return fail(ShrinePlacementFailure.PART_STATE_ENCODING_FAILURE);
            }
        }

        PlacedStructureState state = new PlacedStructureState(
                family.id(), variant.id(), outwardFacing, family.footprint());
        return ShrinePlacementPlanningResult.success(
                new ShrinePlacementPlan(anchorPosition, anchorState, state, cells));
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

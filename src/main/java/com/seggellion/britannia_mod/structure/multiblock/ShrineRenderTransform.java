package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureTransform;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.WorldPosition;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Pure, documented transform contract shared by renderer bounds and focused tests. */
public final class ShrineRenderTransform {
    public static final double MODEL_VOXELS_PER_BLOCK = 16.0;
    public static final double TOLERANCE = 1.0 / 128.0;
    public static final AABB ALL_FACINGS_LOCAL_BOUNDS = new AABB(
            -1.0 - TOLERANCE, -TOLERANCE, -1.0 - TOLERANCE,
            2.0 + TOLERANCE, 1.0 + TOLERANCE, 2.0 + TOLERANCE);
    public static final AABB MONOLITH_ALL_FACINGS_LOCAL_BOUNDS = new AABB(
            -2.0 - TOLERANCE, -TOLERANCE, -2.0 - TOLERANCE,
            3.0 + TOLERANCE, 3.0 + TOLERANCE, 3.0 + TOLERANCE);
    private static final Map<Direction, Float> ROTATIONS = Map.of(
            Direction.NORTH, 0.0F,
            Direction.EAST, -90.0F,
            Direction.SOUTH, 180.0F,
            Direction.WEST, 90.0F);

    private ShrineRenderTransform() {
    }

    public static float yRotationDegrees(Direction facing) {
        Float rotation = ROTATIONS.get(facing);
        if (rotation == null) {
            throw new IllegalArgumentException("Shrine facing must be horizontal: " + facing);
        }
        return rotation;
    }

    public static AABB worldBounds(BlockPos anchor) {
        return ALL_FACINGS_LOCAL_BOUNDS.move(anchor);
    }

    public static AABB worldBounds(
            BlockPos anchor, Optional<PlacedStructureState> placedState) {
        AABB local = placedState
                .filter(state -> state.familyId().equals(ShrineMonolithDefinitions.MONOLITH))
                .map(ignored -> MONOLITH_ALL_FACINGS_LOCAL_BOUNDS)
                .orElse(ALL_FACINGS_LOCAL_BOUNDS);
        return local.move(anchor);
    }

    public static double renderOffsetBlocks(FamilyId familyId) {
        return familyId.equals(ShrineMonolithDefinitions.MONOLITH)
                ? ShrineMonolithDefinitions.MONOLITH_RENDER_OFFSET.y() / 16.0 : 0.0;
    }

    public static double renderOffsetBlocks(LargeStructureAnchorBlockEntity anchor) {
        return anchor.placedState()
                .map(PlacedStructureState::familyId)
                .map(ShrineRenderTransform::renderOffsetBlocks)
                .orElse(0.0);
    }

    /**
     * Converts unrotated Bedrock JSON cube bounds to GeckoLib renderer coordinates.
     * GeckoLib mirrors Bedrock X and converts all model voxels to Minecraft blocks.
     */
    public static AABB geckoRendererBounds(AABB bedrockModelVoxelBounds) {
        return new AABB(
                -bedrockModelVoxelBounds.maxX / MODEL_VOXELS_PER_BLOCK,
                bedrockModelVoxelBounds.minY / MODEL_VOXELS_PER_BLOCK,
                bedrockModelVoxelBounds.minZ / MODEL_VOXELS_PER_BLOCK,
                -bedrockModelVoxelBounds.minX / MODEL_VOXELS_PER_BLOCK,
                bedrockModelVoxelBounds.maxY / MODEL_VOXELS_PER_BLOCK,
                bedrockModelVoxelBounds.maxZ / MODEL_VOXELS_PER_BLOCK);
    }

    /** Mirrors GeoBlockRenderer's anchor-center translation followed by its +Y facing rotation. */
    public static AABB renderedShrineEnvelope(
            BlockPos anchor, Direction facing, AABB bedrockModelVoxelBounds) {
        AABB rendererBounds = geckoRendererBounds(bedrockModelVoxelBounds);
        double radians = Math.toRadians(yRotationDegrees(facing));
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[] {rendererBounds.minX, rendererBounds.maxX}) {
            for (double z : new double[] {rendererBounds.minZ, rendererBounds.maxZ}) {
                double rotatedX = cosine * x + sine * z;
                double rotatedZ = -sine * x + cosine * z;
                minX = Math.min(minX, rotatedX);
                minZ = Math.min(minZ, rotatedZ);
                maxX = Math.max(maxX, rotatedX);
                maxZ = Math.max(maxZ, rotatedZ);
            }
        }
        return new AABB(
                minX + anchor.getX() + 0.5,
                rendererBounds.minY + anchor.getY(),
                minZ + anchor.getZ() + 0.5,
                maxX + anchor.getX() + 0.5,
                rendererBounds.maxY + anchor.getY(),
                maxZ + anchor.getZ() + 0.5);
    }

    /** Rendered envelope including the family-specific vertical model offset. */
    public static AABB renderedStructureEnvelope(
            BlockPos anchor,
            Direction facing,
            AABB bedrockModelVoxelBounds,
            FamilyId familyId) {
        return renderedShrineEnvelope(anchor, facing, bedrockModelVoxelBounds)
                .move(0.0, renderOffsetBlocks(familyId), 0.0);
    }

    /** Exact world-cell union for the four-cell shrine footprint at one facing. */
    public static AABB occupiedShrineFootprintEnvelope(BlockPos anchor, Direction facing) {
        return occupiedFootprintEnvelope(anchor, facing, ShrineMonolithDefinitions.SHRINE);
    }

    /** Exact world-cell union for the eighteen-cell monolith footprint at one facing. */
    public static AABB occupiedMonolithFootprintEnvelope(BlockPos anchor, Direction facing) {
        return occupiedFootprintEnvelope(anchor, facing, ShrineMonolithDefinitions.MONOLITH);
    }

    private static AABB occupiedFootprintEnvelope(
            BlockPos anchor, Direction facing, FamilyId familyId) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (LocalOffset offset : ShrineMonolithDefinitions.catalogue()
                .family(familyId).orElseThrow().footprint()) {
            WorldPosition cell = StructureTransform.worldPosition(
                    new WorldPosition(anchor.getX(), anchor.getY(), anchor.getZ()),
                    LargeStructurePartBlock.horizontalFacing(facing), offset);
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x() + 1.0);
            maxY = Math.max(maxY, cell.y() + 1.0);
            maxZ = Math.max(maxZ, cell.z() + 1.0);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

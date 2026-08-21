package com.seggellion.britannia_mod.resource.placement;

import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapeOffset;
import com.seggellion.britannia_mod.resource.shape.ShapePlan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A deposit decided but not yet written: where it is, what it is, and exactly which cells it wants.
 *
 * <p>The boundary between geometry and world. Everything above this is pure — offsets relative to
 * an origin, planned without a level in sight. Everything below it needs a world. Nothing here
 * mutates anything.
 *
 * <h2>Chunk slicing</h2>
 * {@link #positionsIn(ChunkPos)} is a filter over an already-computed plan, not a re-plan. That is
 * the whole reason a deposit can cross a chunk border safely: the plan was produced without reading
 * a single block, so a deposit spanning four chunks can be sliced four ways without any of those
 * chunks existing, let alone being loaded. The union of the slices is the plan by construction,
 * because slicing partitions a list, and the order the slices are taken in cannot matter for the
 * same reason.
 */
public record PlannedDeposit(
        ResourceDefinition resource,
        String dimensionId,
        BlockPos origin,
        ShapeConfig config,
        ShapePlan plan) {

    /** Every cell this deposit wants, in world coordinates, canonically ordered. */
    public List<BlockPos> positions() {
        List<BlockPos> positions = new ArrayList<>(plan.count());
        for (ShapeOffset offset : plan.offsets()) {
            positions.add(origin.offset(offset.x(), offset.y(), offset.z()));
        }
        return positions;
    }

    /** The cells that fall inside one chunk. */
    public List<BlockPos> positionsIn(ChunkPos chunk) {
        List<BlockPos> positions = new ArrayList<>();
        for (ShapeOffset offset : plan.offsets()) {
            int x = origin.getX() + offset.x();
            int z = origin.getZ() + offset.z();
            if ((x >> 4) == chunk.x && (z >> 4) == chunk.z) {
                positions.add(new BlockPos(x, origin.getY() + offset.y(), z));
            }
        }
        return positions;
    }

    /** Every chunk this deposit reaches into, derived from its bounds without reading the world. */
    public List<ChunkPos> touchedChunks() {
        int minX = (origin.getX() + plan.bounds().minX()) >> 4;
        int maxX = (origin.getX() + plan.bounds().maxX()) >> 4;
        int minZ = (origin.getZ() + plan.bounds().minZ()) >> 4;
        int maxZ = (origin.getZ() + plan.bounds().maxZ()) >> 4;
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    public int count() {
        return plan.count();
    }
}

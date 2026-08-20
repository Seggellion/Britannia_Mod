package com.seggellion.britannia_mod.structure;

import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.Map;


/**
 * Manages registration of structure bounding boxes for efficient lookups.
 *
 * <h2>A chunk is not an address</h2>
 * These were once indexed by chunk coordinate alone, which is an identity only in a world that
 * has one dimension. Two houses at the same X/Z in the Overworld and the Nether shared a key,
 * and therefore shared each other's entry: a door in one could resolve the other's house, an
 * owner's build rights leaked across, and unregistering either emptied the list both were in.
 * The key is now the dimension and the chunk together.
 */
public class StructureRegionManager {

    /** One world's one chunk. Both halves are needed; neither is an identity alone. */
    public record RegionKey(ResourceKey<Level> dimension, long chunk) {
        static RegionKey of(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            return new RegionKey(dimension == null ? Level.OVERWORLD : dimension,
                    ChunkPos.asLong(chunkX, chunkZ));
        }
    }

    // Maps a dimension+chunk key to a list of structure records
    private static final ConcurrentHashMap<RegionKey, List<StructureRecord>> chunkStructureMap =
            new ConcurrentHashMap<>();

    /**
     * Registers a structure record in every chunk it overlaps, in its own dimension.
     */
   public static void registerStructure(StructureRecord structureRecord) {
    AABB bb    = structureRecord.getFullBox();
    UUID uuid  = structureRecord.getHouseUuid();
    ResourceKey<Level> dimension = structureRecord.getDimension();
    ChunkRange range = chunkRangeFromBoundingBox(bb);

    for (int x = range.minChunkX; x <= range.maxChunkX; x++) {
        for (int z = range.minChunkZ; z <= range.maxChunkZ; z++) {
            RegionKey chunkKey = RegionKey.of(dimension, x, z);

            chunkStructureMap.compute(chunkKey, (k, list) -> {
                if (list == null) list = new ArrayList<>();

                // ‑‑ remove any old entry with the same house UUID
                list.removeIf(rec -> rec.getHouseUuid().equals(uuid));

                list.add(structureRecord);
                return list;
            });
        }
    }
}


    /**
     * Unregisters a structure record from every chunk it was registered in.
     *
     * <p>Only from its own dimension. A house elsewhere at the same coordinates is a different
     * house and is left alone.
     */
    public static void unregisterStructure(StructureRecord target) {
        AABB bb = target.getFullBox();
        ResourceKey<Level> dimension = target.getDimension();
        ChunkRange range = chunkRangeFromBoundingBox(bb);

        for (int x = range.minChunkX; x <= range.maxChunkX; x++) {
            for (int z = range.minChunkZ; z <= range.maxChunkZ; z++) {
                RegionKey chunkKey = RegionKey.of(dimension, x, z);
chunkStructureMap.computeIfPresent(chunkKey, (k, list) -> {
    list.removeIf(rec -> rec.getHouseUuid().equals(target.getHouseUuid()));
    return list.isEmpty() ? null : list;
});

            }
        }
    }

    /**
     * Returns all structure records in a given chunk of a given dimension.
     */
    public static List<StructureRecord> getStructuresInChunk(
            ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return chunkStructureMap.getOrDefault(RegionKey.of(dimension, chunkX, chunkZ), List.of());
    }


public static Map<RegionKey, List<StructureRecord>> getChunkStructureMap() {
    return chunkStructureMap;
}

    /**
     * Determines the range of chunk coordinates overlapped by the bounding box.
     */
    private static ChunkRange chunkRangeFromBoundingBox(AABB bb) {
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(bb.minX));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(bb.maxX));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bb.minZ));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bb.maxZ));
        return new ChunkRange(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    /**
     * Simple value type for a chunk range.
     */
    private record ChunkRange(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {}

/**
 * The house with this UUID, in whichever dimension it stands.
 *
 * <p>Not dimension-scoped, and deliberately: a house UUID is unique across the shard, so the
 * question "which house is this" has one answer wherever it is asked from.
 */
@org.jetbrains.annotations.Nullable
public static StructureRecord getStructureByUuid(java.util.UUID uuid) {
    // ConcurrentHashMap values() is weakly-consistent; fine for a quick scan
    for (var list : chunkStructureMap.values()) {
        for (StructureRecord rec : list) {
            if (rec.getHouseUuid().equals(uuid)) {
                return rec;
            }
        }
    }
    return null;    // not found
}

}

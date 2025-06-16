package com.seggellion.britannia_mod.structure;

import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Manages registration of structure bounding boxes for efficient lookups.
 */
public class StructureRegionManager {

    // Maps a chunk key (long) to a list of structure records
    private static final ConcurrentHashMap<Long, List<StructureRecord>> chunkStructureMap = new ConcurrentHashMap<>();

    /**
     * Registers a structure record in every chunk it overlaps.
     */
   public static void registerStructure(StructureRecord structureRecord) {
    AABB bb    = structureRecord.getFullBox();
    UUID uuid  = structureRecord.getHouseUuid();
    ChunkRange range = chunkRangeFromBoundingBox(bb);

    for (int x = range.minChunkX; x <= range.maxChunkX; x++) {
        for (int z = range.minChunkZ; z <= range.maxChunkZ; z++) {
            long chunkKey = ChunkPos.asLong(x, z);

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
     */
    public static void unregisterStructure(StructureRecord target) {
        AABB bb = target.getFullBox();
        ChunkRange range = chunkRangeFromBoundingBox(bb);

        for (int x = range.minChunkX; x <= range.maxChunkX; x++) {
            for (int z = range.minChunkZ; z <= range.maxChunkZ; z++) {
                long chunkKey = ChunkPos.asLong(x, z);
chunkStructureMap.computeIfPresent(chunkKey, (k, list) -> {
    list.removeIf(rec -> rec.getHouseUuid().equals(target.getHouseUuid()));
    return list.isEmpty() ? null : list;
});

            }
        }
    }

    /**
     * Returns all structure records in a given chunk.
     */
    public static List<StructureRecord> getStructuresInChunk(int chunkX, int chunkZ) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        return chunkStructureMap.getOrDefault(chunkKey, List.of());
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
}

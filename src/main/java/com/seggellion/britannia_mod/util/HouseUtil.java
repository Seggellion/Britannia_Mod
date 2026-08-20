package com.seggellion.britannia_mod.util;

import javax.annotation.Nullable;

import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Finds the {@link HouseLotBlockEntity} that owns a block.
 *
 * <h2>Why this is not a proximity search any more</h2>
 * It used to scan a 9x5x9 cube around the caller for any lot block at all. That works for a
 * 9x9 cottage whose single door is four blocks from its lot and nothing else, and it fails
 * everywhere else:
 *
 * <ul>
 *   <li>Three of the castle's four double doors are at z = 5, 13 and 28. All three are out of
 *       reach, today, in production.</li>
 *   <li>Every one of the two-story villa's doors sits at z = 5 or 6.</li>
 *   <li>One of the large patio's four doors is at z = 8.</li>
 * </ul>
 *
 * <p>A door that finds no lot takes {@code LockableDoorBlock}'s "not a private house" branch,
 * which never consults a key -- so it refuses the owner while its {@code locked} flag says
 * true, which is what every shipped structure bakes into its NBT. Widening the radius would
 * only move the boundary and would start binding doors to whichever neighbouring lot happened
 * to be nearest.
 *
 * <p>So the relationship is structural. A block belongs to the house whose registered region
 * contains it -- the same authoritative question {@code LockableDoorBlockEntity} already asks
 * to resolve a lock -- and the lot is then found by identity within that region rather than by
 * distance. Two houses built wall to wall cannot take each other's doors, however close their
 * lot blocks are, because containment is not a tie-break.
 */
public class HouseUtil {

    private HouseUtil() {}

    /**
     * The lot block entity of the house whose region contains {@code pos}, or {@code null} if
     * no registered house contains it.
     *
     * <p>Null also means "no region is registered here yet", which after a restart is every
     * house until {@code StructureRegionRehydrator} has finished. That is the same answer the
     * old scan gave in that window, and the callers already treat it as "not a private house".
     */
    public static @Nullable HouseLotBlockEntity findLot(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;

        StructureRecord record = enclosingStructure(pos);
        return record == null ? null : lotOf(server, record);
    }

    /**
     * The registered house containing {@code pos}.
     *
     * <p>Uses the full box rather than the structure box, matching the lock: the region extends
     * ten blocks below the building, so a door or a block in the basement resolves to the same
     * house as one on the ground floor.
     */
    public static @Nullable StructureRecord enclosingStructure(BlockPos pos) {
        List<StructureRecord> candidates = StructureRegionManager.getStructuresInChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        if (candidates.isEmpty()) return null;

        Vec3 centre = Vec3.atCenterOf(pos);
        for (StructureRecord record : candidates) {
            if (record.getFullBox() != null && record.getFullBox().contains(centre)) {
                return record;
            }
        }
        return null;
    }

    /**
     * The lot block belonging to {@code record}, matched on house UUID.
     *
     * <p>Searched within the region's own chunks and nowhere else, so the answer cannot be a
     * neighbour's lot. Only chunks already resident are examined -- a house is being interacted
     * with, so its chunks are loaded, and forcing a load from a door click is not worth it.
     */
    private static @Nullable HouseLotBlockEntity lotOf(ServerLevel level, StructureRecord record) {
        AABB box = record.getStructureBox() != null ? record.getStructureBox() : record.getFullBox();
        if (box == null) return null;

        UUID houseUuid = record.getHouseUuid();
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(box.maxX));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ));

        HouseLotBlockEntity unidentified = null;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof HouseLotBlockEntity lot)) continue;
                    if (!box.contains(Vec3.atCenterOf(lot.getBlockPos()))) continue;

                    if (houseUuid != null && houseUuid.equals(lot.getHouseUuid())) return lot;
                    // A lot inside this region that does not claim this house UUID. Kept as a
                    // last resort for regions restored before the lot block was written -- a
                    // named match always wins over it.
                    if (unidentified == null) unidentified = lot;
                }
            }
        }
        return unidentified;
    }
}

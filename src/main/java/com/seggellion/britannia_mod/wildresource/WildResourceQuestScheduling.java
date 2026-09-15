package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Brings a wild resource's next placement attempt forward in a bounded area (Rowan farming
 * questline M9 item 5).
 *
 * <p>Quest 1 sends the player to find a dung pile. Dung is a wild resource: the world tries to
 * place one in a given chunk every 6-12 minutes, at most two per chunk, no closer than 8 blocks to
 * another, only on dirt or coarse dirt, only where the target is replaceable and dry. A player who
 * accepts the quest in a well-trodden area can therefore be sent looking for something the world
 * has no reason to produce for another ten minutes.
 *
 * <p>What this does is the smallest possible intervention: it moves the <em>schedule</em> forward,
 * and nothing else. The attempt that then runs is the ordinary one --
 * {@link WildResourceSpawnScheduler#attempt} on the normal server tick, through
 * {@code WildResourceManager}'s own budget -- so the chunk cap, the spacing rule, the substrate
 * rule, the safety rule, the random probe count and the random candidate position are all exactly
 * what they would have been. The pile that results is a tracked node in
 * {@link WildResourceSavedData} like every other pile, which is what makes it harvestable,
 * respawnable, and reportable to Rails as a {@code wild_resource_harvest}.
 *
 * <p>It deliberately does not place a block. A command-placed pile would be untracked: harvesting
 * it would produce no node lookup, no ledger row and no quest action event, so the quest step it
 * was placed for would not advance.
 *
 * <p>It also never delays an attempt. A chunk already due, or due sooner than now, is left alone.
 */
public final class WildResourceQuestScheduling {

    /**
     * How far out, in chunks, the schedule is brought forward. Two chunks is a 5x5 square (80
     * blocks across) centred on the quest giver -- the area a player accepting the quest can
     * plausibly search on foot, and small enough that the extra attempts are a rounding error
     * against {@code WildResourceManager}'s per-tick budget of 32.
     */
    public static final int DEFAULT_CHUNK_RADIUS = 2;

    /** A hard ceiling on the radius, so a future caller cannot turn this into a world-wide sweep. */
    public static final int MAX_CHUNK_RADIUS = 4;

    private WildResourceQuestScheduling() {
    }

    /**
     * Brings forward the next attempt for {@code resourceId} in every chunk within
     * {@code chunkRadius} of {@code center}. Returns how many chunks were actually brought forward.
     */
    public static int expediteNear(ServerLevel level, BlockPos center, ResourceLocation resourceId,
                                   int chunkRadius) {
        if (level == null || center == null || resourceId == null) {
            return 0;
        }
        int radius = Math.max(0, Math.min(chunkRadius, MAX_CHUNK_RADIUS));
        WildResourceSavedData data = WildResourceSavedData.get(level);
        long now = level.getGameTime();
        ChunkPos origin = new ChunkPos(center);
        int expedited = 0;
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                ChunkPos chunk = new ChunkPos(origin.x + offsetX, origin.z + offsetZ);
                // UNSCHEDULED is Long.MAX_VALUE, so a chunk the manager has never visited is
                // covered by the same comparison as one whose cooldown is simply still running.
                if (data.nextAttempt(chunk, resourceId) > now) {
                    data.scheduleAttempt(chunk, resourceId, now);
                    expedited++;
                }
            }
        }
        return expedited;
    }

    /** The dung case, with the default radius. */
    public static int expediteDungNear(ServerLevel level, BlockPos center) {
        return expediteNear(level, center, WildResourceEntries.DUNG, DEFAULT_CHUNK_RADIUS);
    }
}

package com.seggellion.britannia_mod.worldstate;

import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import net.minecraft.server.MinecraftServer;

/**
 * The atomic-commit half of Milestone 13 NeoForge Slice 2: hands a validated {@link
 * WorldStateChangesResponse} to {@link ServiceNpcAssignmentsCache#applyWorldStateChanges}, and --
 * only when that reports {@link ServiceNpcAssignmentsCandidateApply.Applied} -- forces an
 * immediate, synchronous, durable flush, exactly the mechanism {@code BankTransferReceipts}
 * already established and proved for Milestone 8/10 (traced against the real API in that class's
 * own docs): {@code DimensionDataStorage#save()} writes every dirty {@code SavedData} through
 * NeoForge's temp-file-write, {@code fsync}, atomic-rename sequence. Reused verbatim here, not
 * reimplemented -- the durability guarantee is identical, not merely similar.
 *
 * Because {@link ServiceNpcAssignmentsCache#applyWorldStateChanges} updates its snapshot and its
 * {@code lastAppliedWorldStateVersion} together, from one successful result, with exactly one
 * {@link net.minecraft.world.level.saveddata.SavedData#setDirty()} between them, this one flush
 * call commits both to the same on-disk NBT tag in the same write -- there is no window in which
 * a crash could observe one updated without the other.
 *
 * On {@link ServiceNpcAssignmentsCandidateApply.Rejected}, no flush is ever requested: {@code
 * setDirty()} was never called, so nothing durable changes at all, matching this milestone's own
 * "never partially apply, never discard the prior state" invariant for that case.
 *
 * No credentials/Minecraft-Server-Key lookup happens here -- an earlier version needed one only
 * to supply a fallback owning-server identity for a newly-seen spawn point, which is no longer
 * necessary now that Rails sends the real value directly (see {@link
 * ServiceNpcAssignmentsCandidateApply}). By the time a response reaches this method it has
 * already been fetched over an authenticated connection, so there is nothing left for a repeated
 * credentials check here to add.
 */
public final class WorldStateSyncApply {
    private WorldStateSyncApply() {
    }

    public static ServiceNpcAssignmentsCandidateApply.Result applyAndCommit(
            MinecraftServer server, WorldStateChangesResponse response
    ) {
        ServiceNpcAssignmentsCache cache = ServiceNpcAssignmentsCache.get(server.overworld());
        ServiceNpcAssignmentsCandidateApply.Result result =
                cache.applyWorldStateChanges(response.changes(), response.toVersion());
        if (result instanceof ServiceNpcAssignmentsCandidateApply.Applied) {
            forceSynchronousFlush(server);
        }
        return result;
    }

    private static void forceSynchronousFlush(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }
}

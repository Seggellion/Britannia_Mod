package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryCache;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 18: the safe operator diagnostics snapshot for one
 * authoritative spawn post — everything the playbook asks an operator to see
 * (post UUID, assignment, city, type, revisions, last sync, pending outbox
 * depth, duplicate entity detection) collected read-only from the same
 * authoritative state the pipeline itself uses. Assembled here, not in the
 * command, so a GameTest can prove the numbers against a real post; the
 * command only formats.
 */
public final class SpawnPostDiagnostics {
    /** Radius for duplicate stamped-projection detection around the post. */
    private static final double DUPLICATE_SCAN_RADIUS = 64.0D;

    public record Snapshot(
            @Nullable UUID postId,
            @Nullable UUID cityPublicId,
            @Nullable String storedTypeKey,
            boolean economic,
            @Nullable String economicTypeKey,
            boolean enabled,
            String registrationState,
            long configurationRevision,
            @Nullable String lastErrorCode,
            @Nullable UUID assignedNpcPublicId,
            @Nullable String assignedNpcDisplayName,
            long assignmentRevision,
            @Nullable Long lastSuccessfulSyncEpochMillis,
            @Nullable Long lastAcknowledgedAtEpochMillis,
            int pendingOperationCount,
            @Nullable String economicRegistryRevision,
            int stampedEntityCount
    ) {
        /** More than one live stamped projection for one assignment = duplication. */
        public boolean duplicateEntitiesDetected() {
            return stampedEntityCount > 1;
        }
    }

    private SpawnPostDiagnostics() {
    }

    public static Snapshot collect(ServerLevel level, ServiceNpcSpawnBlockEntity post) {
        String storedKey = post.getServiceNpcTypeKey();
        boolean economic = EconomicNpcTypeKeys.isEconomic(storedKey);
        var registry = EconomicNpcRegistryCache.snapshot();

        return new Snapshot(
                post.getSpawnPointId(),
                post.getCityPublicId(),
                storedKey,
                economic,
                economic ? EconomicNpcTypeKeys.strip(storedKey) : null,
                post.isEnabled(),
                post.getRegistrationState().name(),
                post.getConfigurationRevision(),
                post.getLastErrorCode(),
                post.getAssignedNpcPublicId(),
                post.getAssignedNpcDisplayName(),
                post.getAssignmentRevision(),
                post.getLastSuccessfulSyncEpochMillis(),
                post.getLastAcknowledgedRecordedAtEpochMillis(),
                ServiceNpcSpawnPendingData.get(level).snapshot().size(),
                registry == null ? null : registry.revision(),
                countStampedEntities(level, post.getBlockPos(), post.getAssignedNpcPublicId())
        );
    }

    private static int countStampedEntities(ServerLevel level, BlockPos pos, @Nullable UUID worldNpcPublicId) {
        if (worldNpcPublicId == null) return 0;
        AABB area = new AABB(pos).inflate(DUPLICATE_SCAN_RADIUS);
        return level.getEntitiesOfClass(CitizenEntity.class, area,
                citizen -> citizen.isAlive() && worldNpcPublicId.equals(citizen.getWorldNpcPublicId())
        ).size();
    }
}

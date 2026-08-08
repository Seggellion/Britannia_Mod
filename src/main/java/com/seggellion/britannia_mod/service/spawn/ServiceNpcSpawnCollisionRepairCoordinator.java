package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-thread-only coordinator for the bounded, replay-safe A-to-B collision repair.
 * It never performs HTTP, loads a chunk, or scans claims or block entities globally.
 */
public final class ServiceNpcSpawnCollisionRepairCoordinator {
    public static final int MAX_REPAIRS_PER_CYCLE = 2;
    public static final int MAX_REPLACEMENTS = 3;
    public static final int UUID_GENERATION_ATTEMPTS = 32;
    private static final Logger LOGGER = LogUtils.getLogger();

    enum EvidenceVerdict {
        REPAIRABLE,
        SAME_LOCATION,
        INVALID,
        LOCAL_SERVER_KEY_UNAVAILABLE
    }

    private ServiceNpcSpawnCollisionRepairCoordinator() {}

    public static void processCycle(MinecraftServer server) {
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(server.overworld());
        data.snapshotCollisionRepairs(MAX_REPAIRS_PER_CYCLE)
            .forEach(record -> processRecord(server, data, record));
    }

    public static void reconcileBlock(ServerLevel level, ServiceNpcSpawnBlockEntity blockEntity) {
        UUID currentId = blockEntity.getSpawnPointId();
        if (currentId == null) return;
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        ServiceNpcSpawnLocation location = blockEntity.currentLocation(level);
        ServiceNpcSpawnPendingRecord direct = data.findPending(currentId);
        if (direct != null
                && direct.disposition() == ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
                && direct.location().equals(location)) {
            processRecord(level.getServer(), data, direct);
            return;
        }
        ServiceNpcSpawnPendingRecord staged = data.findStagedReplacement(currentId, location);
        if (staged != null) processRecord(level.getServer(), data, staged);
    }

    public static void processExactForGameTest(
            MinecraftServer server, UUID spawnPointId, UUID localServerKey
    ) {
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(server.overworld());
        ServiceNpcSpawnPendingRecord record = data.findPending(spawnPointId);
        if (record == null
                || record.disposition() != ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR) {
            return;
        }
        if (record.supersedesSpawnPointId() == null) {
            processPhaseA(server, data, record, localServerKey);
        } else {
            processPhaseB(server, data, record);
        }
    }

    private static void processRecord(
            MinecraftServer server,
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingRecord record
    ) {
        ServiceNpcSpawnPendingRecord current = data.findPending(record.spawnPointId());
        if (current == null || !current.token().equals(record.token())
                || current.disposition() != ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR) {
            return;
        }
        if (current.supersedesSpawnPointId() == null) {
            processPhaseA(server, data, current, currentServerKey(server));
        } else {
            processPhaseB(server, data, current);
        }
    }

    private static void processPhaseA(
            MinecraftServer server,
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingRecord source,
            @Nullable UUID localServerKey
    ) {
        EvidenceVerdict verdict = validateEvidence(source, localServerKey);
        if (verdict == EvidenceVerdict.LOCAL_SERVER_KEY_UNAVAILABLE) return;
        if (verdict == EvidenceVerdict.SAME_LOCATION) {
            fail(server, data, source, ServiceNpcSpawnFailureCodes.UUID_COLLISION_SAME_LOCATION);
            return;
        }
        if (verdict != EvidenceVerdict.REPAIRABLE) {
            fail(server, data, source, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_INVALID_EVIDENCE);
            return;
        }
        if (source.collisionRepairCount() >= MAX_REPLACEMENTS) {
            fail(server, data, source, ServiceNpcSpawnFailureCodes.UUID_COLLISION_REPAIR_EXHAUSTED);
            LOGGER.warn("Service NPC spawn collision repair exhausted uuid={} operation_id={} repair_count={}",
                source.spawnPointId(), source.operationId(), source.collisionRepairCount());
            return;
        }

        ServerLevel level = loadedLevel(server, source.location());
        if (level == null) {
            LOGGER.debug("Deferred Service NPC spawn collision repair for unloaded location uuid={} operation_id={}",
                source.spawnPointId(), source.operationId());
            return;
        }
        BlockPos pos = source.location().pos();
        if (!level.getBlockState(pos).is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get())) {
            fail(server, data, source, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_BLOCK_MISSING);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity)) {
            fail(server, data, source, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_BLOCK_MISMATCH);
            return;
        }
        if (!blockEntity.matchesCollisionSource(source, level)) {
            String code = source.spawnPointId().equals(blockEntity.getSpawnPointId())
                ? ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_CONFIGURATION_MISMATCH
                : ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_BLOCK_MISMATCH;
            fail(server, data, source, code);
            return;
        }

        ServiceNpcSpawnClaimData claims = ServiceNpcSpawnClaimData.get(level);
        UUID replacementId = generateReplacementUuid(
            source.spawnPointId(), data, claims, UUID::randomUUID
        );
        UUID operationId = generateOperationId(data, UUID::randomUUID);
        if (replacementId == null || operationId == null) {
            fail(server, data, source, ServiceNpcSpawnFailureCodes.UUID_GENERATION_EXHAUSTED);
            return;
        }
        ServiceNpcSpawnPendingData.CollisionReplacementStage staged =
            data.stageCollisionReplacement(
                source.token(),
                source.collisionEvidence(),
                replacementId,
                operationId,
                source.location(),
                source.cityPublicId(),
                source.serviceNpcTypeKey(),
                source.enabled(),
                Math.max(0L, System.currentTimeMillis())
            );
        if (staged.result() != ServiceNpcSpawnPendingData.MutationResult.ACCEPTED
                || staged.replacement() == null) {
            LOGGER.info("Ignored obsolete Service NPC spawn collision repair uuid={} operation_id={}",
                source.spawnPointId(), source.operationId());
            return;
        }
        LOGGER.warn(
            "Staged Service NPC spawn collision replacement old_uuid={} new_uuid={} operation_id={} kind={} repair_count={} location={}",
            source.spawnPointId(), replacementId, operationId,
            source.collisionEvidence().collisionKind(), staged.replacement().collisionRepairCount(),
            source.location()
        );
        processPhaseB(server, data, staged.replacement());
    }

    private static void processPhaseB(
            MinecraftServer server,
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingRecord staged
    ) {
        UUID oldId = staged.supersedesSpawnPointId();
        if (oldId == null || staged.operation() != ServiceNpcSpawnPendingOperation.UPSERT
                || staged.configurationRevision() != 1L
                || staged.collisionRepairCount() < 1
                || staged.collisionRepairCount() > MAX_REPLACEMENTS
                || staged.collisionEvidence() == null) {
            fail(server, data, staged, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_STAGED_STATE_INVALID);
            return;
        }
        ServerLevel level = loadedLevel(server, staged.location());
        if (level == null) return;
        BlockPos pos = staged.location().pos();
        if (!level.getBlockState(pos).is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get())) {
            fail(server, data, staged, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_BLOCK_MISSING);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity)
                || !blockEntity.matchesStagedCollisionReplacement(staged, level)) {
            fail(server, data, staged, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_BLOCK_MISMATCH);
            return;
        }

        ServiceNpcSpawnClaimData claims = ServiceNpcSpawnClaimData.get(level);
        ServiceNpcSpawnClaimData.ClaimResult claimResult =
            claims.claim(staged.spawnPointId(), staged.location());
        if (claimResult == ServiceNpcSpawnClaimData.ClaimResult.CONFLICT
                || claimResult == ServiceNpcSpawnClaimData.ClaimResult.READ_ONLY_SCHEMA) {
            fail(server, data, staged, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_CLAIM_CONFLICT);
            return;
        }
        ServiceNpcSpawnClaim oldClaim = claims.find(oldId);
        if (oldClaim != null && oldClaim.location().equals(staged.location())) {
            claims.releaseIfMatches(oldId, staged.location());
        }

        if (!blockEntity.applyStagedCollisionReplacement(staged, level)
                || !staged.spawnPointId().equals(blockEntity.getSpawnPointId())
                || blockEntity.getConfigurationRevision() != 1L
                || !claims.claimMatches(staged.spawnPointId(), staged.location())
                || claims.claimMatches(oldId, staged.location())) {
            fail(server, data, staged, ServiceNpcSpawnFailureCodes.COLLISION_REPAIR_FAILED);
            return;
        }
        if (data.markCollisionReplacementReady(staged.token(), oldId)) {
            LOGGER.warn(
                "Completed Service NPC spawn collision replacement old_uuid={} new_uuid={} operation_id={} repair_count={} location={}",
                oldId, staged.spawnPointId(), staged.operationId(), staged.collisionRepairCount(),
                staged.location()
            );
        }
    }

    private static void fail(
            MinecraftServer server,
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnPendingRecord record,
            String safeCode
    ) {
        if (!data.markPermanentFailure(record.token(), safeCode)) return;
        ServerLevel level = loadedLevel(server, record.location());
        if (level != null
                && level.getBlockEntity(record.location().pos()) instanceof ServiceNpcSpawnBlockEntity blockEntity) {
            blockEntity.applyCollisionRepairFailure(record, level, safeCode);
        }
        LOGGER.warn(
            "Service NPC spawn collision repair permanently blocked uuid={} operation_id={} repair_count={} code={}",
            record.spawnPointId(), record.operationId(), record.collisionRepairCount(), safeCode
        );
    }

    static EvidenceVerdict validateEvidence(
            ServiceNpcSpawnPendingRecord record, @Nullable UUID localServerKey
    ) {
        if (record == null || record.operation() != ServiceNpcSpawnPendingOperation.UPSERT
                || record.disposition() != ServiceNpcSpawnPendingDisposition.COLLISION_REPAIR
                || record.supersedesSpawnPointId() != null
                || record.collisionEvidence() == null
                || !record.collisionEvidence().replacementUuidRequired()) {
            return EvidenceVerdict.INVALID;
        }
        ServiceNpcSpawnCollisionEvidence evidence = record.collisionEvidence();
        if (evidence.collisionKind() == ServiceNpcSpawnCollisionEvidence.CollisionKind.TOMBSTONED
                || evidence.collisionKind() == ServiceNpcSpawnCollisionEvidence.CollisionKind.REDACTED) {
            return EvidenceVerdict.REPAIRABLE;
        }
        ServiceNpcSpawnCanonicalLocation canonical = evidence.canonicalLocation();
        if (canonical == null) return EvidenceVerdict.INVALID;
        if (localServerKey == null) return EvidenceVerdict.LOCAL_SERVER_KEY_UNAVAILABLE;
        boolean sameCanonicalLocation = canonical.minecraftServerKey().equals(localServerKey)
            && canonical.dimension().equals(record.location().dimension())
            && canonical.x() == record.location().pos().getX()
            && canonical.y() == record.location().pos().getY()
            && canonical.z() == record.location().pos().getZ();
        return sameCanonicalLocation ? EvidenceVerdict.SAME_LOCATION : EvidenceVerdict.REPAIRABLE;
    }

    static UUID generateReplacementUuid(
            UUID oldId,
            ServiceNpcSpawnPendingData data,
            ServiceNpcSpawnClaimData claims,
            Supplier<UUID> source
    ) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID candidate = source.get();
            if (candidate != null && !candidate.equals(oldId) && isVersionFour(candidate)
                    && data.isUuidAvailable(candidate) && claims.isUuidAvailable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static boolean isVersionFour(UUID candidate) {
        return candidate.version() == 4 && candidate.variant() == 2;
    }

    private static UUID generateOperationId(
            ServiceNpcSpawnPendingData data, Supplier<UUID> source
    ) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID candidate = source.get();
            if (candidate != null && isVersionFour(candidate) && data.isOperationIdAvailable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static UUID currentServerKey(MinecraftServer server) {
        ServerCredentials credentials = ServerAuthRegistry.credentials(server).orElse(null);
        return credentials == null ? null : credentials.minecraftServerKey().orElse(null);
    }

    @Nullable
    private static ServerLevel loadedLevel(MinecraftServer server, ServiceNpcSpawnLocation location) {
        if (!server.getWorldData().getLevelName().equals(location.worldName())) return null;
        ResourceKey<net.minecraft.world.level.Level> dimension =
            ResourceKey.create(Registries.DIMENSION, location.dimension());
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return null;
        BlockPos pos = location.pos();
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) ? level : null;
    }
}

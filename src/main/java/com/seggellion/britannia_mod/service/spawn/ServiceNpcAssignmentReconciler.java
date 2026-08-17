package com.seggellion.britannia_mod.service.spawn;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryCache;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Milestone 6 Slice 3c: reconciles a {@link ServiceNpcSpawnBlockEntity}'s currently
 * assigned {@link ServiceNpcEntity} against {@link ServiceNpcAssignmentsCache} (3a).
 *
 * <p>There is no chunk-load/unload NeoForge event anywhere in this mod. The real,
 * load-bearing precedent for "react when a post's chunk is loaded" is
 * {@code ServiceNpcSpawnBlockEntity.serverTick()}, already running
 * {@code ServiceNpcSpawnCollisionRepairCoordinator.reconcileBlock} and
 * {@code ServiceNpcSpawnReceiptReconciler.reconcileLoadedBlock} every tick once the
 * block entity is loaded. This reconciler is a third call in that same sequence, not a
 * new event subscription, matching that established pattern exactly: a static
 * companion class taking {@code (ServerLevel, ServiceNpcSpawnBlockEntity)} and
 * operating externally through the block entity's public getters and a narrow
 * apply*-style write method ({@link ServiceNpcSpawnBlockEntity#applyAssignmentReconciliation}).
 * A block-entity-internal method was deliberately not used: the block entity already
 * juggles identity/collision/receipt concerns, and every existing precedent for this
 * exact kind of cross-cutting reconciliation already lives in its own companion class
 * in this same package.
 *
 * <p>Reads only from {@link ServiceNpcAssignmentsCache}, never a live Rails fetch —
 * the cache already degrades to its last-known-good snapshot (or empty) when Rails is
 * unreachable, so this reconciler behaves identically whether Rails is up or not.
 */
public final class ServiceNpcAssignmentReconciler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Bounded search radius around the post's own block position. Entities are always
     * restricted to a small radius of their assigned post (see
     * {@link ServiceNpcEntity#assignHomePost}), so this comfortably contains any
     * legitimately associated entity without ever scanning the whole loaded world.
     * Final correctness comes from the exact spawnPointId match in the query
     * predicate below, not from this bound.
     */
    private static final double SEARCH_RADIUS = 16.0D;

    private ServiceNpcAssignmentReconciler() {
    }

    public static void reconcileBlock(ServerLevel level, ServiceNpcSpawnBlockEntity blockEntity) {
        UUID spawnPointId = blockEntity.getSpawnPointId();
        if (spawnPointId == null) return;

        ServiceNpcAssignmentsSnapshot snapshot = ServiceNpcAssignmentsCache.get(level).snapshot();
        ServiceNpcAssignmentDefinition activeAssignment = findActiveAssignment(snapshot, spawnPointId);

        List<ServiceNpcEntity> nearby = level.getEntities(
                EntityRegistry.SERVICE_NPC.get(),
                new AABB(blockEntity.getBlockPos()).inflate(SEARCH_RADIUS),
                entity -> spawnPointId.equals(entity.getSpawnPointId()) && entity.isAlive()
        );

        if (activeAssignment == null) {
            if (!nearby.isEmpty()) {
                nearby.forEach(ServiceNpcEntity::discard);
            }
            UUID staleWorldNpcId = blockEntity.getAssignedNpcPublicId();
            if (staleWorldNpcId != null) {
                // Vendor/Trader Milestone 5: economic projections carry no
                // assignment fields of their own; the block's last reconciled
                // World NPC id identifies exactly the entity this post owns.
                level.getEntitiesOfClass(
                        CitizenEntity.class,
                        new AABB(blockEntity.getBlockPos()).inflate(SEARCH_RADIUS),
                        candidate -> !(candidate instanceof ServiceNpcEntity)
                                && staleWorldNpcId.equals(candidate.getWorldNpcPublicId())
                ).forEach(CitizenEntity::discard);
            }
            if (blockEntity.getAssignedNpcPublicId() != null || blockEntity.getAssignmentRevision() != 0L) {
                blockEntity.applyAssignmentReconciliation(null, null, 0L);
            }
            return;
        }

        ServiceNpcAssignmentSpawnPointDefinition spawnPoint = snapshot.spawnPoints().get(spawnPointId);
        ServiceNpcAssignmentWorldNpcDefinition worldNpc = snapshot.worldNpcs().get(activeAssignment.worldNpcPublicId());
        if (spawnPoint == null || worldNpc == null) {
            // filteredForServer() re-derives assignments/world NPCs from retained spawn
            // points, so this should be unreachable for a well-formed snapshot. Do
            // nothing rather than guess at data the cache itself doesn't have.
            LOGGER.warn(
                    "Service NPC assignment reconciliation found an internally inconsistent snapshot "
                            + "spawn_point={} assignment={}",
                    spawnPointId, activeAssignment.publicId()
            );
            return;
        }

        if (spawnPoint.economicNpcTypeKey() != null) {
            reconcileEconomic(level, blockEntity, activeAssignment, worldNpc, spawnPoint);
            return;
        }

        List<ServiceNpcEntity> matching = new ArrayList<>();
        for (ServiceNpcEntity candidate : nearby) {
            if (activeAssignment.publicId().equals(candidate.getAssignmentPublicId())) {
                matching.add(candidate);
            } else {
                // Belongs to a closed or superseded assignment (or a different NPC
                // entirely) — never the current one, regardless of its own revision.
                candidate.discard();
            }
        }

        ServiceNpcEntity canonical = pickCanonical(matching);
        if (canonical != null) {
            for (ServiceNpcEntity duplicate : matching) {
                if (duplicate != canonical) duplicate.discard();
            }
            applyAssignmentData(canonical, spawnPointId, activeAssignment, worldNpc, spawnPoint);
        } else {
            canonical = spawn(level, spawnPointId, activeAssignment, worldNpc, spawnPoint);
            if (canonical == null) {
                LOGGER.warn(
                        "Failed to spawn Service NPC entity spawn_point={} assignment={} world_npc={}",
                        spawnPointId, activeAssignment.publicId(), activeAssignment.worldNpcPublicId()
                );
                return;
            }
        }

        blockEntity.applyAssignmentReconciliation(worldNpc.publicId(), worldNpc.name(), activeAssignment.revision());
    }

    @Nullable
    private static ServiceNpcAssignmentDefinition findActiveAssignment(
            ServiceNpcAssignmentsSnapshot snapshot, UUID spawnPointId
    ) {
        ServiceNpcAssignmentDefinition best = null;
        for (ServiceNpcAssignmentDefinition assignment : snapshot.assignments().values()) {
            if (!spawnPointId.equals(assignment.spawnPointPublicId())) continue;
            if (!"active".equals(assignment.status())) continue;
            if (best == null || assignment.revision() > best.revision()) best = assignment;
        }
        return best;
    }

    /**
     * Vendor/Trader Milestone 5: materializes an economic (Vendor/Trader)
     * assignment as the CONFIGURED entity type from the Rails-owned Economic
     * NPC registry -- never a hardcoded class. Identity invariants match the
     * service path: post UUID != WorldNpc public id != entity UUID; exactly one
     * projection per assignment, deduplicated deterministically by lowest
     * entity UUID; cache truth overwrites whatever the entity carried. Fails
     * closed (no entity) when the registry lacks the type or its entity key --
     * never a fallback entity.
     */
    private static void reconcileEconomic(
            ServerLevel level,
            ServiceNpcSpawnBlockEntity blockEntity,
            ServiceNpcAssignmentDefinition assignment,
            ServiceNpcAssignmentWorldNpcDefinition worldNpc,
            ServiceNpcAssignmentSpawnPointDefinition spawnPoint
    ) {
        EconomicNpcTypeDefinition definition = EconomicNpcRegistryCache.snapshot()
                .economicNpcTypes().get(spawnPoint.economicNpcTypeKey());
        if (definition == null || definition.minecraftEntityTypeKey() == null) {
            LOGGER.warn(
                    "Economic assignment not materialized: registry has no entity for type {} spawn_point={}",
                    spawnPoint.economicNpcTypeKey(), spawnPoint.publicId());
            return;
        }
        ResourceLocation entityKey = ResourceLocation.tryParse(definition.minecraftEntityTypeKey());
        EntityType<?> entityType = entityKey == null
                ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(entityKey).orElse(null);
        if (entityType == null) {
            LOGGER.warn(
                    "Economic assignment not materialized: unknown entity type {} for {}",
                    definition.minecraftEntityTypeKey(), spawnPoint.economicNpcTypeKey());
            return;
        }

        List<CitizenEntity> matching = level.getEntitiesOfClass(
                CitizenEntity.class,
                new AABB(blockEntity.getBlockPos()).inflate(SEARCH_RADIUS),
                candidate -> candidate.isAlive()
                        && !(candidate instanceof ServiceNpcEntity)
                        && worldNpc.publicId().equals(candidate.getWorldNpcPublicId())
        );
        CitizenEntity canonical = null;
        for (CitizenEntity candidate : matching) {
            if (candidate.getType() != entityType) {
                candidate.discard();
                continue;
            }
            if (canonical == null || candidate.getUUID().compareTo(canonical.getUUID()) < 0) {
                canonical = candidate;
            }
        }
        for (CitizenEntity duplicate : matching) {
            if (duplicate != canonical && duplicate.getType() == entityType) {
                duplicate.discard();
            }
        }

        BlockPos pos = new BlockPos(spawnPoint.x(), spawnPoint.y(), spawnPoint.z());
        if (canonical == null) {
            if (!(entityType.create(level) instanceof CitizenEntity created)) {
                LOGGER.warn(
                        "Economic assignment not materialized: {} is not a CitizenEntity",
                        definition.minecraftEntityTypeKey());
                return;
            }
            created.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(created);
            canonical = created;
        }

        canonical.setWorldNpcPublicId(worldNpc.publicId());
        canonical.setEconomicNpcTypeKey(spawnPoint.economicNpcTypeKey());
        canonical.setEconomicCityPublicId(spawnPoint.cityPublicId());
        canonical.setPersonalName(worldNpc.name());
        canonical.setGender(worldNpc.genderKey());
        BootstrapCityDefinition city = spawnPoint.cityPublicId() == null
                ? null
                : BootstrapCityRegistryCache.snapshot().find(spawnPoint.cityPublicId());
        if (city != null) {
            canonical.setCityName(city.displayName());
        }
        if (canonical instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.restrictTo(pos, 2);
        }
        blockEntity.applyAssignmentReconciliation(
                worldNpc.publicId(), worldNpc.name(), assignment.revision());
    }

    /**
     * Mirrors {@link ServiceNpcSpawnCollisionRepairCoordinator}'s own canonical/
     * superseded vocabulary: the survivor is canonical, every other duplicate is
     * superseded and discarded. That coordinator resolves canonical-vs-superseded by
     * matching a candidate against a single durable pending record's token — there is
     * no equivalent shared arbiter for live duplicate entities, so this compares
     * assignmentRevision directly (highest wins), with ties broken by lowest entity
     * UUID so the outcome is deterministic and does not flap between ticks.
     */
    @Nullable
    private static ServiceNpcEntity pickCanonical(List<ServiceNpcEntity> candidates) {
        ServiceNpcEntity canonical = null;
        for (ServiceNpcEntity candidate : candidates) {
            if (canonical == null
                    || candidate.getAssignmentRevision() > canonical.getAssignmentRevision()
                    || (candidate.getAssignmentRevision() == canonical.getAssignmentRevision()
                            && candidate.getUUID().compareTo(canonical.getUUID()) < 0)) {
                canonical = candidate;
            }
        }
        return canonical;
    }

    /**
     * Cache/assignment data is always authoritative over whatever the entity already
     * carries (from NBT or a previous tick), so this unconditionally overwrites rather
     * than conditionally merging — stale NBT can never win. worldNpcPublicId, the
     * personal name, and gender are all sourced from the cache's world-NPC record,
     * never regenerated or left at CitizenEntity's own random/default values.
     * {@code EntityType.create(Level)} (used by both the fresh-spawn path below and
     * GameTestHelper.spawn) never invokes {@code finalizeSpawn} — that hook only fires
     * for natural spawn-placement — so CitizenEntity's own gender randomization in its
     * finalizeSpawn override never runs here; without this explicit set, every fresh
     * Service NPC would silently default to gender "female" regardless of the real
     * World NPC's gender_key.
     */
    private static void applyAssignmentData(
            ServiceNpcEntity entity,
            UUID spawnPointId,
            ServiceNpcAssignmentDefinition assignment,
            ServiceNpcAssignmentWorldNpcDefinition worldNpc,
            ServiceNpcAssignmentSpawnPointDefinition spawnPoint
    ) {
        entity.setWorldNpcPublicId(worldNpc.publicId());
        entity.setPersonalName(worldNpc.name());
        entity.setGender(worldNpc.genderKey());
        entity.setSpawnPointId(spawnPointId);
        entity.setAssignmentPublicId(assignment.publicId());
        entity.setServiceNpcTypeKey(spawnPoint.serviceNpcTypeKey());
        entity.setDefinitionRevision(worldNpc.revision());
        entity.setAssignmentRevision(assignment.revision());
        // Rails does not send a per-spawn-point restriction radius today, only a
        // position — ServiceNpcEntity#assignHomePost(BlockPos, int) already accepts
        // one for whenever that wire field exists; until then this uses its default.
        entity.assignHomePost(new BlockPos(spawnPoint.x(), spawnPoint.y(), spawnPoint.z()));
    }

    @Nullable
    private static ServiceNpcEntity spawn(
            ServerLevel level,
            UUID spawnPointId,
            ServiceNpcAssignmentDefinition assignment,
            ServiceNpcAssignmentWorldNpcDefinition worldNpc,
            ServiceNpcAssignmentSpawnPointDefinition spawnPoint
    ) {
        ServiceNpcEntity entity = EntityRegistry.SERVICE_NPC.get().create(level);
        if (entity == null) return null;
        BlockPos pos = new BlockPos(spawnPoint.x(), spawnPoint.y(), spawnPoint.z());
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        applyAssignmentData(entity, spawnPointId, assignment, worldNpc, spawnPoint);
        // Checked rather than discarded, matching MerchantSpawnBlockEntity/TraderSpawnBlockEntity.
        // addFreshEntity returns false when a mod cancels EntityJoinLevelEvent -- CitySpawner does
        // exactly that for any entity its rules disallow inside a city area. Ignoring the result
        // reported a successful spawn for an entity that never entered the world, which is
        // indistinguishable in-game from Rails never having published the assignment at all.
        if (!level.addFreshEntity(entity)) {
            LOGGER.warn(
                    "Service NPC spawn failed: addFreshEntity rejected npc={} spawnPoint={} pos={}",
                    entity.getUUID(), spawnPointId, pos);
            return null;
        }
        return entity;
    }
}

package com.seggellion.britannia_mod.service;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.worldstate.ServiceNpcAssignmentsCandidateApply;
import com.seggellion.britannia_mod.worldstate.WorldStateChangeRecord;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable, server-wide last-known-good cache of this server's own filtered
 * {@link ServiceNpcAssignmentsSnapshot}, so a startup with Rails unreachable can
 * still recover the most recently successful fetch. Modeled directly on
 * {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnClaimData}
 * (same domain, same milestone): anchored to the overworld's DataStorage regardless
 * of which ServerLevel triggers the read, because spawn points/assignments can span
 * multiple dimensions and must resolve to one canonical store, not a per-dimension
 * fragment.
 *
 * <h2>Three genuinely different failure situations (Milestone 13 NeoForge Slice 2)</h2>
 * <ol>
 *   <li><b>This file itself is unreadable/corrupt/schema-mismatched at load time.</b> This is a
 *       read-optimizing cache of data Rails always remains authoritative for (unlike spawn-point
 *       claims, which have no other authoritative record), so unlike {@code
 *       ServiceNpcSpawnClaimData}'s read-only-preserve policy for an on-disk schema it doesn't
 *       understand, an unreadable/mismatched file here is simply discarded and treated as empty --
 *       exactly like no cache existing at all, matching {@code ServiceNpcRegistryCache}'s own
 *       degrade-to-empty behavior for its analogous (wire-level) unsupported-schema case. This
 *       policy predates Slice 2 and Slice 2 introduces no reason to change it: see {@link #load}.</li>
 *   <li><b>A delta batch handed to {@link #applyWorldStateChanges} cannot be turned into a valid
 *       candidate</b> (a per-resource payload Slice 1's own generic validation does not check, or
 *       an unrecognized change_type). {@link #applyWorldStateChanges} returns {@link
 *       ServiceNpcAssignmentsCandidateApply.Rejected} and this instance's live {@link #snapshot}
 *       and {@link #lastAppliedWorldStateVersion} are left completely untouched -- not partially
 *       applied, not discarded.</li>
 *   <li><b>Every change in the batch applies cleanly, but the resulting candidate fails its
 *       post-apply sanity check</b> (today: a dangling assignment reference). Same treatment as
 *       case 2 -- {@link ServiceNpcAssignmentsCandidateApply.Rejected}, this instance untouched.</li>
 * </ol>
 * Cases 2 and 3 are new in Slice 2; case 1 is unchanged. See {@link
 * ServiceNpcAssignmentsCandidateApply} for where the actual transformation and sanity check live.
 */
public final class ServiceNpcAssignmentsCache extends SavedData {
    public static final String DATA_NAME = "britannia_service_npc_assignments_cache";

    /**
     * On-disk cache format version. Deliberately separate from
     * {@link ServiceNpcAssignmentsSnapshot#SUPPORTED_SCHEMA_VERSION} (the wire
     * schema_version) even though both currently happen to be 1 — one versions the
     * Rails wire payload, the other versions how this cache serializes it to disk.
     *
     * Not bumped for Slice 2's new {@code LastAppliedWorldStateVersion} tag: {@link
     * CompoundTag#getLong} already returns 0 for a tag that does not exist, which is exactly the
     * correct "never synced via world-state deltas yet" default for a schema-1 file written by a
     * pre-Slice-2 build -- so an existing, valid cache file upgrades in place instead of being
     * needlessly discarded.
     */
    public static final int SCHEMA_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServiceNpcAssignmentsSnapshot snapshot = ServiceNpcAssignmentsSnapshot.empty();
    private long lastAppliedWorldStateVersion;

    public static ServiceNpcAssignmentsCache get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServiceNpcAssignmentsCache::new, ServiceNpcAssignmentsCache::load),
                DATA_NAME
        );
    }

    public ServiceNpcAssignmentsSnapshot snapshot() {
        return snapshot;
    }

    public long lastAppliedWorldStateVersion() {
        return lastAppliedWorldStateVersion;
    }

    public void replace(ServiceNpcAssignmentsSnapshot snapshot) {
        this.snapshot = snapshot == null ? ServiceNpcAssignmentsSnapshot.empty() : snapshot;
        setDirty();
    }

    /**
     * Milestone 13 NeoForge Slice 3: the full-bootstrap-fallback commit path, distinct from
     * {@link #replace} -- a player-login bootstrap (Milestone 6's own established path) has no
     * world-state version in its response to record (that field does not exist on the bootstrap
     * wire payload; confirmed by reading it directly), so {@link #replace} correctly leaves
     * {@link #lastAppliedWorldStateVersion} untouched. A poller-triggered full bootstrap fallback
     * is different: it exists specifically to recover from a {@code full_bootstrap_required} poll
     * response, and that response's own {@code current_version} is exactly the version this fresh
     * snapshot corresponds to. Snapshot and version are set together here for the same reason
     * {@link #applyWorldStateChanges} does it -- one {@link #setDirty()}, so a caller that forces a
     * synchronous flush afterward commits both to the same on-disk write, never one without the
     * other.
     */
    public void replaceFromFullBootstrapFallback(ServiceNpcAssignmentsSnapshot snapshot, long newVersion) {
        this.snapshot = snapshot == null ? ServiceNpcAssignmentsSnapshot.empty() : snapshot;
        this.lastAppliedWorldStateVersion = newVersion;
        setDirty();
    }

    /**
     * Case 2/3's actual implementation: builds a candidate via {@link
     * ServiceNpcAssignmentsCandidateApply#apply} from the current {@link #snapshot}, and only on
     * {@link ServiceNpcAssignmentsCandidateApply.Applied} does this instance's live state change
     * at all -- {@link #snapshot} and {@link #lastAppliedWorldStateVersion} are updated together,
     * from the same successful result, followed by exactly one {@link #setDirty()}, so a caller
     * that then forces a synchronous flush (see {@code WorldStateSyncApply}) commits both to the
     * same on-disk NBT tag in the same write. They can never desynchronize because there is no
     * window where one is updated without the other. On {@link
     * ServiceNpcAssignmentsCandidateApply.Rejected}, neither field changes and {@link #setDirty()}
     * is never called -- this instance is exactly as if this call never happened.
     */
    public ServiceNpcAssignmentsCandidateApply.Result applyWorldStateChanges(
            List<WorldStateChangeRecord> changes, long newVersion
    ) {
        ServiceNpcAssignmentsCandidateApply.Result result =
                ServiceNpcAssignmentsCandidateApply.apply(snapshot, changes);
        if (result instanceof ServiceNpcAssignmentsCandidateApply.Applied applied) {
            this.snapshot = applied.candidate();
            this.lastAppliedWorldStateVersion = newVersion;
            setDirty();
        }
        return result;
    }

    public static ServiceNpcAssignmentsCache load(CompoundTag tag, HolderLookup.Provider provider) {
        ServiceNpcAssignmentsCache cache = new ServiceNpcAssignmentsCache();
        int schema = tag.getInt("SchemaVersion");
        if (schema != SCHEMA_VERSION) {
            LOGGER.warn("Service NPC assignments cache schema {} is unsupported; discarding and starting empty", schema);
            return cache;
        }

        try {
            cache.snapshot = snapshotFromNbt(tag);
            // Read alongside the snapshot it is durably committed with, not independently --
            // a tag from a pre-Slice-2 file simply lacks this key, and CompoundTag#getLong
            // already returns 0 for a missing key, which is exactly the correct "never synced
            // via world-state deltas yet" value for that case.
            cache.lastAppliedWorldStateVersion = tag.getLong("LastAppliedWorldStateVersion");
        } catch (RuntimeException exception) {
            LOGGER.error("Discarding corrupt Service NPC assignments cache", exception);
            cache.snapshot = ServiceNpcAssignmentsSnapshot.empty();
            cache.lastAppliedWorldStateVersion = 0L;
        }
        return cache;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putLong("LastAppliedWorldStateVersion", lastAppliedWorldStateVersion);
        snapshotToNbt(snapshot, tag);
        return tag;
    }

    private static void snapshotToNbt(ServiceNpcAssignmentsSnapshot snapshot, CompoundTag tag) {
        tag.putInt("WireSchemaVersion", snapshot.schemaVersion());
        tag.putLong("Revision", snapshot.revision());

        ListTag spawnPoints = new ListTag();
        snapshot.spawnPoints().values().forEach(point -> spawnPoints.add(spawnPointToNbt(point)));
        tag.put("SpawnPoints", spawnPoints);

        ListTag assignments = new ListTag();
        snapshot.assignments().values().forEach(assignment -> assignments.add(assignmentToNbt(assignment)));
        tag.put("Assignments", assignments);

        ListTag worldNpcs = new ListTag();
        snapshot.worldNpcs().values().forEach(npc -> worldNpcs.add(worldNpcToNbt(npc)));
        tag.put("WorldNpcs", worldNpcs);
    }

    private static ServiceNpcAssignmentsSnapshot snapshotFromNbt(CompoundTag tag) {
        int wireSchemaVersion = tag.getInt("WireSchemaVersion");
        long revision = tag.getLong("Revision");

        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints = new LinkedHashMap<>();
        ListTag spawnPointsTag = tag.getList("SpawnPoints", Tag.TAG_COMPOUND);
        for (int index = 0; index < spawnPointsTag.size(); index++) {
            ServiceNpcAssignmentSpawnPointDefinition point = spawnPointFromNbt(spawnPointsTag.getCompound(index));
            spawnPoints.put(point.publicId(), point);
        }

        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs = new LinkedHashMap<>();
        ListTag worldNpcsTag = tag.getList("WorldNpcs", Tag.TAG_COMPOUND);
        for (int index = 0; index < worldNpcsTag.size(); index++) {
            ServiceNpcAssignmentWorldNpcDefinition npc = worldNpcFromNbt(worldNpcsTag.getCompound(index));
            worldNpcs.put(npc.publicId(), npc);
        }

        Map<UUID, ServiceNpcAssignmentDefinition> assignments = new LinkedHashMap<>();
        ListTag assignmentsTag = tag.getList("Assignments", Tag.TAG_COMPOUND);
        for (int index = 0; index < assignmentsTag.size(); index++) {
            ServiceNpcAssignmentDefinition assignment = assignmentFromNbt(assignmentsTag.getCompound(index));
            assignments.put(assignment.publicId(), assignment);
        }

        return new ServiceNpcAssignmentsSnapshot(wireSchemaVersion, revision, spawnPoints, assignments, worldNpcs);
    }

    private static CompoundTag spawnPointToNbt(ServiceNpcAssignmentSpawnPointDefinition point) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PublicId", point.publicId());
        tag.putUUID("MinecraftServerPublicId", point.minecraftServerPublicId());
        if (point.cityPublicId() != null) tag.putUUID("CityPublicId", point.cityPublicId());
        if (point.serviceNpcTypeKey() != null) tag.putString("ServiceNpcTypeKey", point.serviceNpcTypeKey());
        tag.putString("WorldName", point.worldName());
        tag.putString("DimensionKey", point.dimensionKey());
        tag.putInt("X", point.x());
        tag.putInt("Y", point.y());
        tag.putInt("Z", point.z());
        if (point.economicNpcTypeKey() != null) {
            tag.putString("EconomicNpcTypeKey", point.economicNpcTypeKey());
        }
        tag.putBoolean("Enabled", point.enabled());
        tag.putLong("Revision", point.revision());
        return tag;
    }

    private static ServiceNpcAssignmentSpawnPointDefinition spawnPointFromNbt(CompoundTag tag) {
        return new ServiceNpcAssignmentSpawnPointDefinition(
                tag.getUUID("PublicId"),
                tag.getUUID("MinecraftServerPublicId"),
                tag.hasUUID("CityPublicId") ? tag.getUUID("CityPublicId") : null,
                tag.contains("ServiceNpcTypeKey") ? tag.getString("ServiceNpcTypeKey") : null,
                tag.contains("EconomicNpcTypeKey") ? tag.getString("EconomicNpcTypeKey") : null,
                tag.getString("WorldName"),
                tag.getString("DimensionKey"),
                tag.getInt("X"),
                tag.getInt("Y"),
                tag.getInt("Z"),
                tag.getBoolean("Enabled"),
                tag.getLong("Revision")
        );
    }

    private static CompoundTag worldNpcToNbt(ServiceNpcAssignmentWorldNpcDefinition npc) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PublicId", npc.publicId());
        tag.putString("Name", npc.name());
        tag.putString("GenderKey", npc.genderKey());
        tag.putString("ProfessionKey", npc.professionKey());
        if (npc.serviceNpcTypeKey() != null) tag.putString("ServiceNpcTypeKey", npc.serviceNpcTypeKey());
        tag.putLong("Revision", npc.revision());
        return tag;
    }

    private static ServiceNpcAssignmentWorldNpcDefinition worldNpcFromNbt(CompoundTag tag) {
        return new ServiceNpcAssignmentWorldNpcDefinition(
                tag.getUUID("PublicId"),
                tag.getString("Name"),
                tag.getString("GenderKey"),
                tag.getString("ProfessionKey"),
                tag.contains("ServiceNpcTypeKey") ? tag.getString("ServiceNpcTypeKey") : null,
                tag.getLong("Revision")
        );
    }

    private static CompoundTag assignmentToNbt(ServiceNpcAssignmentDefinition assignment) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PublicId", assignment.publicId());
        tag.putUUID("SpawnPointPublicId", assignment.spawnPointPublicId());
        tag.putUUID("WorldNpcPublicId", assignment.worldNpcPublicId());
        tag.putString("Status", assignment.status());
        tag.putLong("Revision", assignment.revision());
        tag.putString("AssignedAt", assignment.assignedAt());
        return tag;
    }

    private static ServiceNpcAssignmentDefinition assignmentFromNbt(CompoundTag tag) {
        return new ServiceNpcAssignmentDefinition(
                tag.getUUID("PublicId"),
                tag.getUUID("SpawnPointPublicId"),
                tag.getUUID("WorldNpcPublicId"),
                tag.getString("Status"),
                tag.getLong("Revision"),
                tag.getString("AssignedAt")
        );
    }
}

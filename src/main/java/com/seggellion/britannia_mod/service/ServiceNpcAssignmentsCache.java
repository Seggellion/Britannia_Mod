package com.seggellion.britannia_mod.service;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
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
 * fragment. This is a read-optimizing cache of data Rails always remains
 * authoritative for (unlike spawn-point claims, which have no other authoritative
 * record) — so unlike ServiceNpcSpawnClaimData's read-only-preserve policy for an
 * on-disk schema it doesn't understand, an unreadable/mismatched cache file here is
 * simply discarded and treated as empty, exactly like no cache existing at all,
 * matching {@code ServiceNpcRegistryCache}'s own degrade-to-empty behavior for its
 * analogous (wire-level) unsupported-schema case.
 */
public final class ServiceNpcAssignmentsCache extends SavedData {
    public static final String DATA_NAME = "britannia_service_npc_assignments_cache";

    /**
     * On-disk cache format version. Deliberately separate from
     * {@link ServiceNpcAssignmentsSnapshot#SUPPORTED_SCHEMA_VERSION} (the wire
     * schema_version) even though both currently happen to be 1 — one versions the
     * Rails wire payload, the other versions how this cache serializes it to disk.
     */
    public static final int SCHEMA_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServiceNpcAssignmentsSnapshot snapshot = ServiceNpcAssignmentsSnapshot.empty();

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

    public void replace(ServiceNpcAssignmentsSnapshot snapshot) {
        this.snapshot = snapshot == null ? ServiceNpcAssignmentsSnapshot.empty() : snapshot;
        setDirty();
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
        } catch (RuntimeException exception) {
            LOGGER.error("Discarding corrupt Service NPC assignments cache", exception);
            cache.snapshot = ServiceNpcAssignmentsSnapshot.empty();
        }
        return cache;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
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

package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record ServiceNpcAssignmentsSnapshot(
        int schemaVersion,
        long revision,
        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints,
        Map<UUID, ServiceNpcAssignmentDefinition> assignments,
        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcs
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final ServiceNpcAssignmentsSnapshot EMPTY = new ServiceNpcAssignmentsSnapshot(
            SUPPORTED_SCHEMA_VERSION,
            0L,
            Map.of(),
            Map.of(),
            Map.of()
    );

    public ServiceNpcAssignmentsSnapshot {
        spawnPoints = immutableOrderedMap(spawnPoints);
        assignments = immutableOrderedMap(assignments);
        worldNpcs = immutableOrderedMap(worldNpcs);
    }

    public static ServiceNpcAssignmentsSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return spawnPoints.isEmpty() && assignments.isEmpty() && worldNpcs.isEmpty();
    }

    /**
     * Bootstrap is authenticated only at the shard level (no per-server credential
     * exists on WorldBootstrapController today), so the wire payload legitimately
     * spans every Minecraft server registered under the shard. This narrows it down
     * to only the entries owned by {@code serverPublicId}, re-deriving assignments
     * and world NPCs from the retained spawn points so the result stays internally
     * consistent (every assignment resolves to a retained spawn point and world NPC).
     */
    public ServiceNpcAssignmentsSnapshot filteredForServer(@Nullable UUID serverPublicId) {
        if (serverPublicId == null) return empty();

        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> filteredSpawnPoints = spawnPoints.values().stream()
                .filter(point -> serverPublicId.equals(point.minecraftServerPublicId()))
                .collect(Collectors.toMap(
                        ServiceNpcAssignmentSpawnPointDefinition::publicId,
                        point -> point,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<UUID, ServiceNpcAssignmentDefinition> filteredAssignments = assignments.values().stream()
                .filter(assignment -> filteredSpawnPoints.containsKey(assignment.spawnPointPublicId()))
                .collect(Collectors.toMap(
                        ServiceNpcAssignmentDefinition::publicId,
                        assignment -> assignment,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> filteredWorldNpcs = worldNpcs.values().stream()
                .filter(npc -> filteredAssignments.values().stream()
                        .anyMatch(assignment -> assignment.worldNpcPublicId().equals(npc.publicId())))
                .collect(Collectors.toMap(
                        ServiceNpcAssignmentWorldNpcDefinition::publicId,
                        npc -> npc,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return new ServiceNpcAssignmentsSnapshot(schemaVersion, revision, filteredSpawnPoints, filteredAssignments, filteredWorldNpcs);
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

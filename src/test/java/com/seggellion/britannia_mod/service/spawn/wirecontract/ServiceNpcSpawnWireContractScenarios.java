package com.seggellion.britannia_mod.service.spawn.wirecontract;

import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnLocation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingDisposition;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingOperation;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fixed, deterministic scenario data shared by the NeoForge-&gt;Rails-&gt;NeoForge wire-contract
 * fixture tests (Milestone 5 closeout). These UUIDs, keys, and coordinates are the single
 * source of truth: the Rails-side wire-contract test ({@code test/integration/service_npc_spawn_wire_contract_test.rb}
 * in the ultimacraft-website worktree) hardcodes the same literal values so both sides agree
 * on fixture content without sharing code. Do not change a value here without updating that
 * file and regenerating every fixture on both sides.
 */
public final class ServiceNpcSpawnWireContractScenarios {
    private ServiceNpcSpawnWireContractScenarios() {}

    public static final String SHARD_NAME = "WireContractShard";
    public static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    public static final String WORLD_NAME = "Britannia";
    public static final UUID CITY_PUBLIC_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final String SERVICE_NPC_TYPE_KEY = "wire_contract_teller";

    public static final ServiceNpcSpawnLocation LOCATION_FRESH =
        new ServiceNpcSpawnLocation(WORLD_NAME, DIMENSION, new BlockPos(100_000, 64, 200_000));
    public static final ServiceNpcSpawnLocation LOCATION_FRESH_MOVED =
        new ServiceNpcSpawnLocation(WORLD_NAME, DIMENSION, new BlockPos(100_000, 64, 200_500));
    public static final ServiceNpcSpawnLocation LOCATION_TOMBSTONE =
        new ServiceNpcSpawnLocation(WORLD_NAME, DIMENSION, new BlockPos(300_000, 70, 400_000));
    public static final ServiceNpcSpawnLocation LOCATION_CROSS_SHARD =
        new ServiceNpcSpawnLocation(WORLD_NAME, DIMENSION, new BlockPos(500_000, 80, 600_000));

    public static final UUID SPAWN_FRESH = UUID.fromString("20000000-0000-4000-8000-000000000001");
    public static final UUID SPAWN_TOMBSTONE = UUID.fromString("20000000-0000-4000-8000-000000000002");
    public static final UUID SPAWN_CROSS_SHARD = UUID.fromString("20000000-0000-4000-8000-000000000003");

    public static final UUID OP_FRESH = UUID.fromString("30000000-0000-4000-8000-000000000001");
    public static final UUID OP_REV2 = UUID.fromString("30000000-0000-4000-8000-000000000002");
    public static final UUID OP_STALE = UUID.fromString("30000000-0000-4000-8000-000000000003");
    public static final UUID OP_REMOVE = UUID.fromString("30000000-0000-4000-8000-000000000004");
    public static final UUID OP_RESUBMIT = UUID.fromString("30000000-0000-4000-8000-000000000005");
    public static final UUID OP_COLLISION = UUID.fromString("30000000-0000-4000-8000-000000000006");
    public static final UUID OP_CROSS_SHARD = UUID.fromString("30000000-0000-4000-8000-000000000007");

    /** Ordered scenario name -&gt; pending record, matching the fixture file naming convention exactly. */
    public static Map<String, ServiceNpcSpawnPendingRecord> all() {
        Map<String, ServiceNpcSpawnPendingRecord> scenarios = new LinkedHashMap<>();
        scenarios.put("fresh_registration_upsert", upsert(
            SPAWN_FRESH, LOCATION_FRESH, true, 1L, OP_FRESH
        ));
        scenarios.put("identical_retry_upsert", upsert(
            SPAWN_FRESH, LOCATION_FRESH, true, 1L, OP_FRESH
        ));
        scenarios.put("revision_plus_one_upsert", upsert(
            SPAWN_FRESH, LOCATION_FRESH, false, 2L, OP_REV2
        ));
        scenarios.put("stale_lower_revision_upsert", upsert(
            SPAWN_FRESH, LOCATION_FRESH, true, 1L, OP_STALE
        ));
        scenarios.put("remove_tombstone", remove(
            SPAWN_TOMBSTONE, LOCATION_TOMBSTONE, 1L, OP_REMOVE
        ));
        scenarios.put("restored_tombstone_resubmission", upsert(
            SPAWN_TOMBSTONE, LOCATION_TOMBSTONE, true, 2L, OP_RESUBMIT
        ));
        scenarios.put("same_shard_uuid_collision_different_coordinate", upsert(
            SPAWN_FRESH, LOCATION_FRESH_MOVED, true, 1L, OP_COLLISION
        ));
        scenarios.put("cross_shard_collision", upsert(
            SPAWN_CROSS_SHARD, LOCATION_CROSS_SHARD, true, 1L, OP_CROSS_SHARD
        ));
        return scenarios;
    }

    private static ServiceNpcSpawnPendingRecord upsert(
        UUID spawnPointId, ServiceNpcSpawnLocation location, boolean enabled, long revision, UUID operationId
    ) {
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.UPSERT, spawnPointId, SHARD_NAME, location,
            CITY_PUBLIC_ID, SERVICE_NPC_TYPE_KEY, enabled, revision, 1_000L, operationId,
            ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L, null, null, 0, null
        );
    }

    private static ServiceNpcSpawnPendingRecord remove(
        UUID spawnPointId, ServiceNpcSpawnLocation location, long revision, UUID operationId
    ) {
        return new ServiceNpcSpawnPendingRecord(
            ServiceNpcSpawnPendingOperation.REMOVE, spawnPointId, SHARD_NAME, location,
            null, null, true, revision, 1_000L, operationId,
            ServiceNpcSpawnPendingDisposition.READY, 0, null, 0L, null, null, 0, null
        );
    }
}

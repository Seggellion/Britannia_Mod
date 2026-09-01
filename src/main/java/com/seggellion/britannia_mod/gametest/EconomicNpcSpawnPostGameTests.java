package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.BakerEntity;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryCache;
import com.seggellion.britannia_mod.service.EconomicNpcRegistryParser;
import com.seggellion.britannia_mod.service.EconomicNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.EconomicNpcTypeDefinition;
import com.seggellion.britannia_mod.service.EconomicNpcTypeKeys;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingData;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRequestAdapter;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRequestSerializer;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 5 acceptance: economic (Vendor/Trader) posts flow
 * through the SAME spawn-post identity, registration pipeline, and assignment
 * reconciliation as Service NPC posts, and materialize the entity type the
 * Rails-owned Economic NPC registry names — with no identity duplication.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class EconomicNpcSpawnPostGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID SERVER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TYPE_KEY = "m5_baker_vendor";
    private static final UUID CITY_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private EconomicNpcSpawnPostGameTests() {
    }

    @GameTest(batch = "economic_spawn_posts", template = TEMPLATE)
    public static void economicRegistrySectionParsesAndRejectsWholesale(GameTestHelper helper) {
        JsonObject valid = JsonParser.parseString("""
                {"economic_npc_registry": {"schema_version": 1, "revision": "abc",
                 "economic_npc_types": [{"key": "baker_vendor", "display_name": "Baker Vendor",
                 "kind": "vendor", "profession_key": "baker",
                 "minecraft_entity_type_key": "britannia_mod:baker",
                 "active": true, "spawnable": true, "definition_revision": 3}]}}
                """).getAsJsonObject();
        EconomicNpcRegistrySnapshot parsed = EconomicNpcRegistryParser.parseBootstrapRoot(valid);
        check(parsed.economicNpcTypes().containsKey("baker_vendor"), "valid section was not parsed");
        EconomicNpcTypeDefinition definition = parsed.economicNpcTypes().get("baker_vendor");
        check("vendor".equals(definition.kind()), "kind was not parsed");
        check("britannia_mod:baker".equals(definition.minecraftEntityTypeKey()), "entity key was not parsed");

        JsonObject invalid = JsonParser.parseString("""
                {"economic_npc_registry": {"schema_version": 1, "revision": "abc",
                 "economic_npc_types": [{"key": "bad_vendor", "display_name": "Bad",
                 "kind": "minter", "profession_key": "x",
                 "active": true, "spawnable": true, "definition_revision": 1}]}}
                """).getAsJsonObject();
        check(EconomicNpcRegistryParser.parseBootstrapRoot(invalid).isEmpty(),
                "invalid section must reject wholesale to the empty snapshot");
        helper.succeed();
    }

    @GameTest(batch = "economic_spawn_posts", template = TEMPLATE)
    public static void economicKeyTravelsThePipelineAndEmitsTheEconomicWireField(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServiceNpcSpawnBlockEntity post = placePost(helper, new BlockPos(1, 1, 1));
        UUID spawnPointId = requireId(post);
        UUID cityId = UUID.randomUUID();

        ServiceNpcSpawnValidationError error = withShardCredentials(helper, () ->
                post.applyConfiguration(
                        level, cityId, EconomicNpcTypeKeys.prefixed(TYPE_KEY), true, 0L));
        check(error == ServiceNpcSpawnValidationError.NONE,
                "economic configuration was rejected: " + error);

        ServiceNpcSpawnPendingRecord pending =
                ServiceNpcSpawnPendingData.get(level).findPending(spawnPointId);
        check(pending != null, "no pending registration was enqueued");
        check(EconomicNpcTypeKeys.prefixed(TYPE_KEY).equals(pending.serviceNpcTypeKey()),
                "pending record did not carry the prefixed economic key");

        String json = new String(
                ServiceNpcSpawnRequestSerializer.serialize(
                        ServiceNpcSpawnPendingRequestAdapter.adapt(pending)),
                StandardCharsets.UTF_8
        );
        JsonObject wire = JsonParser.parseString(json).getAsJsonObject();
        check(wire.has("economic_npc_type_key")
                        && TYPE_KEY.equals(wire.get("economic_npc_type_key").getAsString()),
                "wire request must emit economic_npc_type_key with the raw key");
        check(!wire.has("service_npc_type_key"),
                "wire request must not emit service_npc_type_key for an economic post");
        helper.succeed();
    }

    @GameTest(batch = "economic_spawn_posts", template = TEMPLATE)
    public static void economicAssignmentMaterializesConfiguredEntityWithoutDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        EconomicNpcRegistryCache.replace(new EconomicNpcRegistrySnapshot(1, "test", Map.of(
                TYPE_KEY, new EconomicNpcTypeDefinition(
                        TYPE_KEY, "Baker Vendor", "vendor", "baker",
                        "britannia_mod:baker", true, true, 1L)
        )));
        ServiceNpcAssignmentsCache.get(level).replace(economicSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Marta Vendor", "male", 1L
        ));

        post.serverTick();
        List<CitizenEntity> projections = projections(level, absolute, worldNpcId);
        check(projections.size() == 1, "expected exactly one materialized economic entity");
        CitizenEntity entity = projections.get(0);
        check(entity instanceof BakerEntity,
                "materialized entity must be the registry-configured type, got " + entity.getType());
        check("Marta Vendor".equals(entity.getPersonalName()), "entity name did not come from the World NPC");
        check("male".equals(entity.getGender()), "entity gender did not come from the World NPC");
        check(worldNpcId.equals(post.getAssignedNpcPublicId()), "block did not record the assignment");
        check(TYPE_KEY.equals(entity.getEconomicNpcTypeKey()),
                "projection was not stamped with its economic type key");
        check(CITY_ID.equals(entity.getEconomicCityPublicId()),
                "projection was not stamped with the authoritative city public id");
        check(!spawnPointId.equals(entity.getUUID()) && !worldNpcId.equals(entity.getUUID()),
                "entity UUID must differ from post UUID and World NPC UUID");

        post.serverTick();
        check(projections(level, absolute, worldNpcId).size() == 1,
                "reconciling again must not duplicate the projection");

        // Restart simulation: the projection is discarded (economic projections are
        // not saved entities) and the durable cache recreates it with the same
        // persistent identity and a fresh entity UUID.
        UUID firstEntityUuid = entity.getUUID();
        entity.discard();
        post.serverTick();
        List<CitizenEntity> recreated = projections(level, absolute, worldNpcId);
        check(recreated.size() == 1, "projection was not recreated after removal");
        check(worldNpcId.equals(recreated.get(0).getWorldNpcPublicId()),
                "recreated projection lost its persistent World NPC identity");
        check(!recreated.get(0).getUUID().equals(firstEntityUuid),
                "recreated projection must be a fresh loaded entity, not a resurrected UUID");

        // Assignment removal: the projection is withdrawn.
        ServiceNpcAssignmentsCache.get(level).replace(new ServiceNpcAssignmentsSnapshot(
                1, 2L, Map.of(), Map.of(), Map.of()
        ));
        post.serverTick();
        check(projections(level, absolute, worldNpcId).isEmpty(),
                "projection must be withdrawn when the assignment closes");
        helper.succeed();
    }

    private static ServiceNpcAssignmentsSnapshot economicSnapshot(
            UUID spawnPointId, BlockPos absolutePos, UUID assignmentId, UUID worldNpcId,
            String npcName, String genderKey, long revision
    ) {
        ServiceNpcAssignmentSpawnPointDefinition spawnPoint = new ServiceNpcAssignmentSpawnPointDefinition(
                spawnPointId, SERVER_ID, CITY_ID, null, TYPE_KEY,
                "Test Level", "minecraft:overworld",
                absolutePos.getX(), absolutePos.getY(), absolutePos.getZ(), true, revision
        );
        ServiceNpcAssignmentWorldNpcDefinition worldNpc = new ServiceNpcAssignmentWorldNpcDefinition(
                worldNpcId, npcName, genderKey, "baker", null, revision
        );
        ServiceNpcAssignmentDefinition assignment = new ServiceNpcAssignmentDefinition(
                assignmentId, spawnPointId, worldNpcId, "active", revision, "2026-08-10T00:00:00.000000Z"
        );
        return new ServiceNpcAssignmentsSnapshot(
                1, revision,
                Map.of(spawnPointId, spawnPoint),
                Map.of(assignmentId, assignment),
                Map.of(worldNpcId, worldNpc)
        );
    }

    private static List<CitizenEntity> projections(ServerLevel level, BlockPos pos, UUID worldNpcId) {
        return level.getEntitiesOfClass(
                CitizenEntity.class,
                new AABB(pos).inflate(16.0D),
                candidate -> candidate.isAlive()
                        && !(candidate instanceof ServiceNpcEntity)
                        && worldNpcId.equals(candidate.getWorldNpcPublicId())
        );
    }


    /**
     * Runs {@code body} with credentials naming a deliberately non-default shard, then restores the
     * previous state.
     *
     * <p>Scoped, not installed for the whole run. Credentials are what switch on the world-state
     * poller, the spawn delivery processor and world bootstrap; leaving them installed made every
     * later test in the shared world attempt real HTTP, which produced thousands of connection
     * failures and perturbed an unrelated resource-restoration test into failing. The install lasts
     * exactly as long as the call that needs it.
     *
     * <p>The shard is non-default so a regression back to the compiled constant fails here rather
     * than silently agreeing with it.
     */
    private static <T> T withShardCredentials(GameTestHelper helper, java.util.function.Supplier<T> body) {
        var server = helper.getLevel().getServer();
        com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.installForGameTesting(
                server,
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), java.util.UUID.randomUUID(),
                        "gametest_economic_shard"));
        try {
            return body.get();
        } finally {
            com.seggellion.britannia_mod.server.auth.ServerAuthRegistry.clear(server);
        }
    }
    private static ServiceNpcSpawnBlockEntity placePost(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        if (!(level.getBlockEntity(absolute) instanceof ServiceNpcSpawnBlockEntity post)) {
            throw new GameTestAssertException("missing Service NPC spawn block entity at " + absolute);
        }
        BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().setPlacedBy(
                level, absolute, level.getBlockState(absolute), null, ItemStack.EMPTY
        );
        return post;
    }

    private static UUID requireId(ServiceNpcSpawnBlockEntity post) {
        UUID id = post.getSpawnPointId();
        if (id != null) return id;
        throw new GameTestAssertException("spawn post has no UUID");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

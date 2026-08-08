package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentSpawnPointDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentWorldNpcDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone 6 Slice 3c: {@code ServiceNpcAssignmentReconciler} tying the durable
 * assignment cache (3a) to {@link ServiceNpcEntity} (3b) into live gameplay state,
 * driven through {@code ServiceNpcSpawnBlockEntity.serverTick()} exactly as it runs in
 * real gameplay — no special test-only entry point into the reconciler itself.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ServiceNpcAssignmentReconcilerGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID SERVER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private ServiceNpcAssignmentReconcilerGameTests() {
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void missingEntityIsSpawnedFromActiveAssignment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "First Banker", "active", 1L, 1L
        ));
        check(currentEntities(level, absolute, spawnPointId).isEmpty(),
                "fixture setup unexpectedly already had a Service NPC entity");

        post.serverTick();

        List<ServiceNpcEntity> spawned = currentEntities(level, absolute, spawnPointId);
        check(spawned.size() == 1, "missing entity was not spawned from the active assignment");
        ServiceNpcEntity npc = spawned.get(0);
        check(worldNpcId.equals(npc.getWorldNpcPublicId()), "spawned entity used the wrong World NPC UUID");
        check("First Banker".equals(npc.getPersonalName()), "spawned entity used the wrong permanent name");
        check(spawnPointId.equals(npc.getSpawnPointId()), "spawned entity was not tagged with this post's spawn-point UUID");
        check(assignmentId.equals(npc.getAssignmentPublicId()), "spawned entity used the wrong assignment UUID");
        check("bank_teller".equals(npc.getServiceNpcTypeKey()), "spawned entity used the wrong service type key");
        check(npc.getDefinitionRevision() == 1L, "spawned entity used the wrong definition revision");
        check(npc.getAssignmentRevision() == 1L, "spawned entity used the wrong assignment revision");
        check(npc.hasRestriction() && absolute.equals(npc.getRestrictCenter()),
                "spawned entity was not restricted to its assigned post");
        check(worldNpcId.equals(post.getAssignedNpcPublicId()), "block entity's assignedNpcPublicId was not populated");
        check("First Banker".equals(post.getAssignedNpcDisplayName()), "block entity's assignedNpcDisplayName was not populated");
        check(post.getAssignmentRevision() == 1L, "block entity's assignmentRevision was not populated");
        helper.succeed();
    }

    // Proves display identity, not just plumbing identity: CitizenEntity.finalizeSpawn
    // randomizes gender, but EntityType.create(Level) (used by both the reconciler's
    // fresh-spawn path and GameTestHelper.spawn) never calls finalizeSpawn -- that hook
    // only fires for natural spawn-placement. Without an explicit set, a fresh Service
    // NPC would silently keep CitizenEntity's own gender default ("female") regardless
    // of the real World NPC's gender_key, which this test's fixture deliberately picks
    // to differ from that default so a missed set would be caught, not coincidentally
    // pass.
    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void freshSpawnSetsDisplayNameAndGenderFromCachedWorldNpc(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Gendered Banker", "male", "active", 1L, 1L
        ));
        post.serverTick();

        List<ServiceNpcEntity> spawned = currentEntities(level, absolute, spawnPointId);
        check(spawned.size() == 1, "fixture setup did not spawn the entity");
        ServiceNpcEntity npc = spawned.get(0);
        check("Gendered Banker".equals(npc.getPersonalName()), "spawned entity's personal name did not match the cache");
        check("male".equals(npc.getGender()), "spawned entity's gender did not match the cache's gender_key");
        check(npc.getCustomName() != null && "Gendered Banker".equals(npc.getCustomName().getString()),
                "spawned entity's rendered custom name did not match the cache's World NPC name");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void duplicateEntitiesAtOnePostReduceToOneCanonicalByHighestRevision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcEntity lowerRevision = manuallyPlaceEntity(level, absolute, spawnPointId, assignmentId, worldNpcId, 3L);
        ServiceNpcEntity higherRevision = manuallyPlaceEntity(level, absolute, spawnPointId, assignmentId, worldNpcId, 9L);
        UUID survivorEngineId = higherRevision.getUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Second Banker", "active", 9L, 1L
        ));
        post.serverTick();

        check(lowerRevision.isRemoved(), "lower-revision duplicate was not discarded");
        List<ServiceNpcEntity> remaining = currentEntities(level, absolute, spawnPointId);
        check(remaining.size() == 1, "duplicate entities did not reduce to exactly one canonical instance");
        check(remaining.get(0).getUUID().equals(survivorEngineId),
                "the wrong duplicate survived -- expected the higher-assignment-revision instance to be canonical");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void closedAssignmentRemovesStaleEntityWithoutAlteringBlockUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Third Banker", "active", 1L, 1L
        ));
        post.serverTick();
        check(currentEntities(level, absolute, spawnPointId).size() == 1, "fixture setup did not spawn the entity");

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Third Banker", "closed", 2L, 1L
        ));
        post.serverTick();

        check(currentEntities(level, absolute, spawnPointId).isEmpty(),
                "stale entity was not removed once its assignment closed");
        check(spawnPointId.equals(post.getSpawnPointId()), "closing the assignment altered the block's own spawn-point UUID");
        check(post.getAssignedNpcPublicId() == null, "block entity's assignedNpcPublicId was not cleared");
        check(post.getAssignmentRevision() == 0L, "block entity's assignmentRevision was not cleared");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void reassignmentToDifferentNpcReplacesEntityWithoutAlteringBlockUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID firstWorldNpcId = UUID.randomUUID();
        UUID firstAssignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, firstAssignmentId, firstWorldNpcId, "Original Banker", "active", 1L, 1L
        ));
        post.serverTick();
        List<ServiceNpcEntity> original = currentEntities(level, absolute, spawnPointId);
        check(original.size() == 1 && firstWorldNpcId.equals(original.get(0).getWorldNpcPublicId()),
                "fixture setup did not spawn the original assignment's entity");

        UUID secondWorldNpcId = UUID.randomUUID();
        UUID secondAssignmentId = UUID.randomUUID();
        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, secondAssignmentId, secondWorldNpcId, "Replacement Banker", "active", 1L, 1L
        ));
        post.serverTick();

        List<ServiceNpcEntity> replaced = currentEntities(level, absolute, spawnPointId);
        check(replaced.size() == 1, "reassignment did not converge to exactly one entity");
        check(secondWorldNpcId.equals(replaced.get(0).getWorldNpcPublicId()), "reassignment did not swap to the new NPC's identity");
        check("Replacement Banker".equals(replaced.get(0).getPersonalName()), "reassignment did not use the new NPC's name");
        check(secondAssignmentId.equals(replaced.get(0).getAssignmentPublicId()), "reassignment did not record the new assignment UUID");
        check(spawnPointId.equals(post.getSpawnPointId()), "reassignment altered the block's own spawn-point UUID");
        helper.succeed();
    }

    // A same-World-NPC, cross-post reassignment (Slice 1's own "reassign to a
    // different spawn point" scenario) is distinct from same-post reassignment above:
    // Post A's own active-assignment lookup must go to null not because its
    // assignment's status flips while still present in the snapshot (as
    // closedAssignmentRemovesStaleEntityWithoutAlteringBlockUuid exercises), but
    // because the assignment referencing Post A's spawn-point UUID is entirely absent
    // from the new snapshot -- the more realistic shape of what Rails sends once an
    // assignment moves elsewhere. findActiveAssignment's loop treats "absent" and
    // "present but not active" identically (both leave `best` null), so this proves
    // that equivalence holds rather than assuming it from a similar-looking code path.
    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void crossPostReassignmentMovesEntityFromPostAToPostBWithoutIdentityRegeneration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relativeA = new BlockPos(1, 1, 1);
        BlockPos relativeB = new BlockPos(6, 1, 1);
        ServiceNpcSpawnBlockEntity postA = placePost(helper, relativeA);
        ServiceNpcSpawnBlockEntity postB = placePost(helper, relativeB);
        BlockPos absoluteA = helper.absolutePos(relativeA);
        BlockPos absoluteB = helper.absolutePos(relativeB);
        UUID spawnPointIdA = requireId(postA);
        UUID spawnPointIdB = requireId(postB);
        UUID worldNpcId = UUID.randomUUID();
        UUID firstAssignmentId = UUID.randomUUID();
        ServiceNpcAssignmentWorldNpcDefinition worldNpc = new ServiceNpcAssignmentWorldNpcDefinition(
                worldNpcId, "Traveling Banker", "female", "banker", "bank_teller", 1L
        );

        ServiceNpcAssignmentsCache.get(level).replace(twoPostSnapshot(
                spawnPointIdA, absoluteA, spawnPointIdB, absoluteB,
                List.of(new ServiceNpcAssignmentDefinition(
                        firstAssignmentId, spawnPointIdA, worldNpcId, "active", 1L, "2026-07-18T04:38:00.058754Z")),
                List.of(worldNpc), 1L
        ));
        postA.serverTick();
        List<ServiceNpcEntity> atA = currentEntities(level, absoluteA, spawnPointIdA);
        check(atA.size() == 1 && worldNpcId.equals(atA.get(0).getWorldNpcPublicId()),
                "fixture setup did not spawn the World NPC at Post A");

        UUID secondAssignmentId = UUID.randomUUID();
        ServiceNpcAssignmentsCache.get(level).replace(twoPostSnapshot(
                spawnPointIdA, absoluteA, spawnPointIdB, absoluteB,
                List.of(new ServiceNpcAssignmentDefinition(
                        secondAssignmentId, spawnPointIdB, worldNpcId, "active", 1L, "2026-07-18T04:39:00.058754Z")),
                List.of(worldNpc), 2L
        ));
        postA.serverTick();
        postB.serverTick();

        check(currentEntities(level, absoluteA, spawnPointIdA).isEmpty(),
                "Post A retained a live entity after its assignment moved to Post B");
        List<ServiceNpcEntity> atB = currentEntities(level, absoluteB, spawnPointIdB);
        check(atB.size() == 1, "Post B did not end up with exactly one entity after the reassignment");
        check(worldNpcId.equals(atB.get(0).getWorldNpcPublicId()),
                "the World NPC's UUID was regenerated across the cross-post reassignment");
        check("Traveling Banker".equals(atB.get(0).getPersonalName()),
                "the World NPC's display name was regenerated across the cross-post reassignment");
        check(spawnPointIdA.equals(postA.getSpawnPointId()), "reassignment altered Post A's own spawn-point UUID");
        check(spawnPointIdB.equals(postB.getSpawnPointId()), "reassignment altered Post B's own spawn-point UUID");
        helper.succeed();
    }

    // The realistic operational case: a player is very unlikely to be standing at the
    // old post at the exact moment an admin reassigns its occupant elsewhere, so
    // Post A's chunk (both its block entity and its stale mob entity) is unloaded
    // -- not ticking at all -- while the reassignment actually happens. Confirms
    // cleanup still runs correctly once that chunk is next loaded and ticked, not
    // lost because no tick was running for it in the interim.
    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void crossPostReassignmentCleansUpStalePostAEvenIfItsChunkWasUnloadedAtReassignmentTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relativeA = new BlockPos(1, 1, 1);
        BlockPos relativeB = new BlockPos(6, 1, 1);
        ServiceNpcSpawnBlockEntity postA = placePost(helper, relativeA);
        ServiceNpcSpawnBlockEntity postB = placePost(helper, relativeB);
        BlockPos absoluteA = helper.absolutePos(relativeA);
        BlockPos absoluteB = helper.absolutePos(relativeB);
        UUID spawnPointIdA = requireId(postA);
        UUID spawnPointIdB = requireId(postB);
        UUID worldNpcId = UUID.randomUUID();
        UUID firstAssignmentId = UUID.randomUUID();
        ServiceNpcAssignmentWorldNpcDefinition worldNpc = new ServiceNpcAssignmentWorldNpcDefinition(
                worldNpcId, "Unloaded-Chunk Banker", "female", "banker", "bank_teller", 1L
        );

        ServiceNpcAssignmentsCache.get(level).replace(twoPostSnapshot(
                spawnPointIdA, absoluteA, spawnPointIdB, absoluteB,
                List.of(new ServiceNpcAssignmentDefinition(
                        firstAssignmentId, spawnPointIdA, worldNpcId, "active", 1L, "2026-07-18T04:38:00.058754Z")),
                List.of(worldNpc), 1L
        ));
        postA.serverTick();
        ServiceNpcEntity staleEntity = currentEntities(level, absoluteA, spawnPointIdA).get(0);

        // Post A's chunk unloads: both its block entity and its mob entity leave the
        // live level, exactly like a real chunk unload -- and, critically, Post A's
        // serverTick() does not run again until the chunk reloads below.
        CompoundTag savedStaleEntity = saveForReload(staleEntity);
        staleEntity.discard();
        CompoundTag savedBlockA = postA.saveCustomOnly(level.registryAccess());
        level.removeBlockEntity(absoluteA);

        UUID secondAssignmentId = UUID.randomUUID();
        ServiceNpcAssignmentsCache.get(level).replace(twoPostSnapshot(
                spawnPointIdA, absoluteA, spawnPointIdB, absoluteB,
                List.of(new ServiceNpcAssignmentDefinition(
                        secondAssignmentId, spawnPointIdB, worldNpcId, "active", 1L, "2026-07-18T04:39:00.058754Z")),
                List.of(worldNpc), 2L
        ));
        postB.serverTick();
        List<ServiceNpcEntity> atB = currentEntities(level, absoluteB, spawnPointIdB);
        check(atB.size() == 1 && worldNpcId.equals(atB.get(0).getWorldNpcPublicId()),
                "Post B did not correctly pick up the reassigned World NPC while Post A's chunk was unloaded");

        // Post A's chunk reloads: its block entity and its (now-stale) mob entity are
        // both reconstructed from the NBT captured at the moment of unload, unaware
        // anything changed while they were gone.
        ServiceNpcSpawnBlockEntity reloadedPostA = new ServiceNpcSpawnBlockEntity(
                absoluteA, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState()
        );
        reloadedPostA.loadCustomOnly(savedBlockA, level.registryAccess());
        level.setBlockEntity(reloadedPostA);
        ServiceNpcEntity reloadedStaleEntity = reload(level, savedStaleEntity);
        level.addFreshEntity(reloadedStaleEntity);
        check(currentEntities(level, absoluteA, spawnPointIdA).size() == 1,
                "reloading Post A's chunk did not restore the stale entity to the live level");

        reloadedPostA.serverTick();

        check(currentEntities(level, absoluteA, spawnPointIdA).isEmpty(),
                "stale entity at Post A was not cleaned up once its reloaded chunk finally ticked");
        check(spawnPointIdA.equals(reloadedPostA.getSpawnPointId()), "cleanup altered Post A's own spawn-point UUID");
        check(reloadedPostA.getAssignedNpcPublicId() == null, "Post A's assignedNpcPublicId was not cleared after cleanup");
        check(currentEntities(level, absoluteB, spawnPointIdB).size() == 1,
                "Post A's own cleanup tick unexpectedly disturbed Post B's entity");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void chunkUnloadReloadReturnsSameNamedUuidTeller(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Fourth Banker", "male", "active", 1L, 1L
        ));
        post.serverTick();
        ServiceNpcEntity original = currentEntities(level, absolute, spawnPointId).get(0);

        CompoundTag saved = saveForReload(original);
        original.discard();
        ServiceNpcEntity reloaded = reload(level, saved);
        check(worldNpcId.equals(reloaded.getWorldNpcPublicId()), "reloaded entity lost its World NPC UUID before reconciliation ran");
        // EntityType.loadEntityRecursive only constructs and configures the entity --
        // real chunk loading re-inserts it into the level automatically, which this
        // must do explicitly, matching QuestGiverSpawnBlockEntity's own restore path.
        level.addFreshEntity(reloaded);

        post.serverTick();

        List<ServiceNpcEntity> after = currentEntities(level, absolute, spawnPointId);
        check(after.size() == 1, "chunk unload/reload produced more than one entity for the same post");
        check(after.get(0).getUUID().equals(reloaded.getUUID()),
                "reconciliation spawned a brand-new entity instead of reusing the reloaded one");
        check(worldNpcId.equals(after.get(0).getWorldNpcPublicId()), "chunk unload/reload changed the World NPC UUID");
        check("Fourth Banker".equals(after.get(0).getPersonalName()), "chunk unload/reload changed the permanent name");
        check("male".equals(after.get(0).getGender()), "chunk unload/reload changed the display gender");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void simulatedServerRestartReturnsSameNamedUuidTeller(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Fifth Banker", "male", "active", 1L, 1L
        ));
        post.serverTick();
        ServiceNpcEntity original = currentEntities(level, absolute, spawnPointId).get(0);

        // Reconstruct everything from scratch, the way a real process restart would:
        // the cache is reloaded from its own saved tag (not the live in-memory
        // instance), the block entity is a brand-new Java object loaded from NBT, and
        // the mob entity is a brand-new Java object loaded from NBT -- nothing this
        // test still holds a reference to survives.
        CompoundTag savedCache = ServiceNpcAssignmentsCache.get(level).save(new CompoundTag(), level.registryAccess());
        ServiceNpcAssignmentsCache.get(level).replace(
                ServiceNpcAssignmentsCache.load(savedCache, level.registryAccess()).snapshot()
        );

        CompoundTag savedBlock = post.saveCustomOnly(level.registryAccess());
        level.removeBlockEntity(absolute);
        ServiceNpcSpawnBlockEntity reloadedPost = new ServiceNpcSpawnBlockEntity(
                absolute, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().defaultBlockState()
        );
        reloadedPost.loadCustomOnly(savedBlock, level.registryAccess());
        level.setBlockEntity(reloadedPost);

        CompoundTag savedEntity = saveForReload(original);
        original.discard();
        ServiceNpcEntity reloadedEntity = reload(level, savedEntity);
        // See chunkUnloadReloadReturnsSameNamedUuidTeller: loadEntityRecursive does not
        // re-insert the entity into the level on its own.
        level.addFreshEntity(reloadedEntity);

        reloadedPost.serverTick();

        List<ServiceNpcEntity> after = currentEntities(level, absolute, spawnPointId);
        check(after.size() == 1, "simulated restart produced more than one entity for the same post");
        check(after.get(0).getUUID().equals(reloadedEntity.getUUID()),
                "simulated restart spawned a brand-new entity instead of reusing the reloaded one");
        check(worldNpcId.equals(after.get(0).getWorldNpcPublicId()), "simulated restart changed the World NPC UUID");
        check("Fifth Banker".equals(after.get(0).getPersonalName()), "simulated restart changed the permanent name");
        check("male".equals(after.get(0).getGender()), "simulated restart changed the display gender");
        check(spawnPointId.equals(reloadedPost.getSpawnPointId()), "simulated restart changed the block's own spawn-point UUID");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void offlineRecoveredCacheSnapshotStillReconcilesCorrectly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcAssignmentsSnapshot original = singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Offline Banker", "active", 1L, 1L
        );
        // Simulate a fresh server process where Rails is unreachable: nothing has
        // fetched yet, only the last-known-good on-disk cache tag exists, exactly like
        // ServiceNpcAssignmentsCacheGameTests#offlineStartupRecoversTheLastKnownGoodSnapshot.
        ServiceNpcAssignmentsCache onDisk = new ServiceNpcAssignmentsCache();
        onDisk.replace(original);
        CompoundTag savedTag = onDisk.save(new CompoundTag(), level.registryAccess());
        ServiceNpcAssignmentsSnapshot recovered =
                ServiceNpcAssignmentsCache.load(savedTag, level.registryAccess()).snapshot();
        check(recovered.equals(original), "offline recovery did not reproduce the original snapshot");

        ServiceNpcAssignmentsCache.get(level).replace(recovered);
        post.serverTick();

        List<ServiceNpcEntity> spawned = currentEntities(level, absolute, spawnPointId);
        check(spawned.size() == 1, "reconciliation did not spawn an entity from the offline-recovered snapshot");
        check(worldNpcId.equals(spawned.get(0).getWorldNpcPublicId()),
                "reconciliation against the offline-recovered snapshot used the wrong World NPC UUID");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void staleEntityRevisionNeverOverridesNewerCacheRevision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        ServiceNpcEntity staleEntity = manuallyPlaceEntity(level, absolute, spawnPointId, assignmentId, worldNpcId, 1L);
        staleEntity.setServiceNpcTypeKey("stale_type");
        staleEntity.setDefinitionRevision(1L);
        UUID engineId = staleEntity.getUUID();

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Sixth Banker", "active", 5L, 4L
        ));
        post.serverTick();

        List<ServiceNpcEntity> after = currentEntities(level, absolute, spawnPointId);
        check(after.size() == 1 && after.get(0).getUUID().equals(engineId),
                "cache-authoritative update unexpectedly replaced the entity instead of updating it in place");
        ServiceNpcEntity updated = after.get(0);
        check(updated.getAssignmentRevision() == 5L, "stale NBT assignmentRevision was not overridden by the newer cache revision");
        check(updated.getDefinitionRevision() == 4L, "stale NBT definitionRevision was not overridden by the newer cache revision");
        check("bank_teller".equals(updated.getServiceNpcTypeKey()), "stale NBT serviceNpcTypeKey was not overridden by the cache");
        helper.succeed();
    }

    @GameTest(batch = "world_state_reconciler", template = TEMPLATE)
    public static void questGiverEntityNearAPostIsUnaffectedByReconciliation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        ServiceNpcSpawnBlockEntity post = placePost(helper, relative);
        BlockPos absolute = helper.absolutePos(relative);
        UUID spawnPointId = requireId(post);
        UUID worldNpcId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        QuestGiverEntity questGiver = helper.spawn(EntityRegistry.QUEST_GIVER.get(), new BlockPos(2, 1, 2));
        UUID questGiverWorldNpcId = UUID.randomUUID();
        questGiver.setWorldNpcPublicId(questGiverWorldNpcId);

        ServiceNpcAssignmentsCache.get(level).replace(singleAssignmentSnapshot(
                spawnPointId, absolute, assignmentId, worldNpcId, "Seventh Banker", "active", 1L, 1L
        ));
        post.serverTick();

        check(!questGiver.isRemoved(), "reconciliation discarded an unrelated QuestGiverEntity");
        check(questGiverWorldNpcId.equals(questGiver.getWorldNpcPublicId()),
                "reconciliation mutated an unrelated QuestGiverEntity's World NPC UUID");
        check(currentEntities(level, absolute, spawnPointId).size() == 1,
                "reconciliation did not still correctly spawn the Service NPC for its own post");
        helper.succeed();
    }

    // ---------- Fixtures ----------

    private static ServiceNpcSpawnBlockEntity placePost(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get());
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        ServiceNpcSpawnBlockEntity post = requirePost(level, absolute);
        BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get().setPlacedBy(
                level, absolute, level.getBlockState(absolute), null, ItemStack.EMPTY
        );
        return post;
    }

    private static ServiceNpcSpawnBlockEntity requirePost(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity post) return post;
        throw new IllegalStateException("missing Service NPC spawn block entity at " + pos);
    }

    private static UUID requireId(ServiceNpcSpawnBlockEntity post) {
        UUID id = post.getSpawnPointId();
        if (id != null) return id;
        throw new IllegalStateException("Service NPC spawn post has no UUID");
    }

    private static ServiceNpcAssignmentsSnapshot singleAssignmentSnapshot(
            UUID spawnPointId, BlockPos absolutePos, UUID assignmentId, UUID worldNpcId,
            String npcName, String status, long assignmentRevision, long worldNpcRevision
    ) {
        return singleAssignmentSnapshot(
                spawnPointId, absolutePos, assignmentId, worldNpcId,
                npcName, "female", status, assignmentRevision, worldNpcRevision
        );
    }

    private static ServiceNpcAssignmentsSnapshot singleAssignmentSnapshot(
            UUID spawnPointId, BlockPos absolutePos, UUID assignmentId, UUID worldNpcId,
            String npcName, String genderKey, String status, long assignmentRevision, long worldNpcRevision
    ) {
        ServiceNpcAssignmentSpawnPointDefinition spawnPoint = spawnPointDef(spawnPointId, absolutePos);
        ServiceNpcAssignmentWorldNpcDefinition worldNpc = new ServiceNpcAssignmentWorldNpcDefinition(
                worldNpcId, npcName, genderKey, "banker", "bank_teller", worldNpcRevision
        );
        ServiceNpcAssignmentDefinition assignment = new ServiceNpcAssignmentDefinition(
                assignmentId, spawnPointId, worldNpcId, status, assignmentRevision, "2026-07-18T04:38:00.058754Z"
        );
        return new ServiceNpcAssignmentsSnapshot(
                1, assignmentRevision,
                Map.of(spawnPointId, spawnPoint),
                Map.of(assignmentId, assignment),
                Map.of(worldNpcId, worldNpc)
        );
    }

    private static ServiceNpcAssignmentSpawnPointDefinition spawnPointDef(UUID id, BlockPos absolutePos) {
        return new ServiceNpcAssignmentSpawnPointDefinition(
                id, SERVER_ID, null, "bank_teller",
                "Test Level", "minecraft:overworld",
                absolutePos.getX(), absolutePos.getY(), absolutePos.getZ(), true, 1L
        );
    }

    /**
     * Builds a snapshot spanning both posts in a cross-post reassignment: both spawn
     * points are always present (Rails knows about both registered posts), but the
     * caller controls which assignments/world NPCs are actually included -- in
     * particular, letting a superseded assignment be entirely absent rather than
     * merely status-flipped, since that is the more realistic shape of what Rails
     * sends once an assignment moves to a different spawn point.
     */
    private static ServiceNpcAssignmentsSnapshot twoPostSnapshot(
            UUID spawnPointAId, BlockPos absoluteA,
            UUID spawnPointBId, BlockPos absoluteB,
            List<ServiceNpcAssignmentDefinition> assignments,
            List<ServiceNpcAssignmentWorldNpcDefinition> worldNpcs,
            long revision
    ) {
        Map<UUID, ServiceNpcAssignmentSpawnPointDefinition> spawnPoints = new LinkedHashMap<>();
        spawnPoints.put(spawnPointAId, spawnPointDef(spawnPointAId, absoluteA));
        spawnPoints.put(spawnPointBId, spawnPointDef(spawnPointBId, absoluteB));
        Map<UUID, ServiceNpcAssignmentDefinition> assignmentMap = new LinkedHashMap<>();
        assignments.forEach(assignment -> assignmentMap.put(assignment.publicId(), assignment));
        Map<UUID, ServiceNpcAssignmentWorldNpcDefinition> worldNpcMap = new LinkedHashMap<>();
        worldNpcs.forEach(worldNpc -> worldNpcMap.put(worldNpc.publicId(), worldNpc));
        return new ServiceNpcAssignmentsSnapshot(1, revision, spawnPoints, assignmentMap, worldNpcMap);
    }

    private static List<ServiceNpcEntity> currentEntities(ServerLevel level, BlockPos pos, UUID spawnPointId) {
        return level.getEntities(
                EntityRegistry.SERVICE_NPC.get(),
                new AABB(pos).inflate(16.0D),
                entity -> spawnPointId.equals(entity.getSpawnPointId()) && entity.isAlive()
        );
    }

    private static ServiceNpcEntity manuallyPlaceEntity(
            ServerLevel level, BlockPos pos, UUID spawnPointId, UUID assignmentId, UUID worldNpcId, long assignmentRevision
    ) {
        ServiceNpcEntity entity = EntityRegistry.SERVICE_NPC.get().create(level);
        if (entity == null) throw new IllegalStateException("could not create a ServiceNpcEntity fixture");
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        entity.setWorldNpcPublicId(worldNpcId);
        entity.setSpawnPointId(spawnPointId);
        entity.setAssignmentPublicId(assignmentId);
        entity.setAssignmentRevision(assignmentRevision);
        entity.assignHomePost(pos);
        level.addFreshEntity(entity);
        return entity;
    }

    // Entity.saveWithoutId(CompoundTag) deliberately omits the entity-type "id" tag --
    // EntityType.loadEntityRecursive needs that tag to pick a type to reconstruct.
    // Matches ServiceNpcEntityGameTests#saveForReload and, before that,
    // TraderSpawnBlockEntity#updateSnapshot's own established use of the same pattern.
    private static CompoundTag saveForReload(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return tag;
    }

    private static ServiceNpcEntity reload(ServerLevel level, CompoundTag saved) {
        Entity restored = EntityType.loadEntityRecursive(saved, level, e -> e);
        check(restored instanceof ServiceNpcEntity, "reload did not reconstruct a ServiceNpcEntity");
        return (ServiceNpcEntity) restored;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

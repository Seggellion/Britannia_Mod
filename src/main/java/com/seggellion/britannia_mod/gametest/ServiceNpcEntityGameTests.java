package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Milestone 6 Slice 3b: {@link ServiceNpcEntity} in isolation — persistence, despawn
 * prevention, home-post restriction, and proof that {@code CitizenEntity} and its other
 * subclasses are unaffected. Nothing here spawns the entity through any real gameplay
 * trigger; that wiring is Slice 3c.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ServiceNpcEntityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private static final UUID SAMPLE_WORLD_NPC_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SAMPLE_SPAWN_POINT_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SAMPLE_ASSIGNMENT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    private ServiceNpcEntityGameTests() {
    }

    // ---------- Test-support spawning path ----------
    // GameTestHelper.spawn(EntityType<E>, BlockPos) is vanilla/NeoForge's own generic
    // spawn API and already returns the concrete ServiceNpcEntity directly. That is the
    // "minimal test-support spawning path" this slice needs: no dedicated factory or
    // command was added to production code, since none exists for any other
    // CitizenEntity subclass either, and inventing one would be unexercised production
    // surface area for a slice that must not wire any real spawning trigger.
    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void testSupportSpawnProducesAPlainServiceNpc(GameTestHelper helper) {
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
        check(npc != null && npc.isAlive(), "test-support spawn did not produce a live ServiceNpcEntity");
        helper.succeed();
    }

    // ---------- Persistence round-trip ----------
    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void chunkUnloadReloadPreservesAllPersistentFields(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos homePost = helper.absolutePos(relative);
        ServiceNpcEntity original = helper.spawn(EntityRegistry.SERVICE_NPC.get(), relative);
        applySampleFields(original, homePost);

        CompoundTag saved = saveForReload(original);
        original.discard();

        ServiceNpcEntity reloaded = reload(level, saved);
        assertSampleFields(reloaded, homePost, "chunk unload/reload");
        helper.succeed();
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void simulatedServerRestartPreservesAllPersistentFields(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos homePost = helper.absolutePos(relative);
        ServiceNpcEntity original = helper.spawn(EntityRegistry.SERVICE_NPC.get(), relative);
        applySampleFields(original, homePost);

        // Simulate everything a real process restart discards: the original Java object
        // is gone and only the saved tag survives, exactly mirroring
        // ServiceNpcAssignmentsCacheGameTests#offlineStartupRecoversTheLastKnownGoodSnapshot's
        // own "fresh server process" idiom, applied here to an entity instead of a
        // SavedData cache.
        CompoundTag saved = saveForReload(original);
        original.discard();
        CompoundTag onDisk = saved.copy();

        ServiceNpcEntity reloaded = reload(level, onDisk);
        assertSampleFields(reloaded, homePost, "simulated server restart");
        helper.succeed();
    }

    // ---------- Despawn prevention ----------
    // Direct assertion on the override's behavior, not a real-time despawn simulation:
    // proves isPersistenceRequired() is unconditionally true and stays true even after a
    // reload carries a tampered "PersistenceRequired=false" tag, the exact field vanilla
    // Mob.readAdditionalSaveData would otherwise overwrite unconditionally.
    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void isPersistenceRequiredIsUnconditionallyTrueRegardlessOfNbt(GameTestHelper helper) {
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
        check(npc.isPersistenceRequired(), "ServiceNpcEntity was not despawn-exempt by default");

        CompoundTag tampered = new CompoundTag();
        npc.saveWithoutId(tampered);
        tampered.putBoolean("PersistenceRequired", false);
        npc.readAdditionalSaveData(tampered);

        check(npc.isPersistenceRequired(),
                "isPersistenceRequired() became false after reloading a tampered PersistenceRequired=false tag");
        helper.succeed();
    }

    // ---------- Home-post restriction ----------
    @GameTest(batch = "world_state_entity", template = TEMPLATE, timeoutTicks = 60)
    public static void homePostRestrictionNeverExceedsConfiguredRadius(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos post = helper.absolutePos(relative);
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), relative);
        npc.assignHomePost(post);

        helper.runAfterDelay(40, () -> {
            double distance = Math.sqrt(npc.blockPosition().distSqr(post));
            check(distance <= 1.0D + 1.0E-6,
                    "home-post restriction let the entity wander beyond its configured radius (distance="
                            + distance + ")");
            helper.succeed();
        });
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void homePostRestrictionSurvivesSaveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos post = helper.absolutePos(relative);
        ServiceNpcEntity original = helper.spawn(EntityRegistry.SERVICE_NPC.get(), relative);
        original.assignHomePost(post);
        check(original.hasRestriction() && post.equals(original.getRestrictCenter()),
                "assignHomePost did not apply a restriction before save/reload");

        CompoundTag saved = saveForReload(original);
        original.discard();

        ServiceNpcEntity reloaded = reload(level, saved);
        check(reloaded.hasRestriction() && post.equals(reloaded.getRestrictCenter()),
                "home-post restriction did not survive save/reload");
        helper.succeed();
    }

    // Part A (post-3b): assignHomePost(BlockPos, int) lets Slice 3c pass a
    // per-type/per-assignment radius instead of always accepting the default. Proves
    // that non-default radius round-trips through save/reload too, not just position —
    // the earlier test above only ever exercised the default radius of 1.
    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void assignHomePostAcceptsAndPersistsACustomRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos post = helper.absolutePos(relative);
        ServiceNpcEntity original = helper.spawn(EntityRegistry.SERVICE_NPC.get(), relative);
        original.assignHomePost(post, 4);
        check(original.hasRestriction() && post.equals(original.getRestrictCenter())
                        && original.getRestrictRadius() == 4.0F,
                "assignHomePost(pos, radius) did not apply the custom radius");

        CompoundTag saved = saveForReload(original);
        original.discard();

        ServiceNpcEntity reloaded = reload(level, saved);
        check(reloaded.hasRestriction() && post.equals(reloaded.getRestrictCenter())
                        && reloaded.getRestrictRadius() == 4.0F,
                "custom home-post radius did not survive save/reload");
        helper.succeed();
    }

    // ---------- Quest Trader regression ----------
    // CitizenEntity itself was not modified by this slice. Rather than only assert that
    // by inspection, exercise an unrelated existing CitizenEntity subclass through the
    // same save/reload boundary this slice's own tests exercise on ServiceNpcEntity, and
    // prove its behavior is unaffected rather than merely claiming it.
    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void questGiverEntityIsUnaffectedByThisSlice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        QuestGiverEntity original = helper.spawn(EntityRegistry.QUEST_GIVER.get(), relative);
        UUID worldNpcId = UUID.randomUUID();
        original.setWorldNpcPublicId(worldNpcId);
        check(original.shouldBeSaved(), "QuestGiverEntity unexpectedly stopped being savable");

        CompoundTag saved = saveForReload(original);
        original.discard();

        Entity restored = EntityType.loadEntityRecursive(saved, level, e -> e);
        check(restored instanceof QuestGiverEntity, "QuestGiverEntity reload was broken by this slice");
        check(worldNpcId.equals(((QuestGiverEntity) restored).getWorldNpcPublicId()),
                "QuestGiverEntity's own World NPC UUID did not survive reload after this slice's changes");
        helper.succeed();
    }

    // Entity.saveWithoutId(CompoundTag) deliberately omits the entity-type "id" tag (that
    // is what "WithoutId" refers to, not the UUID) — EntityType.loadEntityRecursive needs
    // that tag to pick a type to reconstruct. Matches the exact pattern this codebase
    // already uses at TraderSpawnBlockEntity#updateSnapshot for the same reason.
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

    private static void applySampleFields(ServiceNpcEntity npc, BlockPos homePost) {
        npc.setWorldNpcPublicId(SAMPLE_WORLD_NPC_ID);
        npc.setSpawnPointId(SAMPLE_SPAWN_POINT_ID);
        npc.setAssignmentPublicId(SAMPLE_ASSIGNMENT_ID);
        npc.setServiceNpcTypeKey("bank_teller");
        npc.setDefinitionRevision(3L);
        npc.setAssignmentRevision(7L);
        npc.assignHomePost(homePost);
    }

    private static void assertSampleFields(ServiceNpcEntity npc, BlockPos homePost, String scenario) {
        check(SAMPLE_WORLD_NPC_ID.equals(npc.getWorldNpcPublicId()),
                scenario + " did not preserve the inherited World NPC UUID");
        check(SAMPLE_SPAWN_POINT_ID.equals(npc.getSpawnPointId()),
                scenario + " did not preserve spawnPointId");
        check(SAMPLE_ASSIGNMENT_ID.equals(npc.getAssignmentPublicId()),
                scenario + " did not preserve assignmentPublicId");
        check("bank_teller".equals(npc.getServiceNpcTypeKey()),
                scenario + " did not preserve serviceNpcTypeKey");
        check(npc.getDefinitionRevision() == 3L, scenario + " did not preserve definitionRevision");
        check(npc.getAssignmentRevision() == 7L, scenario + " did not preserve assignmentRevision");
        check(npc.hasRestriction() && homePost.equals(npc.getRestrictCenter()),
                scenario + " did not preserve the home-post restriction");
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

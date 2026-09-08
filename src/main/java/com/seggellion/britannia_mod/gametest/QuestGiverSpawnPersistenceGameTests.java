package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The quest giver spawner's custom API id has to survive a chunk reload.
 *
 * <p>{@code customApiId} is the Rails key an operator types into the spawn screen for a
 * "Generic Combat" giver, and it was the one configuration field {@code saveAdditional} never
 * wrote. A standing NPC hid that: the spawner keeps a snapshot of it and restores from that,
 * without ever consulting the field. But a spawner that has to rebuild its NPC -- the snapshot
 * gone and the NPC killed -- reads the field to stamp the new one's identity, and a field that
 * was never persisted is empty, so the giver came back as {@code unknown_combat_npc} and
 * resolved to a Rails quest giver nobody configured.
 *
 * <p>Every test here loads into a <em>fresh</em> block entity, because a fresh one is what a
 * chunk load builds. Reloading the same instance would leave the field populated in memory and
 * pass whether or not it ever reached NBT -- which is precisely the defect.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestGiverSpawnPersistenceGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** An operator's Rails key. Prefixed, so the derived display name is visible in it too. */
    private static final String API_ID = "grizzled_mercenary_npc";

    private static final BlockPos SPAWNER = new BlockPos(3, 2, 3);
    private static final int RADIUS = 2;

    /** {@code serverTick} spends this many ticks on its world-loading grace period first. */
    private static final int GRACE_TICKS = 40;

    private QuestGiverSpawnPersistenceGameTests() {
    }

    /** The field itself, read back by a block entity that never saw it set. */
    @GameTest(template = TEMPLATE)
    public static void theCustomApiIdSurvivesAChunkReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeConfiguredSpawner(helper);

        QuestGiverSpawnBlockEntity reloaded = reload(level, pos, saveTag(level, pos));

        check(API_ID.equals(reloaded.getCustomApiId()),
            "the custom api id did not survive the reload, got '" + reloaded.getCustomApiId() + "'");
        check("Generic Combat".equals(reloaded.getNpcName()),
            "the archetype did not survive the reload, got '" + reloaded.getNpcName() + "'");

        helper.setBlock(SPAWNER, Blocks.AIR);
        helper.succeed();
    }

    /** And the point of persisting it: the NPC the reloaded spawner rebuilds carries the key. */
    @GameTest(template = TEMPLATE)
    public static void theReloadedSpawnerStampsTheRespawnedNpcWithIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeConfiguredSpawner(helper);

        QuestGiverSpawnBlockEntity reloaded = reload(level, pos, saveTag(level, pos));
        level.removeBlockEntity(pos);
        level.setBlockEntity(reloaded);
        check(level.getBlockEntity(pos) == reloaded,
            "the reloaded block entity is not the one the world would tick");

        for (int tick = 0; tick <= GRACE_TICKS; tick++) {
            reloaded.serverTick();
        }

        List<QuestGiverEntity> givers = level.getEntitiesOfClass(
            QuestGiverEntity.class, new AABB(pos).inflate(RADIUS + 6.0D));
        check(givers.size() == 1, "expected exactly one respawned giver, got " + givers.size());

        QuestGiverEntity respawned = givers.get(0);
        check(API_ID.equals(respawned.getQuestGiverApiId()),
            "the respawned giver must carry the configured Rails key, got '"
                + respawned.getQuestGiverApiId() + "'");
        check(respawned.getPersonalName() != null && respawned.getPersonalName().endsWith(":" + API_ID),
            "the display name must be derived from the same key, got '"
                + respawned.getPersonalName() + "'");

        helper.setBlock(SPAWNER, Blocks.AIR); // takes the respawned NPC with it, via onDestroyed
        helper.succeed();
    }

    /** A spawner saved before the key existed still loads, and keeps behaving as it did. */
    @GameTest(template = TEMPLATE)
    public static void aSaveWithoutTheKeyStillLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeConfiguredSpawner(helper);

        CompoundTag legacy = saveTag(level, pos);
        legacy.remove("CustomApiId");

        QuestGiverSpawnBlockEntity reloaded = reload(level, pos, legacy);

        check(reloaded.getCustomApiId().isEmpty(),
            "an older save carries no key, so the id must stay empty, got '"
                + reloaded.getCustomApiId() + "'");
        check("Generic Combat".equals(reloaded.getNpcName()) && reloaded.getSpawnRadius() == RADIUS,
            "the rest of the configuration must still load from an older save");

        helper.setBlock(SPAWNER, Blocks.AIR);
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    /**
     * Ground for {@code Util.findGround} to stand an NPC on -- the test template is entirely
     * air -- and then a spawner configured the way the spawn screen configures one.
     */
    private static BlockPos placeConfiguredSpawner(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(SPAWNER, BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get());

        BlockPos pos = helper.absolutePos(SPAWNER);
        spawnerAt(helper.getLevel(), pos).applyConfig("Generic Combat", "Britain", API_ID, "male", RADIUS);
        return pos;
    }

    private static CompoundTag saveTag(ServerLevel level, BlockPos pos) {
        return spawnerAt(level, pos).saveCustomOnly(level.registryAccess());
    }

    /** What a chunk load does: a brand new block entity, filled from nothing but the tag. */
    private static QuestGiverSpawnBlockEntity reload(ServerLevel level, BlockPos pos, CompoundTag tag) {
        QuestGiverSpawnBlockEntity reloaded = new QuestGiverSpawnBlockEntity(
            pos, BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get().defaultBlockState());
        reloaded.loadCustomOnly(tag, level.registryAccess());
        return reloaded;
    }

    private static QuestGiverSpawnBlockEntity spawnerAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof QuestGiverSpawnBlockEntity spawner) return spawner;
        throw new GameTestAssertException("no quest giver spawner at " + pos);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

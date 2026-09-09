package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Rowan farming questline M6: the farming quest giver is placed anywhere by the administrative
 * workflow that already exists, and every instance of her is the same Rails NPC.
 *
 * <h2>What is being driven</h2>
 * The real block, the real handler and the real block entity, in a real level. A configuration is
 * applied through {@link NetworkHandler#applyQuestGiverSpawnConfig} exactly as the packet listener
 * applies it -- so what is tested is a crafted packet, screen or no screen -- and the spawner is
 * then ticked through its own {@code serverTick()} until it does what it would do in the world:
 * forty ticks of world-loading grace, then a spawn.
 *
 * <p>Everything these tests spawn is discarded before the test ends, and the spawner block is
 * cleared, so nothing is still ticking in the arena when the batch moves on.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class RowanQuestGiverSpawnGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BATCH = "rowan_quest_giver_m6";

    /** The spawner's grace period is 40 ticks; the 41st is the one that spawns. */
    private static final int TICKS_TO_FIRST_SPAWN = 41;
    /** The spawner then sleeps for 200 ticks, so this many more reaches its next decision. */
    private static final int TICKS_TO_NEXT_CHECK = 205;

    private static final String HINT = "The water well is behind the mill, north gate.";

    private RowanQuestGiverSpawnGameTests() {
    }

    /**
     * The workflow end to end: an administrator in reach saves a Rowan configuration and a farmer
     * is standing there, dressed, named, sexed, leashed and carrying her local directions.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void anAdministratorPlacesAndConfiguresRowan(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 1, 3);
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper, relative);
        ServerPlayer admin = administratorBeside(helper, relative);

        boolean applied = NetworkHandler.applyQuestGiverSpawnConfig(admin,
            new QuestGiverSpawnConfigC2SPayload(helper.absolutePos(relative), "Rowan", "Britain", "", "female", 3, HINT));

        check(applied, "an administrator in reach could not place Rowan");
        check("Rowan".equals(spawner.getNpcName()), "archetype not applied, got " + spawner.getNpcName());
        check("Britain".equals(spawner.getCityName()), "city not applied, got " + spawner.getCityName());
        check("female".equals(spawner.getGender()), "gender not applied, got " + spawner.getGender());
        check(spawner.getSpawnRadius() == 3, "radius not applied, got " + spawner.getSpawnRadius());
        check(HINT.equals(spawner.getDirections()), "hint not applied, got " + spawner.getDirections());

        tick(spawner, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity rowan = onlyGiverNear(helper, relative, "the configured spawn");

        checkRowan(rowan, "female", HINT);
        check(spawner.getSpawnRadius() == Math.round(rowan.getRestrictRadius()),
            "the wander radius was not leashed onto the NPC, got " + rowan.getRestrictRadius());
        check(helper.absolutePos(relative).equals(rowan.getRestrictCenter()),
            "the leash is not centred on the spawner, got " + rowan.getRestrictCenter());
        check("Britain".equals(rowan.getCityName()), "city not applied to the NPC, got " + rowan.getCityName());
        check("Rowan".equals(rowan.getName().getString()),
            "the nameplate must read Rowan, got " + rowan.getName().getString());

        clear(helper, relative, rowan);
        helper.succeed();
    }

    /**
     * A chunk reload rebuilds the block entity and the NPC from NBT and nothing else. The spawner
     * must adopt the NPC that comes back rather than replace it, and both must still carry the
     * whole configuration -- hint included.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void rowanSurvivesAChunkReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper, relative);
        ServerPlayer admin = administratorBeside(helper, relative);

        check(NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(absolute, "Rowan", "Minoc", "", "male", 1, HINT)),
            "the Rowan configuration was refused");
        tick(spawner, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity original = onlyGiverNear(helper, relative, "the first spawn");
        checkRowan(original, "male", HINT);

        // The chunk unloads: block entity and NPC both leave the live level, captured as the NBT a
        // real unload would have written.
        CompoundTag savedNpc = saveForReload(original);
        CompoundTag savedBlock = spawner.saveCustomOnly(level.registryAccess());
        original.discard();
        level.removeBlockEntity(absolute);

        // ... and reloads: both are reconstructed from that NBT and nothing else.
        QuestGiverSpawnBlockEntity reloaded = new QuestGiverSpawnBlockEntity(
            absolute, BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get().defaultBlockState());
        reloaded.loadCustomOnly(savedBlock, level.registryAccess());
        level.setBlockEntity(reloaded);
        check(HINT.equals(reloaded.getDirections()),
            "the spawner lost its hint across a reload, got " + reloaded.getDirections());

        Entity restored = EntityType.loadEntityRecursive(savedNpc, level, entity -> entity);
        check(restored instanceof QuestGiverEntity, "the reload did not reconstruct a quest giver");
        check(level.addFreshEntity(restored), "the level refused the reloaded NPC");
        UUID reloadedId = restored.getUUID();

        tick(reloaded, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity afterReload = onlyGiverNear(helper, relative, "after the reload");
        check(reloadedId.equals(afterReload.getUUID()),
            "the reloaded Rowan was replaced instead of adopted");
        checkRowan(afterReload, "male", HINT);
        check(Math.round(afterReload.getRestrictRadius()) == 1,
            "the wander radius did not survive the reload, got " + afterReload.getRestrictRadius());

        clear(helper, relative, afterReload);
        helper.succeed();
    }

    /**
     * Losing the NPC entirely is the other half: the spawner drops its snapshot and rebuilds one
     * from its own configuration, which is the path that has to re-derive the identity, the outfit
     * and the hint from the block rather than from anything the old NPC carried.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void rowanRespawnsFromTheBlockAfterBeingLost(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(relative);
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper, relative);
        ServerPlayer admin = administratorBeside(helper, relative);

        check(NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(absolute, "Rowan", "Minoc", "", "male", 1, HINT)),
            "the Rowan configuration was refused");
        tick(spawner, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity original = onlyGiverNear(helper, relative, "the first spawn");
        UUID originalId = original.getUUID();
        checkRowan(original, "male", HINT);

        original.discard();
        tick(spawner, TICKS_TO_NEXT_CHECK);

        QuestGiverEntity respawned = onlyGiverNear(helper, relative, "after the respawn");
        check(!respawned.getUUID().equals(originalId), "nothing was respawned; the old NPC is still here");
        checkRowan(respawned, "male", HINT);
        check(Math.round(respawned.getRestrictRadius()) == 1,
            "the respawned Rowan is not leashed, got " + respawned.getRestrictRadius());

        clear(helper, relative, respawned);
        helper.succeed();
    }

    /**
     * The point of the milestone: two Rowans are one questline. Their identity is identical and
     * only their directions differ.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void twoRowansShareOneIdentityAndKeepTheirOwnDirections(GameTestHelper helper) {
        BlockPos northRelative = new BlockPos(1, 1, 1);
        BlockPos southRelative = new BlockPos(5, 1, 5);
        String northHint = "The well is by the north gate.";
        String southHint = "Follow the river east past the mill.";

        QuestGiverSpawnBlockEntity north = placeSpawner(helper, northRelative);
        QuestGiverSpawnBlockEntity south = placeSpawner(helper, southRelative);
        ServerPlayer admin = administratorBeside(helper, new BlockPos(3, 1, 3));

        check(NetworkHandler.applyQuestGiverSpawnConfig(admin, new QuestGiverSpawnConfigC2SPayload(
                helper.absolutePos(northRelative), "Rowan", "Britain", "", "female", 1, northHint)),
            "the first Rowan was refused");
        check(NetworkHandler.applyQuestGiverSpawnConfig(admin, new QuestGiverSpawnConfigC2SPayload(
                helper.absolutePos(southRelative), "Rowan", "Minoc", "", "male", 1, southHint)),
            "the second Rowan was refused");

        tick(north, TICKS_TO_FIRST_SPAWN);
        tick(south, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity first = onlyGiverNear(helper, northRelative, 2.0D, "the north Rowan");
        QuestGiverEntity second = onlyGiverNear(helper, southRelative, 2.0D, "the south Rowan");

        checkRowan(first, "female", northHint);
        checkRowan(second, "male", southHint);
        check(first.resolveQuestGiverApiId().equals(second.resolveQuestGiverApiId()),
            "two Rowans must be one Rails NPC, got " + first.resolveQuestGiverApiId()
                + " and " + second.resolveQuestGiverApiId());
        check(!first.getLocalDirections().equals(second.getLocalDirections()),
            "both Rowans are giving the same directions");
        check(!first.getUUID().equals(second.getUUID()), "the two spawners produced one entity");

        clear(helper, northRelative, first);
        clear(helper, southRelative, second);
        helper.succeed();
    }

    /**
     * A hint is text, not a key. One shaped like the legacy {@code Name:api_id} encoding must not
     * reach the identity, which is the exact defect the identity field was introduced to end.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aHintCanNeverBecomeTheIdentity(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 1, 3);
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper, relative);
        ServerPlayer admin = administratorBeside(helper, relative);
        String hostileHint = "Ask Iolo:cast_into_the_fire_npc by the well";

        check(NetworkHandler.applyQuestGiverSpawnConfig(admin, new QuestGiverSpawnConfigC2SPayload(
                helper.absolutePos(relative), "Rowan", "Britain", "", "female", 3, hostileHint)),
            "the configuration was refused");
        tick(spawner, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity rowan = onlyGiverNear(helper, relative, "the hinted spawn");

        checkRowan(rowan, "female", hostileHint);
        check("Rowan".equals(rowan.getPersonalName()),
            "the hint leaked into the display name, got " + rowan.getPersonalName());
        check("Rowan".equals(rowan.getName().getString()),
            "the hint leaked into the nameplate, got " + rowan.getName().getString());

        clear(helper, relative, rowan);
        helper.succeed();
    }

    /** An archetype that existed before M6 is placed exactly as it was: no outfit, no new title. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void anExistingArchetypeIsUnchanged(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 1, 3);
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper, relative);
        ServerPlayer admin = administratorBeside(helper, relative);

        check(NetworkHandler.applyQuestGiverSpawnConfig(admin, new QuestGiverSpawnConfigC2SPayload(
                helper.absolutePos(relative), "Iolo", "Britain", "", "male", 3)),
            "the Iolo configuration was refused");
        tick(spawner, TICKS_TO_FIRST_SPAWN);
        QuestGiverEntity iolo = onlyGiverNear(helper, relative, "the Iolo spawn");

        check("Iolo".equals(iolo.getPersonalName()), "name changed, got " + iolo.getPersonalName());
        check("Iolo".equals(iolo.resolveQuestGiverApiId()), "identity changed, got " + iolo.resolveQuestGiverApiId());
        check("Wanderer".equals(iolo.getRoleTitle()), "role title changed, got " + iolo.getRoleTitle());
        check(iolo.getOutfitKey().isEmpty(), "an outfit appeared on an existing archetype: " + iolo.getOutfitKey());
        check(iolo.getLocalDirections().isEmpty(),
            "a hint appeared on a configuration that sent none: " + iolo.getLocalDirections());
        check("male".equals(iolo.getGender()), "gender changed, got " + iolo.getGender());

        clear(helper, relative, iolo);
        helper.succeed();
    }

    /**
     * M1's gate, restated for the new archetype: an ordinary survival player standing at the block
     * cannot place Rowan with a crafted packet, and nothing about the spawner moves.
     */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void anOrdinaryPlayerCannotPlaceRowan(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 1, 3);
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper, relative);
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), "rowan-m6-ordinary");
        BlockPos absolute = helper.absolutePos(relative);
        player.setPos(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 1.5D);

        check(!player.isCreative() && !player.hasPermissions(2), "precondition: an ordinary survival player");
        check(player.canInteractWithBlock(absolute, 1.0D), "precondition: within reach");

        boolean applied = NetworkHandler.applyQuestGiverSpawnConfig(player,
            new QuestGiverSpawnConfigC2SPayload(absolute, "Rowan", "Britain", "", "female", 3, HINT));

        check(!applied, "an ordinary player placed Rowan");
        check(spawner.getNpcName() == null || spawner.getNpcName().isEmpty(),
            "the spawner's archetype changed: " + spawner.getNpcName());
        check(spawner.getCityName() == null || spawner.getCityName().isEmpty(),
            "the spawner's city changed: " + spawner.getCityName());
        check("female".equals(spawner.getGender()), "the spawner's gender changed: " + spawner.getGender());
        check(spawner.getSpawnRadius() == 5, "the spawner's radius changed: " + spawner.getSpawnRadius());
        check(spawner.getDirections().isEmpty(), "the spawner's hint changed: " + spawner.getDirections());

        tick(spawner, TICKS_TO_FIRST_SPAWN);
        check(giversNear(helper, relative, 8.0D).isEmpty(), "a refused configuration still spawned an NPC");

        helper.setBlock(relative, Blocks.AIR);
        helper.succeed();
    }

    // --- assertions ------------------------------------------------------------------------------

    /** Everything a Rowan must be, wherever she came from: fresh spawn, reload or respawn. */
    private static void checkRowan(QuestGiverEntity rowan, String gender, String hint) {
        check("Rowan".equals(rowan.resolveQuestGiverApiId()),
            "the Rails identity must be Rowan, got " + rowan.resolveQuestGiverApiId());
        check("Rowan".equals(rowan.getQuestGiverApiId()),
            "the identity must be the stored field, not the legacy name split, got " + rowan.getQuestGiverApiId());
        check("Rowan".equals(rowan.getPersonalName()), "name, got " + rowan.getPersonalName());
        check("Farmer".equals(rowan.getRoleTitle()), "profession, got " + rowan.getRoleTitle());
        check("farmer".equals(rowan.getOutfitKey()), "outfit, got " + rowan.getOutfitKey());
        check(gender.equals(rowan.getGender()), "gender, got " + rowan.getGender());
        check(hint.equals(rowan.getLocalDirections()), "directions, got " + rowan.getLocalDirections());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static QuestGiverSpawnBlockEntity placeSpawner(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        if (!(blockEntity instanceof QuestGiverSpawnBlockEntity spawner)) {
            throw new GameTestAssertException("the spawn block did not create its block entity: " + blockEntity);
        }
        return spawner;
    }

    /**
     * {@code makeMockServerPlayerInLevel} is creative, which is the administrator M1 gates on.
     *
     * <p>Two blocks clear of the spawner, not one. {@code Util.findGround} refuses a spot occupied
     * by any entity, testing a block box inflated by 0.2 against the player's -- and at the
     * coordinates a GameTest arena actually sits at (x in the millions) the two boxes of a player
     * standing one block away are equal to within a rounding error, so whether the spot counts as
     * occupied is decided by the last bit. A spawner leashed to a radius of 1 has exactly one spot,
     * so that coin flip is the difference between an NPC and none. It is a test-fixture hazard, not
     * a defect: in a real world the administrator is not standing on the doorstep either.
     */
    private static ServerPlayer administratorBeside(GameTestHelper helper, BlockPos relative) {
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(relative);
        admin.setPos(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 2.5D);
        check(admin.isCreative(), "precondition: the mock player is creative");
        check(admin.canInteractWithBlock(absolute, 1.0D), "precondition: the administrator is within reach");
        return admin;
    }

    private static void tick(QuestGiverSpawnBlockEntity spawner, int times) {
        for (int i = 0; i < times; i++) {
            spawner.serverTick();
        }
    }

    private static List<QuestGiverEntity> giversNear(GameTestHelper helper, BlockPos relative, double range) {
        return helper.getLevel().getEntitiesOfClass(QuestGiverEntity.class,
            new AABB(helper.absolutePos(relative)).inflate(range), QuestGiverEntity::isAlive);
    }

    /**
     * The one NPC this spawner is responsible for. On failure it reports every quest giver within a
     * wide radius and where it stands relative to the block, because "found 0" on its own cannot
     * distinguish a spawn that never happened from one that happened somewhere unexpected.
     */
    private static QuestGiverEntity onlyGiverNear(GameTestHelper helper, BlockPos relative, double range,
                                                 String label) {
        List<QuestGiverEntity> found = giversNear(helper, relative, range);
        if (found.size() != 1) {
            throw new GameTestAssertException("expected exactly one quest giver at " + relative
                + " (" + label + "), found " + found.size() + " within " + range
                + "; wider census: " + census(helper, relative));
        }
        return found.get(0);
    }

    private static QuestGiverEntity onlyGiverNear(GameTestHelper helper, BlockPos relative, String label) {
        return onlyGiverNear(helper, relative, 8.0D, label);
    }

    private static String census(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        List<QuestGiverEntity> wide = giversNear(helper, relative, 24.0D);
        if (wide.isEmpty()) return "no quest giver within 24 blocks";

        StringBuilder line = new StringBuilder();
        for (QuestGiverEntity giver : wide) {
            line.append('[').append(giver.getPersonalName())
                .append(" offset=").append(String.format("%.1f,%.1f,%.1f",
                    giver.getX() - absolute.getX(), giver.getY() - absolute.getY(), giver.getZ() - absolute.getZ()))
                .append(']');
        }
        return line.toString();
    }

    private static CompoundTag saveForReload(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return tag;
    }

    /**
     * Breaking the spawn block does not remove its NPC, so both are cleared here: nothing this test
     * spawned may still be standing, or ticking, when the arena is reused.
     */
    private static void clear(GameTestHelper helper, BlockPos relative, Entity spawned) {
        if (spawned != null) spawned.discard();
        helper.setBlock(relative, Blocks.AIR);
    }

    /** Throws {@link GameTestAssertException} only; anything else would crash the GameTest server. */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

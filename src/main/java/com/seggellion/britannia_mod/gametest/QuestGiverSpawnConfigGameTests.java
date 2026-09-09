package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.QuestGiverSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rowan farming questline M1 (discovery D3): the quest-giver spawn configuration is applied only
 * for an administrator standing within reach of a real spawner, from a payload the spawner
 * understands. The handler is driven directly, exactly as the packet listener drives it, so a
 * crafted packet -- one sent without the screen ever opening -- is what is being tested.
 *
 * <p>What cannot be represented here: a Survival player WITH permission level 2. The GameTest
 * server has no operator list, so the permission gate's second clause is covered by the shape of
 * the check ({@code isCreative() || hasPermissions(2)}) rather than by a fixture.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestGiverSpawnConfigGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos SPAWNER = new BlockPos(3, 1, 3);
    private static final BlockPos NOT_A_SPAWNER = new BlockPos(1, 1, 1);

    private QuestGiverSpawnConfigGameTests() {
    }

    /** Permission level 0 in Survival: refused before anything is looked at, nothing changes. */
    @GameTest(template = TEMPLATE)
    public static void anOrdinaryPlayerCannotReconfigureASpawnerEvenWithACraftedPacket(GameTestHelper helper) {
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper);
        ServerPlayer player = ManagedResourceTestPlayers.survival(helper.getLevel(), "rowan-m1-ordinary");
        standBeside(player, helper, SPAWNER);
        check(!player.isCreative() && !player.hasPermissions(2), "precondition: an ordinary survival player");
        check(player.canInteractWithBlock(helper.absolutePos(SPAWNER), 1.0D), "precondition: within reach");

        boolean applied = NetworkHandler.applyQuestGiverSpawnConfig(player, validPayload(helper));

        check(!applied, "an ordinary player reconfigured a quest giver");
        checkUnchanged(spawner);
        helper.succeed();
    }

    /** Creative, but nowhere near the block: the reach gate the block-use packet would have applied. */
    @GameTest(template = TEMPLATE)
    public static void anAdministratorOutOfReachIsRefused(GameTestHelper helper) {
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper);
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(SPAWNER);
        admin.setPos(at.getX() + 40.5D, at.getY(), at.getZ() + 0.5D);
        check(admin.isCreative(), "precondition: the mock player is creative");
        check(!admin.canInteractWithBlock(at, 1.0D), "precondition: out of reach");

        boolean applied = NetworkHandler.applyQuestGiverSpawnConfig(admin, validPayload(helper));

        check(!applied, "a configuration was applied from out of reach");
        checkUnchanged(spawner);
        helper.succeed();
    }

    /** In reach, but the position holds a stone block: nothing to configure, nothing touched. */
    @GameTest(template = TEMPLATE)
    public static void aPositionWithoutASpawnerIsRefused(GameTestHelper helper) {
        helper.setBlock(NOT_A_SPAWNER, Blocks.STONE);
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        standBeside(admin, helper, NOT_A_SPAWNER);
        BlockPos at = helper.absolutePos(NOT_A_SPAWNER);

        boolean applied = NetworkHandler.applyQuestGiverSpawnConfig(admin,
            new QuestGiverSpawnConfigC2SPayload(at, "Iolo", "Britain", "", "male", 7));

        check(!applied, "a configuration was applied to a block that is not a spawner");
        check(helper.getLevel().getBlockState(at).is(Blocks.STONE), "the stone block must be untouched");
        check(helper.getLevel().getBlockEntity(at) == null, "no block entity may have appeared");
        helper.succeed();
    }

    /** In reach of a real spawner, but the payload is not one the spawner understands. */
    @GameTest(template = TEMPLATE)
    public static void aMalformedPayloadIsRefused(GameTestHelper helper) {
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper);
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        standBeside(admin, helper, SPAWNER);
        BlockPos at = helper.absolutePos(SPAWNER);

        check(!NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(at, "Blackthorn", "Britain", "", "male", 7)),
            "an archetype the spawner does not support was applied");
        check(!NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(at, "Iolo", "Britain", "", "male", 7, "north\nthen east")),
            "a directions hint carrying control characters was applied");
        check(!NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(at, "Iolo", "Britain", "", "male", 7, "x".repeat(400))),
            "a directions hint past the server's bound was applied");
        check(!NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(at, "Iolo", "Britain", "", "male", -1)),
            "a negative wander radius was applied");
        check(!NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(at, "Iolo", "", "", "male", 7)),
            "a blank city was applied");
        check(!NetworkHandler.applyQuestGiverSpawnConfig(admin,
                new QuestGiverSpawnConfigC2SPayload(at, "Generic Combat", "Britain", "", "male", 7)),
            "a combat NPC without a Rails api id was applied");
        checkUnchanged(spawner);
        helper.succeed();
    }

    /** The screen's own save, from an administrator standing at the block, still works. */
    @GameTest(template = TEMPLATE)
    public static void anAdministratorInReachConfiguresTheSpawner(GameTestHelper helper) {
        QuestGiverSpawnBlockEntity spawner = placeSpawner(helper);
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        standBeside(admin, helper, SPAWNER);

        boolean applied = NetworkHandler.applyQuestGiverSpawnConfig(admin, validPayload(helper));

        check(applied, "an administrator in reach could not configure the spawner");
        check("Iolo".equals(spawner.getNpcName()), "archetype not applied, got " + spawner.getNpcName());
        check("Britain".equals(spawner.getCityName()), "city not applied, got " + spawner.getCityName());
        check("male".equals(spawner.getGender()), "gender not applied, got " + spawner.getGender());
        check(spawner.getSpawnRadius() == 7, "radius not applied, got " + spawner.getSpawnRadius());
        check("".equals(spawner.getCustomApiId()), "api id not applied, got " + spawner.getCustomApiId());

        // Done before the spawner's grace period ends, so no quest giver is left standing in the arena.
        helper.setBlock(SPAWNER, Blocks.AIR);
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static QuestGiverSpawnBlockEntity placeSpawner(GameTestHelper helper) {
        helper.setBlock(SPAWNER, BlockRegistry.QUEST_GIVER_SPAWN_BLOCK.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(SPAWNER));
        if (!(blockEntity instanceof QuestGiverSpawnBlockEntity spawner)) {
            throw new GameTestAssertException("the spawn block did not create its block entity: " + blockEntity);
        }
        checkUnchanged(spawner);
        return spawner;
    }

    private static void standBeside(ServerPlayer player, GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        player.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 1.5D);
    }

    private static QuestGiverSpawnConfigC2SPayload validPayload(GameTestHelper helper) {
        return new QuestGiverSpawnConfigC2SPayload(helper.absolutePos(SPAWNER), "Iolo", "Britain", "", "male", 7);
    }

    /** A freshly placed spawner: no archetype, no city, the default gender and radius. */
    private static void checkUnchanged(QuestGiverSpawnBlockEntity spawner) {
        check(spawner.getNpcName() == null || spawner.getNpcName().isEmpty(),
            "the spawner's archetype changed: " + spawner.getNpcName());
        check(spawner.getCityName() == null || spawner.getCityName().isEmpty(),
            "the spawner's city changed: " + spawner.getCityName());
        check("female".equals(spawner.getGender()), "the spawner's gender changed: " + spawner.getGender());
        check(spawner.getSpawnRadius() == 5, "the spawner's radius changed: " + spawner.getSpawnRadius());
        check(spawner.getDirections() != null && spawner.getDirections().isEmpty(),
            "the spawner's directions hint changed: " + spawner.getDirections());
    }

    /** Throws {@link GameTestAssertException} only; anything else would crash the GameTest server. */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

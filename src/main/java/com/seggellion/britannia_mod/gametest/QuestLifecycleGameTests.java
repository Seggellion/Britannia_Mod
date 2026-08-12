package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestActionTelemetry;
import com.seggellion.britannia_mod.quest.QuestProxyService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Milestone 8: the lifecycle rows of the regression matrix.
 *
 * <p>Each of these is a moment where something a quest depends on is replaced -- the player
 * entity on death, the quest giver on a chunk reload -- and the historical failure mode was
 * always the same shape: an identifier that survived the replacement in one place and not in
 * another. They are cheap to assert and expensive to discover in production.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestLifecycleGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final long QUEST_ID = 4242L;
    private static final String QUEST_STATE_ID = "9001";

    private QuestLifecycleGameTests() {
    }

    /**
     * The journal is keyed by player UUID, which is what survives death -- the entity does not.
     * A quest that vanished on respawn would look exactly like the historical defect.
     */
    @GameTest(template = TEMPLATE)
    public static void questDataSurvivesDeathAndRespawn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        ServerQuestTable.replaceFromBootstrap(player, List.of(journalEntry()));

        player.setHealth(0.0F);

        check(ServerQuestTable.hasActiveQuestState(playerId, QUEST_STATE_ID),
            "the quest journal must be keyed by something that survives death");
        check(ServerQuestTable.journalSize(playerId) == 1,
            "the journal must not be emptied by a death");
        helper.succeed();
    }

    /**
     * A quest giver that unloads and comes back is a NEW entity with a new runtime id. The
     * interaction path resolves by runtime id within a single interaction, so what matters is
     * that the durable identity -- the one Rails joins on -- is unchanged.
     */
    @GameTest(template = TEMPLATE)
    public static void aReloadedQuestGiverKeepsItsRailsIdentity(GameTestHelper helper) {
        QuestGiverEntity original = spawnGiver(helper);
        original.setQuestGiverApiId("relic_npc");
        original.setPersonalName("Mitexi");

        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        original.saveWithoutId(saved);
        int originalRuntimeId = original.getId();
        original.discard();

        QuestGiverEntity reloaded = EntityRegistry.QUEST_GIVER.get().create(helper.getLevel());
        if (reloaded == null) throw new GameTestAssertException("could not recreate the giver");
        reloaded.load(saved);
        helper.getLevel().addFreshEntity(reloaded);

        check(reloaded.getId() != originalRuntimeId,
            "precondition failed: the reload should have produced a new runtime id");
        check("relic_npc".equals(reloaded.resolveQuestGiverApiId()),
            "a reloaded giver must offer the same quest, got " + reloaded.resolveQuestGiverApiId());
        helper.succeed();
    }

    /** Talking to the wrong entity must be refused, and must say which stage refused it. */
    @GameTest(template = TEMPLATE)
    public static void interactingWithSomethingThatIsNotAQuestGiverIsRefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        ServerQuestTable.replaceFromBootstrap(player, List.of(journalEntry()));

        // A cow is not a quest giver; the runtime id resolves to an entity of the wrong type.
        net.minecraft.world.entity.animal.Cow cow =
            net.minecraft.world.entity.EntityType.COW.create(helper.getLevel());
        if (cow == null) throw new GameTestAssertException("could not create the decoy entity");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        cow.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(cow);
        player.setPos(cow.getX(), cow.getY(), cow.getZ());

        handleQuietly(player, new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.INTERACT, 0L, "", cow.getId(),
            cow.getUUID(), UUID.randomUUID().toString()));

        QuestActionTelemetry.Rejection rejection = QuestActionTelemetry.lastRejection(player.getUUID())
            .orElseThrow(() -> new GameTestAssertException("interacting with a cow was not refused"));
        check(rejection.stage() == QuestActionTelemetry.Stage.NPC_RESOLVE,
            "expected an npc-resolve rejection, got " + rejection.stage());
        check("quest_giver_unresolved".equals(rejection.reason()),
            "expected reason=quest_giver_unresolved, got " + rejection.reason());
        helper.succeed();
    }

    /** A quest giver too far away is refused for the same reason, not silently accepted. */
    @GameTest(template = TEMPLATE)
    public static void aDistantQuestGiverIsRefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        QuestGiverEntity giver = spawnGiver(helper);
        giver.setQuestGiverApiId("relic_npc");
        player.setPos(giver.getX() + 100.0D, giver.getY(), giver.getZ());

        handleQuietly(player, new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.INTERACT, 0L, "", giver.getId(),
            giver.getUUID(), UUID.randomUUID().toString()));

        QuestActionTelemetry.Rejection rejection = QuestActionTelemetry.lastRejection(player.getUUID())
            .orElseThrow(() -> new GameTestAssertException("a distant interaction was not refused"));
        check("quest_giver_unresolved".equals(rejection.reason()),
            "expected reason=quest_giver_unresolved, got " + rejection.reason());
        helper.succeed();
    }

    /** A stale client acting on a quest the server does not hold gets an explicit refusal. */
    @GameTest(template = TEMPLATE)
    public static void aStaleClientIsRefusedExplicitly(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        ServerQuestTable.replaceFromBootstrap(player, List.of(journalEntry()));

        handleQuietly(player, new QuestActionC2SPayload(
            1L, QuestActionC2SPayload.Action.CHOOSE, 999999L, "finish", -1,
            new UUID(0L, 0L), UUID.randomUUID().toString()));

        QuestActionTelemetry.Rejection rejection = QuestActionTelemetry.lastRejection(player.getUUID())
            .orElseThrow(() -> new GameTestAssertException("a stale quest id was not refused"));
        check(rejection.stage() == QuestActionTelemetry.Stage.JOURNAL_GATE,
            "expected a journal-gate rejection, got " + rejection.stage());
        check("quest_not_in_server_journal".equals(rejection.reason()),
            "expected reason=quest_not_in_server_journal, got " + rejection.reason());
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static ClientQuestEntry journalEntry() {
        return new ClientQuestEntry(QUEST_STATE_ID, Long.toString(QUEST_ID), "relic_npc",
            "Mitexi", "Deliver the Relic", "", "", "accepted");
    }

    private static QuestGiverEntity spawnGiver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestGiverEntity giver = EntityRegistry.QUEST_GIVER.get().create(level);
        if (giver == null) throw new GameTestAssertException("quest giver could not be created");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        giver.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(giver);
        return giver;
    }

    /** Tolerates only the known mock-connection limit; see QuestActionTelemetryGameTests. */
    private static void handleQuietly(ServerPlayer player, QuestActionC2SPayload request) {
        try {
            QuestProxyService.handle(player, request);
        } catch (RuntimeException mockConnectionLimit) {
            if (!String.valueOf(mockConnectionLimit.getMessage()).contains("may not be sent to the client")) {
                throw mockConnectionLimit;
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

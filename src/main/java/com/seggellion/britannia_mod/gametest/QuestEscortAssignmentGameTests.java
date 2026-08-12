package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.entity.ai.EscortPlayerGoal;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
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

/**
 * Milestone 1 (finding Q-16): an escort's assignment must survive a cold quest journal.
 *
 * <p>{@code ServerQuestTable} is a RAM-only mirror filled solely by the login bootstrap, which is
 * allowed to fail. The escort goals used to ask it a boolean question and, on {@code false},
 * permanently delete the entity tags that ARE the durable record of the assignment
 * ({@code escort_active}, {@code quest_escort_<player>}, {@code quest_state_id_<id>},
 * {@code quest_id_<id>}, {@code quest_key_<key>}). Because the goal ticks as soon as the chunk
 * loads, an escort standing near its owner lost its assignment within the first ticks after every
 * server restart, irrecoverably.
 *
 * <p>These tests pin the three-state contract: ACTIVE follows, INACTIVE clears (unchanged), and
 * UNKNOWN idles without touching anything. A real server is required because the goal reads live
 * entity tags and resolves the player through the running level.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestEscortAssignmentGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String QUEST_STATE_ID = "9182";
    private static final String QUEST_ID = "4471";

    private QuestEscortAssignmentGameTests() {
    }

    /**
     * The regression itself: no journal entry exists for this player (a fresh mock player never
     * bootstraps), which is exactly the state of every player between server start and their
     * bootstrap landing. The escort must idle and keep every tag.
     */
    @GameTest(template = TEMPLATE)
    public static void unloadedJournalPreservesTheAssignment(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestGiverEntity escort = spawnEscortFor(helper, player);

        check(ServerQuestTable.questStateStatus(player.getUUID(), QUEST_STATE_ID)
                == ServerQuestTable.JournalState.UNKNOWN,
            "precondition failed: this player's journal should be unloaded");

        boolean follows = evaluateGoal(escort);

        check(!follows, "an escort must not follow while the journal is unknown");
        assertAssignmentIntact(escort, "the assignment was destroyed by an unloaded journal");
        helper.succeed();
    }

    /** The journal loads and lists the quest: the escort follows, tags untouched. */
    @GameTest(template = TEMPLATE)
    public static void loadedJournalWithTheQuestFollows(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestGiverEntity escort = spawnEscortFor(helper, player);
        loadJournalWithQuest(player);

        moveAway(helper, escort, player);
        boolean follows = evaluateGoal(escort);

        check(follows, "an escort must follow while its quest is active");
        assertAssignmentIntact(escort, "an active assignment must not be cleared");
        helper.succeed();
    }

    /**
     * The journal loads and does NOT list the quest -- the quest really did end. Clearing here is
     * the pre-existing behaviour and must be preserved; this is what keeps abandoned escorts from
     * following forever.
     */
    @GameTest(template = TEMPLATE)
    public static void loadedJournalWithoutTheQuestClearsTheAssignment(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestGiverEntity escort = spawnEscortFor(helper, player);
        ServerQuestTable.replaceFromBootstrap(player, List.of());

        boolean follows = evaluateGoal(escort);

        check(!follows, "an escort must not follow a quest the journal says is over");
        check(!escort.getTags().contains("escort_active"),
            "an ended quest must still clear the assignment");
        check(escort.getTags().stream().noneMatch(tag -> tag.startsWith("quest_state_id_")),
            "an ended quest must still clear quest_state_id");
        helper.succeed();
    }

    /**
     * The recovery this milestone buys: the escort idles through the unloaded window, the journal
     * then arrives, and the escort resumes on its own -- no re-activation, no player action.
     */
    @GameTest(template = TEMPLATE)
    public static void assignmentRecoversWhenTheJournalArrives(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestGiverEntity escort = spawnEscortFor(helper, player);
        moveAway(helper, escort, player);

        check(!evaluateGoal(escort), "precondition failed: escort followed with an unloaded journal");
        assertAssignmentIntact(escort, "the assignment was destroyed before the journal arrived");

        loadJournalWithQuest(player);

        check(evaluateGoal(escort), "the escort must resume once the journal arrives");
        assertAssignmentIntact(escort, "resuming must not disturb the assignment");
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static QuestGiverEntity spawnEscortFor(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        QuestGiverEntity escort = EntityRegistry.QUEST_GIVER.get().create(level);
        if (escort == null) {
            throw new GameTestAssertException("quest giver entity could not be created");
        }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        escort.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        escort.setPersonalName("Guard:escort_britain_to_jhelom");
        escort.addTag("escort_active");
        escort.addTag("quest_escort_" + player.getUUID());
        escort.addTag("quest_state_id_" + QUEST_STATE_ID);
        escort.addTag("quest_id_" + QUEST_ID);
        escort.addTag("quest_key_escort_britain_to_jhelom");
        level.addFreshEntity(escort);
        return escort;
    }

    private static void loadJournalWithQuest(ServerPlayer player) {
        ServerQuestTable.replaceFromBootstrap(player, List.of(new ClientQuestEntry(
            QUEST_STATE_ID, QUEST_ID, "escort_britain_to_jhelom", "Guard",
            "Escort to Jhelom", "Walk them home.", "", "accepted")));
    }

    /**
     * Puts the player far enough away that distance never masks the journal decision: the goal
     * declines early when it is already standing next to its charge.
     */
    private static void moveAway(GameTestHelper helper, QuestGiverEntity escort, ServerPlayer player) {
        player.setPos(escort.getX() + 8.0D, escort.getY(), escort.getZ() + 8.0D);
    }

    /** Runs one goal evaluation, which is where the destructive path used to fire. */
    private static boolean evaluateGoal(QuestGiverEntity escort) {
        return new EscortPlayerGoal(escort, 1.2D, 3.0F, 20.0F).canUse();
    }

    private static void assertAssignmentIntact(QuestGiverEntity escort, String message) {
        check(escort.getTags().contains("escort_active"), message + " (escort_active)");
        check(escort.getTags().contains("quest_state_id_" + QUEST_STATE_ID),
            message + " (quest_state_id)");
        check(escort.getTags().contains("quest_id_" + QUEST_ID), message + " (quest_id)");
        check(escort.getTags().stream().anyMatch(tag -> tag.startsWith("quest_escort_")),
            message + " (quest_escort)");
        check(escort.getTags().contains("quest_key_escort_britain_to_jhelom"),
            message + " (quest_key)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

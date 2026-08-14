package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestActionTelemetry;
import com.seggellion.britannia_mod.quest.QuestProxyService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Milestone 2 (finding S-1): a refused quest action must say why.
 *
 * <p>{@code QuestProxyService} used to answer 422 {@code invalid_quest_action} and log nothing at
 * all. Several unrelated causes -- a malformed payload, an unresolvable quest giver, a quest
 * missing from the server journal, and a journal that was never fetched because the login
 * bootstrap failed -- produced one identical silent response. The last of those is finding Q-03,
 * the one that disables quests for a whole session, and it was indistinguishable from a client
 * sending nonsense.
 *
 * <p>These tests assert the rejection reasons through {@link QuestActionTelemetry}'s retained
 * last-rejection rather than by scraping log files, so they pin the machine-readable contract that
 * the log lines are formatted from.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestActionTelemetryGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final long QUEST_ID = 77L;

    private QuestActionTelemetryGameTests() {
    }

    /** A loaded journal that simply does not hold this quest is a different fact, and says so. */
    @GameTest(template = TEMPLATE)
    public static void loadedJournalWithoutTheQuestRejectsWithItsOwnReason(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        ServerQuestTable.replaceFromBootstrap(player, List.of(new ClientQuestEntry(
            "4242", "999", "some_other_quest", "Giver", "Another Quest", "", "", "accepted")));

        handleExpectingRejection(player, choose(QUEST_ID, UUID.randomUUID().toString()));

        QuestActionTelemetry.Rejection rejection = requireRejection(player);
        check(rejection.stage() == QuestActionTelemetry.Stage.JOURNAL_GATE,
            "expected a journal-gate rejection, got " + rejection.stage());
        check("quest_not_in_server_journal".equals(rejection.reason()),
            "expected reason=quest_not_in_server_journal, got " + rejection.reason());
        check(rejection.journalSize() == 1,
            "a loaded journal must report its real size, got " + rejection.journalSize());
        helper.succeed();
    }

    /** A malformed correlation id is a shape failure, refused before anything else is consulted. */
    @GameTest(template = TEMPLATE)
    public static void malformedCorrelationIdIsAShapeRejection(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        ServerQuestTable.replaceFromBootstrap(player, List.of());

        handleExpectingRejection(player, choose(QUEST_ID, "not-a-uuid"));

        QuestActionTelemetry.Rejection rejection = requireRejection(player);
        check(rejection.stage() == QuestActionTelemetry.Stage.SHAPE,
            "expected a shape rejection, got " + rejection.stage());
        check("malformed_request_uuid".equals(rejection.reason()),
            "expected reason=malformed_request_uuid, got " + rejection.reason());
        helper.succeed();
    }

    /**
     * The correlation id the client sent is carried on the rejection, so traces join up.
     *
     * <p>The journal is loaded first: since Milestone 5 an UNLOADED journal triggers a fetch
     * rather than an immediate refusal, and that path is covered by
     * {@code QuestJournalRefreshGameTests}.
     */
    @GameTest(template = TEMPLATE)
    public static void rejectionCarriesTheClientCorrelationId(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestActionTelemetry.clear(player.getUUID());
        ServerQuestTable.replaceFromBootstrap(player, List.of());
        String requestUuid = UUID.randomUUID().toString();

        handleExpectingRejection(player, choose(QUEST_ID, requestUuid));

        QuestActionTelemetry.Rejection rejection = requireRejection(player);
        check(requestUuid.equals(rejection.requestUuid()),
            "the rejection must carry the client's correlation id, got " + rejection.requestUuid());
        check(rejection.questId() == QUEST_ID,
            "the rejection must carry the quest id, got " + rejection.questId());
        check("CHOOSE".equals(rejection.action()),
            "the rejection must carry the action, got " + rejection.action());
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static QuestActionC2SPayload choose(long questId, String requestUuid) {
        return new QuestActionC2SPayload(1L, QuestActionC2SPayload.Action.CHOOSE, questId,
            "accept", -1, new UUID(0L, 0L), requestUuid);
    }

    /**
     * Runs the real {@code handle} path, tolerating exactly one known scaffolding limit: a
     * {@code makeMockServerPlayerInLevel} connection refuses play-phase custom payloads, so the
     * 422 reply itself cannot be delivered in a gametest. That throw happens strictly AFTER the
     * rejection is recorded, which is what these tests assert -- and tolerating only this one
     * message means a real failure in the same call still fails the test.
     */
    private static void handleExpectingRejection(ServerPlayer player, QuestActionC2SPayload request) {
        try {
            QuestProxyService.handle(player, request);
        } catch (RuntimeException mockConnectionLimit) {
            String message = String.valueOf(mockConnectionLimit.getMessage());
            if (!message.contains("may not be sent to the client")) {
                throw mockConnectionLimit;
            }
        }
    }

    private static QuestActionTelemetry.Rejection requireRejection(ServerPlayer player) {
        return QuestActionTelemetry.lastRejection(player.getUUID())
            .orElseThrow(() -> new GameTestAssertException(
                "the quest action was refused without recording a reason"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}

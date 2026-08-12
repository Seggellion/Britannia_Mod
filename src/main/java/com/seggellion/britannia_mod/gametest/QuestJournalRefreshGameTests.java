package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.network.payload.QuestActionC2SPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestActionTelemetry;
import com.seggellion.britannia_mod.quest.QuestJournalRefresh;
import com.seggellion.britannia_mod.quest.QuestProxyService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Milestone 5 (findings Q-03, Q-09): a quest action must survive a failed login bootstrap.
 *
 * <p>{@code ServerQuestTable} is RAM only and filled solely by that bootstrap, which
 * {@code WorldBootstrapHandler} may abandon on a timeout, a queue rejection, a stale generation,
 * or unavailable authentication. Because the same table authorizes every quest write, a player
 * whose bootstrap failed had every quest action refused for the rest of the session -- and until
 * Milestone 2, refused silently.
 *
 * <p>These tests drive the real {@code QuestProxyService.handle} with a stand-in transport, so
 * they pin the decision ("fetch, then decide properly") rather than the HTTP call.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestJournalRefreshGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final long QUEST_ID = 4242L;
    private static final String QUEST_STATE_ID = "9001";

    private QuestJournalRefreshGameTests() {
    }

    /** The Q-03 case itself: an unloaded journal fetches, finds the quest, and proceeds. */
    @GameTest(template = TEMPLATE)
    public static void anUnloadedJournalIsFetchedRatherThanRefused(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        AtomicInteger fetches = new AtomicInteger();
        QuestJournalRefresh.installFetcher((server, target) -> {
            fetches.incrementAndGet();
            return Optional.of(List.of(journalEntry()));
        });

        try {
            check(!ServerQuestTable.journalLoaded(player.getUUID()),
                "precondition failed: the journal should be unloaded");

            handleQuietly(player, choose());

            helper.succeedWhen(() -> {
                check(fetches.get() == 1, "expected exactly one journal fetch, saw " + fetches.get());
                check(ServerQuestTable.journalLoaded(player.getUUID()),
                    "the journal must be loaded after the refresh");
                check(QuestActionTelemetry.lastRejection(player.getUUID()).isEmpty(),
                    "the action must not be refused once the journal proves it is the player's: "
                        + QuestActionTelemetry.lastRejection(player.getUUID()));
                QuestJournalRefresh.resetFetcher();
            });
        } catch (RuntimeException error) {
            QuestJournalRefresh.resetFetcher();
            throw error;
        }
    }

    /** After the refresh, a quest that genuinely is not the player's is still refused -- clearly. */
    @GameTest(template = TEMPLATE)
    public static void aQuestMissingAfterTheRefreshIsRefusedWithItsOwnReason(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        QuestJournalRefresh.installFetcher((server, target) -> Optional.of(List.of()));

        try {
            handleQuietly(player, choose());

            helper.succeedWhen(() -> {
                QuestActionTelemetry.Rejection rejection = requireRejection(player);
                check("quest_not_in_server_journal_after_refresh".equals(rejection.reason()),
                    "expected reason=quest_not_in_server_journal_after_refresh, got " + rejection.reason());
                check(ServerQuestTable.journalLoaded(player.getUUID()),
                    "an empty journal is still a loaded journal");
                QuestJournalRefresh.resetFetcher();
            });
        } catch (RuntimeException error) {
            QuestJournalRefresh.resetFetcher();
            throw error;
        }
    }

    /** A refresh that fails still answers, and says the journal was unavailable. */
    @GameTest(template = TEMPLATE)
    public static void aFailedRefreshStillAnswersTheAction(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        QuestJournalRefresh.installFetcher((server, target) -> Optional.empty());

        try {
            handleQuietly(player, choose());

            helper.succeedWhen(() -> {
                QuestActionTelemetry.Rejection rejection = requireRejection(player);
                check("server_journal_unavailable".equals(rejection.reason()),
                    "expected reason=server_journal_unavailable, got " + rejection.reason());
                check(!ServerQuestTable.journalLoaded(player.getUUID()),
                    "a failed fetch must not pretend the journal loaded");
                QuestJournalRefresh.resetFetcher();
                QuestJournalRefresh.clearCooldown(player.getUUID());
            });
        } catch (RuntimeException error) {
            QuestJournalRefresh.resetFetcher();
            QuestJournalRefresh.clearCooldown(player.getUUID());
            throw error;
        }
    }

    /**
     * A failed refresh must not become a hot loop against a service that is already struggling:
     * the next action within the cooldown is refused without a second fetch.
     */
    @GameTest(template = TEMPLATE)
    public static void aFailedRefreshCoolsDownRatherThanHammering(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        AtomicInteger fetches = new AtomicInteger();
        QuestJournalRefresh.installFetcher((server, target) -> {
            fetches.incrementAndGet();
            return Optional.empty();
        });

        try {
            // The first action starts a fetch; the second must land AFTER it failed, which is
            // why it is issued from the polled block rather than inline.
            handleQuietly(player, choose());

            helper.succeedWhen(() -> {
                check(QuestActionTelemetry.lastRejection(player.getUUID()).isPresent(),
                    "waiting for the first refresh to fail");
                QuestActionTelemetry.clear(player.getUUID());
                handleQuietly(player, choose());

                check(fetches.get() == 1,
                    "a failed refresh must cool down, saw " + fetches.get() + " fetches");
                QuestActionTelemetry.Rejection rejection = requireRejection(player);
                check("journal_refresh_cooling_down".equals(rejection.reason()),
                    "expected reason=journal_refresh_cooling_down, got " + rejection.reason());
                QuestJournalRefresh.resetFetcher();
                QuestJournalRefresh.clearCooldown(player.getUUID());
            });
        } catch (RuntimeException error) {
            QuestJournalRefresh.resetFetcher();
            QuestJournalRefresh.clearCooldown(player.getUUID());
            throw error;
        }
    }

    /** A loaded journal is trusted: no fetch, and the pre-existing refusal is unchanged. */
    @GameTest(template = TEMPLATE)
    public static void aLoadedJournalIsNotRefetched(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        ServerQuestTable.replaceFromBootstrap(player, List.of());
        AtomicInteger fetches = new AtomicInteger();
        QuestJournalRefresh.installFetcher((server, target) -> {
            fetches.incrementAndGet();
            return Optional.of(List.of(journalEntry()));
        });

        try {
            handleQuietly(player, choose());

            check(fetches.get() == 0, "a loaded journal must not be refetched");
            QuestActionTelemetry.Rejection rejection = requireRejection(player);
            check("quest_not_in_server_journal".equals(rejection.reason()),
                "expected reason=quest_not_in_server_journal, got " + rejection.reason());
        } finally {
            QuestJournalRefresh.resetFetcher();
        }
        helper.succeed();
    }

    /** Q-09: logout drops the journal, so the next session cannot inherit a stale view. */
    @GameTest(template = TEMPLATE)
    public static void loggingOutForgetsTheJournal(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        ServerQuestTable.replaceFromBootstrap(player, List.of(journalEntry()));
        check(ServerQuestTable.journalLoaded(player.getUUID()), "precondition failed");

        ServerQuestTable.forget(player.getUUID());

        check(!ServerQuestTable.journalLoaded(player.getUUID()),
            "the journal must not survive logout");
        check(ServerQuestTable.journalSize(player.getUUID()) == -1,
            "a forgotten journal reports unloaded, not empty");
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static ServerPlayer prepare(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerQuestTable.forget(player.getUUID());
        QuestJournalRefresh.forget(player.getUUID());
        QuestActionTelemetry.clear(player.getUUID());
        return player;
    }

    private static ClientQuestEntry journalEntry() {
        return new ClientQuestEntry(QUEST_STATE_ID, Long.toString(QUEST_ID), "relic_quest",
            "Giver", "Deliver the Relic", "Deliver it.", "", "accepted");
    }

    private static QuestActionC2SPayload choose() {
        return new QuestActionC2SPayload(1L, QuestActionC2SPayload.Action.CHOOSE, QUEST_ID,
            "finish", -1, new UUID(0L, 0L), UUID.randomUUID().toString());
    }

    /**
     * Runs the real handler, tolerating only the known scaffolding limit: a mock player's
     * connection refuses play-phase custom payloads, so the reply itself cannot be delivered in a
     * gametest. Every decision under test happens before that.
     */
    private static void handleQuietly(ServerPlayer player, QuestActionC2SPayload request) {
        try {
            QuestProxyService.handle(player, request);
        } catch (RuntimeException mockConnectionLimit) {
            if (!String.valueOf(mockConnectionLimit.getMessage()).contains("may not be sent to the client")) {
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

package com.seggellion.britannia_mod.quest.action;

import com.seggellion.britannia_mod.server.auth.ServerCredentialSource;
import com.seggellion.britannia_mod.server.auth.ServerCredentials;
import com.seggellion.britannia_mod.server.http.CancellableHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in proof that a real Rails serves {@code POST /api/v2/quest_action_events} on the tier
 * protocol section 2.3 specifies, and answers a contract result. Skipped -- not failed -- unless
 * all four variables are set, exactly as {@code QuestRewardDeliveryLiveRailsTest} does it:
 *
 * <pre>
 *   ROWAN_LIVE_RAILS_URL     e.g. http://127.0.0.1:3000
 *   ROWAN_LIVE_SHARD_NAME    the shard the credentials belong to
 *   ROWAN_LIVE_SHARD_SECRET  that shard's secret
 *   ROWAN_LIVE_SERVER_KEY    an active minecraft_servers.public_id on that shard
 * </pre>
 *
 * <p>Nothing here changes any quest. The event it posts names a player uuid that has just been
 * generated, so no quest state of any real player subscribes to it and the only correct answers
 * are {@code irrelevant} (the player resolved to nothing subscribed) or {@code 404
 * player_not_found}. The secret is never logged, asserted on, or put in a message -- it only ever
 * reaches the credential file this test writes into JUnit's own temporary directory.
 */
class QuestActionEventLiveRailsTest {
    private static final String URL_ENV = "ROWAN_LIVE_RAILS_URL";
    private static final String SHARD_NAME_ENV = "ROWAN_LIVE_SHARD_NAME";
    private static final String SHARD_SECRET_ENV = "ROWAN_LIVE_SHARD_SECRET";
    private static final String SERVER_KEY_ENV = "ROWAN_LIVE_SERVER_KEY";
    /** Only the subscribed-player test needs this; without it that test skips. */
    private static final String PLAYER_UUID_ENV = "ROWAN_LIVE_PLAYER_UUID";
    /** Only the end-to-end subscription test needs these two; without them it skips. */
    private static final String PLOT_KEY_ENV = "ROWAN_LIVE_PLOT_KEY";
    private static final String CROP_CYCLE_ENV = "ROWAN_LIVE_CROP_CYCLE_UUID";

    @TempDir
    Path gameDirectory;

    @Test
    void anEventForAPlayerNobodyKnowsIsAnsweredOnTheV2TierWithoutChangingAnything() throws Exception {
        ServerCredentials credentials = liveCredentials();
        UUID playerUuid = UUID.randomUUID();

        QuestActionEventClient.SendResult result = post(credentials, event(playerUuid, null));

        if (result instanceof QuestActionEventClient.Failure failure) {
            assertEquals(QuestActionEventProtocol.ERROR_PLAYER_NOT_FOUND, failure.safeCode(),
                "the only acceptable failure for an unknown player is 404 player_not_found; got "
                    + describe(result));
            return;
        }
        assertInstanceOf(QuestActionEventClient.Answered.class, result,
            () -> "POST /api/v2/quest_action_events did not answer the contract: " + describe(result));
        QuestActionEventProtocol.Response response = ((QuestActionEventClient.Answered) result).response();
        assertEquals(QuestActionEventProtocol.Result.IRRELEVANT, response.result(),
            "no quest of a player Rails has never seen can subscribe to a harvest");
    }

    /**
     * The idempotency guarantee of section 2.3 against a real Rails: the same {@code event_uuid}
     * twice is answered {@code duplicate} the second time, whatever the first answer was.
     *
     * <p>Needs a real player so the event reaches the evaluation path rather than stopping at
     * resolution; point it at a disposable database, never at owner or production data.
     */
    @Test
    void theSameEventUuidTwiceIsAnsweredDuplicate() throws Exception {
        ServerCredentials credentials = liveCredentials();
        String playerText = env(PLAYER_UUID_ENV);
        assumeTrue(!playerText.isEmpty(),
            "set " + PLAYER_UUID_ENV + " (a player in a disposable database) to run the idempotency check");
        UUID playerUuid = UUID.fromString(playerText);
        QuestActionEvent event = event(playerUuid, null);

        QuestActionEventClient.SendResult first = post(credentials, event);
        assumeTrue(first instanceof QuestActionEventClient.Answered,
            "the first attempt was not answered on the contract: " + describe(first));
        QuestActionEventProtocol.Result firstResult =
            ((QuestActionEventClient.Answered) first).response().result();

        QuestActionEventClient.SendResult second = post(credentials, event);
        assertInstanceOf(QuestActionEventClient.Answered.class, second,
            () -> "the retry was not answered on the contract: " + describe(second));
        QuestActionEventProtocol.Response replayed = ((QuestActionEventClient.Answered) second).response();
        assertEquals(QuestActionEventProtocol.Result.DUPLICATE, replayed.result(),
            "a retry after a lost response must never be applied a second time");
        assertEquals(firstResult.wireName(), replayed.originalResult(),
            "the duplicate must name the result it is replaying");
        assertTrue(replayed.carriesState(), "a duplicate replays the stored response");
    }

    /**
     * The objective contract end to end: a harvest that a real quest is subscribed to advances it,
     * and the same harvest by anyone but the planter does not.
     *
     * <p>This one MUTATES the quest it is given, so it needs the subscription's own values and
     * skips without them:
     *
     * <pre>
     *   ROWAN_LIVE_PLAYER_UUID       the player whose quest is waiting on the harvest
     *   ROWAN_LIVE_PLOT_KEY          the plot the quest bound itself to
     *   ROWAN_LIVE_CROP_CYCLE_UUID   the crop cycle it bound itself to
     * </pre>
     *
     * Point them at a disposable database seeded for the run, never at owner or production data.
     */
    @Test
    void aSubscribedHarvestAdvancesTheQuestAndOnlyForThePlanter() throws Exception {
        ServerCredentials credentials = liveCredentials();
        String playerText = env(PLAYER_UUID_ENV);
        String plotKey = env(PLOT_KEY_ENV);
        String cycleText = env(CROP_CYCLE_ENV);
        assumeTrue(!playerText.isEmpty() && !plotKey.isEmpty() && !cycleText.isEmpty(),
            "set " + PLAYER_UUID_ENV + ", " + PLOT_KEY_ENV + " and " + CROP_CYCLE_ENV
                + " (a seeded subscription in a disposable database) to run the end-to-end check");
        UUID playerUuid = UUID.fromString(playerText);
        UUID cycleUuid = UUID.fromString(cycleText);

        // Somebody else's hands on the same crop: the planter check must refuse it, and nothing may
        // advance. This runs FIRST so a wrongly-accepted event would be visible as a duplicate below.
        QuestActionEvent byOtherPlayer = harvest(playerUuid, plotKey, cycleUuid, UUID.randomUUID());
        QuestActionEventClient.SendResult refused = post(credentials, byOtherPlayer);
        QuestActionEventProtocol.Response refusedResponse = answered(refused, "the intruder's harvest");
        assertEquals(QuestActionEventProtocol.Result.REJECTED, refusedResponse.result(),
            "a harvest of a crop this player did not plant must be rejected");
        assertEquals("not_planter", refusedResponse.reason(),
            "the refusal must name the planter check, not a generic mismatch");

        // The planter's own harvest of the bound crop and cycle: the objective is satisfied.
        QuestActionEvent byPlanter = harvest(playerUuid, plotKey, cycleUuid, playerUuid);
        QuestActionEventProtocol.Response applied =
            answered(post(credentials, byPlanter), "the planter's harvest");
        assertEquals(QuestActionEventProtocol.Result.APPLIED, applied.result(),
            "the planter's harvest of the bound cycle must be applied");
        assertTrue(applied.carriesState(), "an applied answer carries the state the client must show");

        // The outbox's retry after a lost response must not harvest the quest twice.
        QuestActionEventProtocol.Response replayed =
            answered(post(credentials, byPlanter), "the retried harvest");
        assertEquals(QuestActionEventProtocol.Result.DUPLICATE, replayed.result(),
            "the same event uuid again is a duplicate, never a second advance");
        assertEquals(QuestActionEventProtocol.Result.APPLIED.wireName(), replayed.originalResult(),
            "the duplicate must name what it originally answered");
    }

    private static QuestActionEvent harvest(UUID playerUuid, String plotKey, UUID cycleUuid, UUID planterUuid) {
        return new QuestActionEvent(UUID.randomUUID(), playerUuid, QuestAction.CROP_HARVEST,
            QuestActionEventProtocol.formatOccurredAt(System.currentTimeMillis()),
            "minecraft:overworld", 1203, 64, -488,
            QuestActionEvents.cropHarvestSubject(plotKey, "carrot", cycleUuid, planterUuid, true, 3),
            null);
    }

    private static QuestActionEventProtocol.Response answered(QuestActionEventClient.SendResult result, String what) {
        assertTrue(result instanceof QuestActionEventClient.Answered,
            () -> what + " was not answered on the contract: " + describe(result));
        return ((QuestActionEventClient.Answered) result).response();
    }

    private static QuestActionEvent event(UUID playerUuid, QuestActionEvent.Target target) {
        return new QuestActionEvent(UUID.randomUUID(), playerUuid, QuestAction.CROP_HARVEST,
            QuestActionEventProtocol.formatOccurredAt(System.currentTimeMillis()),
            "minecraft:overworld", 1203, 64, -488,
            QuestActionEvents.cropHarvestSubject("minecraft:overworld:1203:64:-488", "carrot",
                UUID.randomUUID(), playerUuid, true, 3),
            target);
    }

    private static QuestActionEventClient.SendResult post(ServerCredentials credentials, QuestActionEvent event) {
        return client(credentials)
            .send(null, QuestActionEventProtocol.encode(event, UUID.randomUUID()))
            .join();
    }

    /**
     * The client with its transport run inline on this thread. Neither the credentials provider nor
     * the submitter reads the {@code MinecraftServer}, so the calls above pass {@code null} rather
     * than standing a server up for one HTTP request.
     */
    private static QuestActionEventClient client(ServerCredentials credentials) {
        return new QuestActionEventClient(
            server -> Optional.of(credentials),
            (server, task) -> CompletableFuture.completedFuture(task.get()),
            CancellableHttpRequest::new);
    }

    private ServerCredentials liveCredentials() throws Exception {
        String baseUrl = env(URL_ENV);
        String shardName = env(SHARD_NAME_ENV);
        String shardSecret = env(SHARD_SECRET_ENV);
        String serverKey = env(SERVER_KEY_ENV);
        assumeTrue(!baseUrl.isEmpty() && !shardName.isEmpty() && !shardSecret.isEmpty() && !serverKey.isEmpty(),
            "live Rails test: set " + URL_ENV + ", " + SHARD_NAME_ENV + ", " + SHARD_SECRET_ENV
                + " and " + SERVER_KEY_ENV + " to run it");

        Path configDirectory = Files.createDirectories(gameDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve("britannia_mod-server.properties"),
            "shard_name=" + shardName + "\n"
                + "shard_secret=" + shardSecret + "\n"
                + "api_base_url=" + baseUrl + "\n"
                + "minecraft_server_key=" + serverKey + "\n",
            StandardCharsets.UTF_8);

        Optional<ServerCredentials> loaded = ServerCredentialSource.load(gameDirectory);
        assumeTrue(loaded.isPresent(), "the live credential file was not loaded");
        ServerCredentials credentials = loaded.get();
        assumeTrue(shardName.equals(credentials.shardName()),
            "ULTIMACRAFT_* environment credentials are shadowing the live-test configuration");
        assumeTrue(credentials.minecraftServerKey().isPresent(),
            SERVER_KEY_ENV + " must be a UUID; the v2 tier requires Minecraft-Server-Key");
        return credentials;
    }

    /** The result's shape and, for a transport failure, only its already-sanitised code. */
    private static String describe(Object result) {
        if (result instanceof QuestActionEventClient.Failure failure) {
            return "Failure(" + failure.safeCode() + ")";
        }
        return result == null ? "null" : result.getClass().getSimpleName();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}

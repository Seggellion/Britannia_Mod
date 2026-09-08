package com.seggellion.britannia_mod.quest.delivery;

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
 * Opt-in proof that a real Rails actually serves the two v2 reward-delivery endpoints on the tier
 * protocol section 1.6/1.7 specifies. Skipped -- not failed -- unless all four variables are set:
 *
 * <pre>
 *   ROWAN_LIVE_RAILS_URL     e.g. http://127.0.0.1:3000
 *   ROWAN_LIVE_SHARD_NAME    the shard the credentials belong to
 *   ROWAN_LIVE_SHARD_SECRET  that shard's secret
 *   ROWAN_LIVE_SERVER_KEY    an active minecraft_servers.public_id on that shard
 * </pre>
 *
 * <p>Nothing here mutates Rails: a listing is not a mutation, and the acknowledgement is posted
 * for a random delivery uuid that cannot exist, whose only correct answer is
 * {@code 404 delivery_not_found}. The secret is never logged, asserted on, or put in a message --
 * it only ever reaches the credential file this test writes into JUnit's own temporary directory.
 *
 * <p>Credentials are built through the production loader rather than a hand-made object so the
 * test exercises the same signing path the server uses. When {@code ULTIMACRAFT_SHARD_NAME} and
 * {@code ULTIMACRAFT_SHARD_SECRET} are set in the environment that loader prefers them over any
 * file, which would silently point this test at whatever those name -- so it verifies it got the
 * shard it asked for and skips if not.
 */
class QuestRewardDeliveryLiveRailsTest {
    private static final String URL_ENV = "ROWAN_LIVE_RAILS_URL";
    private static final String SHARD_NAME_ENV = "ROWAN_LIVE_SHARD_NAME";
    private static final String SHARD_SECRET_ENV = "ROWAN_LIVE_SHARD_SECRET";
    private static final String SERVER_KEY_ENV = "ROWAN_LIVE_SERVER_KEY";
    /** Only the mutating flow test needs these two; without them it skips. */
    private static final String DELIVERY_UUID_ENV = "ROWAN_LIVE_DELIVERY_UUID";
    private static final String PLAYER_UUID_ENV = "ROWAN_LIVE_PLAYER_UUID";

    @TempDir
    Path gameDirectory;

    @Test
    void thePendingListingIsServedOnTheV2Tier() throws Exception {
        ServerCredentials credentials = liveCredentials();
        UUID playerUuid = UUID.randomUUID();

        QuestRewardDeliveryClient.PendingResult result =
            client(credentials).fetchPending(null, playerUuid).join();

        assertInstanceOf(QuestRewardDeliveryClient.PendingFetched.class, result,
            () -> "GET /api/v2/quest_reward_deliveries/pending did not answer the contract: " + describe(result));
        QuestRewardDeliveryParser.PendingListing listing =
            ((QuestRewardDeliveryClient.PendingFetched) result).listing();
        assertEquals(playerUuid, listing.playerUuid(), "the listing must echo the player it was asked about");
        assertTrue(listing.deliveries().isEmpty(), "a player Rails has never seen owns no pending deliveries");
    }

    @Test
    void anUnknownDeliveryIsRefusedAsDeliveryNotFound() throws Exception {
        ServerCredentials credentials = liveCredentials();
        QuestRewardDeliveryProtocol.AcknowledgementRequest request =
            new QuestRewardDeliveryProtocol.AcknowledgementRequest(
                UUID.randomUUID(), UUID.randomUUID(), QuestRewardDeliveryProtocol.Outcome.APPLIED,
                QuestRewardDeliveryProtocol.formatRecordedAt(System.currentTimeMillis()), UUID.randomUUID());

        QuestRewardDeliveryClient.AcknowledgeResult result =
            client(credentials).acknowledge(null, request).join();

        assertInstanceOf(QuestRewardDeliveryClient.TerminalRejection.class, result,
            () -> "POST /api/v2/quest_reward_deliveries/:delivery_uuid/result did not answer the contract: "
                + describe(result));
        assertEquals(QuestRewardDeliveryProtocol.ERROR_DELIVERY_NOT_FOUND,
            ((QuestRewardDeliveryClient.TerminalRejection) result).code(),
            "a delivery no shard holds is 404 delivery_not_found, never another code");
    }

    /**
     * The whole acknowledgement flow of protocol section 1.7 against a real Rails, for a real
     * pending delivery: list it, take ownership of it, replay that, and contradict it.
     *
     * <p>Unlike the two tests above this one MUTATES the delivery it is given, so it needs two
     * more variables and skips without them:
     *
     * <pre>
     *   ROWAN_LIVE_DELIVERY_UUID  a PENDING delivery on that shard
     *   ROWAN_LIVE_PLAYER_UUID    the Minecraft uuid it belongs to
     * </pre>
     *
     * Point them at a disposable database seeded for the run -- never at owner or production data.
     */
    @Test
    void theAcknowledgementFlowAgreesEndToEnd() throws Exception {
        ServerCredentials credentials = liveCredentials();
        String deliveryText = env(DELIVERY_UUID_ENV);
        String playerText = env(PLAYER_UUID_ENV);
        assumeTrue(!deliveryText.isEmpty() && !playerText.isEmpty(),
            "set " + DELIVERY_UUID_ENV + " and " + PLAYER_UUID_ENV + " (a PENDING delivery in a "
                + "disposable database) to run the mutating flow");
        UUID deliveryUuid = UUID.fromString(deliveryText);
        UUID playerUuid = UUID.fromString(playerText);
        QuestRewardDeliveryClient client = client(credentials);

        // 1. Rails owes it, and the mod's parser reads the listing Rails actually sends.
        QuestRewardDeliveryClient.PendingResult pending = client.fetchPending(null, playerUuid).join();
        assertInstanceOf(QuestRewardDeliveryClient.PendingFetched.class, pending,
            () -> "the pending listing did not answer the contract: " + describe(pending));
        QuestRewardDeliveryParser.PendingListing listing =
            ((QuestRewardDeliveryClient.PendingFetched) pending).listing();
        assertEquals(playerUuid, listing.playerUuid(), "the listing must echo the player it was asked about");
        QuestRewardDelivery owed = listing.deliveries().stream()
            .filter(delivery -> deliveryUuid.equals(delivery.deliveryUuid()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Rails did not list " + deliveryUuid + " as pending for this player; listed "
                    + listing.deliveries().size() + " deliveries"));
        assertTrue(!owed.items().isEmpty(), "a delivery Rails owes must carry the items it owes");

        // 2. The shard takes ownership: the first applied report moves it, exactly once.
        QuestRewardDeliveryProtocol.AcknowledgementResponse applied =
            acknowledged(client, deliveryUuid, playerUuid, QuestRewardDeliveryProtocol.Outcome.APPLIED);
        assertEquals(deliveryUuid, applied.deliveryUuid(), "the answer must name the delivery that was reported");
        assertEquals("acknowledged", applied.state(), "an applied report acknowledges the delivery");
        assertEquals("applied", applied.outcome(), "the outcome Rails records must be the one reported");
        assertTrue(!applied.duplicate(), "the first applied report is not a duplicate");

        // 3. The retry a lost response provokes is idempotent, not a second grant.
        QuestRewardDeliveryProtocol.AcknowledgementResponse replayed =
            acknowledged(client, deliveryUuid, playerUuid, QuestRewardDeliveryProtocol.Outcome.APPLIED);
        assertEquals("acknowledged", replayed.state(), "the replay must leave the delivery acknowledged");
        assertEquals("applied", replayed.outcome(), "the replay must not change the recorded outcome");
        assertTrue(replayed.duplicate(), "the same outcome again is a duplicate, never a second acknowledgement");

        // 4. "Queued" after "applied" contradicts a durable fact and must be refused, not accepted.
        QuestRewardDeliveryClient.AcknowledgeResult conflicting = client.acknowledge(null,
            new QuestRewardDeliveryProtocol.AcknowledgementRequest(deliveryUuid, playerUuid,
                QuestRewardDeliveryProtocol.Outcome.QUEUED,
                QuestRewardDeliveryProtocol.formatRecordedAt(System.currentTimeMillis()), UUID.randomUUID())).join();
        assertInstanceOf(QuestRewardDeliveryClient.TerminalRejection.class, conflicting,
            () -> "queued after applied must be terminal, got " + describe(conflicting));
        assertEquals(QuestRewardDeliveryProtocol.ERROR_CONFLICTING_RESULT,
            ((QuestRewardDeliveryClient.TerminalRejection) conflicting).code(),
            "the contradiction must be reported as conflicting_delivery_result");

        // 5. An acknowledged delivery is no longer owed, so login recovery stops offering it.
        QuestRewardDeliveryClient.PendingResult after = client.fetchPending(null, playerUuid).join();
        assertInstanceOf(QuestRewardDeliveryClient.PendingFetched.class, after,
            () -> "the second listing did not answer the contract: " + describe(after));
        assertTrue(((QuestRewardDeliveryClient.PendingFetched) after).listing().deliveries().stream()
                .noneMatch(delivery -> deliveryUuid.equals(delivery.deliveryUuid())),
            "an acknowledged delivery must leave the pending listing");
    }

    private static QuestRewardDeliveryProtocol.AcknowledgementResponse acknowledged(
            QuestRewardDeliveryClient client, UUID deliveryUuid, UUID playerUuid,
            QuestRewardDeliveryProtocol.Outcome outcome) {
        QuestRewardDeliveryClient.AcknowledgeResult result = client.acknowledge(null,
            new QuestRewardDeliveryProtocol.AcknowledgementRequest(deliveryUuid, playerUuid, outcome,
                QuestRewardDeliveryProtocol.formatRecordedAt(System.currentTimeMillis()), UUID.randomUUID())).join();
        assertInstanceOf(QuestRewardDeliveryClient.Acknowledged.class, result,
            () -> "the " + outcome.wireName() + " report was not accepted: " + describe(result));
        return ((QuestRewardDeliveryClient.Acknowledged) result).response();
    }

    /**
     * The client with its transport run inline on this thread. Neither the credentials provider nor
     * either submitter reads the {@code MinecraftServer}, so the calls above pass {@code null}
     * rather than standing a server up for two HTTP requests.
     */
    private static QuestRewardDeliveryClient client(ServerCredentials credentials) {
        return new QuestRewardDeliveryClient(
            server -> Optional.of(credentials),
            (server, task) -> CompletableFuture.completedFuture(task.get()),
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
        if (result instanceof QuestRewardDeliveryClient.Failure failure) {
            return "Failure(" + failure.safeCode() + ")";
        }
        return result == null ? "null" : result.getClass().getSimpleName();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}

package com.seggellion.britannia_mod.structure.persistence;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.auth.RailsRequestAuthenticator;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import com.seggellion.britannia_mod.server.http.BoundedHttp;
import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver.Endpoint;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRetryPolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers placed houses to Rails, and keeps trying until Rails has them.
 *
 * <h2>What changed and why</h2>
 *
 * <p>The house POST used to be an inline call inside {@code StructurePlacer}, on the server thread,
 * with a logged warning as its entire failure handling. A house that did not land in Rails was gone
 * from the durable record for good, and — because {@code StructureRegionRehydrator} rebuilds every
 * region from Rails at boot — also lost its region at the next restart, taking its door locks and
 * its owner's build rights with it.
 *
 * <p>Delivery is now a queue. {@link HousePersistenceOutbox} records the house before the first
 * attempt, so a crash mid-request still leaves the house owed; the send happens off the server
 * thread, so a slow or hung Rails cannot stall a tick; and a failed attempt backs off through the
 * project's existing {@code ServiceNpcSpawnRetryPolicy} rather than a new schedule of its own.
 *
 * <h2>Idempotency</h2>
 *
 * <p>Retries are safe at both ends and neither end relies on the other. The outbox holds at most
 * one entry per house UUID, so a house is never queued twice; and {@code Api::HousesController}
 * upserts on {@code (uuid, shard_id)}, so a retry that Rails did in fact receive the first time
 * updates the row it already has rather than creating a second. A duplicate house row is therefore
 * impossible rather than unlikely, which matters because the failure mode being handled here is
 * precisely "we do not know whether the request arrived".
 */
public final class HousePersistenceService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Once every ten seconds. These are minutes-scale outages, not tick-scale ones. */
    private static final int SWEEP_INTERVAL_TICKS = 200;

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean SWEEP_IN_FLIGHT = new AtomicBoolean();

    /**
     * One thread, because ordering between houses does not matter and concurrency here buys nothing
     * but a way to hammer a Rails instance that is already struggling. Daemon, so it never holds the
     * process open.
     */
    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "britannia-house-persistence");
        thread.setDaemon(true);
        return thread;
    });

    private static int tickCounter;

    private HousePersistenceService() {
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            NeoForge.EVENT_BUS.register(HousePersistenceService.class);
        }
    }

    /**
     * Records a freshly placed house and sends it.
     *
     * <p>The queue write happens first and on the calling thread, which is the ordering that
     * survives a crash: a house that exists in the world is owed to Rails from the moment it is
     * placed, not from the moment a request succeeds.
     */
    public static void submit(ServerLevel level, UUID houseUuid, String requestBody) {
        HousePersistenceOutbox outbox = HousePersistenceOutbox.get(level);
        outbox.enqueue(houseUuid, requestBody);
        MinecraftServer server = level.getServer();
        SENDER.execute(() -> attempt(server, outbox, houseUuid, requestBody, 0));
    }

    /**
     * Retries whatever is still owed, off the server thread.
     *
     * <p>One sweep at a time. Without that guard a Rails outage that made every request take its
     * full timeout would pile sweeps up behind each other and turn a slow server into a flood.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < SWEEP_INTERVAL_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        HousePersistenceOutbox outbox = HousePersistenceOutbox.get(overworld);
        if (outbox.outstanding() == 0) return;

        long now = System.currentTimeMillis();
        List<HousePersistenceOutbox.PendingHouse> due = outbox.due(now);
        if (due.isEmpty() || !SWEEP_IN_FLIGHT.compareAndSet(false, true)) return;

        SENDER.execute(() -> {
            try {
                for (HousePersistenceOutbox.PendingHouse house : due) {
                    attempt(server, outbox, house.houseUuid(), house.requestBody(), house.attempts());
                }
            } finally {
                SWEEP_IN_FLIGHT.set(false);
            }
        });
    }

    /**
     * One delivery attempt.
     *
     * <p>A 2xx settles the house. A 4xx that is not a rate-limit settles it too, with an error:
     * Rails has rejected this body and will reject it identically forever, so retrying would be an
     * infinite loop against a permanent refusal — the shape of defect the spawn-outcome work already
     * had to unpick once. Everything else, including a thrown exception, is transient and comes
     * back.
     */
    private static void attempt(MinecraftServer server,
                                HousePersistenceOutbox outbox,
                                UUID houseUuid,
                                String requestBody,
                                int attempts) {
        HttpURLConnection connection = null;
        try {
            var credentials = ServerAuthRegistry.credentials(server).orElseThrow();
            var requestUri = credentials.apiUrls().resolve(Endpoint.HOUSE_CREATE);
            connection = (HttpURLConnection) requestUri.toURL().openConnection();
            BoundedHttp.configure(connection);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
            if (!RailsRequestAuthenticator.apply(connection, server, body)) {
                throw new IllegalStateException("Server authentication unavailable");
            }
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_CREATED) {
                outbox.settle(houseUuid);
                LOGGER.info("[housing] House {} is durably recorded with Rails", houseUuid);
                return;
            }
            if (status >= 400 && status < 500 && status != 429) {
                LOGGER.error("[housing] Rails permanently refused house {} (HTTP {}). It will not be "
                        + "retried; the house exists in the world and not in the durable record.",
                        houseUuid, status);
                outbox.settle(houseUuid);
                return;
            }
            defer(outbox, houseUuid, attempts,
                    "Rails answered HTTP " + status + " for house " + houseUuid);
        } catch (Exception transientFailure) {
            defer(outbox, houseUuid, attempts,
                    "Rails was unreachable for house " + houseUuid + ": " + transientFailure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void defer(HousePersistenceOutbox outbox, UUID houseUuid, int attempts, String why) {
        long delay = ServiceNpcSpawnRetryPolicy.backoffMillis(houseUuid, attempts + 1);
        outbox.deferAfterFailure(houseUuid,
                ServiceNpcSpawnRetryPolicy.saturatingAdd(System.currentTimeMillis(), delay));
        LOGGER.warn("[housing] {}; retrying in {}ms (attempt {})", why, delay, attempts + 1);
    }
}

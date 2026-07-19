package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingOpenClient;
import com.seggellion.britannia_mod.service.banking.BankingOpenClientResult;
import com.seggellion.britannia_mod.service.banking.BankingProxyService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Milestone 7 Slice A: {@link ServiceNpcEntity#interactAt} and
 * {@link BankingProxyService}, in isolation. Nothing here talks to a real Rails server —
 * {@link BankingProxyService#useClientForTesting} substitutes a fake
 * {@link BankingOpenClient} for the interaction-gating tests, and the threading proof
 * exercises {@link ServerHttpExecutor} directly without any HTTP client at all.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankingProxyServiceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String BANK_TYPE_KEY = "bank_teller";
    private static final String NON_BANK_TYPE_KEY = "guide";

    private BankingProxyServiceGameTests() {
    }

    // ---------- resolve(): fresh revalidation, never trusts captured state ----------

    @GameTest(template = TEMPLATE)
    public static void resolveRejectsADistantPlayer(GameTestHelper helper) {
        installBankRegistry();
        try {
            ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 100.0, npc.getY(), npc.getZ());

            check(BankingProxyService.resolve(player, npc) == null,
                    "resolve() accepted a player far outside interaction range");
            helper.succeed();
        } finally {
            ServiceNpcRegistryCache.clear();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void resolveAcceptsANearbyBankCapablePlayer(GameTestHelper helper) {
        installBankRegistry();
        try {
            ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

            BankingProxyService.ResolvedTeller resolved = BankingProxyService.resolve(player, npc);
            check(resolved != null, "resolve() rejected a nearby bank-capable teller");
            check(npc.getUUID().equals(resolved.entityUuid()), "resolve() returned the wrong entity UUID");
            check(npc.getWorldNpcPublicId().equals(resolved.worldNpcPublicId()),
                    "resolve() returned the wrong World NPC public id");
            helper.succeed();
        } finally {
            ServiceNpcRegistryCache.clear();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void resolveRejectsATellerWhoseTypeDoesNotSupportBankOpen(GameTestHelper helper) {
        installNonBankRegistry();
        try {
            ServiceNpcEntity npc = spawnEntity(helper, new BlockPos(1, 1, 1), NON_BANK_TYPE_KEY);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

            check(BankingProxyService.resolve(player, npc) == null,
                    "resolve() accepted a teller whose service type does not permit bank.open");
            helper.succeed();
        } finally {
            ServiceNpcRegistryCache.clear();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void resolveRejectsADiscardedEntity(GameTestHelper helper) {
        installBankRegistry();
        try {
            ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());
            npc.discard();

            check(BankingProxyService.resolve(player, npc) == null,
                    "resolve() accepted an entity that is no longer alive");
            helper.succeed();
        } finally {
            ServiceNpcRegistryCache.clear();
        }
    }

    // ---------- interactAt: server-side and capability gating ----------

    @GameTest(template = TEMPLATE)
    public static void interactAtDispatchesForABankCapableTellerOnTheServer(GameTestHelper helper) {
        installBankRegistry();
        AtomicBoolean dispatched = new AtomicBoolean(false);
        BankingProxyService.useClientForTesting(recordingClient(dispatched));
        try {
            ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

            InteractionResult result = npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);

            check(result != InteractionResult.PASS, "interactAt did not report handling the interaction");
            check(dispatched.get(), "interactAt did not dispatch the banking flow for a bank-capable teller");
            helper.succeed();
        } finally {
            BankingProxyService.resetClientForTesting();
            ServiceNpcRegistryCache.clear();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void interactAtDoesNotDispatchForANonBankCapableTeller(GameTestHelper helper) {
        installNonBankRegistry();
        AtomicBoolean dispatched = new AtomicBoolean(false);
        BankingProxyService.useClientForTesting(recordingClient(dispatched));
        try {
            ServiceNpcEntity npc = spawnEntity(helper, new BlockPos(1, 1, 1), NON_BANK_TYPE_KEY);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

            npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);

            check(!dispatched.get(), "interactAt dispatched the banking flow for a non-bank-capable teller");
            helper.succeed();
        } finally {
            BankingProxyService.resetClientForTesting();
            ServiceNpcRegistryCache.clear();
        }
    }

    @GameTest(template = TEMPLATE)
    public static void interactAtDoesNotDispatchForTheOffHandInteraction(GameTestHelper helper) {
        installBankRegistry();
        AtomicBoolean dispatched = new AtomicBoolean(false);
        BankingProxyService.useClientForTesting(recordingClient(dispatched));
        try {
            ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

            npc.interactAt(player, Vec3.ZERO, InteractionHand.OFF_HAND);

            check(!dispatched.get(), "interactAt dispatched the banking flow for an off-hand interaction");
            helper.succeed();
        } finally {
            BankingProxyService.resetClientForTesting();
            ServiceNpcRegistryCache.clear();
        }
    }

    // ---------- handle(): stale-teller discard after async completion ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void handleDiscardsTheResultIfTheTellerIsDiscardedWhileTheCallIsInFlight(GameTestHelper helper) {
        installBankRegistry();
        CompletableFuture<BankingOpenClientResult> pending = new CompletableFuture<>();
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                ignored -> java.util.Optional.empty(), (ignored, task) -> pending, (uri, max) -> null
        ));
        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);
        npc.discard();
        // Completing after discard must not throw, and the enqueued server.execute(...)
        // completion callback (which re-checks isAlive()) must not crash the tick thread
        // when it actually runs a tick or two from now.
        pending.complete(new BankingOpenClientResult.Rejected(
                com.seggellion.britannia_mod.service.banking.BankingOpenOutcome.TELLER_NOT_ACTIVE, false
        ));
        helper.runAfterDelay(2, () -> {
            BankingProxyService.resetClientForTesting();
            ServiceNpcRegistryCache.clear();
            helper.succeed();
        });
    }

    // ---------- handle(): disconnected player during flight ----------
    // Traced PlayerList.remove(ServerPlayer) in the decompiled source: it synchronously
    // removes the player from playersByUUID, on the main server thread -- the same thread
    // the server.execute(...) completion callback runs on. The existing
    // "getPlayerList().getPlayer(connectedPlayerId) != player" guard (mirroring
    // QuestProxyService's own identical check) therefore already catches this: after
    // disconnect, getPlayer(uuid) returns null, which is never == the captured player
    // reference, so the guard trips before applyPlaceholderResult ever touches the
    // disconnected player. This test proves that trace rather than merely asserting it.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void handleDoesNotTouchAPlayerWhoDisconnectedWhileTheCallIsInFlight(GameTestHelper helper) {
        installBankRegistry();
        CompletableFuture<BankingOpenClientResult> pending = new CompletableFuture<>();
        BankingOpenClient.CredentialsProvider credentials = server -> java.util.Optional.of(
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), UUID.randomUUID()
                )
        );
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                credentials, (ignored, task) -> pending, (uri, max) -> null
        ));
        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);
        helper.getLevel().getServer().getPlayerList().remove(player);
        check(helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == null,
                "test setup did not actually remove the player from the player list");

        // Completing after disconnect must not throw here, and the enqueued
        // server.execute(...) callback must not crash the tick thread nor call any
        // player-facing method (displayClientMessage would throw/misbehave on a
        // disconnected player's connection) when it actually runs a tick or two from now.
        pending.complete(new BankingOpenClientResult.Success(new com.seggellion.britannia_mod.service.banking.BankingOpenAccount(
                UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 1
        )));
        helper.runAfterDelay(2, () -> {
            BankingProxyService.resetClientForTesting();
            ServiceNpcRegistryCache.clear();
            helper.succeed();
        });
    }

    // ---------- handle(): a repeat interaction while one is already in flight ----------
    // Traced handle() before this fix: it had no per-player in-flight tracking at all --
    // two rapid interactAt calls (a double-click, or simply not knowing a request is
    // already pending before Slice B's screen exists) each independently pass resolve()
    // and each dispatch their own HTTP call. This test proves the fix: a repeat while one
    // is pending does not reach the transport layer a second time, and a genuinely new
    // interaction after the first completes dispatches normally.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void repeatInteractionWhileOneIsInFlightDoesNotDispatchASecondCall(GameTestHelper helper) {
        installBankRegistry();
        java.util.concurrent.atomic.AtomicInteger dispatchCount = new java.util.concurrent.atomic.AtomicInteger();
        CompletableFuture<BankingOpenClientResult> pending = new CompletableFuture<>();
        BankingOpenClient.CredentialsProvider credentials = server -> java.util.Optional.of(
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), UUID.randomUUID()
                )
        );
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                credentials,
                (ignored, task) -> {
                    dispatchCount.incrementAndGet();
                    return pending;
                },
                (uri, max) -> null
        ));
        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);

        check(dispatchCount.get() == 1,
                "a repeat interaction while one was already in flight dispatched a second HTTP call (count="
                        + dispatchCount.get() + ")");

        pending.complete(new BankingOpenClientResult.Rejected(
                com.seggellion.britannia_mod.service.banking.BankingOpenOutcome.TELLER_NOT_ACTIVE, false
        ));

        // Completing pending only enqueues the completion callback (which clears the
        // IN_FLIGHT marker) via server.execute -- it does not run inline. Rather than
        // guess a fixed tick delay for that queued task to drain, poll every tick
        // (succeedWhen retries its runnable until it stops throwing) so this is robust
        // to however many ticks the enqueue-then-drain round trip actually takes.
        helper.succeedWhen(() -> {
            npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
            check(dispatchCount.get() == 2,
                    "a new interaction after the first completed did not dispatch its own call (count="
                            + dispatchCount.get() + ")");
            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
        });
    }

    // ---------- handle(): synchronous submission failure must still clear IN_FLIGHT ----------
    // Traced client.submit(server, request) in handle(): it is not wrapped in any try/catch.
    // BankingOpenClient.submitConfigured only catches RejectedExecutionException around its
    // own transportSubmitter.submit(...) call (converting it to a normally-returned
    // TransportFailure future) -- any other RuntimeException thrown synchronously by a
    // TransportSubmitter propagates straight out of client.submit(...), meaning the
    // .whenComplete(...) callback that clears IN_FLIGHT is never attached and never runs.
    // This test forces exactly that (a plain RuntimeException, not RejectedExecutionException,
    // so it bypasses both of BankingOpenClient's existing catches) to prove empirically
    // whether the player's UUID is left permanently stuck in IN_FLIGHT.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void handleClearsInFlightWhenSubmissionThrowsSynchronously(GameTestHelper helper) {
        installBankRegistry();
        BankingOpenClient.CredentialsProvider credentials = server -> java.util.Optional.of(
                com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                        java.net.URI.create("http://127.0.0.1"), UUID.randomUUID()
                )
        );
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                credentials,
                (ignored, task) -> {
                    throw new IllegalStateException("simulated synchronous submission failure");
                },
                (uri, max) -> null
        ));
        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);

        // If handle() left the player's UUID stuck in IN_FLIGHT, this second interaction
        // would silently no-op (the "already in flight" branch) forever after -- proving the
        // leak this test targets. Substitute a client that would succeed on this
        // (interaction-scoped only if reachable) so a dispatch is directly observable.
        java.util.concurrent.atomic.AtomicBoolean secondDispatchReached = new java.util.concurrent.atomic.AtomicBoolean();
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                credentials,
                (ignored, task) -> {
                    secondDispatchReached.set(true);
                    return CompletableFuture.completedFuture(new BankingOpenClientResult.Rejected(
                            com.seggellion.britannia_mod.service.banking.BankingOpenOutcome.TELLER_NOT_ACTIVE, false
                    ));
                },
                (uri, max) -> null
        ));
        npc.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);

        check(secondDispatchReached.get(),
                "a normal interaction after a synchronous submission failure did not dispatch -- "
                        + "IN_FLIGHT retained the player's UUID (leak confirmed)");

        BankingProxyService.resetClientForTesting();
        BankingProxyService.resetInFlightTrackingForTesting();
        ServiceNpcRegistryCache.clear();
        helper.succeed();
    }

    // ---------- Threading: the HTTP-performing work never runs on the calling thread ----------

    @GameTest(template = TEMPLATE)
    public static void serverHttpExecutorRunsOffTheCallingThread(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String callingThread = Thread.currentThread().getName();

        CompletableFuture<String> future = ServerHttpExecutor.submit(server, () -> Thread.currentThread().getName());
        String executedOn;
        try {
            executedOn = future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | java.util.concurrent.ExecutionException | TimeoutException error) {
            throw new IllegalStateException("ServerHttpExecutor task did not complete", error);
        }

        check(!executedOn.equals(callingThread),
                "ServerHttpExecutor ran its task on the calling thread instead of a background thread");
        check(executedOn.startsWith("britannia-server-http-"),
                "ServerHttpExecutor task did not run on its own dedicated thread pool (ran on " + executedOn + ")");
        helper.succeed();
    }

    // ---------- Helpers ----------

    /**
     * Records that dispatch reached the client at all, at the earliest possible point
     * ({@code credentialsProvider} is the first thing {@link BankingOpenClient#submit}
     * calls) rather than depending on real {@code ServerCredentials} construction, which
     * is package-private and unavailable from this package.
     */
    private static BankingOpenClient recordingClient(AtomicBoolean dispatched) {
        return new BankingOpenClient(
                server -> {
                    dispatched.set(true);
                    return java.util.Optional.empty();
                },
                (ignored, task) -> CompletableFuture.completedFuture(null),
                (uri, max) -> null
        );
    }

    private static ServiceNpcEntity spawnBankTeller(GameTestHelper helper, BlockPos relative) {
        return spawnEntity(helper, relative, BANK_TYPE_KEY);
    }

    private static ServiceNpcEntity spawnEntity(GameTestHelper helper, BlockPos relative, String serviceNpcTypeKey) {
        ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), relative);
        npc.setWorldNpcPublicId(UUID.randomUUID());
        npc.setServiceNpcTypeKey(serviceNpcTypeKey);
        return npc;
    }

    private static void installBankRegistry() {
        ServiceNpcTypeDefinition bankTeller = new ServiceNpcTypeDefinition(
                BANK_TYPE_KEY, "Bank Teller", "banker", "britannia_mod:service_npc",
                "bank_teller_default", List.of("bank.open"), true, true, 1
        );
        ServiceNpcRegistryCache.replace(
                new ServiceNpcRegistrySnapshot(1, 1, Map.of(), Map.of(bankTeller.key(), bankTeller), Map.of())
        );
    }

    private static void installNonBankRegistry() {
        ServiceNpcTypeDefinition guide = new ServiceNpcTypeDefinition(
                NON_BANK_TYPE_KEY, "Guide", "guide", "britannia_mod:service_npc",
                "guide_default", List.of(), true, true, 1
        );
        ServiceNpcRegistryCache.replace(
                new ServiceNpcRegistrySnapshot(1, 1, Map.of(), Map.of(guide.key(), guide), Map.of())
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

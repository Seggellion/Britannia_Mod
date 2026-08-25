package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.banking.BankingOpenAccount;
import com.seggellion.britannia_mod.service.banking.BankingOpenClient;
import com.seggellion.britannia_mod.service.banking.BankingOpenClientResult;
import com.seggellion.britannia_mod.service.banking.BankingOpenOutcome;
import com.seggellion.britannia_mod.service.banking.BankingProxyService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        ), java.util.List.of()));
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

    // ---------- Slice B: OPENED result opens the real Bank Screen with correct content ----------

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void handleOpensTheAccountScreenWithCorrectContentInGlobalMode(GameTestHelper helper) {
        installBankRegistry();
        BankingOpenAccount account = new BankingOpenAccount(
                UUID.randomUUID(), "global", null, 250, 12.5, 3, 47, 92, 1
        );
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                gameTestCredentials(),
                (ignored, task) -> CompletableFuture.completedFuture(new BankingOpenClientResult.Success(account, java.util.List.of())),
                (uri, max) -> null
        ));
        AtomicReference<BankAccountOpenedS2CPayload> sent = new AtomicReference<>();
        BankingProxyService.useAccountScreenSenderForTesting((player, teller, sentAccount, sentBankItems) ->
                sent.set(BankAccountOpenedS2CPayload.create(teller, sentAccount, sentBankItems, false)));

        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        npc.setPersonalName("Aldric the Banker");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);

        helper.succeedWhen(() -> {
            BankAccountOpenedS2CPayload payload = sent.get();
            check(payload != null, "the account screen sender was never invoked for a successful OPENED result");
            check(payload.tellerName().equals("Aldric the Banker"),
                    "teller name was not carried through: " + payload.tellerName());
            check(payload.cityDisplayName() == null,
                    "global-mode account must not carry a city display name, got " + payload.cityDisplayName());
            check(payload.weightLimit() == 250, "weight limit mismatch: " + payload.weightLimit());
            check(payload.currentWeight() == 12.5, "current weight mismatch: " + payload.currentWeight());
            check(payload.goldBalance() == 3 && payload.silverBalance() == 47 && payload.copperBalance() == 92,
                    "balance mismatch: " + payload.goldBalance() + "/" + payload.silverBalance() + "/" + payload.copperBalance());

            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
        });
    }

    // Own batch: this test's result arrives a tick or more after handle(), via server.execute, and
    // the completion revalidates the teller/player pairing before it will send anything. The banking
    // rigs install process-wide fakes and share ServiceNpcRegistryCache, so a neighbour in the same
    // batch tearing its own registry down between this test's handle() and its completion leaves
    // this teller unresolvable -- the result is discarded as "no longer live/in range" and the
    // sender is never invoked. A batch of its own gives it no concurrent neighbours to be raced by.
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "bankingAccountScreenCityLocal")
    public static void handleOpensTheAccountScreenWithCorrectContentInCityLocalMode(GameTestHelper helper) {
        installBankRegistry();
        UUID cityId = UUID.randomUUID();
        BootstrapCityRegistryCache.replace(BootstrapCityRegistrySnapshot.available(
                List.of(new BootstrapCityDefinition(cityId, "Britain"))
        ));
        BankingOpenAccount account = new BankingOpenAccount(
                UUID.randomUUID(), "city_local", cityId, 250, 0.0, 0, 0, 0, 1
        );
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                gameTestCredentials(),
                (ignored, task) -> CompletableFuture.completedFuture(new BankingOpenClientResult.Success(account, java.util.List.of())),
                (uri, max) -> null
        ));
        AtomicReference<BankAccountOpenedS2CPayload> sent = new AtomicReference<>();
        BankingProxyService.useAccountScreenSenderForTesting((player, teller, sentAccount, sentBankItems) ->
                sent.set(BankAccountOpenedS2CPayload.create(teller, sentAccount, sentBankItems, false)));

        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        npc.setPersonalName("Isolde the Banker");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);

        // The city registry is torn down with the other seams inside succeedWhen, not in a finally.
        // handle() hands its completion to server.execute(...), so the sender runs a tick or more
        // later and BankAccountOpenedS2CPayload.create resolves the city display name at that
        // point, out of this very cache. A finally here runs the instant succeedWhen registers --
        // long before any of that -- so the cache was always empty by the time it was read, and
        // resolveCityDisplayName returned null rather than "Britain".
        helper.succeedWhen(() -> {
            BankAccountOpenedS2CPayload payload = sent.get();
            check(payload != null, "the account screen sender was never invoked for a successful OPENED result");
            check("Britain".equals(payload.cityDisplayName()),
                    "city-local account did not carry the resolved city display name, got " + payload.cityDisplayName());

            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            BootstrapCityRegistryCache.clear();
        });
    }

    // ---------- Slice B: every non-OPENED outcome keeps the screen closed ----------
    // Traced Slice A's full BankingOpenOutcome vocabulary (every value but OPENED itself is
    // a non-success outcome) plus the transport-failure branch: none of them may ever reach
    // the account screen sender. Dispatches one outcome at a time via a recursive
    // runAfterDelay chain (the same fixed-delay-then-continue idiom the existing
    // disconnect/discard tests already use), rather than throwing every tick as a retry
    // signal from inside succeedWhen -- an earlier version of this test did that and it
    // escaped GameTestSequence's tolerance entirely, crashing the whole tick loop instead of
    // just failing the one GameTest. check() still throws, but only ever to report a real
    // assertion failure, exactly like every other test in this file.
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void everyNonOpenedOutcomeNeverOpensTheAccountScreen(GameTestHelper helper) {
        installBankRegistry();
        List<BankingOpenOutcome> outcomes = Arrays.stream(BankingOpenOutcome.values())
                .filter(outcome -> outcome != BankingOpenOutcome.OPENED)
                .toList();

        AtomicBoolean sent = new AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((player, teller, account, bankItems) -> sent.set(true));

        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        dispatchNextNonOpenedOutcome(helper, player, npc, outcomes, 0, sent);
    }

    private static void dispatchNextNonOpenedOutcome(
            GameTestHelper helper, ServerPlayer player, ServiceNpcEntity npc,
            List<BankingOpenOutcome> outcomes, int index, AtomicBoolean sent
    ) {
        if (index >= outcomes.size()) {
            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            helper.succeed();
            return;
        }
        BankingOpenOutcome outcome = outcomes.get(index);
        BankingOpenClientResult result = outcome == BankingOpenOutcome.SERVICE_UNAVAILABLE
                ? new BankingOpenClientResult.TransportFailure("service_unavailable")
                : new BankingOpenClientResult.Rejected(outcome, outcome.expectedRetryable());
        sent.set(false);
        BankingProxyService.resetInFlightTrackingForTesting();
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                gameTestCredentials(),
                (ignored, task) -> CompletableFuture.completedFuture(result),
                (uri, max) -> null
        ));
        BankingProxyService.handle(player, npc);

        helper.runAfterDelay(3, () -> {
            check(!sent.get(), "the account screen opened for a non-OPENED outcome: " + outcome);
            dispatchNextNonOpenedOutcome(helper, player, npc, outcomes, index + 1, sent);
        });
    }

    // ---------- Slice B: session-invalidation is checked once, at completion time ----------
    // Decision (recorded by the retired legacy screen): this screen is a plain, static one-shot
    // snapshot with no ongoing server-side validity check once open, matching
    // QuestDecisionScreen's own real production mechanism exactly. The one and only gate is
    // the existing revalidation in handle()'s completion callback -- these tests prove that
    // gate actually suppresses opening the screen for a session that went stale in flight,
    // directly (by observing the account screen sender), not merely by asserting no crash.

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void handleDoesNotOpenTheAccountScreenForAPlayerWhoDisconnectedWhileTheCallWasInFlight(GameTestHelper helper) {
        installBankRegistry();
        CompletableFuture<BankingOpenClientResult> pending = new CompletableFuture<>();
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                gameTestCredentials(), (ignored, task) -> pending, (uri, max) -> null
        ));
        AtomicBoolean sent = new AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((player, teller, account, bankItems) -> sent.set(true));

        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);
        helper.getLevel().getServer().getPlayerList().remove(player);
        pending.complete(new BankingOpenClientResult.Success(
                new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 1), java.util.List.of()
        ));

        helper.runAfterDelay(4, () -> {
            check(!sent.get(), "the account screen opened for a player who had disconnected while the call was in flight");
            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            ServiceNpcRegistryCache.clear();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void handleDoesNotOpenTheAccountScreenIfTheTellerIsDiscardedWhileTheCallWasInFlight(GameTestHelper helper) {
        installBankRegistry();
        CompletableFuture<BankingOpenClientResult> pending = new CompletableFuture<>();
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                gameTestCredentials(), (ignored, task) -> pending, (uri, max) -> null
        ));
        AtomicBoolean sent = new AtomicBoolean();
        BankingProxyService.useAccountScreenSenderForTesting((player, teller, account, bankItems) -> sent.set(true));

        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);
        npc.discard();
        pending.complete(new BankingOpenClientResult.Success(
                new BankingOpenAccount(UUID.randomUUID(), "global", null, 250, 0.0, 0, 0, 0, 1), java.util.List.of()
        ));

        helper.runAfterDelay(4, () -> {
            check(!sent.get(), "the account screen opened for a teller that had been discarded while the call was in flight");
            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            helper.succeed();
        });
    }

    // ---------- Slice B: non-negotiable invariant -- no bank data persists anywhere ----------
    // ServiceNpcAssignmentsSnapshot (and everything it's built from -- spawn point, world NPC,
    // and assignment definitions) is proven structurally, not just by this test: read in full,
    // none of those four record shapes has any field that could hold currency, weight, or an
    // account identifier. There is no runtime check for that cache below for exactly this
    // reason -- an earlier version of this test asserted its snapshot reference never changed,
    // but that cache is anchored to the whole game-test server's overworld DataStorage (shared
    // across every test and the background WorldBootstrapHandler login coordinator, which
    // legitimately replaces it independently of anything this test does), so a reference
    // check on it is a false-positive-prone proxy for an invariant the type system already
    // guarantees outright. ServiceNpcEntity, in contrast, is a class this slice actually
    // touches (reads its display name) and controls, so it gets a real runtime NBT check.
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void openingAnAccountNeverWritesBankDataToPersistedNbt(GameTestHelper helper) {
        installBankRegistry();
        BankingOpenAccount account = new BankingOpenAccount(
                UUID.randomUUID(), "global", null, 987, 65.25, 111, 222, 333, 4
        );
        BankingProxyService.useClientForTesting(new BankingOpenClient(
                gameTestCredentials(),
                (ignored, task) -> CompletableFuture.completedFuture(new BankingOpenClientResult.Success(account, java.util.List.of())),
                (uri, max) -> null
        ));
        BankingProxyService.useAccountScreenSenderForTesting((player, teller, sentAccount, sentBankItems) -> {
        });

        ServiceNpcEntity npc = spawnBankTeller(helper, new BlockPos(1, 1, 1));
        npc.setPersonalName("Aldric the Banker");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(npc.getX() + 1.0, npc.getY(), npc.getZ());

        BankingProxyService.handle(player, npc);

        helper.runAfterDelay(4, () -> {
            CompoundTag tellerTag = new CompoundTag();
            npc.saveWithoutId(tellerTag);
            Set<String> forbiddenBankDataKeys = Set.of(
                    "GoldBalance", "SilverBalance", "CopperBalance", "WeightLimit", "CurrentWeight",
                    "BankAccountPublicId", "AccountPublicId", "BankingMode", "AccountRevision",
                    "CityPublicId", "BankAccount"
            );
            for (String key : tellerTag.getAllKeys()) {
                check(!forbiddenBankDataKeys.contains(key),
                        "ServiceNpcEntity NBT unexpectedly contains a bank-data key: " + key);
            }

            BankingProxyService.resetClientForTesting();
            BankingProxyService.resetAccountScreenSenderForTesting();
            BankingProxyService.resetInFlightTrackingForTesting();
            ServiceNpcRegistryCache.clear();
            helper.succeed();
        });
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

    /**
     * Real (non-empty) credentials so a substituted {@code TransportSubmitter} genuinely
     * reaches the transport layer instead of {@link BankingOpenClient#submit} short-circuiting
     * with {@code LocalFailure} first -- the same reason the Part A/B verification passes
     * needed {@link com.seggellion.britannia_mod.server.auth.ServerCredentials#forGameTesting}
     * in the first place.
     */
    private static BankingOpenClient.CredentialsProvider gameTestCredentials() {
        return server -> Optional.of(com.seggellion.britannia_mod.server.auth.ServerCredentials.forGameTesting(
                URI.create("http://127.0.0.1"), UUID.randomUUID()
        ));
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

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}

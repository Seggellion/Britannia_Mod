package com.seggellion.britannia_mod.service.banking;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side entry point for {@code bank.open}, structurally mirroring
 * {@link com.seggellion.britannia_mod.quest.QuestProxyService#handle}: revalidate fresh
 * (never trust anything captured earlier), dispatch the HTTP call off the server tick
 * thread via {@link BankingOpenClient} (itself backed by
 * {@link com.seggellion.britannia_mod.server.http.ServerHttpExecutor}, the same bounded
 * per-server thread pool QuestProxyService uses), and marshal the result back onto the
 * main thread via {@code server.execute(...)} before touching any player-visible state.
 *
 * <p>Threading trace (confirmed by reading QuestProxyService.handle, not assumed): that
 * method calls {@code ServerHttpExecutor.submit(server, () -> callRails(...))}, which is
 * {@code CompletableFuture.supplyAsync(task, executor)} against a dedicated 1-2 thread
 * daemon pool per {@code MinecraftServer} ({@code ServerHttpExecutor.newExecutor()}) —
 * never the caller's thread. Its {@code .whenComplete(...)} callback then calls
 * {@code server.execute(() -> {...})}, which enqueues the completion work back onto the
 * main server thread's tick queue rather than running it on the HTTP pool thread. This
 * class calls the exact same two primitives in the exact same order for the exact same
 * reason: the blocking {@link java.net.HttpURLConnection} work in
 * {@link BankingOpenClient} must never run on the thread that called
 * {@link #handle(ServerPlayer, ServiceNpcEntity)}, and the result-handling code that
 * follows must never run on the HTTP pool thread, since it eventually touches
 * {@link ServerPlayer}/{@link net.minecraft.server.level.ServerLevel} state that is only
 * safe to mutate from the main thread.
 */
public final class BankingProxyService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Matches QuestProxyService.resolve's own bound exactly (8 blocks, squared). */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    private static BankingOpenClient client = new BankingOpenClient();

    /**
     * Players with a bank.open request currently in flight. Without this, two rapid
     * interactAt calls (a double-click, or simply not being able to visually tell a
     * request is already pending before Slice B's screen exists) each independently pass
     * resolve() and each dispatch their own HTTP call — wasteful, and would show the
     * player two placeholder messages for one interaction. Cleared unconditionally in the
     * completion callback's first line, regardless of success, rejection, transport
     * failure, or the player/entity having gone stale in the meantime, so a genuinely new
     * interaction after the first completes is never blocked.
     */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private BankingProxyService() {
    }

    /**
     * Test-only seam: substitute a fake client so tests never open a real socket. Public
     * (rather than package-private, unlike {@code ServiceNpcSpawnRegistrationClient}'s own
     * test constructor overload) because GameTests exercising the real
     * {@link com.seggellion.britannia_mod.entity.ServiceNpcEntity} interaction entry point
     * live in the {@code gametest} package by this codebase's own established convention,
     * not this one.
     */
    public static void useClientForTesting(BankingOpenClient testClient) {
        client = testClient;
    }

    public static void resetClientForTesting() {
        client = new BankingOpenClient();
    }

    public static void resetInFlightTrackingForTesting() {
        IN_FLIGHT.clear();
    }

    /**
     * Test-only seam for the Bank Screen open step, mirroring
     * {@link #useClientForTesting(BankingOpenClient)} exactly: substituting a recording
     * sender lets a GameTest assert precisely when (and with what data) the real screen
     * would open, and — just as importantly — that it is never invoked for any non-success
     * outcome or stale session, without needing a real client connection to observe.
     */
    @FunctionalInterface
    public interface AccountScreenSender {
        void send(ServerPlayer player, ServiceNpcEntity teller, BankingOpenAccount account);
    }

    private static AccountScreenSender accountScreenSender = BankAccountOpenedS2CPayload::send;

    public static void useAccountScreenSenderForTesting(AccountScreenSender testSender) {
        accountScreenSender = testSender;
    }

    public static void resetAccountScreenSenderForTesting() {
        accountScreenSender = BankAccountOpenedS2CPayload::send;
    }

    public static void handle(ServerPlayer player, ServiceNpcEntity entity) {
        ResolvedTeller resolved = resolve(player, entity);
        if (resolved == null) return;

        UUID connectedPlayerId = player.getUUID();
        if (!IN_FLIGHT.add(connectedPlayerId)) {
            // A bank.open request for this player is already in flight; a rapid repeat
            // interaction must not dispatch a second HTTP call for the same player.
            LOGGER.info("Ignoring bank.open interaction for {}: a request is already in flight", connectedPlayerId);
            return;
        }

        MinecraftServer server = player.server;
        UUID entityUuid = resolved.entityUuid();
        BankingOpenRequest request = new BankingOpenRequest(connectedPlayerId, resolved.worldNpcPublicId());

        // client.submit(...) itself must never be allowed to leak connectedPlayerId out of
        // IN_FLIGHT: the .whenComplete(...) callback below (which normally does the
        // IN_FLIGHT.remove) is only attached to whatever future submit() returns, so a
        // RuntimeException thrown synchronously by submit() -- before it returns any
        // future at all -- would otherwise leave the player stuck in IN_FLIGHT forever.
        // handle() only ever runs on the main server thread, so it is safe to call
        // applyPlaceholderResult directly here rather than marshalling through
        // server.execute(...).
        final java.util.concurrent.CompletableFuture<BankingOpenClientResult> future;
        try {
            future = client.submit(server, request);
        } catch (RuntimeException submissionFailure) {
            IN_FLIGHT.remove(connectedPlayerId);
            LOGGER.warn("banking/open submission threw synchronously for {}", connectedPlayerId, submissionFailure);
            applyResult(player, null, new BankingOpenClientResult.TransportFailure("synchronous_submission_failure"));
            return;
        }

        future.whenComplete((result, failure) -> server.execute(() -> {
            IN_FLIGHT.remove(connectedPlayerId);
            if (server.getPlayerList().getPlayer(connectedPlayerId) != player) return;
            if (failure != null || result == null) {
                applyResult(player, null, new BankingOpenClientResult.TransportFailure("unexpected_client_error"));
                return;
            }

            // The teller may have been discarded, reassigned, or moved out of range by
            // the chunk-load reconciler (or the player themselves) while the HTTP call
            // was in flight; never apply a result to a stale teller/player pairing. This
            // is also this design's one and only session-validity check for the Bank
            // Screen (see BankScreen's own class doc): once this passes and the screen
            // opens, nothing continues to validate the session afterward.
            Entity current = player.serverLevel().getEntity(entityUuid);
            if (!(current instanceof ServiceNpcEntity teller) || !current.isAlive()
                    || player.distanceToSqr(current) > MAX_INTERACTION_DISTANCE_SQR) {
                LOGGER.info("Discarding banking/open result: teller {} is no longer live/in range", entityUuid);
                return;
            }
            applyResult(player, teller, result);
        }));
    }

    /**
     * Fresh, from-scratch revalidation of the player/entity pairing — never trusts
     * anything the caller captured earlier. Returns {@code null} for any reason the
     * interaction should silently produce no request at all (distant player, dead/wrong
     * entity, missing identifiers, or a teller whose live capability does not currently
     * permit {@code bank.open}).
     */
    @Nullable
    public static ResolvedTeller resolve(@Nullable ServerPlayer player, @Nullable ServiceNpcEntity entity) {
        if (player == null || entity == null) return null;
        if (player.level().isClientSide) return null;
        if (!entity.isAlive()) return null;
        if (player.distanceToSqr(entity) > MAX_INTERACTION_DISTANCE_SQR) return null;
        UUID worldNpcPublicId = entity.getWorldNpcPublicId();
        if (worldNpcPublicId == null) return null;
        if (!BankingCapability.supportsBankOpen(entity.getServiceNpcTypeKey())) return null;
        return new ResolvedTeller(entity.getUUID(), worldNpcPublicId);
    }

    /**
     * Slice A's only-ever chat-message placeholder for {@code OPENED}; from Slice B on,
     * that case opens the real Bank Screen instead ({@link #accountScreenSender}). Every
     * other outcome keeps a chat/status-message-style response exactly as Slice A
     * established — the existing cached teller/dialogue (whatever, if anything, the
     * client currently has on screen) stays visible; nothing here ever opens a screen for
     * a non-success result. Wording is deliberately diegetic (no raw
     * {@link BankingOpenOutcome} wire name or transport code is ever shown to a player),
     * per this milestone's explicit requirement.
     */
    private static void applyResult(ServerPlayer player, @Nullable ServiceNpcEntity teller, BankingOpenClientResult result) {
        LOGGER.info("banking/open result for {}: {}", player.getStringUUID(), result);
        switch (result) {
            case BankingOpenClientResult.Success success ->
                    accountScreenSender.send(player, Objects.requireNonNull(teller, "teller"), success.account());
            case BankingOpenClientResult.Rejected ignored ->
                    player.displayClientMessage(Component.literal(REJECTED_MESSAGE), false);
            case BankingOpenClientResult.TransportFailure ignored ->
                    player.displayClientMessage(Component.literal(SERVICE_UNAVAILABLE_MESSAGE), false);
            case BankingOpenClientResult.LocalFailure ignored ->
                    player.displayClientMessage(Component.literal(NOT_CONFIGURED_MESSAGE), false);
        }
    }

    /**
     * Public (like the rest of this class's test seams) so a GameTest in the {@code
     * gametest} package can assert these read as diegetic in-world dialogue rather than a
     * raw protocol code, without duplicating the literal text.
     */
    public static final String REJECTED_MESSAGE =
            "The teller checks the ledger and shakes their head: \"I'm afraid I can't help you with that right now.\"";
    public static final String SERVICE_UNAVAILABLE_MESSAGE =
            "The ledger is unavailable right now. Please try again shortly.";
    public static final String NOT_CONFIGURED_MESSAGE =
            "Banking is not configured on this server.";

    public record ResolvedTeller(UUID entityUuid, UUID worldNpcPublicId) {
    }
}

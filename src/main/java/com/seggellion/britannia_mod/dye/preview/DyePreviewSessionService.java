package com.seggellion.britannia_mod.dye.preview;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** One-use, player-bound, lazy-cleaned in-memory preview sessions. */
public final class DyePreviewSessionService {
    public static final long DEFAULT_LIFETIME_MILLIS = 30_000L;
    private static final long TOMBSTONE_LIFETIME_MILLIS = 60_000L;

    private final Map<UUID, DyePreviewSession> activeByPlayer = new HashMap<>();
    private final Map<UUID, TerminalSession> terminalBySession = new HashMap<>();
    private final LongSupplier clock;
    private final Supplier<UUID> sessionIds;
    private final long lifetimeMillis;

    public DyePreviewSessionService() {
        this(System::currentTimeMillis, UUID::randomUUID, DEFAULT_LIFETIME_MILLIS);
    }

    public DyePreviewSessionService(LongSupplier clock, Supplier<UUID> sessionIds, long lifetimeMillis) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionIds = Objects.requireNonNull(sessionIds, "sessionIds");
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("lifetimeMillis must be positive");
        }
        this.lifetimeMillis = lifetimeMillis;
    }

    public synchronized Optional<DyePreviewSession> create(
            UUID playerId, ItemStack mainHand, ItemStack offHand,
            DyePreviewPlan plan, RegistrySnapshot snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!plan.successful()) {
            return Optional.empty();
        }
        long now = clock.getAsLong();
        cleanup(now);
        DyePreviewSession replaced = activeByPlayer.remove(playerId);
        if (replaced != null) {
            remember(replaced, TerminalReason.REPLACED, now);
        }
        UUID sessionId = Objects.requireNonNull(sessionIds.get(), "sessionIds returned null");
        if (activeByPlayer.values().stream().anyMatch(session -> session.sessionId().equals(sessionId))
                || terminalBySession.containsKey(sessionId)) {
            return Optional.empty();
        }
        DyePreviewSession session = new DyePreviewSession(
                playerId, sessionId, now, now + lifetimeMillis,
                mainHand.getItem(), mainHand, offHand.getItem(), offHand,
                plan.pigmentId().orElseThrow(), plan.bannerState().orElseThrow(), plan.tubState().orElseThrow(),
                plan.result().orElseThrow(), snapshot, plan.displayData().orElseThrow());
        activeByPlayer.put(playerId, session);
        return Optional.of(session);
    }

    public synchronized SessionClaim claimForConfirmation(UUID playerId, UUID sessionId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        long now = clock.getAsLong();
        DyePreviewSession active = activeByPlayer.get(playerId);
        if (active != null && active.sessionId().equals(sessionId)) {
            activeByPlayer.remove(playerId);
            if (active.expired(now)) {
                remember(active, TerminalReason.EXPIRED, now);
                cleanup(now);
                return SessionClaim.failure(DyeApplicationResultCode.SESSION_EXPIRED);
            }
            remember(active, TerminalReason.CONSUMED, now);
            cleanup(now);
            return SessionClaim.success(active);
        }
        cleanup(now);
        TerminalSession terminal = terminalBySession.get(sessionId);
        if (terminal != null) {
            if (!terminal.playerId().equals(playerId)) {
                return SessionClaim.failure(DyeApplicationResultCode.SESSION_MISMATCH);
            }
            return SessionClaim.failure(switch (terminal.reason()) {
                case EXPIRED -> DyeApplicationResultCode.SESSION_EXPIRED;
                case CANCELLED -> DyeApplicationResultCode.CANCELLED;
                default -> DyeApplicationResultCode.SESSION_REPLAYED;
            });
        }
        boolean belongsToOtherPlayer = activeByPlayer.values().stream()
                .anyMatch(session -> session.sessionId().equals(sessionId));
        if (belongsToOtherPlayer || activeByPlayer.containsKey(playerId)) {
            return SessionClaim.failure(DyeApplicationResultCode.SESSION_MISMATCH);
        }
        return SessionClaim.failure(DyeApplicationResultCode.SESSION_MISSING);
    }

    public synchronized DyeApplicationResultCode cancel(UUID playerId, UUID sessionId) {
        long now = clock.getAsLong();
        DyePreviewSession active = activeByPlayer.get(playerId);
        if (active != null && active.sessionId().equals(sessionId)) {
            activeByPlayer.remove(playerId);
            remember(active, active.expired(now) ? TerminalReason.EXPIRED : TerminalReason.CANCELLED, now);
            cleanup(now);
            return active.expired(now)
                    ? DyeApplicationResultCode.SESSION_EXPIRED : DyeApplicationResultCode.CANCELLED;
        }
        cleanup(now);
        return DyeApplicationResultCode.SESSION_MISSING;
    }

    public synchronized void invalidatePlayer(UUID playerId) {
        activeByPlayer.remove(playerId);
        terminalBySession.values().removeIf(session -> session.playerId().equals(playerId));
    }

    public synchronized void clear() {
        activeByPlayer.clear();
        terminalBySession.clear();
    }

    public synchronized int activeCount() {
        cleanup(clock.getAsLong());
        return activeByPlayer.size();
    }

    public synchronized Optional<DyePreviewSession> activeFor(UUID playerId) {
        cleanup(clock.getAsLong());
        return Optional.ofNullable(activeByPlayer.get(playerId));
    }

    public long lifetimeMillis() {
        return lifetimeMillis;
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<UUID, DyePreviewSession>> sessions = activeByPlayer.entrySet().iterator();
        while (sessions.hasNext()) {
            DyePreviewSession session = sessions.next().getValue();
            if (session.expired(now)) {
                sessions.remove();
                remember(session, TerminalReason.EXPIRED, now);
            }
        }
        terminalBySession.values().removeIf(terminal -> terminal.removeAtMillis() <= now);
    }

    private void remember(DyePreviewSession session, TerminalReason reason, long now) {
        terminalBySession.put(session.sessionId(),
                new TerminalSession(session.playerId(), reason, now + TOMBSTONE_LIFETIME_MILLIS));
    }

    private enum TerminalReason { CONSUMED, CANCELLED, EXPIRED, REPLACED }

    private record TerminalSession(UUID playerId, TerminalReason reason, long removeAtMillis) {
    }

    public record SessionClaim(Optional<DyePreviewSession> session, DyeApplicationResultCode result) {
        public SessionClaim {
            session = Objects.requireNonNull(session, "session");
            Objects.requireNonNull(result, "result");
        }

        static SessionClaim success(DyePreviewSession session) {
            return new SessionClaim(Optional.of(session), DyeApplicationResultCode.SUCCESS);
        }

        static SessionClaim failure(DyeApplicationResultCode result) {
            return new SessionClaim(Optional.empty(), result);
        }
    }
}

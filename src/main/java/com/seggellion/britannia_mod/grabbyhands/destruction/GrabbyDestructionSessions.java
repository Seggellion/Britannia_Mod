package com.seggellion.britannia_mod.grabbyhands.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The outstanding destruction confirmations, one per player.
 *
 * <p>Expiry is lazy and lifecycle invalidation is event-driven, exactly as
 * {@code dye.preview.DyePreviewRuntime} and {@code DyePreviewLifecycle} do it. Nothing here ticks:
 * a confirmation nobody answers simply stops being valid the next time it is looked at.
 *
 * <p>One session per player is deliberate. A second axe interaction replaces the first, so a player
 * can never accumulate a stack of pending questions and answer the wrong one.
 */
public final class GrabbyDestructionSessions {
    /** Long enough to read the question, short enough that walking away invalidates it. */
    public static final long TTL_MILLIS = 30_000L;

    private static final Map<UUID, GrabbyDestructionSession> BY_PLAYER = new ConcurrentHashMap<>();

    private GrabbyDestructionSessions() {
    }

    /** Issues a session, replacing any the player already had. */
    public static GrabbyDestructionSession issue(
            UUID playerId, BlockPos position, BlockState expectedState, ItemStack expectedTool, long nowMillis) {
        GrabbyDestructionSession session = new GrabbyDestructionSession(
                UUID.randomUUID(), playerId, position, expectedState, expectedTool,
                nowMillis, nowMillis + TTL_MILLIS);
        BY_PLAYER.put(playerId, session);
        return session;
    }

    /**
     * Consumes the session a confirmation refers to.
     *
     * <p>Single use: whether or not the destruction then succeeds, the same id can never be answered
     * twice. That is what makes a replayed confirmation packet harmless.
     *
     * @return the session, or empty if it does not exist, belongs to somebody else, or has expired
     */
    public static Optional<GrabbyDestructionSession> consume(UUID playerId, UUID sessionId, long nowMillis) {
        if (playerId == null || sessionId == null) {
            return Optional.empty();
        }
        GrabbyDestructionSession session = BY_PLAYER.get(playerId);
        if (session == null || !session.sessionId().equals(sessionId) || !session.belongsTo(playerId)) {
            return Optional.empty();
        }
        BY_PLAYER.remove(playerId);
        return session.expired(nowMillis) ? Optional.empty() : Optional.of(session);
    }

    /** Drops a player's outstanding question. Used on logout, dimension change and death. */
    public static void invalidate(UUID playerId) {
        if (playerId != null) {
            BY_PLAYER.remove(playerId);
        }
    }

    public static void clear() {
        BY_PLAYER.clear();
    }

    static int outstanding() {
        return BY_PLAYER.size();
    }
}

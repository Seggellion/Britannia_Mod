package com.seggellion.britannia_mod.client.house;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.player.Player;

/**
 * Which way each player has turned the house ghost they are holding a deed for.
 *
 * <h2>One writer, and it is the swing</h2>
 *
 * <p>{@link #rotateClockwise(UUID)} is the only thing in the codebase that changes a rotation, and
 * {@code AbstractHouseDeedItem.onEntitySwing} is its only caller. That is the whole rotation
 * control: a left-click swings the arm, the swing turns the ghost. Right-click places, and must not
 * touch this — it did, for one release, because the deed's {@code use()} returned
 * {@code InteractionResult.SUCCESS}, and {@code Minecraft.startUseItem} swings the arm for any
 * result that both consumes the action and asks for a swing. {@code HouseRotationControlTest} holds
 * both halves of that still: the vanilla contract, and the fact that this class has exactly one
 * caller.
 *
 * <h2>Why the UUID overloads exist</h2>
 *
 * <p>The state machine is four values and a wrap, and it decides what a player's house is going to
 * look like. Keyed by {@link UUID} it can be driven directly by a plain unit test; keyed only by
 * {@link Player} it could not be tested at all without a live client. The {@code Player} overloads
 * simply delegate, so there is still one implementation.
 */
public class HouseRotationData {
    /** The four rotations a structure template can actually be placed at. */
    public static final int STEP_DEGREES = 90;
    public static final int FULL_TURN_DEGREES = 360;

    // Concurrent because the integrated server and the client render thread both read this map.
    private static final Map<UUID, Integer> playerRotationMap = new ConcurrentHashMap<>();

    public static int getRotation(Player player) {
        return getRotation(player.getUUID());
    }

    public static void rotateClockwise(Player player) {
        rotateClockwise(player.getUUID());
    }

    public static void clear(Player player) {
        clear(player.getUUID());
    }

    /** The current rotation in degrees: 0, 90, 180 or 270. Zero for a player who has not turned it. */
    public static int getRotation(UUID playerId) {
        return playerRotationMap.getOrDefault(playerId, 0);
    }

    /** Advances one quarter turn, wrapping 270 back to 0. */
    public static void rotateClockwise(UUID playerId) {
        playerRotationMap.put(playerId, nextRotation(getRotation(playerId)));
    }

    public static void clear(UUID playerId) {
        playerRotationMap.remove(playerId);
    }

    /**
     * The state machine itself, as a pure function.
     *
     * <p>Separate so the four-step cycle is assertable without a player, a level or a client.
     */
    public static int nextRotation(int currentDegrees) {
        return (currentDegrees + STEP_DEGREES) % FULL_TURN_DEGREES;
    }

    /** Test seam: forget every player's rotation. */
    public static void forgetAll() {
        playerRotationMap.clear();
    }
}

package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient per-flower gate that makes near-simultaneous player mutations single-winner.
 * Natural growth and persistence do not use this gate.
 */
public final class FlowerInteractionTransactionGate {
    public static final long CONTENTION_WINDOW_TICKS = 10L;
    private static final Map<ReplacementKey, Long> REPLACEMENT_GATES = new ConcurrentHashMap<>();

    private long nextAllowedGameTime = Long.MIN_VALUE;

    public boolean tryCommit(long gameTime) {
        if (gameTime < nextAllowedGameTime) {
            return false;
        }
        nextAllowedGameTime = gameTime + CONTENTION_WINDOW_TICKS;
        return true;
    }

    public static void markReplacement(ServerLevel level, BlockPos pos) {
        markReplacement(level.dimension().location(), pos.asLong(), level.getGameTime());
    }

    public static boolean suppressReplacementInteraction(ServerLevel level, BlockPos pos) {
        return suppressReplacementInteraction(level.dimension().location(), pos.asLong(), level.getGameTime());
    }

    static void markReplacement(ResourceLocation dimension, long pos, long gameTime) {
        REPLACEMENT_GATES.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        REPLACEMENT_GATES.put(new ReplacementKey(dimension, pos), gameTime + CONTENTION_WINDOW_TICKS);
    }

    static boolean suppressReplacementInteraction(ResourceLocation dimension, long pos, long gameTime) {
        ReplacementKey key = new ReplacementKey(dimension, pos);
        Long nextAllowed = REPLACEMENT_GATES.get(key);
        if (nextAllowed == null) {
            return false;
        }
        if (gameTime < nextAllowed) {
            return true;
        }
        REPLACEMENT_GATES.remove(key, nextAllowed);
        return false;
    }

    private record ReplacementKey(ResourceLocation dimension, long pos) {
    }
}

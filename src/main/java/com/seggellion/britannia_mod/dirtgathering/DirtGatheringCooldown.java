package com.seggellion.britannia_mod.dirtgathering;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Persistent, player-scoped timing for the inexhaustible dirt-gather gesture. */
public final class DirtGatheringCooldown {
    public static final int COOLDOWN_TICKS = 20 * 60;
    public static final int FEEDBACK_THROTTLE_TICKS = 40;
    static final String NEXT_ALLOWED_TICK_TAG = "britannia_mod:dirt_gather_next_tick";
    static final String LAST_FEEDBACK_TICK_TAG = "britannia_mod:dirt_gather_feedback_tick";

    private DirtGatheringCooldown() {
    }

    public static Status inspect(ServerPlayer player) {
        long now = now(player);
        CompoundTag data = player.getPersistentData();
        Status status = inspect(now, data.getLong(NEXT_ALLOWED_TICK_TAG));
        if (status.rewound()) {
            data.remove(NEXT_ALLOWED_TICK_TAG);
        }
        return status;
    }

    static Status inspect(long now, long nextAllowed) {
        if (nextAllowed <= now) {
            return new Status(true, 0L, false);
        }
        long remaining = nextAllowed - now;
        if (remaining > COOLDOWN_TICKS) {
            return new Status(true, 0L, true);
        }
        return new Status(false, remaining, false);
    }

    public static void claim(ServerPlayer player) {
        player.getPersistentData().putLong(NEXT_ALLOWED_TICK_TAG, nextAllowedTick(now(player)));
    }

    static long nextAllowedTick(long now) {
        return now + COOLDOWN_TICKS;
    }

    public static boolean claimCooldownFeedback(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        long now = now(player);
        boolean shouldSend = !data.contains(LAST_FEEDBACK_TICK_TAG)
                || shouldSendFeedback(now, data.getLong(LAST_FEEDBACK_TICK_TAG));
        if (shouldSend) {
            data.putLong(LAST_FEEDBACK_TICK_TAG, now);
        }
        return shouldSend;
    }

    static boolean shouldSendFeedback(long now, long previousTick) {
        return now < previousTick || now - previousTick >= FEEDBACK_THROTTLE_TICKS;
    }

    /** Explicitly preserves the direct persistent-data keys across death and End-return clones. */
    public static void copyToClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();
        copyLong(oldData, newData, NEXT_ALLOWED_TICK_TAG);
        copyLong(oldData, newData, LAST_FEEDBACK_TICK_TAG);
    }

    private static void copyLong(CompoundTag source, CompoundTag destination, String key) {
        if (source.contains(key)) {
            destination.putLong(key, source.getLong(key));
        }
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public record Status(boolean ready, long remainingTicks, boolean rewound) {
        public long remainingSeconds() {
            return (remainingTicks + 19L) / 20L;
        }
    }
}

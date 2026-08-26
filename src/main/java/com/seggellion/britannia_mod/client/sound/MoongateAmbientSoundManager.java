package com.seggellion.britannia_mod.client.sound;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Maintains at most one spatial ambient loop for each loaded permanent Moongate. */
public final class MoongateAmbientSoundManager {
    public static final double AUDIBLE_RANGE_BLOCKS = 10.0D;
    static final double CLEANUP_RANGE_BLOCKS = 12.0D;
    private static final double CLEANUP_RANGE_SQUARED = CLEANUP_RANGE_BLOCKS * CLEANUP_RANGE_BLOCKS;
    private static final Map<SoundKey, MoongateAmbientSound> ACTIVE_SOUNDS = new HashMap<>();

    private MoongateAmbientSoundManager() {
    }

    /** Called by the client-side Moongate block-entity ticker. */
    public static void tick(Level level, BlockPos pos) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }

        SoundKey key = new SoundKey(clientLevel, pos.immutable());
        MoongateAmbientSound active = ACTIVE_SOUNDS.get(key);
        if (!shouldKeepPlaying(clientLevel, pos)) {
            stop(key, active);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || active.isStopped() || !minecraft.getSoundManager().isActive(active)) {
            stop(key, active);
            MoongateAmbientSound replacement = new MoongateAmbientSound(clientLevel, pos);
            ACTIVE_SOUNDS.put(key, replacement);
            minecraft.getSoundManager().play(replacement);
        }
    }

    static boolean shouldKeepPlaying(ClientLevel level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null) {
            return false;
        }
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || !level.getBlockState(pos).is(BlockRegistry.MOONGATE_BLOCK.get())) {
            return false;
        }
        return minecraft.player.distanceToSqr(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= CLEANUP_RANGE_SQUARED;
    }

    static void onStopped(
            ClientLevel level, BlockPos pos, MoongateAmbientSound sound) {
        ACTIVE_SOUNDS.remove(new SoundKey(level, pos), sound);
    }

    public static void stopAll() {
        ACTIVE_SOUNDS.values().forEach(MoongateAmbientSound::finish);
        ACTIVE_SOUNDS.clear();
    }

    private static void stop(SoundKey key, MoongateAmbientSound sound) {
        if (sound != null) {
            sound.finish();
            ACTIVE_SOUNDS.remove(key, sound);
        }
    }

    private record SoundKey(ClientLevel level, BlockPos pos) {
    }
}

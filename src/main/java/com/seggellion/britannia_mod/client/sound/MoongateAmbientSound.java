package com.seggellion.britannia_mod.client.sound;

import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/** One positional ambient loop owned by one loaded permanent Moongate. */
final class MoongateAmbientSound extends AbstractTickableSoundInstance {
    private final ClientLevel level;
    private final BlockPos moongatePos;

    MoongateAmbientSound(ClientLevel level, BlockPos moongatePos) {
        super(ModSounds.PERMANENT_MOONGATE_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.moongatePos = moongatePos.immutable();
        this.x = moongatePos.getX() + 0.5D;
        this.y = moongatePos.getY() + 0.5D;
        this.z = moongatePos.getZ() + 0.5D;
        this.volume = 0.5F;
        this.pitch = 1.0F;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
    }

    @Override
    public void tick() {
        if (!MoongateAmbientSoundManager.shouldKeepPlaying(level, moongatePos)) {
            finish();
            MoongateAmbientSoundManager.onStopped(level, moongatePos, this);
        }
    }

    void finish() {
        stop();
    }
}

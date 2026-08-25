package com.seggellion.britannia_mod.mixin;

import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces vanilla's predicted throw cue with one authoritative cue after the hook is spawned. */
@Mixin(FishingRodItem.class)
public abstract class FishingRodCastSoundMixin {
    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound("
                            + "Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;"
                            + "Lnet/minecraft/sounds/SoundSource;FF)V"
            ),
            remap = false
    )
    private static void britannia$suppressVanillaCastSound(
            Level level,
            Player excludedPlayer,
            double x,
            double y,
            double z,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
    ) {
        if (sound != SoundEvents.FISHING_BOBBER_THROW) {
            level.playSound(excludedPlayer, x, y, z, sound, source, volume, pitch);
        }
    }

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity("
                            + "Lnet/minecraft/world/entity/Entity;)Z"
            ),
            remap = false
    )
    private static boolean britannia$playCastSoundAfterHookSpawn(Level level, Entity entity) {
        boolean added = level.addFreshEntity(entity);
        if (added && level instanceof ServerLevel serverLevel && entity instanceof FishingHook hook) {
            Entity owner = hook.getOwner();
            Entity source = owner != null ? owner : hook;
            serverLevel.playSound(null, source.getX(), source.getY(), source.getZ(),
                    ModSounds.FISHING_CAST.get(), SoundSource.NEUTRAL, 0.7F, 1.0F);
        }
        return added;
    }
}

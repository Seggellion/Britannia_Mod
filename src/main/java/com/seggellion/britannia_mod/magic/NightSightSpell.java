package com.seggellion.britannia_mod.magic;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.effects.SpellEffectHandler;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class NightSightSpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected int getManaCost() {
        return 4;
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(ItemRegistry.SPIDERS_SILK.get()),
            new ItemStack(ItemRegistry.SULPHUROUS_ASH.get())
        };
    }

    @Override
    protected int getCooldownTime() {
        return 1000; // 1-second cooldown
    }

    @Override
    protected void applyEffect(ServerPlayer caster, LivingEntity target) {
        if (caster == null) {
            LOGGER.warn("Caster is null, cannot proceed with applyEffect.");
            return;
        }

        // Determine if the target is the caster themselves or another entity
        if (target == caster) {
            LOGGER.info("applySelfEffect caster: {}", caster.getName().getString());
            applySelfEffect(caster);
        } else {
            LOGGER.info("applyTargetEffect caster: {}, target: {}", caster.getName().getString(), target.getName().getString());
            applyTargetEffect(caster, target);
        }
    }

    @Override
    protected void applySelfEffect(ServerPlayer caster) {
        if (caster == null || caster.getServer() == null) {
            return; // If caster is null, we shouldn't proceed
        }

        // Freeze the player during casting, apply night vision after delay, then unfreeze
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Apply night vision effect to the caster
            caster.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 24000)); // 20 minutes of Night Vision

            // Play night sight spell sound
            ServerLevel level = caster.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent nightSightSound = ModSounds.NIGHT_SIGHT_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the caster
                    nightSightSound, // Sound event for night sight spell
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }

            LOGGER.info("Night vision applied to caster: {}", caster.getName().getString());
        });
    }

    @Override
    protected void applyTargetEffect(ServerPlayer caster, LivingEntity target) {
        if (target == null || target.getServer() == null) {
            LOGGER.warn("Target or target's server is null. Cannot apply target effect.");
            return; // Ensure that the target is valid
        }

        // Freeze the caster during casting, apply night vision to target after delay, then unfreeze
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Apply night vision effect to the target
            target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 24000)); // 20 minutes of Night Vision

            // Play night sight spell sound
            ServerLevel level = target.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent nightSightSound = ModSounds.NIGHT_SIGHT_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    target.getX(), target.getY(), target.getZ(), // Location of the target
                    nightSightSound, // Sound event for night sight spell
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }

            LOGGER.info("Night vision applied to target: {} by caster: {}", target.getName().getString(), caster.getName().getString());
        });
    }

    // Check if the item is the spell item for Night Sight
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == ItemRegistry.NIGHT_SIGHT_ITEM.get();
    }
}
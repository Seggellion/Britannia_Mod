package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.effects.SpellEffectHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ClumsySpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected int getManaCost() {
        return 4;
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(BritanniaMod.NIGHTSHADE.get()),
            new ItemStack(BritanniaMod.BLOOD_MOSS.get())
        };
    }

    @Override
    protected int getCooldownTime() {
        return 1000;
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

        // Freeze the player during casting, then apply clumsy effect after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Apply clumsy effect to the caster
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));

            // Play clumsy spell sound
            ServerLevel level = caster.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent clumsySound = ModSounds.CLUMSY_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the player
                    clumsySound, // Sound event for clumsy spell
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }
        });
    }

    @Override
    protected void applyTargetEffect(ServerPlayer caster, LivingEntity target) {
        if (target == null || target.getServer() == null) {
            LOGGER.warn("Target or target's server is null. Cannot apply target effect.");
            return; // Ensure that the target is valid
        }

        // Freeze the caster during casting, then apply clumsy effect to target after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Apply clumsy effect to the target
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));

            // Play clumsy spell sound
            ServerLevel level = target.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent clumsySound = ModSounds.CLUMSY_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    target.getX(), target.getY(), target.getZ(), // Location of the target
                    clumsySound, // Sound event for clumsy spell
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }
        });
    }

    // Check if the item is the spell item for Clumsy
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.CLUMSY_ITEM.get();
    }
}
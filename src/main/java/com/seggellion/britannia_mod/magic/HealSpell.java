package com.seggellion.britannia_mod.magic;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.effects.SpellEffectHandler;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class HealSpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected int getManaCost() {
        return 4;  // Adjust mana cost as per the heal spell requirement
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(ItemRegistry.GARLIC.get()),
            new ItemStack(ItemRegistry.GINSENG.get()),
            new ItemStack(ItemRegistry.SPIDERS_SILK.get())
        };
    }

    @Override
    protected int getCooldownTime() {
        return 1000; // 1-second cooldown in milliseconds
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

        // Freeze the player during casting, heal after delay, then unfreeze
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            caster.heal(4.0F);  // Heal the player

            // Play heal sound
            ServerLevel level = caster.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent healSound = ModSounds.HEAL_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the player
                    healSound, // Sound event for heal spell
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

        // Number of ticks to cast (1 second = 20 ticks)
        int castTime = (caster != null) ? 20 : 0;
        LOGGER.info("Freezing caster {} while casting on target {}", caster != null ? caster.getName().getString() : "Unknown", target.getName().getString());
        
        // Freeze the caster during casting, apply effect to target after delay, then unfreeze
        if (caster != null) {
            SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
                healTarget(target);
            });
        } else {
            healTarget(target);
        }
    }

    private void healTarget(LivingEntity target) {
        target.heal(4.0F);  // Heal the target

        // Play heal sound
        ServerLevel level = target.getServer().overworld();
        if (level != null && !level.isClientSide) {
            SoundEvent healSound = ModSounds.HEAL_SPELL_CAST.get();
            level.playSound(
                null, // null to play for all nearby players
                target.getX(), target.getY(), target.getZ(), // Location of the target
                healSound, // Sound event for heal spell
                SoundSource.PLAYERS, // Sound category
                1.0F, // Volume
                1.0F  // Pitch
            );
        }
    }

    // Check if the item is the spell item for Heal
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == ItemRegistry.HEAL_ITEM.get();
    }
}
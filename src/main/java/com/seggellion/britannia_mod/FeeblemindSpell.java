package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.effects.SpellEffectHandler;
import com.seggellion.britannia_mod.magic.ManaHandler;  // Make sure to import ManaHandler
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class FeeblemindSpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected int getManaCost() {
        return 4;
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(BritanniaMod.GARLIC.get()),
            new ItemStack(BritanniaMod.NIGHTSHADE.get())
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

        // Freeze the player during casting, then apply feeblemind effect after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            int currentMana = ManaHandler.getMana(caster);
            if (currentMana > 4) {
                ManaHandler.reduceMana(caster, 4); // Reduce mana by 4 points
                LOGGER.info("Reduced mana by 4 points from caster: {}", caster.getName().getString());
            } else {
                LOGGER.info("Caster has insufficient mana to be reduced.");
                caster.sendSystemMessage(Component.literal("You do not have enough mana to cast this spell!"));
            }

            // Play feeblemind spell sound
            ServerLevel level = caster.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent feeblemindSound = ModSounds.FEEBLEMIND_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the player
                    feeblemindSound, // Sound event for feeblemind spell
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
            LOGGER.warn("Target or target's server is null. Cannot proceed with applyTargetEffect.");
            return; // Ensure that the target is valid
        }

        // Freeze the caster during casting, then apply feeblemind effect to target after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            if (target instanceof ServerPlayer targetPlayer) {
                int currentMana = ManaHandler.getMana(targetPlayer);
                if (currentMana > 4) {
                    ManaHandler.reduceMana(targetPlayer, 4); // Reduce mana by 4 points
                    LOGGER.info("Reduced mana by 4 points from target: {} by caster: {}", targetPlayer.getName().getString(), caster.getName().getString());
                } else {
                    LOGGER.info("Target has insufficient mana to be reduced.");
                    caster.sendSystemMessage(Component.literal("The target does not have enough mana to cast this spell!"));
                }

                // Play feeblemind spell sound
                ServerLevel level = target.getServer().overworld();
                if (level != null && !level.isClientSide) {
                    SoundEvent feeblemindSound = ModSounds.FEEBLEMIND_SPELL_CAST.get();
                    level.playSound(
                        null, // null to play for all nearby players
                        target.getX(), target.getY(), target.getZ(), // Location of the target
                        feeblemindSound, // Sound event for feeblemind spell
                        SoundSource.PLAYERS, // Sound category
                        1.0F, // Volume
                        1.0F  // Pitch
                    );
                }
            } else {
                LOGGER.info("Target is not a player, cannot apply feeblemind effect.");
            }
        });
    }

    // Check if the item is the spell item for Feeblemind
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.FEEBLEMIND_ITEM.get();
    }
}

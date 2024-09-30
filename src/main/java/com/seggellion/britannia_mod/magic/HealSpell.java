package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class HealSpell extends Spell {

    private static final Map<Player, Long> cooldowns = new HashMap<>();  // Cooldown map
    private static final long COOLDOWN_TIME_MS = 1000; // 1-second cooldown in milliseconds

    @Override
    protected int getManaCost() {
        return 4;  // Adjust mana cost as per the heal spell requirement
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
                new ItemStack(BritanniaMod.GARLIC.get()),
                new ItemStack(BritanniaMod.GINSENG.get()),
                new ItemStack(BritanniaMod.SPIDERS_SILK.get())
        };
    }

    @Override
    protected void applyEffect(Player caster) {
        if (isOnCooldown(caster)) {
            caster.sendSystemMessage(Component.literal("Spell is on cooldown!"));
            return;
        }

        freezePlayer((ServerPlayer) caster, true);  // Freeze player
        caster.getServer().execute(() -> {
            try {
                // Wait for 1.5 seconds to simulate casting time
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Heal the caster after the delay
            caster.heal(4.0F);  // Adjust healing amount as per your requirement

            // Play heal sound
            Level level = caster.level();
            if (!level.isClientSide) {
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

            freezePlayer((ServerPlayer) caster, false);  // Unfreeze player
            startCooldown(caster);  // Start the cooldown after casting the spell
        });

    }

    // Helper method to check if the spell is on cooldown
    private boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player)) return false;
        long lastCastTime = cooldowns.get(player);
        return (System.currentTimeMillis() - lastCastTime) < COOLDOWN_TIME_MS;
    }

    // Helper method to start the cooldown
    private void startCooldown(Player player) {
        cooldowns.put(player, System.currentTimeMillis());
    }

    // Helper method to freeze or unfreeze the player
    private void freezePlayer(ServerPlayer player, boolean freeze) {
        if (freeze) {
            player.sendSystemMessage(Component.literal("Freezing"));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false)); // Extreme slowness for 2 seconds
            player.setNoGravity(true);  // Disable gravity to prevent jumping
            player.setInvulnerable(true);  // Make player invulnerable while frozen
            player.setDeltaMovement(0, 0, 0);  // Stop all movement

            // Continuously reset velocity to ensure no movement occurs
            player.getServer().execute(() -> {
                for (int i = 0; i < 30; i++) {
                    player.setDeltaMovement(0, 0, 0);
                    try {
                        Thread.sleep(50);  // Freeze for the casting duration
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);  // Remove slowness
            player.setNoGravity(false);  // Re-enable gravity
            player.setInvulnerable(false);  // Re-enable damage
        }
        player.onUpdateAbilities();  // Refresh player abilities
    }

    // Check if the item is the spell item for Heal
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.HEAL_ITEM.get();
    }
}

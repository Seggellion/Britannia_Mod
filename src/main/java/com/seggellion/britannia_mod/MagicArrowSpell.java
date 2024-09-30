package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;  // Make sure to import Player
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class MagicArrowSpell extends Spell {

    private static final Map<Player, Long> cooldowns = new HashMap<>();  // Cooldown map
    private static final long COOLDOWN_TIME_MS = 1000; // 1-second cooldown in milliseconds

    @Override
    protected int getManaCost() {
        return 4;  // Mana cost for Magic Arrow
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{
            new ItemStack(BritanniaMod.SULPHUROUS_ASH.get())  // Magic Arrow only requires sulphurous ash
        };
    }

    @Override
    protected void applyEffect(Player caster) {
        if (isOnCooldown(caster)) {
            caster.sendSystemMessage(Component.literal("Spell is on cooldown!"));
            return;
        }

        freezePlayer((ServerPlayer) caster, true);  // Freeze player for casting time
        caster.getServer().execute(() -> {
            try {
                // Wait for 1.5 seconds to simulate casting time
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Shoot the flaming arrow after the casting delay
            shootMagicArrow((ServerPlayer) caster);

            // Play magic arrow sound
            Level level = caster.level();
            if (!level.isClientSide) {
                SoundEvent magicArrowSound = ModSounds.MAGIC_ARROW_SPELL_CAST.get();  // You can replace with a magic arrow sound if you have one
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the player
                    magicArrowSound, // Sound event for magic arrow
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }

            freezePlayer((ServerPlayer) caster, false);  // Unfreeze player
            startCooldown(caster);  // Start the cooldown after casting the spell
        });
    }

    // Method to shoot a flaming arrow in the direction of the crosshairs
    private void shootMagicArrow(ServerPlayer caster) {
        Level world = caster.level();

        // Create a new arrow entity
        Arrow arrow = new Arrow(EntityType.ARROW, world);
        arrow.setOwner(caster);  // Set caster as the shooter
        arrow.setBaseDamage(5.0D);  // Set arrow damage
        arrow.setCritArrow(true);  // Make it a critical arrow
        arrow.setRemainingFireTicks(100);  // Set arrow on fire for 5 seconds (100 ticks)
        arrow.pickup = Pickup.DISALLOWED;  // Disallow pickup of the arrow

        // Calculate direction based on player's look vector
        Vec3 lookVector = caster.getLookAngle();
        arrow.setPos(caster.getX() + lookVector.x * 2, caster.getEyeY() - 0.1D, caster.getZ() + lookVector.z * 2);
        arrow.shoot(lookVector.x, lookVector.y, lookVector.z, 2.5F, 0);  // Speed and accuracy of the arrow

        world.addFreshEntity(arrow);  // Add the arrow to the world
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

    // Helper method to freeze or unfreeze the player (same as HealSpell)
    private void freezePlayer(ServerPlayer player, boolean freeze) {
        if (freeze) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false)); // Extreme slowness for 1.5 seconds
            player.setNoGravity(true);  // Disable gravity to prevent jumping
            player.setInvulnerable(true);  // Make player invulnerable while frozen
            player.setDeltaMovement(0, 0, 0);  // Stop all movement
        } else {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);  // Remove slowness
            player.setNoGravity(false);  // Re-enable gravity
            player.setInvulnerable(false);  // Re-enable damage
        }
        player.onUpdateAbilities();  // Refresh player abilities
    }

    // Check if the item is the spell item for Magic Arrow
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.MAGIC_ARROW_ITEM.get();  // Change to the correct item for Magic Arrow
    }
}

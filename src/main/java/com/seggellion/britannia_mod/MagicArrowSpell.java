package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.effects.SpellEffectHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class MagicArrowSpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

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

        // Freeze the player during casting, then shoot magic arrow after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Shoot magic arrow at a target direction
            shootMagicArrow(caster);

            // Play magic arrow sound
            ServerLevel level = caster.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent magicArrowSound = ModSounds.MAGIC_ARROW_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    caster.getX(), caster.getY(), caster.getZ(), // Location of the player
                    magicArrowSound, // Sound event for magic arrow
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }
        });
    }

    @Override
    protected void applyTargetEffect(ServerPlayer caster, LivingEntity target) {
        if (caster == null || target == null || target.getServer() == null) {
            LOGGER.warn("Caster or target is null, cannot proceed with applyTargetEffect.");
            return;
        }

        // Freeze the caster during casting, then shoot magic arrow towards target after delay
        int castTime = 20; // Number of ticks to cast (1 second = 20 ticks)
        SpellEffectHandler.freezePlayerDuringCast(caster, castTime, () -> {
            // Shoot magic arrow towards the target
            shootMagicArrowTowardsTarget(caster, target);

            // Play magic arrow sound
            ServerLevel level = target.getServer().overworld();
            if (level != null && !level.isClientSide) {
                SoundEvent magicArrowSound = ModSounds.MAGIC_ARROW_SPELL_CAST.get();
                level.playSound(
                    null, // null to play for all nearby players
                    target.getX(), target.getY(), target.getZ(), // Location of the target
                    magicArrowSound, // Sound event for magic arrow
                    SoundSource.PLAYERS, // Sound category
                    1.0F, // Volume
                    1.0F  // Pitch
                );
            }
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
        arrow.setPos(
            caster.getX() + lookVector.x * 2,
            caster.getEyeY() - 0.1D,
            caster.getZ() + lookVector.z * 2
        );
        arrow.shoot(lookVector.x, lookVector.y, lookVector.z, 2.5F, 0);  // Speed and accuracy of the arrow

        world.addFreshEntity(arrow);  // Add the arrow to the world
    }

    // Method to shoot a magic arrow towards a specific target
    private void shootMagicArrowTowardsTarget(ServerPlayer caster, LivingEntity target) {
        Level world = caster.level();

        // Create a new arrow entity
        Arrow arrow = new Arrow(EntityType.ARROW, world);
        arrow.setOwner(caster);  // Set caster as the shooter
        arrow.setBaseDamage(5.0D);  // Set arrow damage
        arrow.setCritArrow(true);  // Make it a critical arrow
        arrow.setRemainingFireTicks(100);  // Set arrow on fire for 5 seconds (100 ticks)
        arrow.pickup = Pickup.DISALLOWED;  // Disallow pickup of the arrow

        // Calculate direction based on the position of the target
        Vec3 direction = new Vec3(
            target.getX() - caster.getX(),
            target.getEyeY() - caster.getEyeY(),
            target.getZ() - caster.getZ()
        ).normalize();
        arrow.setPos(
            caster.getX() + direction.x * 2,
            caster.getEyeY() - 0.1D,
            caster.getZ() + direction.z * 2
        );
        arrow.shoot(direction.x, direction.y, direction.z, 2.5F, 0);  // Speed and accuracy of the arrow

        world.addFreshEntity(arrow);  // Add the arrow to the world
    }

    // Check if the item is the spell item for Magic Arrow
    public boolean isSpellItem(ItemStack itemStack) {
        return itemStack.getItem() == BritanniaMod.MAGIC_ARROW_ITEM.get();
    }
}
package com.seggellion.britannia_mod.magic;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class MagicArrowSpell extends Spell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected int getManaCost() {
        return 4;  // Mana cost for Magic Arrow
    }

    @Override
    protected ItemStack[] getReagents() {
        return new ItemStack[]{new ItemStack(ItemRegistry.SULPHUROUS_ASH.get())};  // Reagents required
    }

    @Override
    protected int getCooldownTime() {
        return 1000; // Cooldown in milliseconds
    }

    // Override to provide the spell effect for ServerPlayer
    @Override
    public void applyEffect(ServerPlayer caster, LivingEntity target) {
        LOGGER.info("Applying spell effect for ServerPlayer: {}", caster.getName().getString());
        if (target == caster) {
            applySelfEffect(caster);  // Apply self-effect if the target is the caster
        } else {
            applyTargetEffect(caster, target);  // Apply target effect if casting on another entity
        }
    }

    // Implement the applySelfEffect for ServerPlayer from Spell class
    @Override
    protected void applySelfEffect(ServerPlayer caster) {
        LOGGER.info("Applying self-effect for ServerPlayer: {}", caster.getName().getString());
        shootMagicArrow(caster);
        playMagicArrowSound(caster);
    }

    // Implement the applyTargetEffect for ServerPlayer
    @Override
    protected void applyTargetEffect(ServerPlayer caster, LivingEntity target) {
        LOGGER.info("Applying target effect for ServerPlayer: {}", caster.getName().getString());
        shootMagicArrowTowardsTarget(caster, target);
        playMagicArrowSound(target);
    }

    // Overloaded method for LivingEntity casters (e.g., DaemonEntity)
    public void applyEffect(LivingEntity caster, LivingEntity target) {
        LOGGER.info("Applying spell effect for LivingEntity: {}", caster.getName().getString());
        if (caster instanceof ServerPlayer) {
            applyEffect((ServerPlayer) caster, target);  // Delegate to original method for ServerPlayer
        } else {
            if (target == caster) {
                applySelfEffect(caster);  // Generic self-effect
            } else {
                applyTargetEffect(caster, target);  // Generic target-effect
            }
        }
    }

    // Generic self-effect for LivingEntity casters
    private void applySelfEffect(LivingEntity caster) {
        if (caster == null) return;
        LOGGER.info("Applying self-effect to: {}", caster.getName().getString());
        shootMagicArrow(caster);
        playMagicArrowSound(caster);
    }

    // Generic target-effect for LivingEntity casters
    private void applyTargetEffect(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null) return;
        LOGGER.info("Applying target-effect from: {} to target: {}", caster.getName().getString(), target.getName().getString());
        shootMagicArrowTowardsTarget(caster, target);
        playMagicArrowSound(target);
    }

    // Play the magic arrow sound at the entity's location
    private void playMagicArrowSound(LivingEntity entity) {
        Level level = entity.getCommandSenderWorld();  // Use getCommandSenderWorld() to access the level
        if (!level.isClientSide) {
            SoundEvent magicArrowSound = ModSounds.MAGIC_ARROW_SPELL_CAST.get();  // Ensure this sound event exists
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), magicArrowSound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    // Shoots magic arrow in the caster's looking direction
    private void shootMagicArrow(LivingEntity caster) {
        Level world = caster.getCommandSenderWorld();  // Use getCommandSenderWorld() to access the level
        Arrow arrow = createMagicArrow(caster, world);
        Vec3 lookVector = caster.getLookAngle();
        arrow.setPos(caster.getX() + lookVector.x * 2, caster.getEyeY() - 0.1D, caster.getZ() + lookVector.z * 2);
        arrow.shoot(lookVector.x, lookVector.y, lookVector.z, 2.5F, 0);
        world.addFreshEntity(arrow);
    }

    // Shoots magic arrow toward a specific target
    private void shootMagicArrowTowardsTarget(LivingEntity caster, LivingEntity target) {
        Level world = caster.getCommandSenderWorld();  // Use getCommandSenderWorld() to access the level
        Arrow arrow = createMagicArrow(caster, world);
        Vec3 direction = new Vec3(target.getX() - caster.getX(), target.getEyeY() - caster.getEyeY(), target.getZ() - caster.getZ()).normalize();
        arrow.setPos(caster.getX() + direction.x * 2, caster.getEyeY() - 0.1D, caster.getZ() + direction.z * 2);
        arrow.shoot(direction.x, direction.y, direction.z, 2.5F, 0);
        world.addFreshEntity(arrow);
    }

    // Creates a magic arrow entity with custom properties
    private Arrow createMagicArrow(LivingEntity caster, Level world) {
        Arrow arrow = new Arrow(EntityType.ARROW, world);
        arrow.setOwner(caster);
        arrow.setBaseDamage(5.0D);
        arrow.setCritArrow(true);
        arrow.setRemainingFireTicks(100);
        arrow.pickup = Pickup.DISALLOWED;
        return arrow;
    }
}

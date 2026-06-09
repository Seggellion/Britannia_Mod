package com.seggellion.britannia_mod.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// 1. Implement RangedAttackMob
public class RatmanArcherEntity extends BaseBritanniaMonster implements RangedAttackMob {

    public RatmanArcherEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "ratman_archer");
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 36.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.25D)
            .add(Attributes.ATTACK_DAMAGE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 16.0D);
            // Removed ATTACK_SPEED here as it primarily affects melee cooldowns. 
            // Ranged timing is handled by the RangedAttackGoal instead.
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        super.registerGoals(); 
        
        // 2. Replace MeleeAttackGoal with RangedAttackGoal
        // Parameters: (Mob, Speed Modifier, Attack Interval (Ticks), Attack Radius)
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 40, 15.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }

    // 3. Define the actual firing logic
@Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        
        // Retrieve the weapon in the mob's hand
        ItemStack weapon = this.getMainHandItem(); 
        
        // 1.21 CRASH FIX: Ensure the weapon is actually valid for firing arrows.
        // If they are holding nothing (ItemStack.EMPTY) or a non-bow item, provide a dummy Bow.
        if (weapon.isEmpty() || !weapon.is(Items.BOW)) {
            weapon = new ItemStack(Items.BOW);
        }
        
        // Generate the standard arrow entity
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, pullProgress, weapon);

        // Calculate trajectory towards the target
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.333333D) - arrow.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        // Shoot the arrow
        arrow.shoot(d0, d1 + d3 * 0.2D, d2, 1.6F, (float)(14 - this.level().getDifficulty().getId() * 4));

        // Play sound and spawn entity
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing → prevents XP orbs
    }
}
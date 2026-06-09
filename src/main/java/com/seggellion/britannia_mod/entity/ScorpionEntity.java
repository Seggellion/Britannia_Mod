package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class ScorpionEntity extends BaseBritanniaMonster {

    public ScorpionEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "scorpion");
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
             .add(Attributes.MAX_HEALTH, 18.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        // This pulls in FloatGoal, RandomStrollGoal, and RandomLookAroundGoal
        super.registerGoals(); 
        
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }

@Override
    public boolean doHurtTarget(Entity target) {
        // Call the super method to handle the actual base damage and knockback
        boolean attackLanded = super.doHurtTarget(target);

        // If the attack successfully connected, and the target is alive
        if (attackLanded && target instanceof LivingEntity livingTarget) {
            
            // 1/3 chance to inflict poison
            if (this.getRandom().nextInt(3) == 0) {
                
                // Add the Poison effect to the target
                // Parameters: Effect, Duration (in ticks), Amplifier (0 = Level I)
                livingTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), this);
            }
        }

        return attackLanded;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing → prevents XP orbs
    }
}
package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.PathfinderMob;

public class GorillaEntity extends BaseBritanniaAnimal {

    public GorillaEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level, "gorilla");
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
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
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing → prevents XP orbs
    }
}
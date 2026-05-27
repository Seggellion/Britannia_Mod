package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// GeckoLib imports needed for the animation override
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class SerpentSeaEntity extends BaseBritanniaMonster {

    public SerpentSeaEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "serpent_sea");
        // Give the entity the ability to steer and move fluidly underwater
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
    }

    // Override the default ground navigation with water navigation
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_SPEED, -2.0D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        // Movement Goals
        this.goalSelector.addGoal(8, new RandomSwimmingGoal(this, 1.0D, 10)); 
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        
        // Attack Goals
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));

        // Target Goals
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }

    // Animation Override
    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        // 1. Check for attacking first so it overrides movement
        if (this.swinging) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("animation.model.attack"));
            return PlayState.CONTINUE;
        } 
        // 2. Check for movement
        else if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.swim"));
            return PlayState.CONTINUE;
        } 
        // 3. Default to idle
        else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.idle"));
            return PlayState.CONTINUE;
        }
    }

    // Prevent the monster from drowning in any fluid (NeoForge 1.21 specific)
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    // Prevents water currents from pushing the monster around too easily
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing -> prevents XP orbs
    }
}
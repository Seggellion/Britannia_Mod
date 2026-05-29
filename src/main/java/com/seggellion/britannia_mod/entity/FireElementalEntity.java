package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

// GeckoLib imports
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class FireElementalEntity extends BaseBritanniaMonster {

    public FireElementalEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "elemental_fire");

        // 1. Remove hesitation for entering lava and fire
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);

        // 2. 3D movement control (works in lava due to our isInWater override below)
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 1.0F, 1.0F, true);
    }

    // --- NAVIGATION & MOVEMENT OVERRIDES ---

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    /**
     * THE LAVA SWIMMING TRICK:
     * By telling the game the entity "is in water" when it's actually in lava, 
     * we get full 3D swimming AI for free.
     */
    @Override
    public boolean isInWater() {
        return super.isInWater() || this.isInLava();
    }

    // --- ATTRIBUTES ---

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 54.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_SPEED, -2.0D);
    }

    // --- GOALS AND BEHAVIORS ---

    @Override
    protected void registerGoals() {
        // Core movement goals (replacing super.registerGoals to avoid FloatGoal conflicts)
        this.goalSelector.addGoal(7, new RandomSwimmingGoal(this, 1.0D, 10));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        // Attack Goals
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));

        // Target Goals
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }

    // --- ANIMATION OVERRIDE ---

    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (this.swinging) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("animation.model.attack"));
            return PlayState.CONTINUE;
        } 
        else if (state.isMoving()) {
            // Because of our override above, `isInWater()` now returns true in lava too!
            if (this.isInWater()) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.swim"));
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.walk"));
            }
            return PlayState.CONTINUE;
        } 
        else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.idle"));
            return PlayState.CONTINUE;
        }
    }

    // --- FIRE IMMUNITY & FLUID OVERRIDES ---

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isOnFire() {
        // Prevents the visual fire overlay from rendering on the entity
        return false;
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        // Prevents lava/water currents from ruining pathfinding
        return false;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing → prevents XP orbs
    }
}
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

// GeckoLib imports needed for the animation override
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class WaterElementalEntity extends BaseBritanniaMonster {

    public WaterElementalEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "elemental_water");
        
        // 1. Remove hesitation for entering, leaving, and existing in water
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F); 
        
        // 2. Fix the speed modifiers! Parameters: (entity, maxTurnX, maxTurnY, speedInWater, speedInAir, applyGravity)
        // Bumped water speed and air speed modifiers up to 1.0F so they don't bottleneck your Attribute speed.
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 1.0F, 1.0F, true);
    }

    // Give the entity the ability to pathfind on both land and water
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 54.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.70D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_SPEED, -1.0D);
    }

    // Shatter boats on collision if a player is riding them
    @Override
    protected void doPush(Entity entity) {
        super.doPush(entity);
        
        // Only run on the server side and check if we bumped into a boat
        if (!this.level().isClientSide && entity instanceof net.minecraft.world.entity.vehicle.Boat boat) {
            
            // Check if any passenger is a player
            boolean hasPlayer = false;
            for (Entity passenger : boat.getPassengers()) {
                if (passenger instanceof Player) {
                    hasPlayer = true;
                    break;
                }
            }
            
            // If a player is in the boat, smash it!
            if (hasPlayer) {
                // 100.0F damage ensures it instantly breaks and drops its item/planks
                boat.hurt(this.damageSources().mobAttack(this), 100.0F);
            }
        }
    }

    // Prevent the monster from being scooped up by boats
    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (vehicle instanceof net.minecraft.world.entity.vehicle.Boat) {
            return false;
        }
        return super.startRiding(vehicle, force);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        // Re-add the base land goals manually (minus the FloatGoal)
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        
        // Add swimming goal for when it is submerged
        this.goalSelector.addGoal(7, new RandomSwimmingGoal(this, 1.0D, 10));

        // Attack Goals (Increased speed modifier during attack from 1.2D to 1.5D to make it more aggressive)
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.5D, false));

        // Target Goals
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, true));
    }

    // Animation Override: Switches dynamically based on environment
    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (this.swinging) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("animation.model.attack"));
            return PlayState.CONTINUE;
        } 
        else if (state.isMoving()) {
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

    // Prevent the monster from drowning
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    // Prevents water currents from pushing the monster around and ruining its pathfinding
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Do nothing -> prevents XP orbs
    }
}
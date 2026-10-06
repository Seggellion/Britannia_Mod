package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import com.seggellion.britannia_mod.entity.ai.AlligatorMoveControl;
import com.seggellion.britannia_mod.entity.ai.AlligatorNavigation;
import com.seggellion.britannia_mod.entity.ai.AlligatorWaterGoals;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import org.jetbrains.annotations.Nullable;

public class AlligatorEntity extends BaseBritanniaMonster {

    public AlligatorEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level, "alligator");
        this.moveControl = new AlligatorMoveControl(this);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AlligatorNavigation(this, level);
    }

    public boolean usesWaterMovement() {
        // A zero-Y collision tick may temporarily clear onGround while wading. Fluid depth
        // keeps thin water on ordinary control/travel instead of alternating goals and buoyancy.
        return isInWater() && getFluidHeight(net.minecraft.tags.FluidTags.WATER) >= .4;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && usesWaterMovement() && isAffectedByFluids()
                && !canStandOnFluid(level().getFluidState(blockPosition()))) {
            double oldY = getY();
            moveRelative(AlligatorMoveControl.WATER_ACCELERATION * (float)getAttributeValue(NeoForgeMod.SWIM_SPEED), input);
            move(MoverType.SELF, getDeltaMovement());
            Vec3 velocity = getDeltaMovement();
            if (horizontalCollision && onClimbable()) velocity = new Vec3(velocity.x, .2, velocity.z);
            // Neutral buoyancy is confined to ordinary water travel. Finite air, fluid flow,
            // collision hooks and external velocity remain owned by the engine.
            float drag = getWaterSlowDown();
            float efficiency = (float)getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
            if (!onGround()) efficiency *= .5F;
            drag += (.54600006F - drag) * efficiency;
            if (hasEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE)) drag = .96F;
            setDeltaMovement(velocity.multiply(drag, .8, drag));
            if (horizontalCollision && isFree(velocity.x, velocity.y + .6 - getY() + oldY, velocity.z))
                setDeltaMovement(getDeltaMovement().x, .3, getDeltaMovement().z);
        } else {
            super.travel(input);
        }
    }

    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (!usesWaterMovement() && state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.walk"));
            return PlayState.CONTINUE;
        }
        // The shipped asset contains only walk. STOP/reset returns idle and water motion to
        // bind pose, including after a prior walk; attack damage stays server-side.
        state.getController().stop();
        state.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 18.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ATTACK_SPEED, -2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new AlligatorWaterGoals.RecoverAir(this));
        this.goalSelector.addGoal(7, new AlligatorWaterGoals.IdleWater(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 1.0D) {
            @Override public boolean canUse() { return !AlligatorEntity.this.usesWaterMovement() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !AlligatorEntity.this.usesWaterMovement() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        
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

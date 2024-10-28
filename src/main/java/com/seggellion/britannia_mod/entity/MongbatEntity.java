// MongbatEntity.java
package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.world.entity.Entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class MongbatEntity extends Monster implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MongbatEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<MongbatEntity>(this, "controller", 0, this::predicate));
    }

   private PlayState predicate(AnimationState<MongbatEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.walk"));
            return PlayState.CONTINUE;
        } else if (this.swinging) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("animation.model.attack"));
            return PlayState.CONTINUE;
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.idle"));
            return PlayState.CONTINUE;
        }
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        // Return the entity's age in ticks for animation purposes
        return this.tickCount;
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.FLYING_SPEED, 0.4D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // Spawn conditions
    public static boolean canSpawn(EntityType<MongbatEntity> type, ServerLevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return Monster.isDarkEnoughToSpawn(world, pos, random) && Monster.checkMobSpawnRules(type, world, spawnReason, pos, random);
    }


// Sound events for Mongbat
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MONGBAT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return ModSounds.MONGBAT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MONGBAT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        // Mongbats don't have typical footsteps, can use a wing flap sound here if needed.
        this.playSound(ModSounds.MONGBAT_ANGRY.get(), 0.15F, 1.0F);
    }


    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            this.playSound(ModSounds.MONGBAT_ATTACK.get(), 1.0F, 1.0F);
        }
        return result;
    }
}

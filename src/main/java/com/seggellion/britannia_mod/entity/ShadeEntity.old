// ShadeEntity.java
package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.entity.ai.goal.RestrictedStrollGoal;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.levelgen.Heightmap;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;

public class ShadeEntity extends Monster implements GeoAnimatable {
    private static final Logger LOGGER = LogManager.getLogger();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int maxHomeDistance = 5; // Set to match the spawner's radius

    public void setHomePosition(BlockPos homePosition) {
        this.restrictTo(homePosition, maxHomeDistance);
    }

    public ShadeEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setNoGravity(true);

        // Set move control and navigation for flying entities
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.navigation = new FlyingPathNavigation(this, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
       // controllers.add(new AnimationController<ShadeEntity>(this, "controller", 0, this::predicate));
        controllers.add(new AnimationController<ShadeEntity>(this, "controller", 20, this::predicate));

    }

    private PlayState predicate(AnimationState<ShadeEntity> state) {
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
        return this.tickCount;
    }

    // Attributes
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.FLYING_SPEED, 0.4D);
    }

    // Goals and behaviors
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        // Use flying movement goal
        this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));

     //   this.goalSelector.addGoal(2, new RestrictedStrollGoal(this, 1.0D, maxHomeDistance));
    this.goalSelector.addGoal(2, new RestrictedStrollGoal(this, 1.0D, maxHomeDistance));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // Spawn conditions
    public static boolean canSpawn(EntityType<ShadeEntity> type, ServerLevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        BlockPos groundPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        return pos.getY() >= groundPos.getY() + 1.0D && world.getBlockState(pos.below()).isAir();
    }

    @Override
    public void aiStep() {
        super.aiStep();
    }

    // Sound events for Shade
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GHOST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return ModSounds.GHOST_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GHOST_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(ModSounds.GHOST_ANGRY.get(), 0.15F, 1.0F);
    }

@Override
public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
    return false; // Prevents fall damage
}

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            this.playSound(ModSounds.GHOST_ATTACK.get(), 1.0F, 1.0F);
        }
        return result;
    }
}

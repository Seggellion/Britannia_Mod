package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/** One animated entity type for the synchronized Pink, Rose, and White Flamingos. */
public final class FlamingoEntity extends BaseBritanniaAnimal {
    private static final String VARIANT_TAG = "FlamingoVariant";
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(FlamingoEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("death");

    public FlamingoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level, "flamingo");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, FlamingoVariant.PINK.id());
    }

    public FlamingoVariant getVariant() {
        return FlamingoVariant.byId(entityData.get(DATA_VARIANT));
    }

    public void setVariant(FlamingoVariant variant) {
        entityData.set(DATA_VARIANT,
                variant == null ? FlamingoVariant.PINK.id() : variant.id());
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType reason,
            @Nullable SpawnGroupData spawnData) {
        setVariant(FlamingoVariant.byId(random.nextInt(FlamingoVariant.count())));
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 7.0F, 1.0D, 1.25D));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (deathTime > 0) {
            state.getController().setAnimation(DEATH);
        } else if (state.isMoving()) {
            state.getController().setAnimation(WALK);
        } else {
            state.getController().setAnimation(IDLE);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(VARIANT_TAG, getVariant().id());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(FlamingoVariant.byId(tag.getInt(VARIANT_TAG)));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FLAMINGO_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.CHICKEN_STEP, 0.15F, 1.0F);
    }
}

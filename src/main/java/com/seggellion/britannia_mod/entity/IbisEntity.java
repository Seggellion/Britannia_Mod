package com.seggellion.britannia_mod.entity;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import com.seggellion.britannia_mod.entity.ai.IbisFlockGoal;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/** One synchronized/persistent entity type for both white and scarlet ibis. */
public final class IbisEntity extends BaseBritanniaAnimal {
    public static final int REST_ANIMATION_TICKS = 120;
    public static final int WANDER_INTERVAL_TICKS = 40;
    public static final double WANDER_SPEED = 1.15D;
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.model.walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation EATING = RawAnimation.begin().thenLoop("animation.model.eating");
    private static final String VARIANT_TAG = "IbisVariant";
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(IbisEntity.class, EntityDataSerializers.INT);
    private int nextRestAnimationTick;
    private boolean usingEatingAnimation;

    public IbisEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level, "ibis");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, IbisVariant.WHITE.id());
    }

    public IbisVariant getVariant() {
        return IbisVariant.byId(entityData.get(DATA_VARIANT));
    }

    public void setVariant(IbisVariant variant) {
        entityData.set(DATA_VARIANT, variant == null ? IbisVariant.WHITE.id() : variant.id());
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType reason,
            @Nullable SpawnGroupData spawnData) {
        setVariant(IbisVariantPolicy.usesScarletVariant(
                random.nextInt(IbisVariantPolicy.TOTAL_WEIGHT))
                ? IbisVariant.SCARLET
                : IbisVariant.WHITE);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.0D, 1.35D));
        goalSelector.addGoal(3, new IbisFlockGoal(this, 1.1D, 3.0D, 14.0D));
        goalSelector.addGoal(4, new RandomStrollGoal(this, WANDER_SPEED, WANDER_INTERVAL_TICKS));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (state.isMoving()) {
            nextRestAnimationTick = tickCount;
            state.getController().setAnimation(WALK);
            return PlayState.CONTINUE;
        }
        if (tickCount >= nextRestAnimationTick) {
            usingEatingAnimation = IbisAnimationPolicy.usesEatingAnimation(
                    random.nextInt(IbisAnimationPolicy.TOTAL_WEIGHT));
            nextRestAnimationTick = tickCount + REST_ANIMATION_TICKS;
        }
        state.getController().setAnimation(usingEatingAnimation ? EATING : IDLE);
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
        setVariant(IbisVariant.byId(tag.getInt(VARIANT_TAG)));
    }

    @Override
    protected void dropExperience(@Nullable Entity killer) {
        // Ambient city wildlife does not award experience.
    }
}

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** One synchronized/persistent entity type for both white and scarlet ibis. */
public final class IbisEntity extends BaseBritanniaAnimal {
    private static final String VARIANT_TAG = "IbisVariant";
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(IbisEntity.class, EntityDataSerializers.INT);

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
        setVariant(random.nextBoolean() ? IbisVariant.WHITE : IbisVariant.SCARLET);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.0D, 1.35D));
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

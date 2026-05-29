package com.seggellion.britannia_mod.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.neoforged.neoforge.entity.PartEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;

// 1. Changed generic type to BaseBritanniaMonster so ANY of your custom mobs can use it
public class SerpentPartEntity extends PartEntity<BaseBritanniaMonster> {
    public final BaseBritanniaMonster parentMob;
    public final String name;
    private final EntityDimensions size;

    public SerpentPartEntity(BaseBritanniaMonster parent, String name, float width, float height) {
        super(parent);
        this.parentMob = parent;
        this.name = name;
        this.size = EntityDimensions.fixed(width, height);
        this.refreshDimensions();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 2. Call the built-in hurt method directly on the parent
        return this.parentMob.hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.parentMob == entity;
    }

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return this.size;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {}

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}
}
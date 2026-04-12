package com.seggellion.britannia_mod.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.PathfinderMob; 
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootTable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import com.seggellion.britannia_mod.ModSounds;

public abstract class BaseBritanniaAnimal extends PathfinderMob implements IBritanniaEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String entityName;

    protected BaseBritanniaAnimal(EntityType<? extends PathfinderMob> entityType, Level level, String entityName) {
        super(entityType, level);
        this.entityName = entityName;
    }

    public String getEntityName() {
        return this.entityName;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.walk"));
            return PlayState.CONTINUE;
        }
        state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.idle"));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return this.tickCount;
    }

    
    @Override
    protected SoundEvent getAmbientSound() {
        var group = ModSounds.ENTITY_SOUNDS.get(this.getEntityName());
        return group != null ? group.ambient().get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        var group = ModSounds.ENTITY_SOUNDS.get(this.getEntityName());
        return group != null ? group.hurt().get() : null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        var group = ModSounds.ENTITY_SOUNDS.get(this.getEntityName());
        return group != null ? group.death().get() : null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        // Based on your earlier code, you used 'angry' for footsteps
        var group = ModSounds.ENTITY_SOUNDS.get(this.getEntityName());
        if (group != null) {
            this.playSound(group.angry().get(), 0.15F, 1.0F);
        }
    }

    // Default peaceful animal goals
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D)); // Animals flee when hit
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "entities/" + this.entityName)
        );
    }
}
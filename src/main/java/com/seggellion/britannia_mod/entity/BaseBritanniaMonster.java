package com.seggellion.britannia_mod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
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

public abstract class BaseBritanniaMonster extends Monster implements IBritanniaEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    
    // Red color for monsters, or use 0x2194A5 if you want them to match the QuestGiver
    private static final Style MONSTER_STYLE = Style.EMPTY
            .withFont(FONT_UO_CLASSIC)
            .withColor(0x848484); // Standard Minecraft Red

    // Pass the internal name (e.g., "orc", "troll") to automate loot tables
    private final String entityName;

    protected BaseBritanniaMonster(EntityType<? extends Monster> entityType, Level level, String entityName) {
        super(entityType, level);
        this.entityName = entityName;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    protected PlayState predicate(AnimationState<? extends GeoAnimatable> state) {
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
    protected SoundEvent getAmbientSound() {
        var group = ModSounds.ENTITY_SOUNDS.get(this.getEntityName());
        return group != null ? group.ambient().get() : null;
    }

@Override
    public Component getName() {
        // This looks for "entity.britannia_mod.orc" in your lang JSON files
        String langKey = "entity.britannia_mod." + this.entityName;
        return Component.translatable(langKey).withStyle(MONSTER_STYLE);
    }

    /**
     * This handles the actual hover-text/display name used in the world.
     */
    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    /**
     * To make the name always visible (like a Quest Giver), override this.
     * If you only want it to show when looked at, remove this method.
     */
    @Override
    public boolean shouldShowName() {
        return true; 
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
        // Randomly choose between feet13a and feet13b
        SoundEvent stepSound = this.getRandom().nextBoolean() ? ModSounds.FEET13A.get() : ModSounds.FEET13B.get();
        this.playSound(stepSound, 0.15F, 1.0F);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            var group = ModSounds.ENTITY_SOUNDS.get(this.getEntityName());
            if (group != null) {
                this.playSound(group.attack().get(), 1.0F, 1.0F);
            }
        }
        return result;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public String getEntityName() {
        return this.entityName;
    }

    @Override
    public double getTick(Object object) {
        return this.tickCount;
    }

    // Default base goals every monster should probably have
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // You'll add specific Target and Attack goals in the child classes
    }

    // Unified Spawning Logic for your BlockEntity
    public static boolean canSpawn(EntityType<? extends Monster> type, ServerLevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return true; 
    }

    // Automate the Loot Table resolution
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "entities/" + this.entityName)
        );
    }
}
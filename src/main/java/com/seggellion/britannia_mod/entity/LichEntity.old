// LichEntity.java
package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.magic.Caster;
import com.seggellion.britannia_mod.magic.MagicArrowSpell;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
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

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LichEntity extends Monster implements GeoAnimatable, Caster {
    private static final Logger LOGGER = LogManager.getLogger();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Mana-related attributes
    private int mana = 50;
    private final int maxMana = 10;
    private int manaCooldown = 0;
    private static final int MANA_REGEN_COOLDOWN = 20;  // 1 second in ticks
    private static final int MAGIC_ARROW_COST = 4;

    private int maxHomeDistance = 5; // Set to match the spawner's radius

    public void setHomePosition(BlockPos homePosition) {
        this.restrictTo(homePosition, maxHomeDistance);
    }

    public LichEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
              LOGGER.warn("Lich loaded.");
        this.setPersistenceRequired();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<LichEntity> state) {
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

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LichCastMagicGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (manaCooldown > 0) {
            manaCooldown--;
        } else if (mana < maxMana) {
            mana++;
            manaCooldown = MANA_REGEN_COOLDOWN; // Reset cooldown after regen
        }
    }
    

    @Override
    public LivingEntity asLivingEntity() {
        return this;
    }

    public boolean canCastMagicArrow() {
        return this.getMana() >= MAGIC_ARROW_COST;
    }

    public void castMagicArrow(LivingEntity target) {
        if (canCastMagicArrow()) {
            consumeMana(MAGIC_ARROW_COST);
            MagicArrowSpell magicArrowSpell = new MagicArrowSpell();
            magicArrowSpell.applyEffect(this, target);
        }
    }

  @Override
    public void consumeMana(int amount) {
        this.mana = Math.max(0, this.mana - amount);
    }

    public int getMana() {
        return mana;
    }

    public static boolean canSpawn(EntityType<LichEntity> type, ServerLevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.LICH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return ModSounds.LICH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LICH_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(ModSounds.LICH_ANGRY.get(), 0.15F, 1.0F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result) {
            this.playSound(ModSounds.LICH_ATTACK.get(), 1.0F, 1.0F);
        }
        return result;
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "entities/lich")
        );
    }
}

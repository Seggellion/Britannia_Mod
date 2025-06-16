// WispEntity.java
package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.entity.ai.goal.WispTravelGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

public class WispEntity extends FlyingMob implements GeoAnimatable {
    private static final Logger LOGGER = LogManager.getLogger();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<BlockPos> lightBlockPositions = new HashSet<>(); // Tracks light block positions


    public WispEntity(EntityType<? extends FlyingMob> entityType, Level level) {
        super(entityType, level);

        this.setPersistenceRequired();
        this.setNoGravity(true); // Wisps float
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<WispEntity> state) {
        state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.model.float"));
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

    public static AttributeSupplier.Builder createAttributes() {
        return FlyingMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new WispTravelGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, world);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

   private BlockPos previousLightBlockPos = null; // Track the last light block position

@Override
public void tick() {
    super.tick();

    if (!this.level().isClientSide) {
        double targetHeight = 3.0; // Desired height above the ground
        BlockPos groundPos = this.blockPosition().below();
        double groundY = getGroundHeight(groundPos);
        double desiredY = groundY + targetHeight;

        // Adjust height smoothly
        if (Math.abs(this.getY() - desiredY) > 0.1) {
            this.setPos(this.getX(), desiredY, this.getZ());
            this.setDeltaMovement(this.getDeltaMovement().x, 0, this.getDeltaMovement().z);
        }

        BlockPos currentPos = this.blockPosition();

        // Remove the light block from the previous position
        if (previousLightBlockPos != null && !previousLightBlockPos.equals(currentPos)) {
            removeLightBlock(previousLightBlockPos);
        }

        // Place a new light block at the current position
        placeLightBlock(currentPos);
        previousLightBlockPos = currentPos;
    }

    // Client-side particles
    if (this.level().isClientSide) {
        for (int i = 0; i < 2; ++i) {
            this.level().addParticle(ParticleTypes.GLOW, 
                this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                this.getY() + this.random.nextDouble() * this.getBbHeight(),
                this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                0.0, 0.0, 0.0);
        }
    }

    // Disappear during the day
    if (!this.level().isClientSide && this.level().getDayTime() % 24000 < 12000) {
        this.discard();
    }
}

/**
 * Gets the Y-coordinate of the highest solid block at the specified position.
 */
private double getGroundHeight(BlockPos pos) {
    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
    while (this.level().isEmptyBlock(mutablePos) && mutablePos.getY() > this.level().getMinBuildHeight()) {
        mutablePos.move(0, -1, 0); // Move downward to find the ground
    }
    return mutablePos.getY();
}


@Override
public void remove(RemovalReason reason) {
    // Remove the light block when the entity is removed for any reason
    if (previousLightBlockPos != null) {
        BlockState previousState = this.level().getBlockState(previousLightBlockPos);
        if (previousState.is(Blocks.LIGHT)) {
            this.level().setBlockAndUpdate(previousLightBlockPos, Blocks.AIR.defaultBlockState());
        }
    }

    super.remove(reason); // Call the parent class method to handle standard removal logic
}

@Override
public void die(DamageSource cause) {
        removeAllTrackedLightBlocks();
    super.die(cause); // Call the parent class method to handle standard death logic
}

private void removeAllTrackedLightBlocks() {
    for (BlockPos pos : new HashSet<>(lightBlockPositions)) { // Use a copy to avoid concurrent modification
        removeLightBlock(pos);
    }
    lightBlockPositions.clear();
}

private void placeLightBlock(BlockPos pos) {
    if (!lightBlockPositions.contains(pos)) {
        this.level().setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        lightBlockPositions.add(pos);
    }
}

private void removeLightBlock(BlockPos pos) {
    if (lightBlockPositions.contains(pos)) {
        BlockState state = this.level().getBlockState(pos);
        if (state.is(Blocks.LIGHT)) {
            this.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        lightBlockPositions.remove(pos);
    }
}



    public static boolean canSpawn(EntityType<WispEntity> type, ServerLevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getLevel().getBrightness(LightLayer.SKY, pos) < 10;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.WISP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return ModSounds.WISP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WISP_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(ModSounds.WISP_ANGRY.get(), 0.15F, 1.0F);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false; // Prevents fall damage
    }


public int getLightEmission() {
    return 15; // Emit maximum block light level
}


    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
            Registries.LOOT_TABLE,
        ResourceLocation.fromNamespaceAndPath("britannia_mod", "entities/wisp")
        );
    }

}

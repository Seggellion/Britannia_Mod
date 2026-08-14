package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Transient, server-triggered animation owner for the training-dummy root. */
public final class TrainingDummyBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final String CONTROLLER = "training_dummy";
    public static final String HIT_TRIGGER = "hit";
    private static final RawAnimation HIT_ANIMATION =
            RawAnimation.begin().thenPlay("animation.training_dummy.hit");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int acceptedHitCount;

    public TrainingDummyBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.TRAINING_DUMMY_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void triggerHitAnimation() {
        if (level == null || level.isClientSide) {
            return;
        }
        acceptedHitCount++;
        triggerAnim(CONTROLLER, HIT_TRIGGER);
    }

    public int acceptedHitCount() {
        return acceptedHitCount;
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(HIT_TRIGGER, HIT_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0D, 1.0D, 2.0D).expandTowards(0.0D, 3.0D, 0.0D);
    }
}

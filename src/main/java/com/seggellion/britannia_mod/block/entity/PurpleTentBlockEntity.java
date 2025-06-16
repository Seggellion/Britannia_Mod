package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;



public class PurpleTentBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private static final Logger LOGGER = LogUtils.getLogger();

    public PurpleTentBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.PURPLE_TENT_BLOCK_ENTITY_TYPE.get(), pos, state);
   
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public Direction getFacing() {
    return this.getBlockState().getValue(PurpleTentBlock.FACING);
}


public AABB getRenderBoundingBox() {
    return new AABB(this.getBlockPos()).inflate(9); // Adjust the inflate size as needed
}

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 10, event -> {
            event.getController().setAnimation(DefaultAnimations.IDLE);
            return PlayState.CONTINUE;
        }));
    }

}

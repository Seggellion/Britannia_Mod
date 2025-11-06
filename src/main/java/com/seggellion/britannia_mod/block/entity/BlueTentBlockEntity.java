package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

import net.minecraft.core.Direction;

// GeckoLib imports
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.Map;
import java.util.function.Supplier;
import java.util.HashMap;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.world.phys.AABB;



public class BlueTentBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private static final Logger LOGGER = LogUtils.getLogger();

    public BlueTentBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.BLUE_TENT_BLOCK_ENTITY_TYPE.get(), pos, state);
   
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public Direction getFacing() {
    return this.getBlockState().getValue(BlueTentBlock.FACING);
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

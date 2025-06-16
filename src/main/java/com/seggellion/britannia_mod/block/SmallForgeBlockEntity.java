package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SmallForgeBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String STORED_PURITY_KEY = "StoredPurity";
    private int storedPurity = 0;
    private final Map<String, Integer> storedPurityMap = new HashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SmallForgeBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.SMALL_FORGE_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

        public void addPurity(String oreType, int purityToAdd, Supplier<Item> ingotSupplier) {
            int currentPurity = storedPurityMap.getOrDefault(oreType, 0);
            currentPurity += purityToAdd;

            if (currentPurity >= 6) {
                int ingotsToDrop = (currentPurity / 6) * 2;
                currentPurity %= 6;

                if (level != null && !level.isClientSide) {
                    ItemStack ingotStack = new ItemStack(ingotSupplier.get(), ingotsToDrop);
                    ItemEntity ingotEntity = new ItemEntity(
                        level,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5,
                        ingotStack
                    );
                    level.addFreshEntity(ingotEntity);
                }
            }

            storedPurityMap.put(oreType, currentPurity);
            setChanged();
        }

@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.saveAdditional(tag, provider);

    CompoundTag purityTag = new CompoundTag();
    for (Map.Entry<String, Integer> entry : storedPurityMap.entrySet()) {
        purityTag.putInt(entry.getKey(), entry.getValue());
    }

    tag.put(STORED_PURITY_KEY, purityTag);
}

@Override
protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.loadAdditional(tag, provider);
    storedPurityMap.clear();

    if (tag.contains(STORED_PURITY_KEY, Tag.TAG_COMPOUND)) {
        CompoundTag purityTag = tag.getCompound(STORED_PURITY_KEY);
        for (String key : purityTag.getAllKeys()) {
            storedPurityMap.put(key, purityTag.getInt(key));
        }
    }
}

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 10, event -> {
            event.getController().setAnimation(DefaultAnimations.IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ✅ These are **not** overridden methods in GeoBlockEntity or GeoAnimatable!
    public ResourceLocation getModelResource() {
        return ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "geo/small_forge.geo.json"
        );
    }

    public ResourceLocation getTextureResource() {
        return ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "textures/block/small_forge.png"
        );
    }

    public ResourceLocation getAnimationResource() {
        return ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "animations/small_forge.animation.json"
        );
    }
}
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
// GeckoLib imports
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SmallForgeBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String STORED_PURITY_KEY = "StoredPurity";
    private int storedPurity = 0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SmallForgeBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.SMALL_FORGE_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void addPurity(int purity) {
        this.storedPurity += purity;

        // 🔄 Convert ore into ingots when purity reaches 6
        if (storedPurity >= 6) {
            int ingotsProduced = (storedPurity / 6) * 2;
            storedPurity %= 6; // Keep the remainder

            if (level != null && !level.isClientSide) {

                ItemStack ingotStack = new ItemStack(Items.IRON_INGOT, ingotsProduced);
                ItemEntity ingotEntity = new ItemEntity(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), ingotStack);
                level.addFreshEntity(ingotEntity);

      
            }
        }

        setChanged(); 
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        storedPurity = tag.getInt(STORED_PURITY_KEY);
    }

  
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt(STORED_PURITY_KEY, storedPurity);
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
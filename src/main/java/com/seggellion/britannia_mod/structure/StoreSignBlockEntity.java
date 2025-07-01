package com.seggellion.britannia_mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;



public class StoreSignBlockEntity extends BlockEntity {

    private String storeName = "Unnamed Store";

    public StoreSignBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.STORE_SIGN.get(), pos, state);
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String name) {
        this.storeName = name;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("store_name", storeName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("store_name")) {
            storeName = tag.getString("store_name");
        }
    }
}

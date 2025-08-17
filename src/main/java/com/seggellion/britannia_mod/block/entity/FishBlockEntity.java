package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FishBlockEntity extends BlockEntity {
    private double weight = 0.0;
    private String fishType = "unknown";

    public FishBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.FISH_BLOCK.get(), pos, state);
    }

    public void setWeight(double w) { this.weight = w; setChanged(); }
    public double getWeight() { return weight; }

    public void setFishType(String t) { this.fishType = t; setChanged(); }
    public String getFishType() { return fishType; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putDouble("FishWeight", weight);
        tag.putString("FishType", fishType);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("FishWeight")) this.weight = tag.getDouble("FishWeight");
        if (tag.contains("FishType")) this.fishType = tag.getString("FishType");
    }
}

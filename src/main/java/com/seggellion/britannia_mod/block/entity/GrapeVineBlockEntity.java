package com.seggellion.britannia_mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;

public class GrapeVineBlockEntity extends BlockEntity {
    private String variety = "Unknown";

    public GrapeVineBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.GRAPE_VINE_BE.get(), pos, blockState);
    }

    public void setVariety(String variety) {
        this.variety = variety;
        setChanged(); // Mark for saving
    }

    public String getVariety() {
        return variety;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("GrapeVariety", variety);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.variety = tag.getString("GrapeVariety");
    }
}
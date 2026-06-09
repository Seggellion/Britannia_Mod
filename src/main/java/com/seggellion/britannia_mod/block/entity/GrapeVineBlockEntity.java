package com.seggellion.britannia_mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;

public class GrapeVineBlockEntity extends BlockEntity {
    private String variety = GrapesItem.DEFAULT_VARIETY_ID;

    public GrapeVineBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.GRAPE_VINE_BE.get(), pos, blockState);
    }

    public void setVariety(String variety) {
        this.variety = variety == null || variety.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : variety;
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
        setVariety(tag.contains(GrapesItem.GRAPE_VARIETY_KEY) ? tag.getString(GrapesItem.GRAPE_VARIETY_KEY) : GrapesItem.DEFAULT_VARIETY_ID);
    }
}

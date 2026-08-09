package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.item.WeightedWoodType;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WeightedWoodBlockEntity extends BlockEntity {
    private String woodType = WeightedWoodType.OAK.id();
    private double woodWeight = WeightedWoodType.OAK.averageWeight();

    public WeightedWoodBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.WEIGHTED_WOOD_BE.get(), pos, blockState);
    }

    public String getWoodType() {
        return woodType;
    }

    public WeightedWoodType getWoodTypeDefinition() {
        return WeightedWoodType.byIdOrDefault(woodType);
    }

    public double getWoodWeight() {
        return woodWeight;
    }

    public void setWoodData(String woodType, double woodWeight) {
        WeightedWoodType definition = WeightedWoodType.byIdOrDefault(woodType);
        this.woodType = definition.id();
        this.woodWeight = woodWeight > 0.0D ? woodWeight : definition.averageWeight();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("WoodType", woodType);
        tag.putDouble("WoodWeight", woodWeight);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.woodType = WeightedWoodType.byIdOrDefault(tag.getString("WoodType")).id();
        WeightedWoodType definition = WeightedWoodType.byIdOrDefault(this.woodType);
        this.woodWeight = tag.contains("WoodWeight") ? tag.getDouble("WoodWeight") : definition.averageWeight();
    }
}

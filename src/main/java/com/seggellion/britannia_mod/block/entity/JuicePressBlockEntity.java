package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class JuicePressBlockEntity extends BlockEntity {
    private String grapeVariety = "";
    private int quality = 0;
    private boolean hasGrapes = false;
    private String region = "Britannia";

    public JuicePressBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.JUICE_PRESS_BE.get(), pos, blockState);
    }

    public boolean hasGrapes() {
        return hasGrapes;
    }

    // Called when player clicks with Grapes
    public void addGrapes(String variety, int quality, String region) {
        this.grapeVariety = variety;
        this.quality = quality;
        this.hasGrapes = true;
        this.region = region;
        this.setChanged(); // Marks chunk as dirty to save data
    }

    // Called when player clicks with Empty Pitcher
    public JuiceData extractJuice() {
        if (!hasGrapes) return null;
        
        JuiceData data = new JuiceData(this.grapeVariety, this.quality, this.region);
        
        // Reset state
        this.hasGrapes = false;
        this.grapeVariety = "";
        this.region = "Britannia";
        this.quality = 0;
        this.setChanged();
        
        return data;
    }

    // Data Transfer Object
    public record JuiceData(String variety, int quality, String region) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("HasGrapes", hasGrapes);
        if (hasGrapes) {
            tag.putString("GrapeVariety", grapeVariety);
            tag.putInt("Quality", quality);
            tag.putString("GrapeRegion", region);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.hasGrapes = tag.getBoolean("HasGrapes");
        if (this.hasGrapes) {
            this.grapeVariety = tag.getString("GrapeVariety");
            this.quality = tag.getInt("Quality");
            if (tag.contains("GrapeRegion")) {
                this.region = tag.getString("GrapeRegion");
            } else {
                this.region = "Britannia";
            }
        }
    }
}
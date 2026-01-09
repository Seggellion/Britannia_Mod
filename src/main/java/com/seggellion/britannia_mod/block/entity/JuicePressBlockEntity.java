package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class JuicePressBlockEntity extends BlockEntity {
    // UPDATED: Set to 50 to match the text in your JuicePressBlock.java
    public static final int REQUIRED_GRAPES = 50; 

    private String grapeVariety = "";
    private int quality = 0;
    private int grapeCount = 0;
    private String region = "Britannia";

    public JuicePressBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.JUICE_PRESS_BE.get(), pos, blockState);
    }

    // --- NEW METHOD ADDED HERE ---
    public String getVariety() {
        return grapeVariety;
    }
    // -----------------------------

    public int getGrapeCount() {
        return grapeCount;
    }

    public boolean isFull() {
        return grapeCount >= REQUIRED_GRAPES;
    }

    public boolean isEmpty() {
        return grapeCount == 0;
    }

    public int addGrapes(String variety, int itemQuality, String region, int amountToAdd) {
        // If press is not empty, ensure variety and region match
        if (!isEmpty()) {
            if (!this.grapeVariety.equals(variety) || !this.region.equals(region)) {
                return 0; // Cannot mix varieties/regions
            }
        } else {
            // First grape initializes the batch
            this.grapeVariety = variety;
            this.region = region;
            this.quality = itemQuality; // Initialize quality
        }

        // Calculate how many we can actually fit
        int spaceRemaining = REQUIRED_GRAPES - this.grapeCount;
        int actuallyAdded = Math.min(amountToAdd, spaceRemaining);

        if (actuallyAdded > 0) {
            // Update Quality using Weighted Average
            long totalQualityExisting = (long) this.quality * this.grapeCount;
            long totalQualityAdded = (long) itemQuality * actuallyAdded;

            this.grapeCount += actuallyAdded;
            this.quality = (int) ((totalQualityExisting + totalQualityAdded) / this.grapeCount);

            this.setChanged();
        }

        return actuallyAdded;
    }

    public JuiceData extractJuice() {
        if (!isFull()) return null; // Can only extract if full

        JuiceData data = new JuiceData(this.grapeVariety, this.quality, this.region);

        // Reset state
        this.grapeCount = 0;
        this.grapeVariety = "";
        this.region = "Britannia";
        this.quality = 0;
        this.setChanged();

        return data;
    }

    public record JuiceData(String variety, int quality, String region) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("GrapeCount", grapeCount);
        if (grapeCount > 0) {
            tag.putString("GrapeVariety", grapeVariety);
            tag.putInt("Quality", quality);
            tag.putString("GrapeRegion", region);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.grapeCount = tag.getInt("GrapeCount");
        if (this.grapeCount > 0) {
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
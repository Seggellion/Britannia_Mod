package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FarmingBlockEntity extends BlockEntity {
    // Soil Nutrients
    private float nitrogen = 0.0f;
    private float phosphorus = 0.0f;
    private float potassium = 0.0f;
    private float organicMatter = 0.0f;

    // Hydration (0-5)
    private int hydration = 0;

    // Stored Seed Data (Waiting for Trellis)
    private String storedSeedVariety = "";

    // Ideal values
    private static final float IDEAL_N = 0.6f;
    private static final float IDEAL_P = 0.3f;
    private static final float IDEAL_K = 0.8f;
    private static final float IDEAL_OM = 0.5f;

    public FarmingBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.FARMING_BLOCK_BE.get(), pos, blockState);
    }

    // --- Seed Logic ---
    public void setStoredSeed(String variety) {
        this.storedSeedVariety = variety;
        setChanged();
        syncData();
    }

    public String getStoredSeed() {
        return storedSeedVariety;
    }

    public void clearStoredSeed() {
        this.storedSeedVariety = "";
        setChanged();
        syncData();
    }

    // --- Nutrient Logic ---
    public void addNutrients(float n, float p, float k, float om) {
        this.nitrogen = Math.min(1.0f, this.nitrogen + n);
        this.phosphorus = Math.min(1.0f, this.phosphorus + p);
        this.potassium = Math.min(1.0f, this.potassium + k);
        this.organicMatter = Math.min(1.0f, this.organicMatter + om);
        setChanged();
        syncData();
    }

    public void setHydration(int amount) {
        this.hydration = Math.max(0, Math.min(5, amount));
        setChanged();
        syncData();
    }

    public int getHydration() { return hydration; }

    public int calculateQualityScore() {
        float nScore = 1.0f - Math.abs(nitrogen - IDEAL_N);
        float pScore = 1.0f - Math.abs(phosphorus - IDEAL_P);
        float kScore = 1.0f - Math.abs(potassium - IDEAL_K);
        float omScore = 1.0f - Math.abs(organicMatter - IDEAL_OM);
        float totalScore = (nScore + pScore + kScore + omScore) / 4.0f;
        return Math.round(Math.max(0, totalScore * 10));
    }

    // --- Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Nitrogen", nitrogen);
        tag.putFloat("Phosphorus", phosphorus);
        tag.putFloat("Potassium", potassium);
        tag.putFloat("OrganicMatter", organicMatter);
        tag.putInt("Hydration", hydration);
        tag.putString("StoredSeed", storedSeedVariety);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.nitrogen = tag.getFloat("Nitrogen");
        this.phosphorus = tag.getFloat("Phosphorus");
        this.potassium = tag.getFloat("Potassium");
        this.organicMatter = tag.getFloat("OrganicMatter");
        this.hydration = tag.getInt("Hydration");
        this.storedSeedVariety = tag.getString("StoredSeed");
    }

    private void syncData() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class WineBarrelBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Data
    private String variety = "";
    private String region = "Britannia";
    private int quality = 0;
    
    // Filling State
    private int pitchersStored = 0;
    public static final int MAX_PITCHERS_TO_FILL = 32;

    // Fermentation State
    private boolean isFermenting = false;
    private boolean isReady = false;
    private int fermentationTicks = 0;
    
    // Bottling State
    private int bottlesRemaining = 0;
    public static final int MAX_BOTTLES = 300;

    public static final int MAX_FERMENTATION_TIME = 1000;

    public WineBarrelBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.WINE_BARREL_BE.get(), pos, blockState);
    }

    /**
     * Tries to add juice to the barrel.
     * @return 
     * 0 = Success (Added, kept filling)
     * 1 = Success (Added, Barrel Full & Started Fermentation)
     * -1 = Fail (Barrel is busy fermenting or ready)
     * -2 = Fail (Wrong Variety/Region mix)
     * -3 = Fail (Barrel is already physically full, though this shouldn't happen if logic is tight)
     */
    public int tryAddJuice(String inputVariety, int inputQuality, String inputRegion) {
        // 1. Check State
        if (isFermenting || isReady) return -1;
        if (pitchersStored >= MAX_PITCHERS_TO_FILL) return -3;

        // 2. Check Compatibility (if not empty)
        if (pitchersStored > 0) {
            if (!this.variety.equals(inputVariety)) return -2;
            if (!this.region.equals(inputRegion)) return -2;
        } else {
            // First pitcher initializes the barrel data
            this.variety = inputVariety;
            this.region = inputRegion;
            this.quality = 0; // Reset for calculation below
        }

        // 3. Calculate Average Quality
        // New Average = ((CurrentAvg * CurrentCount) + NewEntry) / NewCount
        double totalQuality = (this.quality * this.pitchersStored) + inputQuality;
        this.pitchersStored++;
        this.quality = (int) (totalQuality / this.pitchersStored);

        // 4. Check if Full
        if (this.pitchersStored >= MAX_PITCHERS_TO_FILL) {
            startFermentation();
            return 1;
        }

        this.setChanged();
        this.notifyUpdate(); // Sync for tooltips/visuals
        return 0;
    }

    private void startFermentation() {
        this.isFermenting = true;
        this.isReady = false;
        this.fermentationTicks = 0;
        this.setChanged();
        this.notifyUpdate(); 
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WineBarrelBlockEntity be) {
        // --- CLIENT SIDE: Visuals ---
        if (level.isClientSide) {
            if (be.isFermenting) {
                be.animateFermentation(level, pos);
            }
            return;
        }

        // --- SERVER SIDE: Logic ---
        if (!be.isFermenting || be.isReady) return;

        be.fermentationTicks++;

        if (be.fermentationTicks >= MAX_FERMENTATION_TIME) {
            be.finishFermentation();
        }

        if (be.fermentationTicks % 2000 == 0) {
            be.setChanged();
        }
    }

    public String getVariety() { return this.variety; }
    public int getQuality() { return this.quality; }
    public String getRegion() { return this.region; }
    public int getPitchersStored() { return this.pitchersStored; }

    public void emptyBarrel() {
        this.isReady = false;
        this.isFermenting = false;
        this.fermentationTicks = 0;
        this.quality = 0;
        this.region = "Britannia";
        this.variety = "";
        this.pitchersStored = 0; // Reset filling count
        this.bottlesRemaining = 0;
        this.setChanged();
        this.notifyUpdate();
    }

    // Used for extracting bottles (unchanged logic mostly)
    public boolean extractOneBottle() {
        if (this.bottlesRemaining > 0) {
            this.bottlesRemaining--;
            if (this.bottlesRemaining <= 0) {
                emptyBarrel();
            } else {
                this.setChanged(); 
            }
            return true;
        }
        return false;
    }

    public void decrementBottleCount() {
        LOGGER.info("Decerementing count {}", this.bottlesRemaining);
        if (this.bottlesRemaining > 0) {
            this.bottlesRemaining--;
            
            // If that was the last bottle, reset the barrel
            if (this.bottlesRemaining <= 0) {
                emptyBarrel();
            } else {
                this.setChanged(); // Just save the new count
                // We do NOT call notifyUpdate() here to save bandwidth, 
                // unless you want to show the count on a client-side tooltip.
            }
        }
    }

    private void animateFermentation(Level level, BlockPos pos) {
        float chance = 0.05f; 
        if (level.random.nextFloat() < chance) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.1;
            double z = pos.getZ() + 0.5;
            level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.01, 0.0);
            if (level.random.nextFloat() < 0.2f) {
                 level.addParticle(ParticleTypes.EFFECT, x, y, z, 0.0, 0.05, 0.0);
            }
        }
    }

    private void finishFermentation() {
        this.isReady = true;
        this.isFermenting = false;
        this.bottlesRemaining = MAX_BOTTLES;
        this.setChanged();
        this.notifyUpdate();
    }
    
    private void notifyUpdate() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public String getBubblerStatus() {
        if (isReady) return "The airlock is silent. The wine is ready for bottling.";
        
        // New Status: Filling Phase
        if (!isFermenting && pitchersStored > 0 && pitchersStored < MAX_PITCHERS_TO_FILL) {
            return "Filling Phase: " + pitchersStored + "/" + MAX_PITCHERS_TO_FILL + " pitchers (" + variety + ")";
        }
        
        if (!isFermenting) return "The barrel is empty.";

        if (fermentationTicks < 1000) return "No bubbles in the airlock, yet.";
        if (fermentationTicks < 5000) return "One faint bubble every minute. Yeast is waking up.";
        if (fermentationTicks < 20000) return "Steady bubbling. Fermentation is active.";
        if (fermentationTicks < 100000) return "Rapid bubbling! The yeast is feasting.";

        return "The bubbling is slowing down...";
    }

    public boolean isFermenting() { return isFermenting; }
    public boolean isReady() { return isReady; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsFermenting", isFermenting);
        tag.putBoolean("IsReady", isReady);
        tag.putInt("FermentationTicks", fermentationTicks);
        tag.putString("GrapeVariety", variety);
        tag.putInt("BottlesRemaining", bottlesRemaining);
        tag.putInt("Quality", quality);
        tag.putString("GrapeRegion", region);
        // New Tag
        tag.putInt("PitchersStored", pitchersStored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isFermenting = tag.getBoolean("IsFermenting");
        this.isReady = tag.getBoolean("IsReady");
        this.fermentationTicks = tag.getInt("FermentationTicks");
        this.variety = tag.getString("GrapeVariety");
        this.quality = tag.getInt("Quality");
        if (tag.contains("BottlesRemaining")) {
            this.bottlesRemaining = tag.getInt("BottlesRemaining");
        }
        if (tag.contains("GrapeRegion")) {
            this.region = tag.getString("GrapeRegion");
        } else {
            this.region = "Britannia"; 
        }
        // New Tag
        if (tag.contains("PitchersStored")) {
            this.pitchersStored = tag.getInt("PitchersStored");
        } else {
            this.pitchersStored = 0;
        }
    }

    // --- DATA SYNCHRONIZATION ---
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
    }
}
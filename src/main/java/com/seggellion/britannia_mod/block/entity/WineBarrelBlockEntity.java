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
    private String variety = "";
    private int quality = 0;
   private static final Logger LOGGER = LogUtils.getLogger();
    // Fermentation State
    private boolean isFermenting = false;
    private boolean isReady = false;
    private int fermentationTicks = 0;
private String region = "Britannia";
    private int bottlesRemaining = 0;
    public static final int MAX_BOTTLES = 300;

    // 7 Minecraft Days = 168,000 ticks
   // public static final int MAX_FERMENTATION_TIME = 168000;
 public static final int MAX_FERMENTATION_TIME = 1000;
    public WineBarrelBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.WINE_BARREL_BE.get(), pos, blockState);
    }

    public void startFermentation(String variety, int quality, String region) {
        this.variety = variety;
        this.quality = quality;
        this.isFermenting = true;
        this.region = region;
        this.isReady = false;
        this.fermentationTicks = 0;
        this.setChanged();
        this.notifyUpdate(); // Sync to client immediately
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

        // Save progress occasionally
        if (be.fermentationTicks % 2000 == 0) {
            be.setChanged();
        }
    }


    public String getVariety() { 
        return this.variety; 
    }

    public int getQuality() { 
        return this.quality; 
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

    public void emptyBarrel() {
        this.isReady = false;
        this.isFermenting = false;
        this.fermentationTicks = 0;
        this.quality = 0;
       this.region = "Britannia";
        this.variety = "";
        this.setChanged();
        this.bottlesRemaining = 0;
        this.notifyUpdate(); // Syncs to client so the bubbles stop
    }

    public String getRegion() {
        return this.region;
    }

    public boolean extractOneBottle() {
        if (this.bottlesRemaining > 0) {
            this.bottlesRemaining--;
            
            // If that was the last bottle, empty the barrel automatically
            if (this.bottlesRemaining <= 0) {
                emptyBarrel();
            } else {
                this.setChanged(); 

            }
            return true;
        }
        return false;
    }

    private void animateFermentation(Level level, BlockPos pos) {
        // Use random chance to control frequency based on fermentation stage
        // Starts slow, gets fast, slows down at end
        float chance = 0.05f; // Default 5% chance per tick (1 bubble per second)

        // Simple visual logic:
        if (level.random.nextFloat() < chance) {
            // Spawn a "Bubble Pop" particle at the top center of the barrel (the airlock)
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.1; // Just above the block
            double z = pos.getZ() + 0.5;
            
            level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.01, 0.0);
            
            // Optional: Add a subtle "spell effect" swirl occasionally
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
        this.notifyUpdate(); // Sync to client
    }
    
    // Helper to trigger a block update so the client gets the new NBT data
    private void notifyUpdate() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public String getBubblerStatus() {
        if (isReady) return "The airlock is silent. The wine is ready for bottling.";
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
            this.region = "Britannia"; // Legacy support
        }
    }

    // --- DATA SYNCHRONIZATION (Crucial for Client visuals) ---

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
        // This ensures the client reads the tag when the packet arrives
        // (loadAdditional is called automatically by super logic in most cases, but this is safe)
    }
}
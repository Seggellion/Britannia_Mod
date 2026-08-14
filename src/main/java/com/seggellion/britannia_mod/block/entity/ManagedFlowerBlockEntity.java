package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Synchronized rendering projection of the authoritative managed-node flower state. */
public final class ManagedFlowerBlockEntity extends BlockEntity {
    private static final String SPECIES_KEY = "SpeciesId";
    private static final String STAGE_KEY = "GrowthStage";

    @Nullable
    private ResourceLocation speciesId;
    private int growthStage;

    public ManagedFlowerBlockEntity(BlockPos position, BlockState state) {
        super(BlockEntityRegistry.MANAGED_FLOWER_BE.get(), position, state);
    }

    public boolean initialize(ResourceLocation species, int stage) {
        if (speciesId != null || !valid(species, stage)) {
            return false;
        }
        speciesId = species;
        growthStage = stage;
        setChangedAndSync();
        return true;
    }

    public boolean setGrowthStage(ResourceLocation species, int stage) {
        if (!valid(species, stage) || speciesId == null || !speciesId.equals(species)
                || growthStage == stage) {
            return false;
        }
        growthStage = stage;
        setChangedAndSync();
        return true;
    }

    public Optional<ResourceLocation> speciesId() {
        return Optional.ofNullable(speciesId);
    }

    public int growthStage() {
        return growthStage;
    }

    public int visualTint() {
        FlowerRegistry registry = FlowerRegistry.initial();
        FlowerDefinition definition = speciesId == null ? null : registry.byId(speciesId).orElse(null);
        return definition == null ? 0xFFFFFF : registry.fallbackColor(definition).tintValue();
    }

    public boolean matches(ResourceLocation species, int stage) {
        return speciesId != null && speciesId.equals(species) && growthStage == stage;
    }

    private static boolean valid(ResourceLocation species, int stage) {
        return species != null && stage >= 1 && stage <= 7
                && FlowerRegistry.initial().byId(species).isPresent();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (speciesId != null) {
            tag.putString(SPECIES_KEY, speciesId.toString());
            tag.putInt(STAGE_KEY, growthStage);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speciesId = null;
        growthStage = 0;
        ResourceLocation loadedSpecies = ResourceLocation.tryParse(tag.getString(SPECIES_KEY));
        int loadedStage = tag.getInt(STAGE_KEY);
        if (valid(loadedSpecies, loadedStage)) {
            speciesId = loadedSpecies;
            growthStage = loadedStage;
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

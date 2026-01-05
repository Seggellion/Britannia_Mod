package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.component.WineData; // Your record from Day 4
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WineBottleBlockEntity extends BlockEntity {
    private WineData wineData = WineData.EMPTY;

    public WineBottleBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.WINE_BOTTLE_BE.get(), pos, blockState);
    }

    public void setWineData(WineData data) {
        this.wineData = data;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public WineData getWineData() {
        return wineData;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Save the record manually to NBT
        tag.putString("WineryName", wineData.wineryName());
        tag.putString("GrapeType", wineData.grapeType());
        tag.putInt("Year", wineData.year());
        tag.putInt("Quality", wineData.quality());
        tag.putString("Region", wineData.region());
        tag.putString("LabelColor", wineData.labelColor());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.wineData = new WineData(
            tag.getString("WineryName"),
            tag.getString("GrapeType"),
            tag.getInt("Year"),
            tag.getInt("Quality"),
            tag.getString("Region"),
            tag.contains("LabelColor") ? tag.getString("LabelColor") : "red"
        );
    }

    // Sync for client-side rendering (if you want custom labels later)
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
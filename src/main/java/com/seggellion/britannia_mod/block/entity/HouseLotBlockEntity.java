package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.house.HouseSize;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;

import java.util.UUID;

public class HouseLotBlockEntity extends BlockEntity {
    private UUID ownerUUID;
    private String ownerUsername;
    private HouseSize size;
    private String regionName;
    private boolean forSale = false;
    private int price = 0;
    private long placedAt = System.currentTimeMillis();

    public HouseLotBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.HOUSE_LOT_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    // ✅ Correctly named for 1.21.1 — loadAdditional instead of load()
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("ownerUUID", Tag.TAG_INT_ARRAY)) this.ownerUUID = tag.getUUID("ownerUUID");
        if (tag.contains("ownerUsername", Tag.TAG_STRING)) this.ownerUsername = tag.getString("ownerUsername");
        if (tag.contains("houseSize", Tag.TAG_STRING)) this.size = HouseSize.valueOf(tag.getString("houseSize"));
        if (tag.contains("regionName", Tag.TAG_STRING)) this.regionName = tag.getString("regionName");
        if (tag.contains("forSale", Tag.TAG_BYTE)) this.forSale = tag.getBoolean("forSale");
        if (tag.contains("price", Tag.TAG_INT)) this.price = tag.getInt("price");
        if (tag.contains("placedAt", Tag.TAG_LONG)) this.placedAt = tag.getLong("placedAt");
    }

    // ✅ Correct signature for 1.21.1+ — must accept HolderLookup.Provider
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        if (ownerUUID != null) tag.putUUID("ownerUUID", ownerUUID);
        if (ownerUsername != null) tag.putString("ownerUsername", ownerUsername);
        if (size != null) tag.putString("houseSize", size.name());
        if (regionName != null) tag.putString("regionName", regionName);
        tag.putBoolean("forSale", forSale);
        tag.putInt("price", price);
        tag.putLong("placedAt", placedAt);
    }
}
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.block.AdaptiveRoofBlock;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;


import net.minecraft.network.Connection;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;


public class AdaptiveRoofBlockEntity extends BlockEntity {


    private static final Logger LOGGER = LogUtils.getLogger();


 private ResourceLocation bottomTexture = null;
private static final String TAG_KEY = "BottomTexture";

    public AdaptiveRoofBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ADAPTIVE_ROOF.get(), pos, state);
    }

    public void setBottomTexture(ResourceLocation texture) {
    this.bottomTexture = "minecraft:block/air".equals(texture.toString()) ? null : texture;
    boolean supports = bottomTexture != null;

    setChanged();

    if (level instanceof ServerLevel server) {
        server.blockEntityChanged(worldPosition);
    }

    BlockState current = getBlockState();

    // Determine if the block supports the lantern property and update accordingly
    if (current.hasProperty(TopOnlySlabBlock.SUPPORTS_LANTERN)) {
        if (current.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN) != supports) {
            level.setBlock(worldPosition,
                           current.setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, supports),
                           Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        } else if (!level.isClientSide) {
            level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
        }
    } else if (current.hasProperty(AdaptiveRoofBlock.SUPPORTS_LANTERN)) {
        if (current.getValue(AdaptiveRoofBlock.SUPPORTS_LANTERN) != supports) {
            level.setBlock(worldPosition,
                           current.setValue(AdaptiveRoofBlock.SUPPORTS_LANTERN, supports),
                           Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        } else if (!level.isClientSide) {
            level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
        }
    }

    if (!level.isClientSide) {
        level.getChunkAt(worldPosition).setUnsaved(true);
    }
}


    public ResourceLocation getBottomTexture() {
        return bottomTexture;
    }

@Override
public void onLoad() {
    super.onLoad();

    if (!level.isClientSide) {
 
        // Fix legacy blocks with missing textures but incorrect blockstate
        if (bottomTexture == null) {
            BlockState current = getBlockState();

            if (current.hasProperty(TopOnlySlabBlock.SUPPORTS_LANTERN) &&
                current.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN)) {
                level.setBlock(worldPosition,
                    current.setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, false),
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            } else if (current.hasProperty(AdaptiveRoofBlock.SUPPORTS_LANTERN) &&
                       current.getValue(AdaptiveRoofBlock.SUPPORTS_LANTERN)) {
                level.setBlock(worldPosition,
                    current.setValue(AdaptiveRoofBlock.SUPPORTS_LANTERN, false),
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
        }

        ((ServerLevel) level).blockEntityChanged(worldPosition);
    }
}

@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.saveAdditional(tag, provider);
    if (bottomTexture != null) {
    tag.putString("BottomTexture", bottomTexture.toString());
    }
}


@Override
public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
    CompoundTag tag = super.getUpdateTag(provider);
    if (bottomTexture != null) {
        tag.putString(TAG_KEY, bottomTexture.toString());
    }
    return tag;
}

public void handleUpdateTag(CompoundTag tag) {
    if (tag.contains("BottomTexture")) {
        this.bottomTexture = ResourceLocation.tryParse(tag.getString("BottomTexture"));
        readTexture(tag); 
    }
}

@Override
public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}


public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
    handleUpdateTag(pkt.getTag());
}


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("BottomTexture")) {
            bottomTexture = ResourceLocation.tryParse(tag.getString("BottomTexture"));
               readTexture(tag);    
        }
    }

/* ------------------------------------------------------------ */
/*  Helper – parse tag and notify renderer                      */
/* ------------------------------------------------------------ */
private void readTexture(CompoundTag tag) {
    if (!tag.contains("BottomTexture")) return;

    bottomTexture = ResourceLocation.tryParse(tag.getString("BottomTexture"));

    // If we’re on the logical client, force the chunk to re‑render
    if (level != null && level.isClientSide) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                               Block.UPDATE_CLIENTS);        // <‑‑ refresh
    }
}



}

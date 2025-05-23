package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;

public class HouseSignBlockEntity extends BlockEntity {
    private String ownerUsername = "";

    public HouseSignBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.HOUSE_SIGN.get(), pos, state);
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putString("owner", ownerUsername);

        BlockState state = getBlockState();
        HouseSignBlock.HolderType holderType = state.getValue(HouseSignBlock.HOLDER_TYPE);
        HouseSignBlock.SignType signType = state.getValue(HouseSignBlock.SIGN_TYPE);

        tag.putString("holder_type", holderType.getSerializedName());
        tag.putString("sign_type", signType.getSerializedName());
        tag.putString("sign_shape", signType.getShape().name().toLowerCase());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerUsername = tag.getString("owner");
    }
}
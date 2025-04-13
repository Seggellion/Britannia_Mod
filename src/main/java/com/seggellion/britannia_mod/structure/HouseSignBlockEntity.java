package com.seggellion.britannia_mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;


public class HouseSignBlockEntity extends BlockEntity {
    private String ownerUsername = "";

    public HouseSignBlockEntity(BlockPos pos, BlockState state) {
        // Assume you have registered a BlockEntityType in your BlockEntityRegistry
        super(BlockEntityRegistry.HOUSE_SIGN.get(), pos, state);
    }
    
    public String getOwnerUsername() {
        return ownerUsername;
    }
    
    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
        setChanged();
    }
    
    // In a full solution, override load() and saveAdditional() to persist ownerUsername.
}

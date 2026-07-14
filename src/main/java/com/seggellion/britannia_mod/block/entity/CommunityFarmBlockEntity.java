package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.block.CommunityFarmBlock;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CommunityFarmBlockEntity extends BlockEntity {
    public static final long PREPARED_EXPIRY_TICKS = 3600L;
    private long preparedExpiresAt = 0L;

    public CommunityFarmBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.COMMUNITY_FARM_BLOCK_BE.get(), pos, blockState);
    }

    public void markPrepared(long gameTime) {
        this.preparedExpiresAt = gameTime + PREPARED_EXPIRY_TICKS;
        setChanged();
    }

    public void clearPreparedExpiry() {
        this.preparedExpiresAt = 0L;
        setChanged();
    }

    public long getPreparedExpiresAt() {
        return preparedExpiresAt;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CommunityFarmBlockEntity blockEntity) {
        boolean prepared = state.getBlock() instanceof CommunityHoedFarmBlock
                || (state.getBlock() instanceof CommunityFarmBlock && state.getValue(CommunityFarmBlock.PREPARED));
        if (!prepared) {
            if (blockEntity.preparedExpiresAt != 0L) {
                blockEntity.clearPreparedExpiry();
            }
            return;
        }

        if (blockEntity.preparedExpiresAt <= 0L) {
            blockEntity.markPrepared(level.getGameTime());
            return;
        }

        if (blockEntity.preparedExpiresAt > 0L && level.getGameTime() >= blockEntity.preparedExpiresAt) {
            blockEntity.preparedExpiresAt = 0L;
            blockEntity.setChanged();
            level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("PreparedExpiresAt", preparedExpiresAt);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        preparedExpiresAt = tag.getLong("PreparedExpiresAt");
    }
}

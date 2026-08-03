package com.seggellion.britannia_mod.structure.multiblock;

import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative owner of a placed structure; persistence is intentionally deferred. */
public final class LargeStructureAnchorBlockEntity extends BlockEntity {
    private Optional<PlacedStructureState> placedState = Optional.empty();

    public LargeStructureAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(LargeStructureRegistry.LARGE_STRUCTURE.get(), pos, state);
    }

    public Optional<PlacedStructureState> placedState() {
        return placedState;
    }

    public boolean initialize(PlacedStructureState state) {
        if (state == null || placedState.isPresent()) {
            return false;
        }
        placedState = Optional.of(state);
        setChanged();
        return true;
    }

    public void synchronize() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}

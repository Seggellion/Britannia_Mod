package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.chess.ChessGameState;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ChessBoardBlockEntity extends BlockEntity {
    private final ChessGameState gameState = new ChessGameState();

    public ChessBoardBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.CHESS_BOARD.get(), pos, state);
    }

    public ChessGameState gameState() {
        return gameState;
    }

    public boolean move(int fromX, int fromY, int toX, int toY) {
        boolean moved = gameState.move(fromX, fromY, toX, toY);
        if (moved) {
            setChanged();
        }
        return moved;
    }

    public void resetGame() {
        gameState.reset();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        gameState.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        gameState.load(tag);
    }
}

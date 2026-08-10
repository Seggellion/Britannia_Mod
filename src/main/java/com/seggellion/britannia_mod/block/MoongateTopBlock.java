package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Compatibility-only remnant of the former two-cell city moongate. */
@Deprecated(forRemoval = false)
public class MoongateTopBlock extends Block {
    public MoongateTopBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(-1.0F, 3600000.0F) // Unbreakable and explosion-proof
                .noLootTable() // No drops when broken
                .noCollission() // Players can walk through
                .lightLevel((state) -> 15) // Emits maximum light
                .sound(SoundType.GLASS) // Sound type when interacted
                .randomTicks()
        );
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        removeLegacyCell(level, pos, state);
    }

    @Override
    public void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        removeLegacyCell(level, pos, state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        removeLegacyCell(level, pos, state);
    }

    private static void removeLegacyCell(Level level, BlockPos pos, BlockState expectedState) {
        if (!level.isClientSide && level.getBlockState(pos) == expectedState) {
            level.removeBlock(pos, false);
        }
    }
}

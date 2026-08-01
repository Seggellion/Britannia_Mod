package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.FlowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** One generic soil-hosting flower block for every species and stage. */
public final class FlowerBlock extends Block implements EntityBlock {
    public static final IntegerProperty HYDRATION = FarmingBlock.HYDRATION;
    public static final IntegerProperty FERTILIZER = FarmingBlock.FERTILIZER;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public FlowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HYDRATION, 0)
                .setValue(FERTILIZER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HYDRATION, FERTILIZER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof FlowerBlockEntity flower) || !flower.isInitialized()) {
            return;
        }

        int hydration = flower.hydration();
        if (state.getValue(HYDRATION) != hydration) {
            level.setBlock(pos, state.setValue(HYDRATION, hydration), 3);
            state = level.getBlockState(pos);
        }

        if (FarmingBlock.shouldDecayHydration(hydration, random)) {
            hydration--;
        }
        hydration = FarmingBlock.hydrationAfterRain(level, pos, hydration);

        if (state.getValue(HYDRATION) != hydration) {
            level.setBlock(pos, state.setValue(HYDRATION, hydration), 3);
        }
        flower.tickGrowth(level, hydration);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlowerBlockEntity(pos, state);
    }
}

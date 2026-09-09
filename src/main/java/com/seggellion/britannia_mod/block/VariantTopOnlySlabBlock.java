package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Reusable top-only adaptive roof whose selected texture variation persists in block state.
 *
 * <p>The logical server is the sole source of placement randomness. A client placement pass
 * keeps the default variation until the authoritative state arrives, so texture selection is
 * never tied to rendering or to a client-only random stream.</p>
 */
public class VariantTopOnlySlabBlock extends TopOnlySlabBlock implements VariantCyclable {
    public static final IntegerProperty VARIATION = IntegerProperty.create("variation", 0, 5);

    public VariantTopOnlySlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIATION, 0));
    }

    @Override
    public IntegerProperty variationProperty() {
        return VARIATION;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : applyPlacementVariation(
                state, context.getLevel().getRandom(), context.getLevel().isClientSide());
    }

    BlockState applyPlacementVariation(BlockState state, RandomSource random, boolean clientSide) {
        if (clientSide) {
            return state;
        }
        return state.setValue(VARIATION, random.nextInt(VARIATION.getPossibleValues().size()));
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        // Main-hand decoration must reach the item's bottom-clear handler. The offhand
        // retains top cycling without touching the acquired lower texture.
        return hand == InteractionHand.OFF_HAND
                ? cycleVariation(stack, state, level, pos, player)
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}

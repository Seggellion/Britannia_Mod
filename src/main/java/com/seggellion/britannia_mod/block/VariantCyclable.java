package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Shared behaviour for blocks whose texture is swapped by right-clicking with the
 * interior decorator tool. The cycle length is read straight off the block state
 * property, so a block only has to declare its {@link IntegerProperty} once and the
 * blockstate JSON stays the single source of truth for how many variants exist.
 */
public interface VariantCyclable {

    /** The property holding the current texture index. Must be a {@code static} constant. */
    IntegerProperty variationProperty();

    /**
     * Advances to the next texture when the player is holding the interior decorator tool.
     * Call this from {@code useItemOn}; it returns
     * {@link ItemInteractionResult#PASS_TO_DEFAULT_BLOCK_INTERACTION} when the item is not the tool.
     */
    default ItemInteractionResult cycleVariation(ItemStack stack, BlockState state, Level level,
                                                 BlockPos pos, Player player) {

        if (player.isSpectator() || !stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide()) {
            IntegerProperty variation = variationProperty();
            int next = (state.getValue(variation) + 1) % variation.getPossibleValues().size();

            level.setBlock(pos, state.setValue(variation, next), Block.UPDATE_ALL);
            level.playSound(null, pos, state.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS, 0.5f, 1.2f);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}

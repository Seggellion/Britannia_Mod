package com.seggellion.britannia_mod.textile;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Immediate, server-authoritative textile exchanges with no hidden machine inventory. */
public final class TextileProcessing {
    public static final int SPIN_INPUT_COUNT = 1;
    public static final int WEAVE_INPUT_COUNT = 5;

    private TextileProcessing() {
    }

    public static Optional<Recipe> spinningRecipe(ItemStack input) {
        if (input.is(ItemTags.WOOL)) {
            return Optional.of(new Recipe(SPIN_INPUT_COUNT, ItemRegistry.BALL_OF_YARN.get()));
        }
        if (input.is(ItemRegistry.COTTON.get()) || input.is(ItemRegistry.FLAX.get())) {
            return Optional.of(new Recipe(SPIN_INPUT_COUNT, ItemRegistry.SPOOL_OF_THREAD.get()));
        }
        return Optional.empty();
    }

    public static Optional<Recipe> loomRecipe(ItemStack input) {
        if (input.is(ItemRegistry.BALL_OF_YARN.get()) || input.is(ItemRegistry.SPOOL_OF_THREAD.get())) {
            return Optional.of(new Recipe(WEAVE_INPUT_COUNT, ItemRegistry.FOLDED_CLOTH_ITEM.get()));
        }
        return Optional.empty();
    }

    public static ItemInteractionResult spin(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack input) {
        return exchange(level, pos, player, input, spinningRecipe(input), SoundEvents.WOOL_PLACE);
    }

    public static ItemInteractionResult weave(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack input) {
        return exchange(level, pos, player, input, loomRecipe(input), SoundEvents.UI_LOOM_TAKE_RESULT);
    }

    private static ItemInteractionResult exchange(
            Level level,
            BlockPos pos,
            Player player,
            ItemStack input,
            Optional<Recipe> candidate,
            net.minecraft.sounds.SoundEvent sound) {
        if (candidate.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Recipe recipe = candidate.orElseThrow();
        if (input.getCount() < recipe.inputCount()) {
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            if (!player.hasInfiniteMaterials()) {
                input.shrink(recipe.inputCount());
            }
            ItemStack output = new ItemStack(recipe.output());
            if (!player.getInventory().add(output)) {
                player.drop(output, false);
            }
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public record Recipe(int inputCount, Item output) {
        public Recipe {
            if (inputCount <= 0) throw new IllegalArgumentException("inputCount must be positive");
        }
    }
}

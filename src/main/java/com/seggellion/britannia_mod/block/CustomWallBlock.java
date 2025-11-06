package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public abstract class CustomWallBlock extends Block {

    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 15);
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    public CustomWallBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(VARIANT, 0)
            .setValue(MIRRORED, false));
    }

    public abstract List<String> getTextureVariants();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, MIRRORED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RandomSource random = context.getLevel().random;
        int randomIndex = random.nextInt(getTextureVariants().size());
        boolean mirror = random.nextBoolean();
        return this.defaultBlockState()
            .setValue(VARIANT, randomIndex)
            .setValue(MIRRORED, mirror);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()
            && stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())
            && !player.isSpectator()) {

            int currentVariant = state.getValue(VARIANT);
            boolean mirrored = state.getValue(MIRRORED);

            // Toggle mirrored after cycling through all variants
            int variantCount = getTextureVariants().size();
            int nextVariant = currentVariant;
            boolean nextMirrored = mirrored;

            // Cycle: variant0→variant1→...→lastVariant→sameVariant but mirrored→repeat
            if (!mirrored) {
                if (currentVariant + 1 < variantCount) {
                    nextVariant = currentVariant + 1;
                } else {
                    nextVariant = currentVariant;
                    nextMirrored = true;
                }
            } else {
                if (currentVariant > 0) {
                    nextVariant = currentVariant - 1;
                } else {
                    nextVariant = 0;
                    nextMirrored = false;
                }
            }

            level.setBlock(pos, state.setValue(VARIANT, nextVariant).setValue(MIRRORED, nextMirrored), 3);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}

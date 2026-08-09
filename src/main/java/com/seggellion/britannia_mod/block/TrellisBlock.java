package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.EquipmentSlot; 

public class TrellisBlock extends Block {
    // A cross-shape or post-shape similar to fences/panes

private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 32, 16);
    public TrellisBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

@Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        
        // 1. Check if the player is holding an Axe
        if (stack.is(ItemTags.AXES)) {
            
            if (!level.isClientSide) {
                // 2. Play Sound
                level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);

                // 3. Drop the Trellis Item
                popResource(level, pos, new ItemStack(ItemRegistry.TRELLIS_ITEM.get()));

                // 4. Destroy the block
                level.destroyBlock(pos, false);

                // 5. Damage the Axe
                // FIX: In 1.21, hurtAndBreak takes (amount, entity, EquipmentSlot)
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            // Return success (handles animation/swing)
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos farmPos = pos.below();
        BlockState farmState = level.getBlockState(farmPos);
        if (farmState.getBlock() instanceof FarmingBlock) {
            ItemInteractionResult plantResult = FarmingBlock.tryPlantSeed(level, farmPos, farmState, player, stack, true, "trellis_block");
            if (plantResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return plantResult;
            }

            ItemInteractionResult careResult = FarmingBlock.applyCareItem(level, farmPos, farmState, player, hand, stack);
            if (careResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return careResult;
            }

            if (level.getBlockEntity(farmPos) instanceof FarmingBlockEntity farmBe) {
                ItemInteractionResult harvestResult = FarmingBlock.tryHarvestCrop(farmBe, farmState, level, farmPos, player, stack, hand, "trellis_block");
                if (harvestResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                    return harvestResult;
                }
                if (stack.is(ItemRegistry.SCISSORS.get()) && farmBe.hasCrop()) {
                    CropDefinition crop = CropRegistry.byId(farmBe.getPlantedCropId()).orElse(null);
                    if (crop != null && !level.isClientSide) {
                        player.displayClientMessage(Component.literal(crop.displayName() + " is not ready to harvest.").withStyle(ChatFormatting.YELLOW), true);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        // Pass event if not holding an axe
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos farmPos = pos.below();
        BlockState farmState = level.getBlockState(farmPos);
        BlockEntity blockEntity = level.getBlockEntity(farmPos);
        if (farmState.getBlock() instanceof FarmingBlock && blockEntity instanceof FarmingBlockEntity farmBe) {
            ItemInteractionResult result = FarmingBlock.tryHarvestCrop(farmBe, farmState, level, farmPos, player, ItemStack.EMPTY, InteractionHand.MAIN_HAND, "trellis_block");
            return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    ? InteractionResult.PASS
                    : InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

// --- CRITICAL: Make Unbreakable without an Axe ---
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.getMainHandItem().is(ItemTags.AXES)) {
            return 0.0f; // Unbreakable
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    // --- Drops ---
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            // Drop the Trellis Item manually
            popResource(level, pos, new ItemStack(ItemRegistry.TRELLIS_ITEM.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

}

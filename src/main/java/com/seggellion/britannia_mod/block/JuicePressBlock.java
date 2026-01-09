package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.JuicePressBlockEntity;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.mojang.serialization.MapCodec; // Import this

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class JuicePressBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<JuicePressBlock> CODEC = simpleCodec(JuicePressBlock::new);

    public JuicePressBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JuicePressBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof JuicePressBlockEntity press)) return ItemInteractionResult.FAIL;

        // 1. Insert Grapes
        if (stack.getItem() instanceof GrapesItem) {
            // If the press is already full, stop here
            if (press.isFull()) {
                player.displayClientMessage(Component.literal("The press is full (50/50). Use an empty pitcher."), true);
                return ItemInteractionResult.SUCCESS;
            }

            // Check compatibility before attempting logic
            String variety = GrapesItem.getVariety(stack);
            String region = GrapesItem.getRegion(stack);
            
            // If press has grapes, prevent mixing
            if (!press.isEmpty() && (!press.getVariety().equals(variety))) {
                player.displayClientMessage(Component.literal("Cannot mix grape varieties! Press contains: " + press.getVariety()), true);
                return ItemInteractionResult.SUCCESS;
            }

            CompoundTag itemTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
            int quality = itemTag.contains("Quality") ? itemTag.getInt("Quality") : 50;

            // Attempt to add as many as possible from the hand
            int amountToAdd = stack.getCount();
            int consumed = press.addGrapes(variety, quality, region, amountToAdd);

            if (consumed > 0) {
                stack.shrink(consumed);
                level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
                
                // Feedback message
                player.displayClientMessage(Component.literal("Added " + consumed + " " + variety + " grapes. (" + press.getGrapeCount() + "/50)"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // 2. Extract Juice
        if (stack.getItem() == ItemRegistry.PITCHER_EMPTY.get()) {
            if (press.isFull()) {
                JuicePressBlockEntity.JuiceData data = press.extractJuice();
                stack.shrink(1);

                // DETERMINE OUTPUT ITEM BASED ON COLOR
                GrapeVariety varietyInfo = GrapeVarietyManager.getVariety(data.variety());
                
                // Default to Red Juice
                Item resultItem = ItemRegistry.PITCHER_RED_GRAPE_JUICE.get(); 
                
                if (varietyInfo != null) {
                    GrapeColor color = varietyInfo.colorType(); 
                    
                    if (color == GrapeColor.GREEN || 
                        color == GrapeColor.YELLOW || 
                        color == GrapeColor.LIGHT_GREEN || 
                        color == GrapeColor.DARK_GREEN) {
                        
                        resultItem = ItemRegistry.PITCHER_WHITE_GRAPE_JUICE.get();
                    }
                }

                // Create the specific pitcher
                ItemStack fullPitcher = new ItemStack(resultItem);
                
                CompoundTag newTag = new CompoundTag();
                newTag.putString("GrapeVariety", data.variety());
                newTag.putInt("Quality", data.quality());
                newTag.putString("GrapeRegion", data.region());
                fullPitcher.set(DataComponents.CUSTOM_DATA, CustomData.of(newTag));
                
                if (!player.getInventory().add(fullPitcher)) {
                    player.drop(fullPitcher, false);
                }

                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                return ItemInteractionResult.SUCCESS;
            } else {
                // Feedback if they try to extract too early
                if (!press.isEmpty()) {
                    player.displayClientMessage(Component.literal("Not enough grapes! (" + press.getGrapeCount() + "/50)"), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
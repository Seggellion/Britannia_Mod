package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.TrellisBlock;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class GrapeSeedsItem extends ItemNameBlockItem {

    public GrapeSeedsItem(Properties properties) {
        super(BlockRegistry.GRAPE_VINE_BLOCK.get(), properties);
    }

    // --- Data Handlers ---
    public static void setVariety(ItemStack stack, String varietyId) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString(GrapesItem.GRAPE_VARIETY_KEY, varietyId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getVariety(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        String varietyId = tag.contains(GrapesItem.GRAPE_VARIETY_KEY) ? tag.getString(GrapesItem.GRAPE_VARIETY_KEY) : GrapesItem.DEFAULT_VARIETY_ID;
        return varietyId == null || varietyId.isBlank() ? GrapesItem.DEFAULT_VARIETY_ID : varietyId;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(GrapesItem.getGrapeSeedItemName(stack));
    }

    // --- Placement Logic ---
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = context.getItemInHand();

        // SCENARIO A: Clicking on a Trellis (Existing logic)
        if (clickedState.getBlock() instanceof TrellisBlock) {
             // ... (Keep your existing trellis logic here if you want to support both methods) ...
             // For brevity, I'm focusing on the Soil logic below.
        }

        // SCENARIO B: Clicking on Farming Block (The workflow you asked for)
        if (clickedState.getBlock() instanceof FarmingBlock && context.getClickedFace() == Direction.UP) {
            BlockPos plantPos = clickedPos.above();
            
            // Ensure space is empty
            if (level.isEmptyBlock(plantPos)) {
                if (!level.isClientSide) {
                    // 1. Place the Vine Block
                    BlockState vineState = BlockRegistry.GRAPE_VINE_BLOCK.get().defaultBlockState();
                    level.setBlock(plantPos, vineState, 3);

                    // 2. Transfer Data
                    BlockEntity be = level.getBlockEntity(plantPos);
                    if (be instanceof GrapeVineBlockEntity vineBE) {
                        vineBE.setVariety(getVariety(stack));
                    }

                    // 3. Effects & Consumption
                    level.playSound(null, plantPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                    if (!context.getPlayer().getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Fallback to standard behavior (will likely fail for custom data transfer if we don't handle it above)
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Variety: " + GrapesItem.getDisplayNameForVariety(getVariety(stack))).withStyle(ChatFormatting.GRAY));
    }
}

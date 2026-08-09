package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.mojang.logging.LogUtils;
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
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;

public class GrapeSeedsItem extends ItemNameBlockItem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_GRAPE_FLOW = false;

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

        if (clickedState.getBlock() instanceof FarmingBlock && context.getClickedFace() == Direction.UP) {
            BlockEntity be = level.getBlockEntity(clickedPos);
            CropDefinition crop = CropRegistry.byId("grapes").orElse(null);
            if (!(be instanceof FarmingBlockEntity farmBe) || crop == null) {
                return InteractionResult.FAIL;
            }
            if (farmBe.hasCrop() || clickedState.getValue(FarmingBlock.HAS_SEEDS)) {
                if (!level.isClientSide && context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.literal("A crop is already planted here.").withStyle(ChatFormatting.YELLOW), true);
                }
                return InteractionResult.SUCCESS;
            }

            if (!level.isClientSide) {
                String varietyId = getVariety(stack);
                farmBe.plant(crop, varietyId);
                level.setBlock(clickedPos, clickedState.setValue(FarmingBlock.HAS_SEEDS, true), 3);
                debugSeedPlacement(stack, clickedPos, varietyId);
                level.playSound(null, clickedPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                    FarmingSkill.award(serverPlayer, FarmingActionType.PLANT, crop.tier(), crop.farmingSkillModifier());
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Fallback to standard behavior (will likely fail for custom data transfer if we don't handle it above)
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Variety: " + GrapesItem.getDisplayNameForVariety(getVariety(stack))).withStyle(ChatFormatting.GRAY));
    }

    private static void debugSeedPlacement(ItemStack stack, BlockPos pos, String varietyId) {
        if (!DEBUG_GRAPE_FLOW) {
            return;
        }
        GrapeVariety variety = GrapeVarietyManager.getVariety(varietyId);
        LOGGER.info(
            "Grape seed placement at {}: seedVarietyId={}, displayName={}, colorType={}, baseColor=0x{}",
            pos,
            varietyId,
            variety.getFormattedName(),
            variety.colorType(),
            Integer.toHexString(variety.baseColor())
        );
    }
}

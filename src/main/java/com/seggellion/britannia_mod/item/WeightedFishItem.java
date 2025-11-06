package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.HorizontalFacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class WeightedFishItem extends BlockItem {
    private static final Logger LOGGER = LogManager.getLogger();

    // Allow-list of ground surfaces the fish can be placed on
    private static final TagKey<Block> PLACEABLE_ON_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("britannia_mod:fish_placeable_on"));

    public WeightedFishItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();

        // 1) Try our controlled ground-placement first (server actually sets the block)
        boolean placed = tryPlaceGroundFish(ctx);
        if (placed) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // 2) If we didn't place, let vanilla handle Survival/Creative as usual
        return super.useOn(ctx);
    }

    private boolean tryPlaceGroundFish(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide()) {
            // Client mirrors server decision via sidedSuccess; do nothing here.
            return canPlaceHereClientCheck(ctx); // return a best-effort guess so hand anim feels right
        }

        // Only allow placing on top face (ground placement). Side faces can be added later if needed.
        if (ctx.getClickedFace() != Direction.UP) return false;

        BlockPos clickedPos = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        // If clicked block is replaceable, place there; otherwise, place above it.
        boolean clickedReplaceable = clickedState.canBeReplaced();
        BlockPos placePos = clickedReplaceable ? clickedPos : clickedPos.above();

        // Target must be empty/replaceable
        BlockState targetState = level.getBlockState(placePos);
        if (!targetState.isAir() && !targetState.canBeReplaced()) {
            // LOGGER.debug("Fish place failed: target not empty at {}", placePos);
            return false;
        }

        // Support must match our tag (block below the target)
        BlockPos supportPos = placePos.below();
        if (!level.getBlockState(supportPos).is(PLACEABLE_ON_TAG)) {
            // LOGGER.debug("Fish place failed: support {} not in tag britannia_mod:fish_placeable_on", supportPos);
            return false;
        }

        // Build final state with facing toward the player
        BlockState stateToPlace = this.getBlock().defaultBlockState();
        if (stateToPlace.hasProperty(HorizontalFacingBlock.FACING)) {
            stateToPlace = stateToPlace.setValue(HorizontalFacingBlock.FACING, ctx.getHorizontalDirection().getOpposite());
        }

        if (level.setBlock(placePos, stateToPlace, 3)) {
            if (!ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
            return true;
        }
        return false;
    }

    // Client-side heuristic so right-click still “feels” successful when server will place.
    private boolean canPlaceHereClientCheck(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) return false;
        Level level = ctx.getLevel();
        BlockPos clickedPos = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        boolean clickedReplaceable = clickedState.canBeReplaced();
        BlockPos placePos = clickedReplaceable ? clickedPos : clickedPos.above();
        BlockPos supportPos = placePos.below();
        return (level.getBlockState(placePos).isAir() || level.getBlockState(placePos).canBeReplaced())
                && level.getBlockState(supportPos).is(PLACEABLE_ON_TAG);
    }

    // ===== Your existing custom data & UI =====
    private static final net.minecraft.core.component.DataComponentType<CustomData> CUSTOM_DATA = DataComponents.CUSTOM_DATA;

    public void setWeight(ItemStack stack, double weight) {
        CustomData cd = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = cd.copyTag();
        tag.putDouble("FishWeight", weight);
        stack.set(CUSTOM_DATA, CustomData.of(tag));
    }

    public double getWeight(ItemStack stack) {
        CustomData cd = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = cd.copyTag();
        return tag.contains("FishWeight") ? tag.getDouble("FishWeight") : 0.0;
    }

    public void setFishType(ItemStack stack, String fishType) {
        CustomData cd = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = cd.copyTag();
        tag.putString("FishType", fishType);
        stack.set(CUSTOM_DATA, CustomData.of(tag));
    }

    public String getFishType(ItemStack stack) {
        CustomData cd = stack.getOrDefault(CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag tag = cd.copyTag();
        return tag.contains("FishType") ? tag.getString("FishType") : "unknown";
    }

    @Override
    public Component getName(ItemStack stack) {
        Component baseName = super.getName(stack);
        double weight = getWeight(stack);
        String displayName = baseName.getString() + " (" + String.format("%.2f", weight) + " stones)";
        return Component.literal(displayName);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String fishType = getFishType(stack);
        if (!"unknown".equals(fishType)) {
            tooltip.add(Component.literal("Type: " + fishType));
        }
        double weight = getWeight(stack);
        tooltip.add(Component.literal("Weight: " + String.format("%.2f", weight) + " stones"));
    }
}

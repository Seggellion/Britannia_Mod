package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.farming.CropQualityCalculator;
import com.seggellion.britannia_mod.farming.FruitProvenance;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.ModTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.List;
import java.util.Optional;

// Make sure this class is registered to listen to events.
// If you're using NeoForge, you must register this class to the event bus.
// For static registration, you can use @Mod.EventBusSubscriber, 
// or you can register it in your main mod class if required.

public class GlobalEventHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof WeightedCommodityItem || stack.getItem() instanceof GrapesItem) {
            return;
        }
        if (FruitProvenance.hasRegionName(stack)) {
            FruitProvenance.appendTooltip(stack, event.getToolTip());
        }
        if (CropQualityCalculator.hasCropQuality(stack)) {
            event.getToolTip().add(CropQualityCalculator.qualityTooltip(stack));
        }
    }

    @SubscribeEvent
     public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            Level level = player.level();
            if (level instanceof ServerLevel serverLevel) {
                var blockRegistry = serverLevel.registryAccess().registryOrThrow(Registries.BLOCK);
                var dirtHolderSet = blockRegistry.getOrCreateTag(BlockTags.DIRT); 

                BlockPredicate placeOnPredicate = new BlockPredicate(
                    Optional.of(dirtHolderSet),
                    Optional.empty(),
                    Optional.empty()
                );
                var logHolderSet = blockRegistry.getOrCreateTag(BlockTags.LOGS);
                var fruitTreeHolderSet = blockRegistry.getOrCreateTag(ModTags.Blocks.FRUIT_TREE_BLOCKS);
                var ladderHolderSet = blockRegistry.getOrCreateTag(ModTags.Blocks.ADVENTURE_LADDERS);
                BlockPredicate ladderBlockPredicate = new BlockPredicate(
                        Optional.of(ladderHolderSet), Optional.empty(), Optional.empty());
                AdventureModePredicate twoHandedAxeBreakPredicate = new AdventureModePredicate(List.of(
                        new BlockPredicate(Optional.of(logHolderSet), Optional.empty(), Optional.empty()),
                        new BlockPredicate(Optional.of(fruitTreeHolderSet), Optional.empty(), Optional.empty()),
                        ladderBlockPredicate
                ), true);
                AdventureModePredicate ladderAxeBreakPredicate =
                        new AdventureModePredicate(List.of(ladderBlockPredicate), true);

                AdventureModePredicate canPlacePredicate = new AdventureModePredicate(List.of(placeOnPredicate), true);
                AdventureModePredicate ladderCanPlacePredicate = new AdventureModePredicate(List.of(
                        new BlockPredicate(Optional.empty(), Optional.empty(), Optional.empty())), true);

                for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                    ItemStack stack = serverPlayer.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                            && blockItem.getBlock().defaultBlockState().is(BlockTags.SAPLINGS)) {
                        AdventureModePredicate current = stack.get(DataComponents.CAN_PLACE_ON);
                        if (current == null) {
                            stack.set(DataComponents.CAN_PLACE_ON, canPlacePredicate);
                            serverPlayer.getInventory().setItem(i, stack);
                        }
                    }
                    if (!stack.isEmpty() && stack.is(ItemRegistry.LADDER_ITEM.get())) {
                        AdventureModePredicate current = stack.get(DataComponents.CAN_PLACE_ON);
                        if (current == null) {
                            stack.set(DataComponents.CAN_PLACE_ON, ladderCanPlacePredicate);
                            serverPlayer.getInventory().setItem(i, stack);
                        }
                    }
                    if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
                        AdventureModePredicate current = stack.get(DataComponents.CAN_BREAK);
                        if (current == null) {
                            stack.set(DataComponents.CAN_BREAK, stack.is(ItemRegistry.TWO_HANDED_AXE.get())
                                    ? twoHandedAxeBreakPredicate
                                    : ladderAxeBreakPredicate);
                            serverPlayer.getInventory().setItem(i, stack);
                        }
                    }
                }
            }
        }
    }


}

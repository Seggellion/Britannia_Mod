package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.List;
import java.util.Optional;

// Make sure this class is registered to listen to events.
// If you're using NeoForge, you must register this class to the event bus.
// For static registration, you can use @Mod.EventBusSubscriber, 
// or you can register it in your main mod class if required.

public class GlobalEventHandler {

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

                AdventureModePredicate canPlacePredicate = new AdventureModePredicate(List.of(placeOnPredicate), true);

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
                }
            }
        }
    }


}

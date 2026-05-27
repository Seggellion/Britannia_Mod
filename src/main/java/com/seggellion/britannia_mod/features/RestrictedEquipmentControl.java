package com.seggellion.britannia_mod.features;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

public class RestrictedEquipmentControl {
    private static final Set<String> BLOCKED_MATERIALS = Set.of(
            "diamond",
            "netherite",
            "iron"
    );

    private static final Set<String> BLOCKED_EQUIPMENT_TYPES = Set.of(
            "sword",
            "pickaxe",
            "axe",
            "shovel",
            "hoe",
            "helmet",
            "chestplate",
            "leggings",
            "boots"
    );

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        handlePlayerInventory(event.getEntity());
    }

    private void handlePlayerInventory(Player player) {
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
            removeBlockedEquipment(serverPlayer);
        }
    }

    private void removeBlockedEquipment(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (isBlockedEquipment(itemStack)) {
                player.getInventory().removeItem(i, itemStack.getCount());
            }
        }
    }

    private boolean isBlockedEquipment(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        String itemPath = itemId.getPath();

        for (String material : BLOCKED_MATERIALS) {
            String prefix = material + "_";
            if (itemPath.startsWith(prefix)) {
                String equipmentType = itemPath.substring(prefix.length());
                return BLOCKED_EQUIPMENT_TYPES.contains(equipmentType);
            }
        }

        return false;
    }
}

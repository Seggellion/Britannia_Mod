package com.seggellion.britannia_mod.sync;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

public final class BlessedItemInventorySync {

    public static void apply(ServerPlayer player, List<BlessedItemSyncAPI.BlessedRow> rows) {
        Inventory inv = player.getInventory();

        for (BlessedItemSyncAPI.BlessedRow row : rows) {
            if (row.used()) continue;                                   // ignore consumed

            Item target = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(row.itemName()));            // remember: parse()

            // scan for an existing stack with matching deed_id
            boolean found = inv.items.stream().anyMatch(stack ->
                    isSameBlessedDeed(stack, target, row.deedId()));

            if (found) continue;

            // add / replace
            ItemStack stack = new ItemStack(target);
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("blessed", true);
            tag.putString("owner", player.getStringUUID());
            tag.putString("deed_id", row.deedId());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            inv.add(stack);
        }
    }

  private static void addBlessedItem(Inventory inv, Item target,
                                       ServerPlayer player, String deedId) {

        ItemStack stack = new ItemStack(target);

        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("blessed", true);
        nbt.putString ("owner",   player.getStringUUID());
        nbt.putString ("deed_id", deedId);

        // ✅ wrap the tag
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        inv.add(stack);
    }

    private static boolean isSameBlessedDeed(ItemStack stack,
                                             Item item, String deedId) {

        if (!stack.is(item)) return false;

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;

        CompoundTag tag = cd.copyTag();          // or cd.getTag() in older mappings
        return tag.getBoolean("blessed")
            && deedId.equals(tag.getString("deed_id"));
    }

}

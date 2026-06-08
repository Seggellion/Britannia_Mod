package com.seggellion.britannia_mod.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;

public class ChestKeyItem extends Item {
    private static final String TAG_LOCK_ID = "LockId";

    public ChestKeyItem(Properties props) {
        super(props.stacksTo(1));
    }

    public ItemStack createKey(UUID lockId) {
        ItemStack stack = new ItemStack(this);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_LOCK_ID, lockId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public boolean matches(ItemStack key, UUID lockId) {
        CustomData cd = key.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;

        CompoundTag tag = cd.copyTag();
        return tag.hasUUID(TAG_LOCK_ID) && lockId.equals(tag.getUUID(TAG_LOCK_ID));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        if (!flag.isAdvanced()) return;

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        String text = cd != null && cd.copyTag().hasUUID(TAG_LOCK_ID)
                ? cd.copyTag().getUUID(TAG_LOCK_ID).toString()
                : "UNBOUND";
        lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}

package com.seggellion.britannia_mod.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * One key per private-house.  
 * The structure UUID is stored in the {@link DataComponents#CUSTOM_DATA} component
 * under the tag name “LockId”.
 */
public class HouseKeyItem extends Item {

    private static final String TAG_LOCK_ID = "LockId";

    public HouseKeyItem(Properties props) {
        super(props.stacksTo(1));
    }

    /* --------------------------------------------------------------------- */
    /*  Creation & matching                                                   */
    /* --------------------------------------------------------------------- */

    /** Returns a fresh key bound to {@code lockId}. */
    public ItemStack createKey(UUID lockId) {
        ItemStack     stack = new ItemStack(this);
        CompoundTag   tag   = new CompoundTag();
        tag.putUUID(TAG_LOCK_ID, lockId);

        // CustomData.of(...) is the public factory; constructor is private.
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** True if this key opens the structure identified by {@code lockId}. */
    public boolean matches(ItemStack key, UUID lockId) {
        CustomData cd = key.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;

        CompoundTag tag = cd.copyTag();      // public accessor in 1.21
        return tag.hasUUID(TAG_LOCK_ID) &&
               lockId.equals(tag.getUUID(TAG_LOCK_ID));
    }

    /* --------------------------------------------------------------------- */
    /*  Debug tooltip (shows UUID in F3-H “advanced tooltips” mode)           */
    /* --------------------------------------------------------------------- */
    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext ctx,
                                List<Component> lines,
                                TooltipFlag flag) {
        if (!flag.isAdvanced()) return;

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        String text;
        if (cd != null && cd.copyTag().hasUUID(TAG_LOCK_ID)) {
            text = cd.copyTag().getUUID(TAG_LOCK_ID).toString();
        } else {
            text = "UNBOUND";
        }
        lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}

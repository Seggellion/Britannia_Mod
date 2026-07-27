package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.renderdata.BannerPreviewRenderState;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Builds detached client-only stacks; the held authoritative banner is never touched for preview. */
public record BannerPreviewStacks(ItemStack current, ItemStack proposed) {
    public BannerPreviewStacks {
        current = current.copy();
        proposed = proposed.copy();
    }

    public static BannerPreviewStacks create(
            Item bannerItem,
            DataComponentType<BannerInstanceState> componentType,
            BannerPreviewRenderState current,
            BannerPreviewRenderState proposed) {
        return new BannerPreviewStacks(stack(bannerItem, componentType, current),
                stack(bannerItem, componentType, proposed));
    }

    private static ItemStack stack(
            Item bannerItem,
            DataComponentType<BannerInstanceState> componentType,
            BannerPreviewRenderState state) {
        ItemStack stack = new ItemStack(bannerItem);
        stack.set(componentType, new BannerInstanceState(
                BannerDyeingConstants.CURRENT_SCHEMA_VERSION,
                state.bannerDefinitionId(), state.materialId(), state.resolvedColourId(),
                state.sourcePigmentId(), state.mountId()));
        return stack;
    }

    @Override public ItemStack current() { return current.copy(); }
    @Override public ItemStack proposed() { return proposed.copy(); }
}

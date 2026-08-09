package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Small typed extraction layer. Reads display data only and never validates, repairs, or mutates gameplay state. */
public final class BannerRenderStateExtractor {
    private BannerRenderStateExtractor() {
    }

    public static BannerItemRenderState extract(
            ItemStack stack,
            Item bannerItem,
            DataComponentType<BannerInstanceState> componentType,
            ClientBannerRenderPublication publication,
            BannerAssetAvailability assets) {
        return extract(stack, bannerItem, componentType, publication, assets, 0);
    }

    public static BannerItemRenderState extract(
            ItemStack stack,
            Item bannerItem,
            DataComponentType<BannerInstanceState> componentType,
            ClientBannerRenderPublication publication,
            BannerAssetAvailability assets,
            long resourceGeneration) {
        if (stack.getItem() != bannerItem) {
            return new BannerItemRenderState(BannerAppearanceResolver.fallback(
                    null, publication.generation(), resourceGeneration,
                    BannerRenderFailure.INVALID_ITEM, itemId(stack)));
        }
        BannerInstanceState state = stack.get(componentType);
        return new BannerItemRenderState(BannerAppearanceResolver.resolve(
                state, publication, assets, resourceGeneration));
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? stack.getItem().getClass().getName() : id.toString();
    }
}

package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The sole placed-block to shared-item conversion path; it never consults or repairs registry content. */
public final class BannerBlockItemTransfer {
    private BannerBlockItemTransfer() {
    }

    public static ItemStack fromBlockEntity(BannerBlockEntity blockEntity) {
        return create(BannerItemRegistry.BANNER.get(), DataComponentRegistry.BANNER_INSTANCE_STATE.get(),
                blockEntity == null ? Optional.empty() : blockEntity.bannerState());
    }

    public static ItemStack create(
            Item sharedBannerItem,
            DataComponentType<BannerInstanceState> componentType,
            Optional<BannerInstanceState> state) {
        ItemStack stack = new ItemStack(sharedBannerItem);
        state.ifPresent(value -> stack.set(componentType, value));
        return stack;
    }
}

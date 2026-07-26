package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Exactly one item registration represents every authored banner definition. */
public final class BannerItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BritanniaMod.MODID);
    public static final DeferredHolder<Item, BannerItem> BANNER = ITEMS.register(
            "banner",
            () -> new BannerItem(new Item.Properties().stacksTo(1),
                    DataComponentRegistry.BANNER_INSTANCE_STATE.get()));

    private BannerItemRegistry() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

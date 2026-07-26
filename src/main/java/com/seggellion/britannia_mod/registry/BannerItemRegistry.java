package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerPatternItem;
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
    public static final DeferredHolder<Item, BannerPatternItem> BANNER_PATTERN = ITEMS.register(
            "banner_pattern",
            () -> new BannerPatternItem(new Item.Properties().stacksTo(1),
                    DataComponentRegistry.BANNER_PATTERN_DEFINITION.get()));
    public static final DeferredHolder<Item, Item> COTTON_CLOTH = ITEMS.register(
            "cotton_cloth", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> LINEN_CLOTH = ITEMS.register(
            "linen_cloth", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> BRASS_MOUNT = ITEMS.register(
            "brass_banner_mount", () -> new Item(new Item.Properties()));

    private BannerItemRegistry() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

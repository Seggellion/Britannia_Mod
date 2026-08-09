package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registered production-shaped shared banner content for real ItemStack codec tests. */
public final class Milestone7RegisteredTestContent {
    public static final ResourceLocation COMPONENT_ID = id("banner_instance_state");
    public static final ResourceLocation BANNER_ID = id("banner");
    private static DataComponentType<BannerInstanceState> component;
    private static BannerItem banner;

    private Milestone7RegisteredTestContent() {
    }

    public static synchronized void ensureRegistered() {
        if (component != null) {
            return;
        }
        Milestone6RegisteredTestContent.ensureRegistered();
        component = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, COMPONENT_ID,
                DataComponentRegistry.createBannerInstanceStateType());
        banner = Registry.register(BuiltInRegistries.ITEM, BANNER_ID,
                new BannerItem(new Item.Properties().stacksTo(1), component));
    }

    public static DataComponentType<BannerInstanceState> component() {
        ensureRegistered();
        return component;
    }

    public static BannerItem banner() {
        ensureRegistered();
        return banner;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }
}

package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.item.BannerPatternItem;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registered component-bearing pattern content for persistent/network recipe tests. */
public final class Milestone14RegisteredTestContent {
    private static DataComponentType<BannerDefinitionId> patternComponent;
    private static BannerPatternItem pattern;

    private Milestone14RegisteredTestContent() {
    }

    public static synchronized void ensureRegistered() {
        if (patternComponent != null) {
            return;
        }
        Milestone7RegisteredTestContent.ensureRegistered();
        patternComponent = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                ResourceLocation.parse("britannia_mod:banner_pattern_definition"),
                DataComponentRegistry.createBannerPatternDefinitionType());
        pattern = Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.parse("britannia_mod:banner_pattern"),
                new BannerPatternItem(new Item.Properties().stacksTo(1), patternComponent));
    }

    public static DataComponentType<BannerDefinitionId> patternComponent() {
        ensureRegistered();
        return patternComponent;
    }

    public static BannerPatternItem pattern() {
        ensureRegistered();
        return pattern;
    }
}

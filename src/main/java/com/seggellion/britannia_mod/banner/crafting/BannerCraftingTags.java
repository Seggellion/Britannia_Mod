package com.seggellion.britannia_mod.banner.crafting;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class BannerCraftingTags {
    public static final TagKey<Item> COTTON = fabric("cotton");
    public static final TagKey<Item> WOOL = fabric("wool");
    public static final TagKey<Item> LINEN = fabric("linen");
    public static final TagKey<Item> SILK = fabric("silk");
    public static final TagKey<Item> BRASS = mount("brass");
    public static final TagKey<Item> IRON = mount("iron");

    private BannerCraftingTags() {
    }

    private static TagKey<Item> fabric(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "banner_fabric/" + path));
    }

    private static TagKey<Item> mount(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "banner_mount/" + path));
    }
}

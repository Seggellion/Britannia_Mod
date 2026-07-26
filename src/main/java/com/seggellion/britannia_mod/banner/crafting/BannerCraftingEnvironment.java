package com.seggellion.britannia_mod.banner.crafting;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Immutable dependency boundary used by synchronized recipes and deterministic tests. */
public record BannerCraftingEnvironment(
        Supplier<Item> bannerItem,
        Supplier<Item> patternItem,
        Supplier<DataComponentType<BannerDefinitionId>> patternComponent,
        Supplier<RegistrySnapshot> snapshot,
        BooleanSupplier registryAvailable,
        BiPredicate<ItemStack, TagKey<Item>> tagLookup,
        BannerFabricCraftingResolver fabricResolver,
        BannerMountCraftingResolver mountResolver) {
    public BannerCraftingEnvironment {
        Objects.requireNonNull(bannerItem, "bannerItem");
        Objects.requireNonNull(patternItem, "patternItem");
        Objects.requireNonNull(patternComponent, "patternComponent");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(registryAvailable, "registryAvailable");
        Objects.requireNonNull(tagLookup, "tagLookup");
        Objects.requireNonNull(fabricResolver, "fabricResolver");
        Objects.requireNonNull(mountResolver, "mountResolver");
    }

    public static BannerCraftingEnvironment production() {
        return new BannerCraftingEnvironment(
                BannerItemRegistry.BANNER::get,
                BannerItemRegistry.BANNER_PATTERN::get,
                DataComponentRegistry.BANNER_PATTERN_DEFINITION::get,
                BannerDataRegistries::current,
                BannerDataRegistries::isAvailable,
                ItemStack::is,
                new BannerFabricCraftingResolver(),
                new BannerMountCraftingResolver());
    }

    public BannerItemFactory factory() {
        Item item = bannerItem.get();
        if (!(item instanceof BannerItem banner)) {
            throw new IllegalStateException("Configured banner item is not BannerItem");
        }
        return new BannerItemFactory(item, banner.stateAccess());
    }
}

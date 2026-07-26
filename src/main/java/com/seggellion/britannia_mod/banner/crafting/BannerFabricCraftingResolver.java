package com.seggellion.britannia_mod.banner.crafting;

import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Deterministically adapts item tags to the existing fabric-domain stable IDs. */
public final class BannerFabricCraftingResolver {
    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping(BannerCraftingTags.COTTON, FabricMaterialId.parse("britannia_mod:cotton")),
            new Mapping(BannerCraftingTags.WOOL, FabricMaterialId.parse("britannia_mod:wool")),
            new Mapping(BannerCraftingTags.LINEN, FabricMaterialId.parse("britannia_mod:linen")),
            new Mapping(BannerCraftingTags.SILK, FabricMaterialId.parse("britannia_mod:silk")));

    public CraftingIdentityResolution<FabricMaterialId> resolve(ItemStack stack, RegistrySnapshot snapshot) {
        return resolve(stack, snapshot, ItemStack::is);
    }

    public CraftingIdentityResolution<FabricMaterialId> resolve(
            ItemStack stack, RegistrySnapshot snapshot, BiPredicate<ItemStack, TagKey<Item>> tagLookup) {
        List<Mapping> matches = MAPPINGS.stream().filter(mapping -> tagLookup.test(stack, mapping.tag)).toList();
        List<net.minecraft.resources.ResourceLocation> evidence =
                matches.stream().map(mapping -> mapping.tag.location()).toList();
        if (matches.isEmpty()) {
            return CraftingIdentityResolution.failure(CraftingIdentityFailure.UNKNOWN_INPUT, List.of());
        }
        if (matches.size() != 1) {
            return CraftingIdentityResolution.failure(CraftingIdentityFailure.AMBIGUOUS_INPUT, evidence);
        }
        FabricMaterialId id = matches.getFirst().id;
        if (snapshot.fabricMaterials().find(id).isEmpty()) {
            boolean disabled = snapshot.fabricMaterials().disabledEntries().stream()
                    .map(DefinitionEntry::id).anyMatch(id::equals);
            return CraftingIdentityResolution.failure(
                    disabled ? CraftingIdentityFailure.DISABLED_DEFINITION
                            : CraftingIdentityFailure.MISSING_DEFINITION,
                    evidence);
        }
        return CraftingIdentityResolution.success(id, evidence);
    }

    private record Mapping(TagKey<Item> tag, FabricMaterialId id) {
    }
}

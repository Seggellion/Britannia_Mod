package com.seggellion.britannia_mod.banner.crafting;

import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves brass or iron from authoritative item tags and validates current banner data. */
public final class BannerMountCraftingResolver {
    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping(BannerCraftingTags.BRASS, MountId.parse("britannia_mod:brass")),
            new Mapping(BannerCraftingTags.IRON, MountId.parse("britannia_mod:iron")));

    public CraftingIdentityResolution<MountId> resolve(
            ItemStack stack, RegistrySnapshot snapshot, BannerDefinition definition) {
        return resolve(stack, snapshot, definition, ItemStack::is);
    }

    public CraftingIdentityResolution<MountId> resolve(
            ItemStack stack,
            RegistrySnapshot snapshot,
            BannerDefinition definition,
            BiPredicate<ItemStack, TagKey<Item>> tagLookup) {
        List<Mapping> matches = MAPPINGS.stream().filter(mapping -> tagLookup.test(stack, mapping.tag)).toList();
        List<net.minecraft.resources.ResourceLocation> evidence =
                matches.stream().map(mapping -> mapping.tag.location()).toList();
        if (matches.isEmpty()) {
            return CraftingIdentityResolution.failure(CraftingIdentityFailure.UNKNOWN_INPUT, List.of());
        }
        if (matches.size() != 1) {
            return CraftingIdentityResolution.failure(CraftingIdentityFailure.AMBIGUOUS_INPUT, evidence);
        }
        MountId id = matches.getFirst().id;
        if (snapshot.mounts().find(id).isEmpty()) {
            boolean disabled = snapshot.mounts().disabledEntries().stream()
                    .map(DefinitionEntry::id).anyMatch(id::equals);
            return CraftingIdentityResolution.failure(
                    disabled ? CraftingIdentityFailure.DISABLED_DEFINITION
                            : CraftingIdentityFailure.MISSING_DEFINITION,
                    evidence);
        }
        if (!definition.supportedMounts().contains(id)) {
            return CraftingIdentityResolution.failure(CraftingIdentityFailure.UNSUPPORTED_BY_BANNER, evidence);
        }
        return CraftingIdentityResolution.success(id, evidence);
    }

    private record Mapping(TagKey<Item> tag, MountId id) {
    }
}

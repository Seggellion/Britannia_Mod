package com.seggellion.britannia_mod.farming;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Read-only planting-item to authoritative species-definition resolution. */
public final class FarmingSkillRequirementResolver {
    private static volatile Map<Item, ResolvedRequirement> plantingItems;

    private FarmingSkillRequirementResolver() {
    }

    public static Optional<ResolvedRequirement> resolve(Item plantingItem) {
        if (plantingItem == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(plantingItems().get(plantingItem));
    }

    public static Optional<ResolvedRequirement> resolve(ResourceLocation plantingItemId) {
        if (plantingItemId == null || !BuiltInRegistries.ITEM.containsKey(plantingItemId)) {
            return Optional.empty();
        }
        return resolve(BuiltInRegistries.ITEM.get(plantingItemId));
    }

    /** Builds one immutable identity-keyed mapping after deferred item registration is available. */
    private static Map<Item, ResolvedRequirement> plantingItems() {
        Map<Item, ResolvedRequirement> current = plantingItems;
        if (current != null) {
            return current;
        }
        synchronized (FarmingSkillRequirementResolver.class) {
            current = plantingItems;
            if (current != null) {
                return current;
            }

            IdentityHashMap<Item, ResolvedRequirement> built = new IdentityHashMap<>();
            for (CropDefinition crop : CropRegistry.all()) {
                putUnique(built, crop.seedItem().get(), new ResolvedRequirement(crop.id(), crop));
            }
            for (FlowerDefinition flower : FlowerRegistry.initial().definitions().values()) {
                if (!BuiltInRegistries.ITEM.containsKey(flower.seedItemId())) {
                    throw new IllegalStateException("Missing registered flower planting item " + flower.seedItemId());
                }
                putUnique(built, BuiltInRegistries.ITEM.get(flower.seedItemId()),
                        new ResolvedRequirement(flower.id().toString(), flower));
            }
            plantingItems = Collections.unmodifiableMap(built);
            return plantingItems;
        }
    }

    private static void putUnique(
            IdentityHashMap<Item, ResolvedRequirement> mappings,
            Item plantingItem,
            ResolvedRequirement resolved
    ) {
        ResolvedRequirement previous = mappings.putIfAbsent(plantingItem, resolved);
        if (previous != null) {
            throw new IllegalStateException("Conflicting farming species for planting item "
                    + BuiltInRegistries.ITEM.getKey(plantingItem) + ": "
                    + previous.speciesId() + " and " + resolved.speciesId());
        }
    }

    public record ResolvedRequirement(String speciesId, FarmingSkillRequirement requirement) {
        public float minimumFarmingSkill() {
            return requirement.minimumFarmingSkill();
        }
    }
}

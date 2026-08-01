package com.seggellion.britannia_mod.farming;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only planting-item to authoritative species-definition resolution. */
public final class FarmingSkillRequirementResolver {
    private FarmingSkillRequirementResolver() {
    }

    public static Optional<ResolvedRequirement> resolve(Item plantingItem) {
        if (plantingItem == null) {
            return Optional.empty();
        }

        List<ResolvedRequirement> matches = new ArrayList<>(2);
        CropRegistry.bySeed(plantingItem).ifPresent(definition -> matches.add(
                new ResolvedRequirement(definition.id(), definition)
        ));

        ResourceLocation plantingItemId = BuiltInRegistries.ITEM.getKey(plantingItem);
        FlowerRegistry.initial().bySeedItemId(plantingItemId).ifPresent(definition -> matches.add(
                new ResolvedRequirement(definition.id().toString(), definition)
        ));

        if (matches.size() > 1) {
            throw new IllegalStateException("Conflicting farming species for planting item " + plantingItemId
                    + ": " + matches.stream().map(ResolvedRequirement::speciesId).toList());
        }
        return matches.stream().findFirst();
    }

    public static Optional<ResolvedRequirement> resolve(ResourceLocation plantingItemId) {
        if (plantingItemId == null || !BuiltInRegistries.ITEM.containsKey(plantingItemId)) {
            return Optional.empty();
        }
        return resolve(BuiltInRegistries.ITEM.get(plantingItemId));
    }

    public record ResolvedRequirement(String speciesId, FarmingSkillRequirement requirement) {
        public float minimumFarmingSkill() {
            return requirement.minimumFarmingSkill();
        }
    }
}

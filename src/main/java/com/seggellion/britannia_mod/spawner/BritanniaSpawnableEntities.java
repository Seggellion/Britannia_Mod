package com.seggellion.britannia_mod.spawner;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BritanniaSpawnableEntities {
    private static final String MOD_ID = "britannia_mod";

    private static final Map<ResourceLocation, String> VANILLA_PASSIVE_DISPLAY_NAMES = Map.ofEntries(
            Map.entry(id(EntityType.COW), "Cow"),
            Map.entry(id(EntityType.FOX), "Fox"),
            Map.entry(id(EntityType.PANDA), "Panda"),
            Map.entry(id(EntityType.LLAMA), "Llama"),
            Map.entry(id(EntityType.RABBIT), "Rabbit"),
            Map.entry(id(EntityType.GOAT), "Goat"),
            Map.entry(id(EntityType.PIG), "Pig"),
            Map.entry(id(EntityType.CHICKEN), "Chicken"),
            Map.entry(id(EntityType.SHEEP), "Sheep")
    );

    private BritanniaSpawnableEntities() {
    }

    public static List<ResourceLocation> allowedIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (MOD_ID.equals(key.getNamespace()) && isSpawnBlockCategory(type.getCategory())) {
                ids.add(key);
            }
        }

        ids.addAll(VANILLA_PASSIVE_DISPLAY_NAMES.keySet());

        List<ResourceLocation> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(BritanniaSpawnableEntities::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(sorted);
    }

    public static boolean isAllowed(ResourceLocation id) {
        return allowedIds().contains(id);
    }

    public static String displayName(ResourceLocation id) {
        String explicitName = VANILLA_PASSIVE_DISPLAY_NAMES.get(id);
        if (explicitName != null) {
            return explicitName;
        }

        String[] words = id.getPath().split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                name.append(word.substring(1));
            }
        }
        return name.isEmpty() ? id.toString() : name.toString();
    }

    private static boolean isSpawnBlockCategory(MobCategory category) {
        return category == MobCategory.MONSTER || category == MobCategory.CREATURE;
    }

    private static ResourceLocation id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }
}

package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.entity.*;
import com.seggellion.britannia_mod.trader.ITrader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class CitySpawnRules {
    public static final int MAX_ENTITIES_PER_AREA = 10;
    public static final int SPAWN_ATTEMPTS = 5;
    public static final int TICK_INTERVAL = 200;
    private static final String VANILLA_NAMESPACE = "minecraft";

    private static final Set<EntityType<?>> ALLOWED_TYPES = Set.of(
        EntityType.CAT,
        EntityType.BEE,
        EntityType.VILLAGER,

        EntityRegistry.RAT_ENTITY.get(),
        EntityRegistry.CUSTOM_CAT_ENTITY.get(),
        EntityRegistry.WOOD_MERCHANT_ENTITY.get(),
        EntityRegistry.STONE_MERCHANT_ENTITY.get(),
        EntityRegistry.METAL_MERCHANT_ENTITY.get(),
        EntityRegistry.HORSE_MERCHANT_ENTITY.get(),
        EntityRegistry.FISH_TRADER.get(),
        EntityRegistry.SALVAGE_TRADER.get(),
        EntityRegistry.ALCOHOL_TRADER.get(),
        EntityRegistry.ARCHITECT_ENTITY.get(),
        EntityRegistry.QUEST_GIVER.get(),
        EntityRegistry.TOWNSPERSON.get()
    );

    private static final Set<EntityType<?>> CRITICAL_TYPES = Set.of(
        EntityType.VILLAGER,

        EntityRegistry.WOOD_MERCHANT_ENTITY.get(),
        EntityRegistry.STONE_MERCHANT_ENTITY.get(),
        EntityRegistry.METAL_MERCHANT_ENTITY.get(),
        EntityRegistry.HORSE_MERCHANT_ENTITY.get(),
        EntityRegistry.FISH_TRADER.get(),
        EntityRegistry.SALVAGE_TRADER.get(),
        EntityRegistry.QUEST_GIVER.get(),
        EntityRegistry.TOWNSPERSON.get()
    );

    private static final List<Supplier<? extends EntityType<?>>> CITY_AMBIENT_SPAWN_POOL = List.of(
        EntityRegistry.RAT_ENTITY,
        EntityRegistry.CUSTOM_CAT_ENTITY
    );

    public static boolean isAllowed(Entity entity) {
        return isExplicitlyAllowed(entity) || isAllowedVanillaAnimal(entity);
    }

    public static boolean isCritical(Entity entity) {
        return entity instanceof ITrader || CRITICAL_TYPES.contains(entity.getType());
    }

    // The city cap manages only intentional city entities, not every allowed vanilla creature.
    public static boolean isManagedByCityLimit(Entity entity) {
        return isExplicitlyAllowed(entity);
    }

    public static boolean isAllowedVanillaAnimal(Entity entity) {
        EntityType<?> entityType = entity.getType();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        return entityType.getCategory() == MobCategory.CREATURE
            && entityId != null
            && VANILLA_NAMESPACE.equals(entityId.getNamespace());
    }

    public static boolean isDisallowed(Entity entity) {
        return entity instanceof LivingEntity
            && !(entity instanceof Player)
            && !isAllowed(entity);
    }

    public static Entity createRandomCityAmbientEntity(Level level) {
        if (CITY_AMBIENT_SPAWN_POOL.isEmpty()) {
            return null;
        }

        int index = level.getRandom().nextInt(CITY_AMBIENT_SPAWN_POOL.size());
        EntityType<?> entityType = CITY_AMBIENT_SPAWN_POOL.get(index).get();

        return entityType.create(level);
    }

    public static boolean isCityAmbientEntity(Entity entity) {
        return entity.getType() == EntityRegistry.RAT_ENTITY.get()
            || entity.getType() == EntityRegistry.CUSTOM_CAT_ENTITY.get();
    }

    private static boolean isExplicitlyAllowed(Entity entity) {
        return entity instanceof ITrader || ALLOWED_TYPES.contains(entity.getType());
    }
}

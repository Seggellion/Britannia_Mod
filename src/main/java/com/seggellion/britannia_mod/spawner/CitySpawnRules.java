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
        EntityRegistry.BAKER.get(),
        EntityRegistry.TAVERNKEEPER.get(),
        EntityRegistry.COSTERMONGER.get(),
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
        EntityRegistry.BAKER.get(),
        EntityRegistry.TAVERNKEEPER.get(),
        EntityRegistry.COSTERMONGER.get(),
        EntityRegistry.QUEST_GIVER.get(),
        EntityRegistry.TOWNSPERSON.get()
    );

    /**
     * The single source of truth for city ambient wildlife: what {@link #createRandomCityAmbientEntity}
     * spawns, what {@link #isCityAmbientEntity} counts against {@link #MAX_ENTITIES_PER_AREA}, and
     * what {@link #isAllowed} lets through the join-level guard. Those three used to be maintained
     * separately, which is exactly how a type ends up spawnable but uncounted -- and an uncounted
     * ambient type never trips the population gate, so the spawner refills it forever.
     *
     * <p>Parrots and ocelots are vanilla {@code minecraft:} creatures, so {@link
     * #isAllowedVanillaAnimal} already tolerated them inside a city; being in this pool is what
     * makes the city actively populate them rather than merely permit one that wandered in.
     * Flamingos are {@code britannia_mod:}, which that predicate deliberately does not cover, so
     * for them this pool is also what grants the {@link #isAllowed} that stops {@code CitySpawner}
     * cancelling their {@code EntityJoinLevelEvent} outright.
     */
    private static final List<Supplier<? extends EntityType<?>>> CITY_AMBIENT_SPAWN_POOL = List.of(
        EntityRegistry.RAT_ENTITY,
        EntityRegistry.CUSTOM_CAT_ENTITY,
        EntityRegistry.FLAMINGO_ENTITY,
        () -> EntityType.PARROT,
        () -> EntityType.OCELOT
    );

    public static boolean isAllowed(Entity entity) {
        return isExplicitlyAllowed(entity)
            // Every CitizenEntity subclass, which is what ServiceNpcEntity (bank tellers and the
            // thirteen Guildmasters) is. Without this the join-level guard cancels a Service NPC
            // the moment it spawns inside a city, and since a Service NPC only exists because Rails
            // assigned it to a spawn point, the block sits at "Assigned" forever with nothing in the
            // world and no error anywhere. Deliberately NOT folded into isExplicitlyAllowed: that
            // predicate also defines isManagedByCityLimit, and Rails owns Service NPC population
            // through CityStaffing::DesiredStaffing.staffing_max_per_city. Letting the local cap
            // discard them too would fight the authoritative count and desync the assignment.
            || entity instanceof ICityEntity
            || entity instanceof IbisEntity
            || isCityAmbientEntity(entity)
            || isAllowedVanillaAnimal(entity);
    }

    public static boolean isCritical(Entity entity) {
        return entity instanceof ITrader
            || entity instanceof AbstractEconomyMerchantEntity
            // Reported in CitySpawner's rejection/removal logs, so this stays truthful even though
            // a Service NPC is not managed by the city limit today and therefore never reaches the
            // cull. If it ever does become managed, discarding one would orphan a live Rails
            // NpcSpawnAssignment -- this is what stops that.
            || entity instanceof ServiceNpcEntity
            || CRITICAL_TYPES.contains(entity.getType());
    }

    /**
     * The city cap manages only intentional city entities, not every allowed vanilla creature —
     * and no longer the ambient wildlife this class spawns itself.
     *
     * <p>Ambient wildlife is excluded because the two halves of the cap were fighting each other.
     * {@code MAX_ENTITIES_PER_AREA} is the budget for BOTH the ambient spawn gate (counted by
     * {@link #isCityAmbientEntity}) and {@code CitySpawner.enforceEntityLimit}'s cull (counted by
     * this predicate). Merchants, traders, villagers and quest givers are all managed and nearly
     * all {@link #isCritical}, so in any populated city they consumed the whole budget and the cull
     * had only rats and cats left to delete. The result was ambient wildlife that spawned and then
     * vanished a tick interval later, which reads in-game as "custom cats never spawn".
     *
     * <p>Dropping them from the cull does not make their population unbounded: the spawner only
     * spawns while {@code getCityAmbientEntityCount} is under the same maximum, and none of the
     * pool types have natural spawn wiring (no biome modifiers, no spawn placements — spawn eggs
     * and this spawner are the only routes in). It also stops the cull deleting an animal a player
     * placed deliberately with an egg.
     */
    public static boolean isManagedByCityLimit(Entity entity) {
        return isExplicitlyAllowed(entity) && !isCityAmbientEntity(entity);
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

    /**
     * Derived from {@link #CITY_AMBIENT_SPAWN_POOL} rather than restating it: this is the count the
     * spawner gates on, so a pool member missing here would be spawned without ever being counted
     * and the city would fill with it without bound. A plain loop, not a stream -- this runs as an
     * entity predicate over every entity in every city area on each tick interval.
     */
    public static boolean isCityAmbientEntity(Entity entity) {
        EntityType<?> type = entity.getType();

        for (Supplier<? extends EntityType<?>> candidate : CITY_AMBIENT_SPAWN_POOL) {
            if (candidate.get() == type) {
                return true;
            }
        }

        return false;
    }

    private static boolean isExplicitlyAllowed(Entity entity) {
        return entity instanceof ITrader
            || entity instanceof AbstractEconomyMerchantEntity
            || ALLOWED_TYPES.contains(entity.getType());
    }
}

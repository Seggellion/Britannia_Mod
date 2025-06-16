package com.seggellion.britannia_mod.spawner;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.*;
import com.seggellion.britannia_mod.registry.CityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.util.List;

public class BritainCitySpawner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CITY_NAME = "Britain";
    private static final int MAX_ENTITIES_PER_AREA = 10;
    private static final int SPAWN_ATTEMPTS = 5; // Reduced attempts for performance


@SubscribeEvent
public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    Level level = event.getLevel();
    if (level.isClientSide()) return; // Skip client-side processing

    Entity entity = event.getEntity();

    // Exclude players explicitly using instanceof
    if (entity instanceof net.minecraft.world.entity.player.Player) {
        return;
    }

    // Skip processing if the entity is not disallowed
    if (!isDisallowedEntity(entity)) return;

    List<AABB> cityAreas = CityRegistry.getCityAreas(CITY_NAME);

    for (AABB area : cityAreas) {
        if (area.contains(entity.position())) {
            event.setCanceled(true); // Cancel the event only when inside a defined city area
            return;
        }
    }

    // Log entities outside defined areas for debugging
}


    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || level.getGameTime() % 200 != 0) return; // Skip unnecessary ticks

        List<AABB> cityAreas = CityRegistry.getCityAreas(CITY_NAME);

        for (AABB area : cityAreas) {
            enforceEntityLimit(level, area, MAX_ENTITIES_PER_AREA);

            if (getEntityCount(level, area, Monster.class) < MAX_ENTITIES_PER_AREA) {
                spawnEntityInArea(level, area);
            }
        }
    }

    private static <T extends Entity> int getEntityCount(Level level, AABB area, Class<T> entityType) {
        return level.getEntitiesOfClass(entityType, area, BritainCitySpawner::isAllowedEntity).size();
    }

    private static boolean isAllowedEntity(Entity entity) {
        return entity instanceof RatEntity
                || entity.getType().toString().equals("minecraft:cat")
                || entity.getType().toString().equals("minecraft:bee")
                || entity instanceof EntityWoodMerchant
                || entity instanceof EntityStoneMerchant
                || entity instanceof EntityMetalMerchant
                || entity instanceof Villager
              //  || entity instanceof EntityJourneymanBlacksmith
                || entity instanceof EntityHorseMerchant
                || entity instanceof CustomCatEntity
                || entity instanceof EntityFishMerchant
                || entity instanceof TownPersonEntity;
    }

    private static boolean isDisallowedEntity(Entity entity) {
        return entity instanceof LivingEntity && !isAllowedEntity(entity);
    }

    private static void enforceEntityLimit(Level level, AABB area, int maxEntities) {
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, area, BritainCitySpawner::isAllowedEntity);

        // Separate critical and non-critical entities
        List<Entity> criticalEntities = entities.stream().filter(BritainCitySpawner::isCriticalEntity).toList();
        List<Entity> nonCriticalEntities = entities.stream().filter(e -> !isCriticalEntity(e)).toList();

        int excess = (criticalEntities.size() + nonCriticalEntities.size()) - maxEntities;

        if (excess > 0 && !nonCriticalEntities.isEmpty()) {
            nonCriticalEntities.stream().limit(excess).forEach(Entity::discard);
  
        }
    }

    private static boolean isCriticalEntity(Entity entity) {
        return entity instanceof EntityWoodMerchant
                || entity instanceof EntityFishMerchant
                 || entity instanceof EntityMetalMerchant
                  || entity instanceof EntityStoneMerchant
                  || entity instanceof Villager
            //    || entity instanceof EntityJourneymanBlacksmith
                || entity instanceof EntityHorseMerchant
                || entity instanceof TownPersonEntity;
    }

    private static void spawnEntityInArea(Level level, AABB area) {
        RandomSource random = level.getRandom();

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int x = (int) area.minX + random.nextInt((int) (area.maxX - area.minX + 1));
            int z = (int) area.minZ + random.nextInt((int) (area.maxZ - area.minZ + 1));

            // Use the WORLD_SURFACE heightmap to find the correct Y position
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);

            if (isChunkLoaded(level, spawnPos) && level.getBlockState(spawnPos.below()).isSolidRender(level, spawnPos.below())) {
                Entity entity = random.nextBoolean()
                        ? new RatEntity(EntityRegistry.RAT_ENTITY.get(), level)
                        : EntityRegistry.CUSTOM_CAT_ENTITY.get().create(level);

                if (entity != null) {
                    entity.moveTo(x + 0.5, y, z + 0.5);
                    level.addFreshEntity(entity);
                    return;
                }
            }
        }
    }

    private static boolean isChunkLoaded(Level level, BlockPos pos) {
        return level.hasChunkAt(pos);
    }
}

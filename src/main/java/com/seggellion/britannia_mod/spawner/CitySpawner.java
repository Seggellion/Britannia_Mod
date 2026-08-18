package com.seggellion.britannia_mod.spawner;

import com.seggellion.britannia_mod.registry.CityRegistry;
import com.seggellion.britannia_mod.registry.CitySpawnRules;
import com.seggellion.britannia_mod.entity.IbisEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class CitySpawner {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();

        if (level.isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity instanceof IbisEntity ibis) {
            if (!JhelomIbisPopulation.canJoin(level, ibis)) {
                LOGGER.debug("Jhelom ibis policy rejecting uuid={} pos={} reason=outside_region_or_cap population={} max={}",
                        ibis.getUUID(), ibis.blockPosition(),
                        JhelomIbisPopulation.getIbisInJhelom(level).size(),
                        JhelomIbisPopulation.MAX_POPULATION);
                event.setCanceled(true);
            }
            return;
        }

        if (!CitySpawnRules.isDisallowed(entity)) {
            return;
        }

        for (AABB area : CityRegistry.getAllCityAreas()) {
            if (area.contains(entity.position())) {
                LOGGER.debug("City spawn rules rejecting entity type={} uuid={} pos={} allowed={} critical={} managed={} reason=disallowed_city_entity",
                        entityTypeId(entity), entity.getUUID(), entity.blockPosition(),
                        CitySpawnRules.isAllowed(entity),
                        CitySpawnRules.isCritical(entity),
                        CitySpawnRules.isManagedByCityLimit(entity));
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();

        if (level.isClientSide()) {
            return;
        }

        if (level.getGameTime() % CitySpawnRules.TICK_INTERVAL != 0) {
            return;
        }

        JhelomIbisPopulation.tick(level);

        for (AABB area : CityRegistry.getAllCityAreas()) {
            enforceEntityLimit(level, area);

            if (getCityAmbientEntityCount(level, area) < CitySpawnRules.MAX_ENTITIES_PER_AREA) {
                spawnEntityInArea(level, area);
            }
        }
    }

    private static int getCityAmbientEntityCount(Level level, AABB area) {
        return level.getEntitiesOfClass(
            Entity.class,
            area,
            CitySpawnRules::isCityAmbientEntity
        ).size();
    }

    private static void enforceEntityLimit(Level level, AABB area) {
        List<Entity> entities = level.getEntitiesOfClass(
            Entity.class,
            area,
            CitySpawnRules::isManagedByCityLimit
        );

        List<Entity> nonCriticalEntities = entities.stream()
            .filter(entity -> !CitySpawnRules.isCritical(entity))
            .toList();

        int excess = entities.size() - CitySpawnRules.MAX_ENTITIES_PER_AREA;

        if (excess > 0 && !nonCriticalEntities.isEmpty()) {
            nonCriticalEntities.stream()
                .limit(excess)
                .forEach(entity -> {
                    LOGGER.debug("City entity limit removing entity type={} uuid={} pos={} allowed={} critical={} managed={} reason=excess_population total={} max={} excess={}",
                            entityTypeId(entity), entity.getUUID(), entity.blockPosition(),
                            CitySpawnRules.isAllowed(entity),
                            CitySpawnRules.isCritical(entity),
                            CitySpawnRules.isManagedByCityLimit(entity),
                            entities.size(), CitySpawnRules.MAX_ENTITIES_PER_AREA, excess);
                    entity.discard();
                });
        }
    }

    private static void spawnEntityInArea(Level level, AABB area) {
        RandomSource random = level.getRandom();

        for (int attempt = 0; attempt < CitySpawnRules.SPAWN_ATTEMPTS; attempt++) {
            int x = (int) area.minX + random.nextInt((int) (area.maxX - area.minX + 1));
            int z = (int) area.minZ + random.nextInt((int) (area.maxZ - area.minZ + 1));
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

            BlockPos spawnPos = new BlockPos(x, y, z);

            if (!level.hasChunkAt(spawnPos)) {
                continue;
            }

            if (!level.getBlockState(spawnPos.below()).isSolidRender(level, spawnPos.below())) {
                continue;
            }

            Entity entity = CitySpawnRules.createRandomCityAmbientEntity(level);

            if (entity == null) {
                continue;
            }

            entity.moveTo(x + 0.5, y, z + 0.5);
            level.addFreshEntity(entity);
            return;
        }
    }

    private static String entityTypeId(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? String.valueOf(entity.getType()) : id.toString();
    }
}

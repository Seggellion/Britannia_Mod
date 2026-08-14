package com.seggellion.britannia_mod.spawner;

import com.seggellion.britannia_mod.entity.IbisEntity;
import com.seggellion.britannia_mod.registry.CityRegistry;
import com.seggellion.britannia_mod.registry.CitySpawnRules;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Independent Jhelom-wide population policy; ibis never consume the generic city-animal cap. */
public final class JhelomIbisPopulation {
    public static final String CITY_NAME = "Jhelom";
    public static final int MAX_POPULATION = 15;

    private JhelomIbisPopulation() {
    }

    public static boolean isInJhelom(Vec3 position) {
        return isInsideAny(position, CityRegistry.getCityAreas(CITY_NAME));
    }

    public static boolean isInsideAny(Vec3 position, List<AABB> areas) {
        return areas.stream().anyMatch(area -> area.contains(position));
    }

    public static int excessFor(int population) {
        return Math.max(0, population - MAX_POPULATION);
    }

    public static boolean canJoin(Level level, IbisEntity joining) {
        return Level.OVERWORLD.equals(level.dimension())
                && isInJhelom(joining.position())
                && getIbisInJhelom(level).size() < MAX_POPULATION;
    }

    public static void tick(Level level) {
        if (!(level instanceof ServerLevel serverLevel)
                || !Level.OVERWORLD.equals(level.dimension())
                || level.getGameTime() % CitySpawnRules.TICK_INTERVAL != 0) {
            return;
        }

        List<IbisEntity> population = getIbisInJhelom(level);
        int excess = excessFor(population.size());
        for (int i = 0; i < excess; i++) {
            population.get(population.size() - 1 - i).discard();
        }

        int remaining = MAX_POPULATION - (population.size() - excess);
        for (int attempt = 0; attempt < Math.min(remaining, CitySpawnRules.SPAWN_ATTEMPTS); attempt++) {
            if (!spawnOne(serverLevel)) {
                break;
            }
        }
    }

    public static List<IbisEntity> getIbisInJhelom(Level level) {
        return CityRegistry.getCityAreas(CITY_NAME).stream()
                .flatMap(area -> level.getEntitiesOfClass(IbisEntity.class, area, Entity::isAlive).stream())
                .distinct()
                .toList();
    }

    private static boolean spawnOne(ServerLevel level) {
        List<AABB> areas = CityRegistry.getCityAreas(CITY_NAME);
        if (areas.isEmpty()) {
            return false;
        }

        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < CitySpawnRules.SPAWN_ATTEMPTS; attempt++) {
            AABB area = areas.get(random.nextInt(areas.size()));
            int width = Math.max(1, Mth.floor(area.maxX - area.minX));
            int depth = Math.max(1, Mth.floor(area.maxZ - area.minZ));
            int x = Mth.floor(area.minX) + random.nextInt(width);
            int z = Mth.floor(area.minZ) + random.nextInt(depth);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.hasChunkAt(column)) {
                continue;
            }

            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);

            if (y < area.minY || y >= area.maxY
                    || !level.getBlockState(spawnPos.below()).isSolidRender(level, spawnPos.below())) {
                continue;
            }

            IbisEntity ibis = EntityRegistry.IBIS_ENTITY.get().create(level);
            if (ibis == null) {
                return false;
            }
            ibis.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            ibis.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.NATURAL, null);
            return level.addFreshEntity(ibis);
        }
        return false;
    }
}

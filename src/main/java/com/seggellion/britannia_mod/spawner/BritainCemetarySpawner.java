package com.seggellion.britannia_mod.spawner;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.GhoulEntity;
import com.seggellion.britannia_mod.entity.WraithEntity;
import com.seggellion.britannia_mod.entity.LichEntity;
import com.seggellion.britannia_mod.entity.ShadeEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

import java.util.List;

public class BritainCemetarySpawner {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Define the coordinates for the Britain cemetery area
    private static final int MIN_X = 5010;
    private static final int MAX_X = 5102;
    private static final int MIN_Y = 66;
    private static final int MAX_Y = 79;
    private static final int MIN_Z = 3888;
    private static final int MAX_Z = 4002;
    private static final int MAX_ENTITIES = 15;
    private static final int MAX_SPAWN_LIMIT = 50;  // New maximum cap for entities in the area

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        Entity entity = event.getEntity();

        Vec3 minPos = new Vec3(MIN_X, MIN_Y, MIN_Z);
        Vec3 maxPos = new Vec3(MAX_X, MAX_Y, MAX_Z);
        AABB spawnArea = new AABB(minPos, maxPos);

        if (!spawnArea.contains(entity.position())) {
            return; // Ignore entities outside the spawn area
        }

        // Remove disallowed entities
        if (!isAllowedSpawnEntity(entity) && entity instanceof Monster) {
            entity.discard();
         //   LOGGER.info("Discarded disallowed entity {} at {}", entity.getType().getDescriptionId(), entity.position());
            return;
        }

        enforceEntityLimit(level, spawnArea);

        int currentMonsterCount = getMonsterCount(level, spawnArea);
    //    LOGGER.info("Current monster count in cemetery: {}. Allowed max: {}", currentMonsterCount, MAX_SPAWN_LIMIT);

        if (currentMonsterCount < MAX_ENTITIES && currentMonsterCount < MAX_SPAWN_LIMIT) {
            spawnRandomEntity(level);
        } else {
      //      LOGGER.info("Spawn skipped due to entity limit reached in cemetery area");
        }
    }

    private static boolean isAllowedSpawnEntity(Entity entity) {
        return entity instanceof Zombie ||
                entity instanceof Skeleton ||
                entity instanceof GhoulEntity ||
                entity instanceof WraithEntity ||
                entity instanceof ShadeEntity ||
                entity instanceof LichEntity;
    }

    private static int getMonsterCount(Level level, AABB spawnArea) {
        return level.getEntitiesOfClass(Monster.class, spawnArea).size();
    }

    private static void enforceEntityLimit(Level level, AABB area) {
        List<Monster> entities = level.getEntitiesOfClass(Monster.class, area);
        if (entities.size() > MAX_SPAWN_LIMIT) {
            int excessCount = entities.size() - MAX_SPAWN_LIMIT;
      //      LOGGER.warn("Clearing {} excess entities in cemetery area", excessCount);
            for (int i = 0; i < excessCount; i++) {
                entities.get(i).discard();
            }
        }
    }

    private static void spawnRandomEntity(Level level) {
        RandomSource random = level.getRandom();

        for (int attempt = 0; attempt < 10; attempt++) { // Attempt spawning up to 10 times
            int x = MIN_X + random.nextInt(MAX_X - MIN_X + 1);
            int z = MIN_Z + random.nextInt(MAX_Z - MIN_Z + 1);
            int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
            BlockPos spawnPos = new BlockPos(x, y, z);

            // Check ground and clearance conditions
            if (!isGroundSolid(level, spawnPos.below()) || !isAreaClear(level, spawnPos, 3)) {
           //     LOGGER.debug("Spawn position invalid at ({}, {}, {}), retrying...", x, y, z);
                continue;
            }

            // Check proximity to other monsters
            if (isNearAnotherMonster(level, spawnPos)) {
           //     LOGGER.debug("Spawn position near another monster at ({}, {}, {}), retrying...", x, y, z);
                continue;
            }

            // Create a random entity
            Entity entityToSpawn = switch (random.nextInt(4)) {
                case 0 -> new Zombie(EntityType.ZOMBIE, level);
                case 1 -> {
                    Skeleton skeleton = new Skeleton(EntityType.SKELETON, level);
                    skeleton.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // Remove the bow
                    yield skeleton;
                }
                case 2 -> new GhoulEntity(EntityRegistry.GHOUL_ENTITY.get(), level);
                case 3 -> new WraithEntity(EntityRegistry.WRAITH_ENTITY.get(), level);
                default -> null;
            };

            if (entityToSpawn == null) {
                LOGGER.warn("Failed to create random entity");
                return;
            }

            // Add the entity to the world
            entityToSpawn.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360F, 0);
            if (level.addFreshEntity(entityToSpawn)) {
          //      LOGGER.info("Spawned {} at ({}, {}, {})", entityToSpawn.getType().getDescriptionId(), x, y, z);
                return; // Stop after a successful spawn
            } else {
          //      LOGGER.warn("Failed to add entity to the world at ({}, {}, {})", x, y, z);
            }
        }

     //   LOGGER.warn("Failed to find a suitable spawn location after 10 attempts");
    }

    private static boolean isGroundSolid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolidRender(level, pos);
    }

    private static boolean isAreaClear(Level level, BlockPos pos, int height) {
        for (int offsetY = 0; offsetY < height; offsetY++) {
            BlockPos checkPos = pos.above(offsetY);
            if (!level.getBlockState(checkPos).isAir()) {
                return false; // Space is obstructed
            }
        }
        return true;
    }

    private static boolean isNearAnotherMonster(Level level, BlockPos pos) {
        AABB proximityArea = new AABB(pos).inflate(1.0); // 1 block radius
        return !level.getEntitiesOfClass(Monster.class, proximityArea).isEmpty();
    }
}

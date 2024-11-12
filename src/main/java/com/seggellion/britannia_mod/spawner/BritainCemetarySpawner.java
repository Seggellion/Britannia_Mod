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

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        Entity entity = event.getEntity();

        Vec3 minPos = new Vec3(MIN_X, MIN_Y, MIN_Z);
        Vec3 maxPos = new Vec3(MAX_X, MAX_Y, MAX_Z);
        AABB spawnArea = new AABB(minPos, maxPos);

        // Prevent any non-allowed monster from spawning in the defined area
        if (spawnArea.contains(entity.position()) && !isAllowedSpawnEntity(entity)) {
            if (entity instanceof Monster) {  // Applies only to hostile mobs
                event.setCanceled(true);
            }
            return;
        }

        if (spawnArea.contains(entity.position())) {

            List<Entity> spawnedEntities = level.getEntitiesOfClass(Entity.class, spawnArea, BritainCemetarySpawner::isAllowedSpawnEntity);
            if (spawnedEntities.size() < MAX_ENTITIES) {
                spawnRandomEntity(level);
            }
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

    private static void spawnRandomEntity(Level level) {

        RandomSource random = level.getRandom();
        int x = MIN_X + random.nextInt(MAX_X - MIN_X + 1);
        int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
        int z = MIN_Z + random.nextInt(MAX_Z - MIN_Z + 1);
        BlockPos spawnPos = new BlockPos(x, y, z);

        // Randomly select one of the allowed entities
        int randomIndex = random.nextInt(4);  // Since we have 4 entities
        Entity entityToSpawn;
        switch (randomIndex) {
            case 0:
                entityToSpawn = new Zombie(EntityType.ZOMBIE, level);
                break;
            case 1:
                entityToSpawn = new Skeleton(EntityType.SKELETON, level);
                // Remove the skeleton's bow
                ((Skeleton) entityToSpawn).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                break;
            case 2:
                entityToSpawn = new GhoulEntity(EntityRegistry.GHOUL_ENTITY.get(), level);
                break;
            case 3:
                entityToSpawn = new WraithEntity(EntityRegistry.WRAITH_ENTITY.get(), level);
                break;
            default:
                entityToSpawn = new Zombie(EntityType.ZOMBIE, level);
        }

        entityToSpawn.moveTo(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), random.nextFloat() * 360F, 0);

        boolean addedSuccessfully = level.addFreshEntity(entityToSpawn);
        if (!addedSuccessfully) {
            LOGGER.warn("Failed to spawn entity at {}", spawnPos);
        }
    }
}

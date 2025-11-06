package com.seggellion.britannia_mod.spawner;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.GoldOreElementalEntity;
import com.seggellion.britannia_mod.entity.EarthElementalEntity;
import com.seggellion.britannia_mod.entity.ShadowOreElementalEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.util.List;

public class ShameDungeonSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Define dungeon area coordinates
    private static final int DUNGEON_MIN_X = 2906;
    private static final int DUNGEON_MAX_X = 3156;
    private static final int DUNGEON_MIN_Z = 3496;
    private static final int DUNGEON_MAX_Z = 3893;

    // Define floor Y ranges
    private static final int FLOOR1_MIN_Y = 88;
    private static final int FLOOR1_MAX_Y = 108;

    private static final int FLOOR2_MIN_Y = 53;
    private static final int FLOOR2_MAX_Y = 84;

    private static final int FLOOR3_MIN_Y = 20;
    private static final int FLOOR3_MAX_Y = 52;

    private static final int FLOOR4_MIN_Y = -3;
    private static final int FLOOR4_MAX_Y = 19;

    // Maximum number of entities per floor
    private static final int MAX_ENTITIES_PER_FLOOR = 40;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        Entity entity = event.getEntity();

        // Define the overall dungeon area
        AABB dungeonArea = new AABB(new Vec3(DUNGEON_MIN_X, 0, DUNGEON_MIN_Z), new Vec3(DUNGEON_MAX_X, 255, DUNGEON_MAX_Z));

        // Check if entity is within dungeon area
        if (dungeonArea.contains(entity.position())) {
            // Prevent disallowed entities from spawning
            if (isDisallowedEntity(entity)) {
                event.setCanceled(true);
          //      LOGGER.info("Canceled spawn of disallowed entity {} at {}", entity.getType().getDescriptionId(), entity.position());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) {
            // Perform spawning every 10 seconds (200 ticks)
            if (level.getGameTime() % 200 == 0) {
                for (int floorNumber = 1; floorNumber <= 3; floorNumber++) {
                    AABB floorArea = getFloorArea(floorNumber);
                    enforceEntityLimit(level, floorArea, MAX_ENTITIES_PER_FLOOR);
                    int currentMonsterCount = getMonsterCount(level, floorArea);
                    if (currentMonsterCount < MAX_ENTITIES_PER_FLOOR) {
                        spawnEntityOnFloor(level, floorNumber);
                    } else {
                 //       LOGGER.info("Entity limit reached on floor {}", floorNumber);
                    }
                }
            }
        }
    }

    private static AABB getFloorArea(int floorNumber) {
        switch (floorNumber) {
            case 1:
                return new AABB(new Vec3(DUNGEON_MIN_X, FLOOR1_MIN_Y, DUNGEON_MIN_Z), new Vec3(DUNGEON_MAX_X, FLOOR1_MAX_Y, DUNGEON_MAX_Z));
            case 2:
                return new AABB(new Vec3(DUNGEON_MIN_X, FLOOR2_MIN_Y, DUNGEON_MIN_Z), new Vec3(DUNGEON_MAX_X, FLOOR2_MAX_Y, DUNGEON_MAX_Z));
            case 3:
                return new AABB(new Vec3(DUNGEON_MIN_X, FLOOR3_MIN_Y, DUNGEON_MIN_Z), new Vec3(DUNGEON_MAX_X, FLOOR3_MAX_Y, DUNGEON_MAX_Z));
            default:
                return null;
        }
    }

    private static int getMonsterCount(Level level, AABB area) {
        // Count only monsters of allowed types within the area
        List<Monster> entities = level.getEntitiesOfClass(Monster.class, area, ShameDungeonSpawner::isAllowedEntity);
        return entities.size();
    }

    private static boolean isAllowedEntity(Entity entity) {
        return entity instanceof EarthElementalEntity ||
               entity instanceof GoldOreElementalEntity ||
               entity instanceof ShadowOreElementalEntity;
    }

    private static boolean isDisallowedEntity(Entity entity) {
   // Allow players and your custom entities
    if (entity instanceof Player || isAllowedEntity(entity)) {
        return false;
    }
    // Disallow all other living entities
    return entity instanceof LivingEntity;
    }

    private static void enforceEntityLimit(Level level, AABB area, int maxEntities) {
        List<Monster> entities = level.getEntitiesOfClass(Monster.class, area, ShameDungeonSpawner::isDisallowedEntity);
        for (Entity entity : entities) {
            entity.discard(); // Remove disallowed entities
        //    LOGGER.warn("Removed disallowed entity {}", entity.getType().getDescriptionId());
        }

        entities = level.getEntitiesOfClass(Monster.class, area, ShameDungeonSpawner::isAllowedEntity);
        if (entities.size() > maxEntities) {
            int excessCount = entities.size() - maxEntities;
       //    LOGGER.warn("Clearing {} excess entities in area {}", excessCount, area);
            for (int i = 0; i < excessCount; i++) {
                entities.get(i).discard();
            }
        }
    }

    private static void spawnEntityOnFloor(Level level, int floorNumber) {
        RandomSource random = level.getRandom();

//        LOGGER.info("Attempting to spawn entity on floor {}", floorNumber);

        for (int attempt = 0; attempt < 30; attempt++) { // Attempt spawning up to 10 times
            int x = DUNGEON_MIN_X + random.nextInt(DUNGEON_MAX_X - DUNGEON_MIN_X + 1);
            int z = DUNGEON_MIN_Z + random.nextInt(DUNGEON_MAX_Z - DUNGEON_MIN_Z + 1);
            int y = switch (floorNumber) {
                case 1 -> FLOOR1_MIN_Y + random.nextInt(FLOOR1_MAX_Y - FLOOR1_MIN_Y + 1);
                case 2 -> FLOOR2_MIN_Y + random.nextInt(FLOOR2_MAX_Y - FLOOR2_MIN_Y + 1);
                case 3 -> FLOOR3_MIN_Y + random.nextInt(FLOOR3_MAX_Y - FLOOR3_MIN_Y + 1);
                default -> 0;
            };

            BlockPos spawnPos = new BlockPos(x, y, z);
         //   LOGGER.debug("Attempt {}: Checking spawn position at ({}, {}, {})", attempt + 1, x, y, z);

            // Check if the block below is solid
            if (!isGroundSolid(level, spawnPos.below())) {
              //  LOGGER.info("Ground is not solid at ({}, {}, {})", x, y - 1, z);
                continue; // Skip to the next attempt
            }

            // Check if there are 3 blocks of clear space above the spawn position
            if (!isAreaClear(level, spawnPos, 2)) {
              //  LOGGER.info("Area is not clear for 3 blocks above ({}, {}, {})", x, y, z);
                continue; // Skip to the next attempt
            }

            Entity entity = null;

            switch (floorNumber) {
                case 1:
                    entity = new EarthElementalEntity(EntityRegistry.EARTH_ELEMENTAL_ENTITY.get(), level);
                    break;
                case 2:
                    entity = new GoldOreElementalEntity(EntityRegistry.GOLD_ORE_ELEMENTAL_ENTITY.get(), level);
                    break;
                case 3:
                    entity = new ShadowOreElementalEntity(EntityRegistry.SHADOW_ORE_ELEMENTAL_ENTITY.get(), level);
                    break;
                default:
                    LOGGER.warn("Invalid floor number {} for spawning", floorNumber);
                    return;
            }

            entity.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360F, 0);
            boolean added = level.addFreshEntity(entity);

            if (added) {
              //  LOGGER.info("Successfully spawned {} at ({}, {}, {})", entity.getType().getDescriptionId(), x, y, z);
                return; // Stop trying once a valid spawn occurs
            } else {
            //    LOGGER.warn("Failed to add entity to the level at ({}, {}, {})", x, y, z);
            }
        }

    //    LOGGER.warn("Failed to find a suitable spawn location after 30 attempts for floor {}", floorNumber);
    }

private static boolean isGroundSolid(Level level, BlockPos pos) {
    if (!level.hasChunkAt(pos)) {
        return false;
    }
    BlockState state = level.getBlockState(pos);
    Block block = state.getBlock();

    // Check if the block is polished andesite or smooth sandstone
    if (block == Blocks.POLISHED_ANDESITE || block == Blocks.SMOOTH_SANDSTONE) {
        LOGGER.info("Cannot spawn on prohibited block {} at ({}, {}, {})", BuiltInRegistries.BLOCK.getKey(block), pos.getX(), pos.getY(), pos.getZ());
        return false;
    }

    boolean isSolid = state.isFaceSturdy(level, pos, Direction.UP);
  //  LOGGER.info("Block at ({}, {}, {}) is {} and isSolid: {}", pos.getX(), pos.getY(), pos.getZ(), BuiltInRegistries.BLOCK.getKey(block), isSolid);
    return isSolid;
}


private static boolean isAreaClear(Level level, BlockPos pos, int height) {
    for (int offsetY = 0; offsetY < height; offsetY++) {
        BlockPos checkPos = pos.above(offsetY);
        if (!level.hasChunkAt(checkPos)) {
            return false;
        }
        BlockState state = level.getBlockState(checkPos);
        if (!state.isAir()) {
   //         LOGGER.info("Block at ({}, {}, {}) is obstructing spawn ({}).", checkPos.getX(), checkPos.getY(), checkPos.getZ(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            return false; // Block obstructs the space
        }
    }
    return true;
}


}

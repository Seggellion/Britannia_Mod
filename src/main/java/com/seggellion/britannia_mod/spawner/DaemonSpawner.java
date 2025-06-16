package com.seggellion.britannia_mod.spawner;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.entity.DaemonEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

import java.util.List;

public class DaemonSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MIN_X = 12426;
    private static final int MAX_X = 12512;
    private static final int MIN_Y = 75;
    private static final int MAX_Y = 95;
    private static final int MIN_Z = 8436;
    private static final int MAX_Z = 8587;
    private static final int MAX_DAEMONS = 10;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        Entity entity = event.getEntity();

        Vec3 minPos = new Vec3(MIN_X, MIN_Y, MIN_Z);
        Vec3 maxPos = new Vec3(MAX_X, MAX_Y, MAX_Z);
        AABB spawnArea = new AABB(minPos, maxPos);

        // Prevent any non-Daemon monster from spawning in the defined area
        if (spawnArea.contains(entity.position()) && !(entity instanceof DaemonEntity)) {
            if (entity instanceof Monster) {  // Applies only to hostile mobs
                event.setCanceled(true);
            }
            return;
        }

        if (spawnArea.contains(entity.position())) {

            List<DaemonEntity> daemonEntities = level.getEntitiesOfClass(DaemonEntity.class, spawnArea);
            if (daemonEntities.size() < MAX_DAEMONS) {
                spawnDaemon(level);
            }
        }
    }

    private static void spawnDaemon(Level level) {

        RandomSource random = level.getRandom();
        int x = MIN_X + random.nextInt(MAX_X - MIN_X + 1);
        int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
        int z = MIN_Z + random.nextInt(MAX_Z - MIN_Z + 1);
        BlockPos spawnPos = new BlockPos(x, y, z);
        DaemonEntity daemon = new DaemonEntity(EntityRegistry.DAEMON_ENTITY.get(), level);
        daemon.moveTo(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), random.nextFloat() * 360F, 0);

        boolean addedSuccessfully = level.addFreshEntity(daemon);
    }
}

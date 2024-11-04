package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.entity.MongbatEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;
import net.neoforged.bus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModSpawnPlacementRegistry {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // Register spawn placement for Mongbat
        event.register(
            EntityRegistry.MONGBAT_ENTITY.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            MongbatEntity::canSpawn,
            Operation.REPLACE
        );
    }
}

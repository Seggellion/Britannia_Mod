package com.seggellion.britannia_mod.trader;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

public interface TraderSpawnProvider {
    EntityType<? extends Mob> entityType();

    TraderSpawnSettings spawnSettings();
}

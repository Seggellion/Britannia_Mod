package com.seggellion.britannia_mod.features;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

public class MobSpawnControl {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        LOGGER.info("Entity joined level: {}", entity.getType().getDescription().getString());

        if (entity instanceof Creeper || entity instanceof EnderMan) {
            event.setCanceled(true);
            LOGGER.info("Prevented spawning of {}", entity.getType().getDescription().getString());
        }
    }
}

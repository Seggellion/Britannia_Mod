package com.seggellion.britannia_mod.villager;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class BlacksmithPOIHandler {
    @SubscribeEvent
    public void onPOIPlaced(BlockEvent.EntityPlaceEvent event) {
        BlockState state = event.getState();
    LevelAccessor levelAccessor = event.getLevel();
        BlockPos pos = event.getPos();

        // Ensure the event is happening in a ServerLevel
        if (!(levelAccessor instanceof ServerLevel serverLevel)) return;

        // Check if the placed block is the Blacksmith's POI
        if (state.is(BlockRegistry.BLACKSMITH_SPAWN_BLOCK.get())) {
            serverLevel.players().forEach(player -> {
                serverLevel.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16)).forEach(villager -> {
                    if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
                    //    villager.refreshBrain(serverLevel); // Force job selection
                    }
                });
            });
        }
    }
}

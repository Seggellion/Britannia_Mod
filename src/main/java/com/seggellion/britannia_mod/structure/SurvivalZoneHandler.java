package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;


public class SurvivalZoneHandler {


private static final Logger LOGGER = LogManager.getLogger();

     @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {


        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
      

        // Iterate over online players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Skip if the player is in Creative or Spectator mode.
            GameType currentMode = player.gameMode.getGameModeForPlayer();
            if (currentMode == GameType.CREATIVE || currentMode == GameType.SPECTATOR) {
                continue;
            }
   LOGGER.info("Not in creative mode");
            BlockPos playerPos = player.blockPosition();
            int chunkX = SectionPos.blockToSectionCoord(playerPos.getX());
            int chunkZ = SectionPos.blockToSectionCoord(playerPos.getZ());

            List<StructureRecord> structuresInChunk = StructureRegionManager.getStructuresInChunk(chunkX, chunkZ);
            if (structuresInChunk.isEmpty()) {
                if (currentMode != GameType.ADVENTURE) {
                    player.setGameMode(GameType.ADVENTURE);
                }
                continue;
            }
   LOGGER.info("Structure found in chunk.");
            boolean insideOwnStructure = false;
            Vec3 playerVec = player.position();
            for (StructureRecord record : structuresInChunk) {
                if (!record.getOwnerUuid().equals(player.getUUID())) {
                    continue;
                }
                if (record.getOwnerUuid().equals(player.getUUID()) &&
                    record.getFullBox().contains(playerVec)) {
                    insideOwnStructure = true;
                    break;
                }

            }

            if (insideOwnStructure) {
                   LOGGER.info("Is inside structure");
                if (currentMode != GameType.SURVIVAL) {
                    player.setGameMode(GameType.SURVIVAL);
                }
            } else {
                if (currentMode != GameType.ADVENTURE) {
                    player.setGameMode(GameType.ADVENTURE);
                }
            }
        }
    }
}

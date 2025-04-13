package com.seggellion.britannia_mod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * Event handler that checks each tick whether a player is inside the bounds of their own structure.
 * When inside, switches game mode to SURVIVAL; when outside, switches to ADVENTURE.
 *
 * Designed for thousands of players by limiting checks to the player's current chunk.
 */
public class SurvivalZoneHandler {

    // This method is intended to be registered with NeoForge's event bus.
    // For example, in BritanniaMod's constructor:
    //   NeoForge.EVENT_BUS.register(new SurvivalZoneHandler());
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

            boolean insideOwnStructure = false;
            Vec3 playerVec = player.position();
            for (StructureRecord record : structuresInChunk) {
                if (!record.getOwnerUuid().equals(player.getUUID())) {
                    continue;
                }
                AABB bb = record.getBoundingBox();
                if (bb.contains(playerVec)) {
                    insideOwnStructure = true;
                    break;
                }
            }

            if (insideOwnStructure) {
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

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
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem; 
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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

            // [FIX] Check if holding a tool that grants Survival rights
            boolean isHoldingTool = (player.getMainHandItem().getItem() instanceof QualityToolItem) || 
                                    (player.getMainHandItem().getItem() instanceof TwoHandedAxeItem);

            BlockPos playerPos = player.blockPosition();
            int chunkX = SectionPos.blockToSectionCoord(playerPos.getX());
            int chunkZ = SectionPos.blockToSectionCoord(playerPos.getZ());

            List<StructureRecord> structuresInChunk = StructureRegionManager.getStructuresInChunk(chunkX, chunkZ);
            if (structuresInChunk.isEmpty()) {
                if (currentMode != GameType.ADVENTURE && !isHoldingTool) {
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
                if (currentMode != GameType.ADVENTURE && !isHoldingTool) {
                    player.setGameMode(GameType.ADVENTURE);
                }
            }
        }
    }
}

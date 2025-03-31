package com.seggellion.britannia_mod.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TreeKarmaHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    public static final Map<UUID, Long> treeCutTimestamps = new HashMap<>();
    public static final long REPLANT_TIMEOUT_MS = 60000; // 60 seconds timeout to replant

   @SubscribeEvent
    public void onTreeCut(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();

        LOGGER.info("TreeCutEvent triggered. Block: {}, Position: {}", state, pos);
 if (player.isCreative() || player.hasPermissions(2)) {
        LOGGER.info("Skipping TreeKarmaHandler: Player {} is in Creative or an OP.", player.getName().getString());
     return;
    }
        if (state.is(BlockTags.LOGS)) { // Check if the broken block is a log
            if (player != null && !player.level().isClientSide) {
                UUID playerId = player.getUUID();
                long currentTime = System.currentTimeMillis();
                treeCutTimestamps.put(playerId, currentTime);
                  
                LOGGER.info("Player {} cut a log at {}. Timestamp recorded: {}", player.getName().getString(), pos, currentTime);
                player.sendSystemMessage(Component.literal("You cut down a tree. Replant a sapling to avoid karma loss!"));
            } else {
                LOGGER.warn("TreeCutEvent: Player was null or on the client side. Event ignored.");
            }
        } else {
            LOGGER.debug("TreeCutEvent: Block at {} is not a log. Event ignored.", pos);
        }
    }


    @SubscribeEvent
    public void onSaplingPlant(BlockEvent.EntityPlaceEvent event) {
        BlockState state = event.getPlacedBlock();
        if (state.is(BlockTags.SAPLINGS)) { // Check if the placed block is a sapling
            if (event.getEntity() instanceof Player player) {
                UUID playerId = player.getUUID();
                if (treeCutTimestamps.containsKey(playerId)) {
                    treeCutTimestamps.remove(playerId);
                    player.sendSystemMessage(Component.literal("You replanted a sapling. Good job!"));
                }
            }
        }
    }
}

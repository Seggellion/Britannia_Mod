package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.UUID;

public class BlockCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("brokenblocks")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("list")
                .executes(BlockCommands::listBrokenBlocks))
            .then(Commands.literal("destroy")
                .then(Commands.argument("blockId", StringArgumentType.string())
                    .executes(BlockCommands::destroyBrokenBlock)))
            .then(Commands.literal("destroy_all")
                .executes(BlockCommands::destroyAllBrokenBlocks)));
    }

private static int listBrokenBlocks(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ServerLevel level = source.getLevel(); // ✅ This line was missing
    BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
    Map<BlockPos, BrokenBlockData> brokenBlocks = storage.getBrokenBlocks();

    if (brokenBlocks.isEmpty()) {
        source.sendSuccess(() -> Component.literal("No broken blocks recorded."), false);
        return 0;
    }

    source.sendSuccess(() -> Component.literal("Listing all broken blocks:"), false);

    final int[] count = {0};
    for (Map.Entry<BlockPos, BrokenBlockData> entry : brokenBlocks.entrySet()) {
        BrokenBlockData data = entry.getValue();
        Block block = data.originalState.getBlock();
        BlockPos pos = data.pos;

        String timeBroken = java.time.Instant.ofEpochMilli(data.brokenTime)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .toString();

        String playerName = getPlayerName(source.getServer(), data.playerUUID);

        source.sendSuccess(() -> Component.literal(String.format(
            "ID: %s | Block: %s | Pos: (%d, %d, %d) | Player: %s | Time: %s",
            getUniqueId(data),
            block.getName().getString(),
            pos.getX(), pos.getY(), pos.getZ(),
            playerName,
            timeBroken
        )), false);
        count[0]++;
    }

    source.sendSuccess(() -> Component.literal("Total broken blocks: " + count[0]), false);
    return count[0];
}


    private static int destroyBrokenBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel(); 
        String blockId = StringArgumentType.getString(context, "blockId");
        Map<BlockPos, BrokenBlockData> brokenBlocks = BrokenBlockTracker.getBrokenBlocks(level);

        for (Map.Entry<BlockPos, BrokenBlockData> entry : brokenBlocks.entrySet()) {
            BrokenBlockData data = entry.getValue();
            if (getUniqueId(data).equals(blockId)) {
                BrokenBlockTracker.removeBlock(level, data.pos);
                source.sendSuccess(() -> Component.literal("Removed block: " + blockId), false);
                return 1;
            }
        }

        source.sendFailure(Component.literal("No block found with ID: " + blockId));
        return 0;
    }

    private static int destroyAllBrokenBlocks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel(); // Get the current server world

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        int count = storage.getBrokenBlocks().size();
        
        storage.getBrokenBlocks().clear();  // Clear the tracked blocks
        storage.setDirty();                 // Mark as dirty so it will be saved

        source.sendSuccess(() -> Component.literal("Destroyed all " + count + " recorded broken blocks."), true);
        return count;
    }


    private static String getUniqueId(BrokenBlockData data) {
        return UUID.nameUUIDFromBytes((data.pos.toShortString() + data.brokenTime).getBytes()).toString();
    }

    /**
     * Get the player's name from their UUID.
     */
    private static String getPlayerName(MinecraftServer server, UUID playerUUID) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player != null) {
            return player.getName().getString();
        }
        return "Unknown Player";
    }
}

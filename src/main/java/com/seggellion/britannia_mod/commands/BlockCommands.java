package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.MinecraftServer;


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
        Map<BlockPos, BrokenBlockData> brokenBlocks = BrokenBlockTracker.getBrokenBlocks();

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

            // Convert the broken time to a readable date/time format
            String timeBroken = java.time.Instant.ofEpochMilli(data.brokenTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .toString();

            // Retrieve the player name from UUID
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
        String blockId = StringArgumentType.getString(context, "blockId");
        Map<BlockPos, BrokenBlockData> brokenBlocks = BrokenBlockTracker.getBrokenBlocks();

        for (Map.Entry<BlockPos, BrokenBlockData> entry : brokenBlocks.entrySet()) {
            BrokenBlockData data = entry.getValue();
            if (getUniqueId(data).equals(blockId)) {
                BrokenBlockTracker.removeBlock(data.pos);
                source.sendSuccess(() -> Component.literal("Removed block: " + blockId), false);
                return 1;
            }
        }

        source.sendFailure(Component.literal("No block found with ID: " + blockId));
        return 0;
    }

    private static int destroyAllBrokenBlocks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = BrokenBlockTracker.getBrokenBlocks().size();
        BrokenBlockTracker.getBrokenBlocks().clear();
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

package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.player.PlayerData;
import com.seggellion.britannia_mod.player.PlayerDataManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LeaderboardCommands {

    
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("leaderboard")
            .requires(source -> source.hasPermission(0))
            .then(Commands.literal("top_contributors")
                .then(Commands.argument("cityName", StringArgumentType.string())
                    .then(Commands.argument("category", StringArgumentType.string())
                    .executes(LeaderboardCommands::showTopContributors))))
            .then(Commands.literal("biggest_fish")
                .then(Commands.argument("fishType", StringArgumentType.string())
                    .executes(LeaderboardCommands::showBiggestFish))));
    }

    private static String getPlayerName(ServerLevel serverLevel, UUID playerUUID) {
    // Try to find an online player first
    PlayerList playerList = serverLevel.getServer().getPlayerList();
    ServerPlayer onlinePlayer = playerList.getPlayer(playerUUID);
    if (onlinePlayer != null) {
        return onlinePlayer.getGameProfile().getName();
    }

    // If the player is not online, attempt to resolve their name via the GameProfile cache
    Optional<GameProfile> profile = serverLevel.getServer().getProfileCache().get(playerUUID);
    return profile.map(GameProfile::getName).orElse("Unknown Player");
}

private static int showTopContributors(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    String cityName = StringArgumentType.getString(context, "cityName");
    String category = StringArgumentType.getString(context, "category").toLowerCase();
    ServerLevel serverLevel = source.getLevel();

    // Map categories to their sub-types
    Map<String, List<String>> categoryMap = Map.of(
        "food", List.of("fish", "grains", "meats", "vegetables"),
        "fish", List.of("cod", "salmon", "tropical_fish"),
        "wood", List.of("oak", "birch", "spruce", "jungle", "acacia", "dark_oak")
    );

    List<String> subTypes = categoryMap.getOrDefault(category, List.of());
    if (subTypes.isEmpty()) {
        source.sendFailure(Component.literal("Invalid category: " + category));
        return 0;
    }

    PlayerDataManager manager = PlayerDataManager.get(serverLevel);

    // Compute total contributions for the specified category
    List<PlayerData> topContributors = manager.getAllPlayers().stream()
        .sorted((a, b) -> Double.compare(
            getCategoryContribution(b, subTypes),
            getCategoryContribution(a, subTypes)
        ))
        .limit(10)
        .collect(Collectors.toList());

    source.sendSuccess(() -> Component.literal("Top Contributors for " + category + " in " + cityName + ":"), false);
    for (int i = 0; i < topContributors.size(); i++) {
        final int index = i;
        final PlayerData data = topContributors.get(i);
        String playerName = getPlayerName(serverLevel, data.getPlayerUUID());
        double totalContribution = getCategoryContribution(data, subTypes);
        source.sendSuccess(() -> Component.literal((index + 1) + ". " + playerName + ": " + totalContribution + " stones"), false);
    }

    return 1;
}

private static double getCategoryContribution(PlayerData data, List<String> subTypes) {
    return subTypes.stream()
        .mapToDouble(subType -> data.getTotalContributions().getOrDefault(subType, 0.0))
        .sum();
}

    private static int showBiggestFish(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String fishType = StringArgumentType.getString(context, "fishType");
        ServerLevel serverLevel = source.getLevel();

        PlayerDataManager manager = PlayerDataManager.get(serverLevel);
        List<PlayerData> biggestFishCatchers = manager.getBiggestFishCatchers(fishType, 10);

        source.sendSuccess(() -> Component.literal("Biggest Fish Caught for " + fishType + ":"), false);
        for (int i = 0; i < biggestFishCatchers.size(); i++) {
            final int index = i;
            final PlayerData data = biggestFishCatchers.get(i);
            String playerName = getPlayerName(serverLevel, data.getPlayerUUID());
            System.out.println("Biggest Fish Map: " + data.getBiggestFish());
     LOGGER.info("Biggest Fish Map {}", data.getBiggestFish());
     LOGGER.info("Requested Fish Type {}", fishType);
            source.sendSuccess(() -> Component.literal((index + 1) + ". " + playerName + ": " + data.getBiggestFish().get(fishType) + " stones"), false);
        }

        return 1;
    }
}

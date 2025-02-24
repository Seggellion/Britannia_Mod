package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.util.CityAPITokenData;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CityCommands {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cityinventory")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("cityName", StringArgumentType.string())
                // Original command to show inventory
                .executes(CityCommands::showCityInventory)
                // Category and subcategory logic
                .then(Commands.argument("category", StringArgumentType.string())
                    .executes(CityCommands::showCityInventoryCategoryWeight)
                    .then(Commands.argument("subcategory", StringArgumentType.string())
                        .executes(CityCommands::showCityInventorySubcategory)))
                // New: Show population command with clear subcommand
                .then(Commands.literal("population")
                    .executes(CityCommands::showCityPopulation)
                    .then(Commands.literal("clear")
                        .executes(CityCommands::clearCityPopulation))
                    .then(Commands.literal("list")
                        .executes(CityCommands::listCityPopulation)))
                // New: Clear inventory command
                .then(Commands.literal("clear")
                    .executes(CityCommands::clearCityInventory)))
            .then(Commands.literal("delete_cities")
                .executes(CityCommands::deleteAllCities)));

            //dispatcher for fish testing
            dispatcher.register(Commands.literal("createfish")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("fishType", StringArgumentType.string())
                            .then(Commands.argument("weight", FloatArgumentType.floatArg(0.1f, 100.0f))
                                .executes(CityCommands::createWeightedFish))));

        // New command for setting the API token:
        dispatcher.register(
            Commands.literal("britannia_api")
                .requires(source -> source.hasPermission(2)) // ensure only ops
                .then(Commands.literal("set_token")
                    .then(Commands.argument("token", StringArgumentType.string())
                        .executes(CityCommands::setApiToken)
                    )
                )
        );

    }

      // This is the method that actually sets the token
    private static int setApiToken(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String token = StringArgumentType.getString(context, "token");

        // We assume this command is run on the server context
        if (source.getLevel() instanceof ServerLevel serverLevel) {
            // Retrieve or create the saved data
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            data.setApiToken(token);

            source.sendSuccess(() -> Component.literal("API Token set successfully!"), false);

            LOGGER.info("API Token stored on the server: {}", token);
            return 1;
        }

        source.sendFailure(Component.literal("Unable to set API token. Not in a server level context."));
        return 0;
    }

    private static int showCityInventory(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    String cityNameInput = StringArgumentType.getString(context, "cityName");
    String cityName = cityNameInput; // No standardization
    ServerPlayer player = source.getPlayer();

    LOGGER.warn("Command executed: /cityinventory {} inventory", cityNameInput);

    if (player != null && source.getLevel() instanceof ServerLevel serverLevel) {
        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);
        if (city != null) {
            CityInventory cityInventory = city.getInventory();
            source.sendSuccess(() -> Component.literal("City Inventory for " + cityName + ":"), false);

            // Display all commodities
            cityInventory.getAllCommodities().forEach((category, subcategories) -> {
                subcategories.forEach((subcategory, items) -> {
                    items.forEach((itemName, quantity) -> {
                        source.sendSuccess(() -> Component.literal("- " + category + " -> " + subcategory + " -> " + itemName + ": " + quantity), false);
                    });
                });
            });

            // Display food weight totals
            String foodCategory = "food";
            Map<String, Double> foodWeights = cityInventory.getAllCategoryWeights(foodCategory);
            if (!foodWeights.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Weight Totals by Subcategory for " + foodCategory + ":"), false);
                foodWeights.forEach((subCategory, totalWeight) -> {
                    source.sendSuccess(() -> Component.literal("- " + subCategory + ": " + totalWeight + " stones total"), false);
                });
            } else {
                source.sendSuccess(() -> Component.literal("(No weighted food commodities recorded yet)"), false);
            }

            // Display wood weight totals
            String woodCategory = "wood";
            Map<String, Double> woodWeights = cityInventory.getAllCategoryWeights(woodCategory);
            if (!woodWeights.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Weight Totals by Subcategory for " + woodCategory + ":"), false);
                woodWeights.forEach((subCategory, totalWeight) -> {
                    source.sendSuccess(() -> Component.literal("- " + subCategory + ": " + totalWeight + " stones total"), false);
                });
            } else {
                source.sendSuccess(() -> Component.literal("(No weighted wood commodities recorded yet)"), false);
            }

            return 1;
        } else {
            source.sendFailure(Component.literal("City not found: " + cityName));
            return 0;
        }
    }

    source.sendFailure(Component.literal("Failed to retrieve City Inventory."));
    return 0;
}

    private static int showCityInventoryCategoryWeight(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String cityNameInput = StringArgumentType.getString(context, "cityName");
        String cityName = cityNameInput; // No standardization
        String category = StringArgumentType.getString(context, "category");
        ServerPlayer player = source.getPlayer();

        LOGGER.warn("Command executed: /cityinventory {} category {}", cityNameInput, category);

        if (player != null && source.getLevel() instanceof ServerLevel serverLevel) {
            CityManager cityManager = CityManager.get(serverLevel);
            City city = cityManager.getCity(cityName);
            if (city != null) {
                CityInventory cityInventory = city.getInventory();
                double totalWeight = cityInventory.getCategoryTotalWeight(category);
                if (totalWeight > 0) {
                    source.sendSuccess(() -> Component.literal("Total weight for " + cityName + " -> " + category + ": " + totalWeight + " stones"), false);
                } else {
                    source.sendSuccess(() -> Component.literal("No weight found for category: " + category + " in city: " + cityName), false);
                }
                return 1;
            } else {
                source.sendFailure(Component.literal("City not found: " + cityNameInput));
                return 0;
            }
        }

        source.sendFailure(Component.literal("Failed to retrieve City Inventory."));
        return 0;
    }

    private static int showCityInventorySubcategory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String cityNameInput = StringArgumentType.getString(context, "cityName");
        String cityName = cityNameInput; // No standardization
        ServerPlayer player = source.getPlayer();

        if (player != null && source.getLevel() instanceof ServerLevel serverLevel) {
            CityManager cityManager = CityManager.get(serverLevel);
            City city = cityManager.getCity(cityName);
            if (city != null) {
                CityInventory cityInventory = city.getInventory();
                String category = "food";
                String subcategoryArg = "fish"; // Replace with the actual subcategory
                int totalQuantity = cityInventory.getSubcategoryTotal(category, subcategoryArg);

                if (totalQuantity > 0) {
                    source.sendSuccess(() -> Component.literal("Total " + cityName + " -> food -> " + subcategoryArg + ": " + totalQuantity), false);
                } else {
                    source.sendSuccess(() -> Component.literal("No commodities found for subcategory: " + subcategoryArg + " in city: " + cityName), false);
                }
                return 1;
            } else {
                source.sendFailure(Component.literal("City not found: " + cityNameInput));
                return 0;
            }
        }

        source.sendFailure(Component.literal("Failed to retrieve City Inventory."));
        return 0;
    }

    private static int showCityPopulation(CommandContext<CommandSourceStack> context) {
        String cityName = StringArgumentType.getString(context, "cityName"); // No standardization
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        LOGGER.warn("Command executed: /cityinventory {} population", cityName);

        if (player != null && source.getLevel() instanceof ServerLevel serverLevel) {
            CityManager cityManager = CityManager.get(serverLevel);
            City city = cityManager.getCity(cityName);

            if (city != null) {
                CityInventory cityInventory = city.getInventory();
                int population = cityInventory.getPopulation(); // No parameter
                LOGGER.warn("Retrieved population for city {}: {}", cityName, population);
                source.sendSuccess(() -> Component.literal("Population of " + cityName + ": " + population), true);
                return 1;
            } else {
                LOGGER.warn("City not found: {}", cityName);
                source.sendFailure(Component.literal("City not found: " + cityName));
                return 0;
            }
        }

        source.sendFailure(Component.literal("Failed to retrieve City Population."));
        return 0;
    }

private static int clearCityPopulation(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    String cityNameInput = StringArgumentType.getString(context, "cityName");
    String cityName = cityNameInput; // No standardization
    ServerPlayer player = source.getPlayer();

    LOGGER.warn("Command executed: /cityinventory {} population clear", cityNameInput);

    if (player != null && source.getLevel() instanceof ServerLevel serverLevel) {
        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);
        if (city != null) {
            CityInventory cityInventory = city.getInventory();
            cityInventory.clearPopulation(); // Ensure this method exists in CityInventory

            source.sendSuccess(() -> Component.literal("Cleared population for city: " + cityName), true);
            LOGGER.warn("Cleared population for city {}", cityName);
            return 1;
        } else {
            source.sendFailure(Component.literal("City not found: " + cityName));
            LOGGER.warn("Attempted to clear population for non-existent city {}", cityName);
            return 0;
        }
    }

    source.sendFailure(Component.literal("Failed to clear City Population."));
    LOGGER.warn("Failed to clear City Population for city {}", cityNameInput);
    return 0;
}


    private static int clearCityInventory(CommandContext<CommandSourceStack> context) {
        String cityName = StringArgumentType.getString(context, "cityName"); // No standardization
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        LOGGER.warn("Command executed: /cityinventory {} clear", cityName);

        if (player != null && source.getLevel() instanceof ServerLevel serverLevel) {
            CityManager cityManager = CityManager.get(serverLevel);
            City city = cityManager.getCity(cityName);

            if (city != null) {
                CityInventory cityInventory = city.getInventory();
                cityInventory.clearAllCommodities(); // Ensure this method exists
                source.sendSuccess(() -> Component.literal("Cleared inventory for city: " + cityName), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("City not found: " + cityName));
                return 0;
            }
        }

        source.sendFailure(Component.literal("Failed to clear City Inventory."));
        return 0;
    }


    private static int listCityPopulation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String cityName = StringArgumentType.getString(context, "cityName");
        
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Command can only be executed in a server world."));
            return 0;
        }

        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);

        if (city == null) {
            source.sendFailure(Component.literal("City not found: " + cityName));
            return 0;
        }

        CityInventory inventory = city.getInventory();
        List<Entity> npcs = inventory.getAssociatedEntities(serverLevel);

        if (npcs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No NPCs found for city: " + cityName), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("NPCs for city: " + cityName), false);
        for (Entity npc : npcs) {
            String npcType = npc.getType().toString();
            source.sendSuccess(() -> Component.literal("- UUID: " + npc.getUUID() + ", Type: " + npcType + ", City: " + cityName), false);
        }

        return 1;
    }

    private static int deleteAllCities(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Command can only be executed in a server world."));
            return 0;
        }

        CityManager cityManager = CityManager.get(serverLevel);
        for (City city : cityManager.getCities().values()) {
            CityInventory inventory = city.getInventory();

            // Despawn all NPCs
            List<Entity> npcs = inventory.getAssociatedEntities(serverLevel);
            for (Entity npc : npcs) {
                npc.discard();
            }

            // Clear city inventory
            inventory.clearPopulation();
            inventory.clearAllCommodities();
        }

        // Clear all cities
        cityManager.getCities().clear();
        cityManager.setDirty(); // Mark for saving

        source.sendSuccess(() -> Component.literal("All cities and associated NPCs have been deleted."), true);
        return 1;
    }


 private static int createWeightedFish(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        String fishType = StringArgumentType.getString(context, "fishType").toLowerCase();
        float weight = FloatArgumentType.getFloat(context, "weight");

        LOGGER.warn("Command executed: /createfish {} {}", fishType, weight);

        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        Item fishItem = getFishItemByType(fishType);
        if (fishItem == null) {
            source.sendFailure(Component.literal("Invalid fish type: " + fishType));
            return 0;
        }

        // Create the WeightedFishItem stack
        ItemStack weightedFishStack = new ItemStack(fishItem);
        WeightedFishItem weightedFish = (WeightedFishItem) weightedFishStack.getItem();

        // Set the weight and fish type into the WeightedFishItem
        weightedFish.setWeight(weightedFishStack, weight);
        weightedFish.setFishType(weightedFishStack, fishType);

        // Give the weighted fish directly to the player
        if (!player.getInventory().add(weightedFishStack)) {
            player.drop(weightedFishStack, false);
        }

        source.sendSuccess(() -> Component.literal("Created a " + fishType + " weighing " + weight + " stones."), true);
        LOGGER.info("Gave WeightedFishItem (type: {}, weight: {}) directly to the player", fishType, weight);

        return 1;
    }

    private static Item getFishItemByType(String fishType) {
        switch (fishType) {
            case "cod":
                return ItemRegistry.COD.get();
            case "salmon":
                return ItemRegistry.SALMON.get();
            case "tuna":
                return ItemRegistry.TUNA.get();
            case "trout":
                return ItemRegistry.TROUT.get();
            case "swordfish":
                return ItemRegistry.SWORDFISH.get();
            default:
                return null;
        }
    }


}

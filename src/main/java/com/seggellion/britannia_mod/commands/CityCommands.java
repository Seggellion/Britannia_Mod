package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.inventory.CityInventory;
import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
                        .executes(CityCommands::clearCityPopulation)))
                // New: Clear inventory command
                .then(Commands.literal("clear")
                    .executes(CityCommands::clearCityInventory))));
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

                cityInventory.getAllCommodities().forEach((category, subcategories) -> {
                    subcategories.forEach((subcategory, items) -> {
                        items.forEach((itemName, quantity) -> {
                            source.sendSuccess(() -> Component.literal("- " + category + " -> " + subcategory + " -> " + itemName + ": " + quantity), false);
                        });
                    });
                });

                // Define the category you want to display weights for
                String weightCategory = "food"; // Replace with the desired category
                Map<String, Double> categoryWeights = cityInventory.getAllCategoryWeights(weightCategory);

                if (!categoryWeights.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("Weight Totals by Subcategory for " + weightCategory + ":"), false);
                    categoryWeights.forEach((subCategory, totalWeight) -> {
                        source.sendSuccess(() -> Component.literal("- " + subCategory + ": " + totalWeight + " stones total"), false);
                    });
                } else {
                    source.sendSuccess(() -> Component.literal("(No weighted commodities recorded yet)"), false);
                }

                return 1;
            } else {
                // sendFailure with direct component
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
}

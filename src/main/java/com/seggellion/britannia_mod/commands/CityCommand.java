package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.city.City;
import com.seggellion.britannia_mod.city.CityManager;
import com.seggellion.britannia_mod.inventory.CityInventory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Map;

public class CityCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("city")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("all")
                    .executes(CityCommand::showAllCities))
                .then(Commands.argument("cityName", StringArgumentType.string())
                    .executes(CityCommand::showCity))
        );
    }

    private static int showAllCities(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Command must be run on a server world."));
            return 0;
        }

        CityManager cityManager = CityManager.get(serverLevel);
        if (cityManager.getCities().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No cities found."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== City Overview ==="), false);
        for (City city : cityManager.getCities().values()) {
            showCityDetails(source, city);
        }

        return 1;
    }

    private static int showCity(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String cityName = StringArgumentType.getString(context, "cityName");

        if (!(source.getLevel() instanceof ServerLevel serverLevel)) {
            source.sendFailure(Component.literal("Command must be run on a server world."));
            return 0;
        }

        CityManager cityManager = CityManager.get(serverLevel);
        City city = cityManager.getCity(cityName);

        if (city == null) {
            source.sendFailure(Component.literal("City not found: " + cityName));
            return 0;
        }

        showCityDetails(source, city);
        return 1;
    }

private static void showCityDetails(CommandSourceStack source, City city) {
    CityInventory inventory = city.getInventory();

    int gold = inventory.getCurrencyAmount("gold");
    int silver = inventory.getCurrencyAmount("silver");
    int copper = inventory.getCurrencyAmount("copper");

    double food = inventory.getFoodSupply();
    double wood = inventory.getWoodSupply();
    double metal = inventory.getMetalSupply();
    double stone = inventory.getStoneSupply();
    double textile = inventory.getTextileSupply();
    double alcohol = inventory.getAlcoholSupply();
    double tech = inventory.getTechnologySupply();

    String cityLine = String.format(
        "City: %-12s | Treasury: %3dg %3ds %3dc | Supplies → Food: %.1f  Wood: %.1f  Metal: %.1f  Stone: %.1f  Textile: %.1f  Alcohol: %.1f  Tech: %.1f",
        city.getName(),
        gold, silver, copper,
        food, wood, metal, stone, textile, alcohol, tech
    );

    source.sendSuccess(() -> Component.literal(cityLine), false);
}

}

package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.commands.CityCommands;
import com.seggellion.britannia_mod.commands.LeaderboardCommands;
import com.seggellion.britannia_mod.commands.MerchantCommands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

public class CommandRegistry {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CommandRegistry::onServerStarting);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        CityCommands.register(event.getServer().getCommands().getDispatcher());
        MerchantCommands.register(event.getServer().getCommands().getDispatcher());
        LeaderboardCommands.register(event.getServer().getCommands().getDispatcher());
        // Register additional command classes here
    }
}

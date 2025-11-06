package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.commands.CityCommand;
import com.seggellion.britannia_mod.commands.BlockCommands;
import com.seggellion.britannia_mod.commands.BootstrapCommands;
import com.seggellion.britannia_mod.commands.APITokenCommands;
import com.seggellion.britannia_mod.commands.StructureCommands;
import com.seggellion.britannia_mod.commands.LeaderboardCommands;
import com.seggellion.britannia_mod.commands.PopulateOresCommand;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

public class CommandRegistry {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CommandRegistry::onServerStarting);
    }

    private static void onServerStarting(ServerStartingEvent event) {
       // CityCommands.register(event.getServer().getCommands().getDispatcher());
        CityCommand.register(event.getServer().getCommands().getDispatcher());
        APITokenCommands.register(event.getServer().getCommands().getDispatcher());
        StructureCommands.register(event.getServer().getCommands().getDispatcher());

        LeaderboardCommands.register(event.getServer().getCommands().getDispatcher());
        BlockCommands.register(event.getServer().getCommands().getDispatcher());
        BootstrapCommands.register(event.getServer().getCommands().getDispatcher());
        PopulateOresCommand.register(event.getServer().getCommands().getDispatcher());
        // Register additional command classes here
    }
}

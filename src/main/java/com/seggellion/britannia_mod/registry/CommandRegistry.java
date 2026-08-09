package com.seggellion.britannia_mod.registry;

import com.seggellion.britannia_mod.commands.CityCommand;
import com.seggellion.britannia_mod.commands.QuestCommand;
import com.seggellion.britannia_mod.commands.SetSkillCommand;
import com.seggellion.britannia_mod.commands.BlockCommands;
import com.seggellion.britannia_mod.commands.BootstrapCommands;
import com.seggellion.britannia_mod.commands.APITokenCommands;
import com.seggellion.britannia_mod.commands.StructureCommands;
import com.seggellion.britannia_mod.commands.LeaderboardCommands;
import com.seggellion.britannia_mod.commands.PopulateOresCommand;
import com.seggellion.britannia_mod.commands.RandomizeWallsCommand;
import com.seggellion.britannia_mod.commands.VerifyCommand;
import com.seggellion.britannia_mod.commands.FarmingDebugCommand;
import com.seggellion.britannia_mod.commands.BannerDyeAdminCommands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class CommandRegistry {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CommandRegistry::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
       // CityCommands.register(event.getServer().getCommands().getDispatcher());
        CityCommand.register(event.getDispatcher());
        QuestCommand.register(event.getDispatcher());
        SetSkillCommand.register(event.getDispatcher());
        APITokenCommands.register(event.getDispatcher());
        StructureCommands.register(event.getDispatcher());

        LeaderboardCommands.register(event.getDispatcher());
        BlockCommands.register(event.getDispatcher());
        BootstrapCommands.register(event.getDispatcher());
        PopulateOresCommand.register(event.getDispatcher());
        RandomizeWallsCommand.register(event.getDispatcher());
        VerifyCommand.register(event.getDispatcher());
        FarmingDebugCommand.register(event.getDispatcher());
        BannerDyeAdminCommands.register(event.getDispatcher());

        // Register additional command classes here
    }
}

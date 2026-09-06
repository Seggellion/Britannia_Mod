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
import com.seggellion.britannia_mod.commands.ManagedDepositCommands;
import com.seggellion.britannia_mod.commands.ManagedVegetationCommands;
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
        // Vendor/Trader Milestone 18: read-only operator diagnostics.
        com.seggellion.britannia_mod.commands.EconomyDiagnosticsCommands.register(event.getDispatcher());
        PopulateOresCommand.register(event.getDispatcher());
        RandomizeWallsCommand.register(event.getDispatcher());
        VerifyCommand.register(event.getDispatcher());
        FarmingDebugCommand.register(event.getDispatcher());
        // Mining milestone 9: read-only operator diagnostics (skill, requirement, resolved
        // resource, restoration status). Mutates nothing.
        com.seggellion.britannia_mod.commands.MiningDebugCommand.register(event.getDispatcher());
        BannerDyeAdminCommands.register(event.getDispatcher());
        ManagedVegetationCommands.register(event.getDispatcher());
        ManagedDepositCommands.register(event.getDispatcher());
        // OreVein milestone 8: read-only ledger diagnostics. /manageddeposit answers questions
        // about a place; this answers questions about the ledger.
        com.seggellion.britannia_mod.commands.OreVeinDiagnosticsCommand.register(event.getDispatcher());
        // Grabby Hands server-parity milestone: read-only. /grabby env identifies the running
        // artifact and the environment gates; /grabby debug <player> names the first gate that
        // refuses one player's pickup. Needed because the gates above the event bus - spawn
        // protection in particular - drop the interaction where no handler can report it.
        com.seggellion.britannia_mod.commands.GrabbyDiagnosticsCommand.register(event.getDispatcher());

        // Crate Column milestone 4: operator scaffolding for building compact columns before
        // top-click stacking exists. Disposable once players can build them by hand.
        com.seggellion.britannia_mod.commands.CrateStackDebugCommand.register(event.getDispatcher());

        // Register additional command classes here
    }
}

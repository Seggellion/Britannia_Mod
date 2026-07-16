package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.seggellion.britannia_mod.server.auth.ServerAuthRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class APITokenCommands {
    private APITokenCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("britannia_api")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
        );
    }

    private static int status(CommandSourceStack source) {
        var server = source.getServer();
        var configured = ServerAuthRegistry.credentials(server);
        if (configured.isEmpty()) {
            source.sendFailure(Component.literal(
                "Server authentication unavailable: " + ServerAuthRegistry.unavailableReason(server)
            ));
            return 0;
        }

        var credentials = configured.get();
        source.sendSuccess(() -> Component.literal(
            "Server authentication: source=" + credentials.source()
                + ", shard=" + credentials.shardName()
                + ", fingerprint=" + credentials.fingerprint()
                + ", last_bootstrap=" + ServerAuthRegistry.lastBootstrapResult(server)
        ), false);
        return 1;
    }
}

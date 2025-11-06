package com.seggellion.britannia_mod.commands;

import com.seggellion.britannia_mod.util.CityAPITokenData;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APITokenCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(APITokenCommands.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("britannia_api")
                .requires(source -> source.hasPermission(2)) // only ops
                .then(Commands.literal("set_token")
                    .then(Commands.argument("token", StringArgumentType.string())
                        .executes(APITokenCommands::setApiToken)
                    )
                )
                .then(Commands.literal("set_secret")
                    .then(Commands.argument("secret", StringArgumentType.string())
                        .executes(APITokenCommands::setShardSecret)
                    )
                )
        );
    }

    private static int setApiToken(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String token = StringArgumentType.getString(context, "token");

        if (source.getLevel() instanceof ServerLevel serverLevel) {
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            data.setApiToken(token);

            source.sendSuccess(() -> Component.literal("API Token set successfully!"), false);
            LOGGER.info("API Token updated: {}", token);
            return 1;
        }

        source.sendFailure(Component.literal("Unable to set API token — not in server context."));
        return 0;
    }

    private static int setShardSecret(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String secret = StringArgumentType.getString(context, "secret");

        if (source.getLevel() instanceof ServerLevel serverLevel) {
            CityAPITokenData data = CityAPITokenData.getOrCreate(serverLevel);
            data.setShardSecret(secret);

            source.sendSuccess(() -> Component.literal("Shard secret set successfully!"), false);
            LOGGER.info("Shard secret updated: {}", secret);
            return 1;
        }

        source.sendFailure(Component.literal("Unable to set shard secret — not in server context."));
        return 0;
    }
}

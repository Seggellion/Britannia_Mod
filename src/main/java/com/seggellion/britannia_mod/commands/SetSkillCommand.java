package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.seggellion.britannia_mod.skill.SkillManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SetSkillCommand {

    // In NeoForge, you pass the dispatcher from the RegisterCommandsEvent
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("skill")
                        .requires(source -> source.hasPermission(2)) // Admin only
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("skillname", StringArgumentType.string())
                                        .then(Commands.argument("skillvalue", FloatArgumentType.floatArg(0.0f, 120.0f))
                                                .executes(ctx -> {
                                                    CommandSourceStack source = ctx.getSource();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                    String skillName = StringArgumentType.getString(ctx, "skillname");
                                                    float skillValue = FloatArgumentType.getFloat(ctx, "skillvalue");

                                                    // Pass to SkillManager to update map, send network packet, and API call
                                                    SkillManager.setSkillAdmin(target, skillName, skillValue);

                                                    // Send success feedback back to the admin who ran the command
                                                    source.sendSuccess(
                                                            () -> Component.literal(String.format("Successfully set %s's %s skill to %.2f",
                                                                    target.getGameProfile().getName(), skillName, skillValue)),
                                                            true
                                                    );

                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }
}
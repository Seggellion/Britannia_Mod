package com.seggellion.britannia_mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.seggellion.britannia_mod.entity.HorseSellerNPC;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.EntityRegistry;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawn_horse_seller")
                .requires(source -> source.hasPermission(2)) // Permission level 2 (OP)
                .executes(ModCommands::spawnHorseSeller));
    }

    private static int spawnHorseSeller(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player != null) {
            HorseSellerNPC npc = EntityRegistry.HORSE_SELLER_NPC.get().create(player.level());

            if (npc != null) {
                npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                player.level().addFreshEntity(npc);
                source.sendSuccess(() -> Component.literal("Horse Seller NPC spawned."), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("Failed to spawn Horse Seller NPC."));
        return 0;
    }
}

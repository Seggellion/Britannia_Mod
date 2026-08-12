package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationManager;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationNode;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationProfile;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationSavedData;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Permission-level-two administration for persistent managed vegetation nodes. */
public final class ManagedVegetationCommands {
    private ManagedVegetationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("managedvegetation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .executes(context -> add(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "position")
                                ))))
                .then(Commands.literal("addhere")
                        .executes(context -> add(
                                context.getSource(),
                                BlockPos.containing(context.getSource().getPosition())
                        )))
                .then(Commands.literal("remove")
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .executes(context -> remove(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "position")
                                ))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "position")
                                ))))
                .then(Commands.literal("force")
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .executes(context -> force(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "position")
                                ))))
                .then(Commands.literal("reroll")
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .executes(context -> reroll(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "position")
                                ))))
                .then(Commands.literal("debug")
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .executes(context -> debug(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "position")
                                ))))
                .then(Commands.literal("spawn")
                        .then(spawnFamily("grass", ManagedVegetationProfile.GRASS_FAMILY_ID))
                        .then(spawnFamily("fern", ManagedVegetationProfile.FERN_ID))
                        .then(spawnFamily("flower", ManagedVegetationProfile.FLOWER_ID)))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnFamily(
            String name,
            ResourceLocation entryId
    ) {
        return Commands.literal(name)
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .executes(context -> spawn(
                                context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context, "position"),
                                entryId,
                                name
                        )));
    }

    private static int add(CommandSourceStack source, BlockPos position) {
        ServerLevel level = source.getLevel();
        if (!ManagedVegetationService.registerNode(level, position)) {
            source.sendFailure(Component.literal(
                    "Managed vegetation requires grass below and three clear, dry blocks starting at the node."
            ));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Registered invisible managed node at " + position.toShortString()
                        + "; first growth is scheduled in " + dueInTicks(level, position)
                        + " normal server ticks. Use /managedvegetation debug "
                        + position.getX() + " " + position.getY() + " " + position.getZ()
        ), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, BlockPos position) {
        if (!ManagedVegetationService.removeNode(source.getLevel(), position)) {
            source.sendFailure(Component.literal("No managed vegetation node exists at " + position.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Removed managed vegetation at " + position.toShortString()
        ), true);
        return 1;
    }

    private static int inspect(CommandSourceStack source, BlockPos position) {
        ManagedVegetationNode node = ManagedVegetationSavedData.get(source.getLevel())
                .nodeAt(position).orElse(null);
        if (node == null) {
            source.sendFailure(Component.literal("No managed vegetation node exists at " + position.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "Managed vegetation %s: lifecycle=%s entry=%s flower=%s stage=%d next_transition=%d",
                position.toShortString(),
                node.lifecycle(),
                node.vegetationEntryId().map(Object::toString).orElse("<none>"),
                node.flowerSpeciesId().map(Object::toString).orElse("<none>"),
                node.flowerStage(),
                node.nextTransitionGameTime()
        )), false);
        return 1;
    }

    private static int force(CommandSourceStack source, BlockPos position) {
        if (!ManagedVegetationService.forceTransition(source.getLevel(), position)) {
            source.sendFailure(Component.literal("No managed vegetation node exists at " + position.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Scheduled the next random managed vegetation transition for the next server tick at "
                        + position.toShortString()
        ), true);
        return 1;
    }

    private static int reroll(CommandSourceStack source, BlockPos position) {
        if (!ManagedVegetationService.rerollNode(source.getLevel(), position)) {
            source.sendFailure(Component.literal("No managed vegetation node exists at " + position.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Returned managed vegetation to regrowth at " + position.toShortString()
        ), true);
        return 1;
    }

    private static int debug(CommandSourceStack source, BlockPos position) {
        source.sendSuccess(() -> Component.literal(
                ManagedVegetationService.debugReport(source.getLevel(), position)
        ), false);
        return 1;
    }

    private static int spawn(
            CommandSourceStack source,
            BlockPos position,
            ResourceLocation entryId,
            String family
    ) {
        if (!ManagedVegetationManager.debugSpawn(source.getLevel(), position, entryId)) {
            source.sendFailure(Component.literal(
                    "Could not spawn " + family + " at " + position.toShortString()
                            + "; register the node first and check /managedvegetation debug."
            ));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Immediately spawned managed " + family + " at " + position.toShortString()
        ), true);
        return 1;
    }

    private static long dueInTicks(ServerLevel level, BlockPos position) {
        return ManagedVegetationSavedData.get(level).nodeAt(position)
                .map(node -> Math.max(0L, node.nextTransitionGameTime() - level.getGameTime()))
                .orElse(0L);
    }
}

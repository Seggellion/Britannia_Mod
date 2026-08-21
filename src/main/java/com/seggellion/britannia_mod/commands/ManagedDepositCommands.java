package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Permission-level-two authoring for managed resource deposits.
 *
 * <p>Deposits are placed, never generated. {@code /populateores} is the equivalent for the ore
 * veins, and it reads its positions from the shard's vein table in Rails; clay has no such table
 * and is not getting one in this pass, so this is the deliberate hand-authoring path — enough to
 * put a handful of test beds on a riverbank before live validation, and nothing that decides
 * where clay is in the world on its own.
 *
 * <p>Shaped after {@code /managedvegetation}: a literal per verb, a {@code BlockPosArgument},
 * and a "here" form for the common case of standing on the spot.
 */
public final class ManagedDepositCommands {

    private ManagedDepositCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("manageddeposit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("place")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .executes(context -> place(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "type"),
                                                BlockPosArgument.getLoadedBlockPos(context, "position")
                                        )))))
                .then(Commands.literal("placehere")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> place(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "type"),
                                        BlockPos.containing(context.getSource().getPosition()).below()
                                ))))
                .then(removeAt())
                .then(inspectAt())
                .then(Commands.literal("types").executes(context -> types(context.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeAt() {
        return Commands.literal("remove")
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .executes(context -> remove(
                                context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context, "position")
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> inspectAt() {
        return Commands.literal("inspect")
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .executes(context -> inspect(
                                context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context, "position")
                        )));
    }

    private static int place(CommandSourceStack source, String type, BlockPos position) {
        ResourceDefinition deposit = ManagedDeposits.byName(type).orElse(null);
        if (deposit == null) {
            source.sendFailure(Component.literal("Unknown deposit type: " + type));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockState existing = level.getBlockState(position);
        if (ManagedDeposits.resolve(existing).isPresent()) {
            source.sendFailure(Component.literal(
                    "A managed deposit already stands at " + position.toShortString()));
            return 0;
        }
        level.setBlockAndUpdate(position, ManagedDeposits.block(deposit).defaultBlockState());
        // A deposit placed on top of a pending restoration would be overwritten when that
        // restoration came due, so the stale record goes with the old block.
        BrokenBlockDataStorage.get(level).remove(position);
        source.sendSuccess(() -> Component.literal(
                "Placed " + deposit.id() + " at " + position.toShortString()
                        + ", replacing " + existing.getBlock().getName().getString()), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, BlockPos position) {
        ServerLevel level = source.getLevel();
        ResourceDefinition deposit = ManagedDeposits.resolve(level.getBlockState(position)).orElse(null);
        if (deposit == null) {
            source.sendFailure(Component.literal(
                    "No managed deposit at " + position.toShortString()));
            return 0;
        }
        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        BrokenBlockDataStorage.get(level).remove(position);
        source.sendSuccess(() -> Component.literal(
                "Removed " + deposit.id() + " at " + position.toShortString()), true);
        return 1;
    }

    /**
     * What stands here, and — if it was worked — how long until it comes back.
     *
     * <p>Read straight out of the shared restoration store rather than from a clay-specific one,
     * which is the point: there is no clay-specific one.
     */
    private static int inspect(CommandSourceStack source, BlockPos position) {
        ServerLevel level = source.getLevel();
        ResourceDefinition standing = ManagedDeposits.resolve(level.getBlockState(position)).orElse(null);
        BrokenBlockData pending = BrokenBlockDataStorage.get(level).getBrokenBlocks().get(position);

        if (standing == null && pending == null) {
            source.sendFailure(Component.literal(
                    "Nothing managed at " + position.toShortString()));
            return 0;
        }
        StringBuilder report = new StringBuilder(position.toShortString()).append(": ");
        report.append(standing == null ? "no deposit standing" : standing.id() + " standing");
        if (pending != null) {
            // Milestone 4: the due moment is stored on the record, resolved from the resource's own
            // regeneration policy when the cell was worked. Recomputing it from a global constant
            // here would have reported six hours for a silica bed that is actually owed at 24.
            long now = System.currentTimeMillis();
            long remaining = pending.dueAt - now;
            report.append("; ")
                    .append(pending.originalState.getBlock().getName().getString())
                    .append(remaining > 0
                            ? " restores in " + (remaining / 60_000L) + " minutes"
                            : " is due to restore");
            if (pending.retryCount > 0) {
                report.append(" (blocked ").append(pending.retryCount)
                        .append(" time(s), next attempt in ")
                        .append(Math.max(0L, pending.retryAt - now) / 60_000L).append(" minutes)");
            }
            report.append(pending.owned()
                    ? ", deposit " + Long.toHexString(pending.instanceId)
                    : ", no owning deposit");
        }
        String message = report.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int types(CommandSourceStack source) {
        for (ResourceDefinition deposit : ManagedDeposits.all()) {
            // Milestone 2: every fact on this line now comes from the resource catalogue, so the
            // listing is a readout of the data rather than of a compiled-in constant. The
            // regeneration hours are included because they are now per resource -- silica's 24 is
            // the first value that differs from the historical six.
            String line = deposit.id() + " -> " + deposit.yield().count() + "x "
                    + ManagedDeposits.yieldStack(deposit).getItem().getDescriptionId()
                    + " with " + deposit.extractionToolTag()
                    + ", regenerates in " + deposit.regenerationHours() + "h";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return ManagedDeposits.all().size();
    }
}

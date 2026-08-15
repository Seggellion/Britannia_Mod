package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seggellion.britannia_mod.event.BlockRestoreHandler;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.mining.MineableDefinition;
import com.seggellion.britannia_mod.mining.Mineables;
import com.seggellion.britannia_mod.mining.MiningBreakGate;
import com.seggellion.britannia_mod.mining.MiningProvenance;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.Optional;

/**
 * Read-only operator diagnostics for Mining (milestone 9).
 *
 * <p>Answers the four questions an operator actually has when a player reports "I can't mine this":
 * what resource is this block, what does it require, what does the player have, and is anything
 * pending here. Deliberately mutates nothing — no skill is set, no restoration is triggered, no
 * provenance is written — so running it can never change the situation it is describing. Skill
 * changes remain the job of the existing {@code /setskill} command.
 *
 * <p>Shaped after {@code FarmingDebugCommand}: operator permission level 2, raycast at what the
 * player is looking at, one fact per line.
 */
public final class MiningDebugCommand {

    private MiningDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mining")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .executes(MiningDebugCommand::debugLookedAtBlock))
                .then(Commands.literal("skill")
                        .executes(context -> reportSkill(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> reportSkill(context,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("restorations")
                        .executes(MiningDebugCommand::reportRestorations)));
    }

    private static int debugLookedAtBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("Run this as a player looking at a block."));
            return 0;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }

        HitResult hit = player.pick(20.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a block and run /mining debug again."));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Optional<MineableDefinition> definition = Mineables.resolve(state);
        boolean playerPlaced = MiningProvenance.isPlayerPlaced(level, pos);

        source.sendSuccess(() -> Component.literal("Mining debug at " + pos.toShortString()
                + " in " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("block=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + ", player_placed=" + playerPlaced
                + (playerPlaced ? " (construction: not a Mining resource)" : "")), false);

        if (definition.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "resource=<none> -- Mining does not govern this block, so it breaks normally"), false);
        } else {
            MineableDefinition mineable = definition.orElseThrow();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "resource=%s (%s), category=%s, required_mining=%.1f, challenge=%.1f",
                    mineable.id(), mineable.displayName(),
                    mineable.category().name().toLowerCase(Locale.ROOT),
                    mineable.requiredMining(), mineable.challenge())), false);
            source.sendSuccess(() -> Component.literal("drop=\"" + mineable.dropName()
                    + "\", economy_commodity=" + mineable.economyCommodity().orElse("<none: unsellable>")
                    + ", restorable=" + mineable.restorable()), false);
        }

        // The same evaluation the break gate itself performs, for this player, right now.
        MiningBreakGate.Evaluation evaluation = MiningBreakGate.evaluate(player, state, level, pos);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "gate=%s (%s), your_mining=%s, required=%s",
                evaluation.type().name(),
                evaluation.permitsBreak() ? "would break" : "would be denied",
                formatSkill(evaluation.currentMining()),
                formatSkill(evaluation.requiredMining()))), false);

        BrokenBlockData pending = BrokenBlockDataStorage.get(level).getBrokenBlocks().get(pos);
        if (pending == null) {
            source.sendSuccess(() -> Component.literal("restoration=<none pending here>"), false);
        } else {
            long remaining = pending.brokenTime + BlockRestoreHandler.RESTORE_DELAY - System.currentTimeMillis();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "restoration: owes=%s, broken_by=%s, due_in=%s, cell_free=%s",
                    BuiltInRegistries.BLOCK.getKey(pending.originalState.getBlock()),
                    pending.playerUUID,
                    remaining <= 0 ? "now (waiting for a free, loaded cell)" : formatDuration(remaining),
                    BlockRestoreHandler.canRestoreInto(level, pos))), false);
        }
        return 1;
    }

    private static int reportSkill(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        CommandSourceStack source = context.getSource();
        SkillManager.SkillSnapshot snapshot =
                SkillManager.getSkillSnapshot(target.getUUID(), MiningSkill.SKILL_ID);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s: mining=%s, data_state=%s%s",
                target.getGameProfile().getName(),
                formatSkill(snapshot.value()),
                snapshot.state().name(),
                snapshot.state() == SkillManager.SkillDataState.AVAILABLE
                        ? "" : " (mining is denied until skill data loads)")), false);
        return 1;
    }

    private static int reportRestorations(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long now = System.currentTimeMillis();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            var pending = BrokenBlockDataStorage.get(level).getBrokenBlocks();
            if (pending.isEmpty()) {
                continue;
            }
            long due = pending.values().stream()
                    .filter(data -> now - data.brokenTime >= BlockRestoreHandler.RESTORE_DELAY)
                    .count();
            long blocked = pending.entrySet().stream()
                    .filter(entry -> now - entry.getValue().brokenTime >= BlockRestoreHandler.RESTORE_DELAY)
                    .filter(entry -> level.isLoaded(entry.getKey()))
                    .filter(entry -> !BlockRestoreHandler.canRestoreInto(level, entry.getKey()))
                    .count();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "%s: pending=%d, due_now=%d, waiting_on_occupied_cell=%d",
                    level.dimension().location(), pending.size(), due, blocked)), false);
        }
        source.sendSuccess(() -> Component.literal(
                "provenance: " + MiningProvenance.trackedCount(source.getLevel())
                        + " player-placed mineables tracked in " + source.getLevel().dimension().location()), false);
        return 1;
    }

    private static String formatSkill(float value) {
        return Float.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "?";
    }

    private static String formatDuration(long milliseconds) {
        long minutes = milliseconds / 60_000L;
        return minutes >= 60 ? (minutes / 60) + "h" + (minutes % 60) + "m" : minutes + "m";
    }
}

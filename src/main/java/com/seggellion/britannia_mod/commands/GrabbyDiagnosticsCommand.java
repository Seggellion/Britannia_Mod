package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.grabbyhands.diagnostics.GrabbyEnvironmentReport;
import com.seggellion.britannia_mod.grabbyhands.diagnostics.GrabbyInteractionDiagnosis;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

/**
 * Read-only operator diagnostics for Grabby Hands.
 *
 * <p>{@code /grabby env} answers "which artifact is this server running, and can the environment
 * stop an interaction before the mod sees it". {@code /grabby debug [player]} answers "why did that
 * exact click do nothing", by walking every gate in the order the server applies them and naming the
 * first that refuses.
 *
 * <p>The optional player argument is the point of the command rather than a convenience. Two gates
 * answer differently for staff - vanilla spawn protection exempts operators outright, and
 * {@code GrabbyPolicy} lets them act inside a structure they do not own - so an operator diagnosing
 * themselves can be told everything is fine about a click that fails for the player who reported it.
 * Run it against the affected player, using that player's own position, posture, hands and
 * permissions.
 *
 * <p>Mutates nothing: no claim is taken, no block is touched, no item moves. Shaped after
 * {@code MiningDebugCommand} and {@code OreVeinDiagnosticsCommand}.
 */
public final class GrabbyDiagnosticsCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GrabbyDiagnosticsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("grabby")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("env")
                        .executes(GrabbyDiagnosticsCommand::reportEnvironment))
                .then(Commands.literal("debug")
                        .executes(context -> diagnose(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> diagnose(context,
                                        EntityArgument.getPlayer(context, "player"))))));
    }

    private static int reportEnvironment(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Grabby Hands environment")
                .withStyle(ChatFormatting.GOLD), false);
        for (String line : GrabbyEnvironmentReport.lines(source.getServer())) {
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return 1;
    }

    private static int diagnose(CommandContext<CommandSourceStack> context, ServerPlayer subject) {
        CommandSourceStack source = context.getSource();
        if (!(subject.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("That player is not in a server level."));
            return 0;
        }

        HitResult hit = subject.pick(20.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal(
                    subject.getGameProfile().getName() + " is not looking at a block."));
            return 0;
        }
        BlockPos pos = blockHit.getBlockPos();

        GrabbyInteractionDiagnosis diagnosis = GrabbyInteractionDiagnosis.pickup(level, subject, pos);
        String heading = "Grabby Hands pickup diagnosis for " + subject.getGameProfile().getName()
                + " at " + pos.toShortString() + " in " + level.dimension().location();

        // The verdict goes FIRST as well as last. It is the only line that answers the question, and
        // a thirty-line report scrolls it out of a chat window before anyone reads it - which is
        // exactly what happened on the first production run.
        boolean refused = diagnosis.firstRefusal().isPresent();
        source.sendSuccess(() -> Component.literal(heading).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(diagnosis.verdict())
                .withStyle(refused ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        for (GrabbyInteractionDiagnosis.Gate gate : diagnosis.gates()) {
            source.sendSuccess(() -> Component.literal("  " + gate).withStyle(styleOf(gate)), false);
        }
        source.sendSuccess(() -> Component.literal(diagnosis.verdict())
                .withStyle(refused ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        if (!refused) {
            source.sendSuccess(() -> Component.literal(
                    "  (a sneak + right-click here would pick the object up)")
                    .withStyle(ChatFormatting.GREEN), false);
        }

        logForCopying(heading, diagnosis);
        return 1;
    }

    /**
     * Writes the whole diagnosis to the server log, as one statement.
     *
     * <p>Chat is where an operator reads the answer; the log is where they copy it from. The first
     * production run of this command came back as four of its thirty lines, because a chat window is
     * a bad place to select text out of, and the line that mattered was not among them.
     *
     * <p>One multi-line statement rather than a line each on purpose. Britannia has been bitten
     * before by bursts of console output: the Apex console rate-limits at around 160 lines a second
     * and blocks the server thread while it does, which shows up as players timing out with no crash.
     * A diagnostic that can stall the server it is diagnosing would be worse than useless.
     */
    private static void logForCopying(String heading, GrabbyInteractionDiagnosis diagnosis) {
        StringBuilder report = new StringBuilder(heading).append(System.lineSeparator());
        for (GrabbyInteractionDiagnosis.Gate gate : diagnosis.gates()) {
            report.append("  ").append(gate).append(System.lineSeparator());
        }
        report.append(diagnosis.verdict());
        LOGGER.info("[grabby-hands][debug]{}{}", System.lineSeparator(), report);
    }

    /**
     * Grey for facts, green for a gate that passed, red for the one that did not.
     *
     * <p>The colouring is the whole readability budget of this command: the output is long, and an
     * operator copying it out of chat needs the refusing line to be findable at a glance.
     */
    private static ChatFormatting styleOf(GrabbyInteractionDiagnosis.Gate gate) {
        if (gate.informational()) {
            return ChatFormatting.GRAY;
        }
        return gate.refuses() ? ChatFormatting.RED : ChatFormatting.DARK_GREEN;
    }
}

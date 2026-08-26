package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
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

/**
 * Read-only operator diagnostics for Grabby Hands.
 *
 * <p>{@code /grabby env} answers "which artifact is this server running, and can the environment
 * stop an interaction before the mod sees it". {@code /grabby debug [player]} answers "why did that
 * exact click do nothing", by walking every gate in the order the server applies them and naming the
 * first that refuses.
 *
 * <p>The optional player argument is the point of the command rather than a convenience. The failure
 * being diagnosed only happens to players who are <em>not</em> operators, and several of the gates -
 * spawn protection, permission level, policy - answer differently for an operator. An operator
 * running this on themselves would be told everything is fine. So an operator runs it against the
 * affected player, using that player's own position, posture, hands and permissions.
 *
 * <p>Mutates nothing: no claim is taken, no block is touched, no item moves. Shaped after
 * {@code MiningDebugCommand} and {@code OreVeinDiagnosticsCommand}.
 */
public final class GrabbyDiagnosticsCommand {

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

        source.sendSuccess(() -> Component.literal("Grabby Hands pickup diagnosis for "
                        + subject.getGameProfile().getName() + " at " + pos.toShortString()
                        + " in " + level.dimension().location())
                .withStyle(ChatFormatting.GOLD), false);
        for (GrabbyInteractionDiagnosis.Gate gate : diagnosis.gates()) {
            source.sendSuccess(() -> Component.literal("  " + gate)
                    .withStyle(gate.refuses() ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        }
        diagnosis.firstRefusal().ifPresentOrElse(
                gate -> source.sendSuccess(() -> Component.literal(
                        "  -> first refusal: [" + gate.layer() + "] " + gate.question())
                        .withStyle(ChatFormatting.RED), false),
                () -> source.sendSuccess(() -> Component.literal(
                        "  -> nothing refuses; a sneak + right-click here would pick the object up")
                        .withStyle(ChatFormatting.GREEN), false));
        return 1;
    }
}

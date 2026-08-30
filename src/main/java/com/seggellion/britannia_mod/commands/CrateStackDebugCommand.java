package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CratePlacement;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.LogicalCrate;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads back what a crate believes about itself.
 *
 * <p>A crate column is several crates sharing one position, and a founded crate is drawn from an
 * origin recorded in its block entity rather than from where it sits. Neither is visible from
 * outside, so when what is drawn and what is recorded disagree, this is how an operator tells which
 * of the two is wrong.
 *
 * <p>Read-only, and permission level 2. It changes nothing and creates nothing a player could
 * obtain.
 */
public final class CrateStackDebugCommand {

    private CrateStackDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cratestack")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(CrateStackDebugCommand::describe))));
    }

    /**
     * Prints what an ordinary crate believes about itself, including what it is standing on.
     *
     * <p>Worth reporting even for a crate on the ground: a crate that thinks it is resting on
     * something that is no longer there is drawn by nothing at all, and reading that back is the only
     * way to tell that case apart from a rendering fault.
     */
    private static int describeCrate(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrateBlock crate) || !crate.hasValidPart(state)) {
            source.sendFailure(Component.literal("No crate column or crate at " + pos));
            return 0;
        }
        BlockPos anchor = crate.anchorPosition(pos, state);
        if (!(level.getBlockEntity(anchor) instanceof CrateBlockEntity crateEntity)) {
            source.sendFailure(Component.literal(
                    "Crate cell at " + pos + " whose anchor " + anchor + " has no inventory"));
            return 0;
        }
        boolean founded = crateEntity.hasFoundation();
        boolean standingOnSomething =
                CrateFoundation.foundationUnder(level, anchor).isPresent();
        source.sendSuccess(() -> Component.literal("Crate at " + anchor)
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  %d slots, %d cells, part %d",
                crateEntity.getContainerSize(), crate.cells().size(),
                state.getValue(CrateBlock.PART))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  origin %+.2f voxels (%s)",
                crateEntity.originHundredths()
                        / (double) CrateStackLayout.HUNDREDTHS_PER_VOXEL,
                !founded
                        ? "standing on its own floor"
                        : standingOnSomething
                                ? "resting on the crate below"
                                : "ORPHANED - resting on a crate that is gone")), false);
        return 1;
    }

    /** Prints what a column believes about itself, which is the fastest way to read a visual defect. */
    private static int describe(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = source.getLevel();
        BlockPos root = CrateStackBlock.rootOf(pos, level.getBlockState(pos));

        if (!(level.getBlockEntity(root) instanceof CrateStackBlockEntity stack)) {
            return describeCrate(source, level, pos);
        }
        source.sendSuccess(() -> Component.literal("Crate column at " + root)
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  %d crates, %.2f voxels, %d cells",
                stack.crateCount(), stack.layout().totalVoxels(), stack.requiredCellCount())), false);
        // Which surface the column is standing on is the first thing worth knowing when what is drawn
        // and what is recorded disagree, so it is reported even when there is no foundation.
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  origin %+.2f voxels (%s)",
                stack.originHundredths() / (double) CrateStackLayout.HUNDREDTHS_PER_VOXEL,
                stack.hasFoundation()
                        ? "resting on a foundation below " + root
                        : "freestanding")), false);
        for (LogicalCrate crate : stack.crates()) {
            CratePlacement placement = stack.placementOf(crate.id());
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  id %d  %-6s %-5s  %.2f -> %.2f  cells %d..%d",
                    crate.id(), crate.variant().serializedName(), crate.facing(),
                    placement.baseVoxels(), placement.topVoxels(),
                    placement.firstCell(), placement.lastCell())), false);
        }
        return 1;
    }
}

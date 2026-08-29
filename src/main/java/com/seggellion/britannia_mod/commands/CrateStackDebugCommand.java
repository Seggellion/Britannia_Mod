package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CratePlacement;
import com.seggellion.britannia_mod.crate.CrateStackColumnSync;
import com.seggellion.britannia_mod.crate.CrateStackPromotion;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.crate.LogicalCrate;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Operator scaffolding for looking at crate columns before players can build them.
 *
 * <p>Compact columns are assembled by promotion and append, and neither is reachable from ordinary
 * play yet — that arrives with the interaction milestone. Until then this is how a column gets built
 * in a live client so its geometry, lighting and contact can actually be looked at.
 *
 * <p>Disposable. Once top-click stacking exists a player can build every one of these fixtures by
 * hand, and this command has no reason to remain. It mutates only what it is pointed at, needs
 * permission level 2, and creates nothing a player could obtain.
 */
public final class CrateStackDebugCommand {

    /** The named fixtures, each a column read bottom upwards. */
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("a", "small + small", CrateVariant.SMALL, CrateVariant.SMALL),
            new Fixture("b", "small + small + small",
                    CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL),
            new Fixture("c", "medium + medium", CrateVariant.MEDIUM, CrateVariant.MEDIUM),
            new Fixture("d", "small + medium", CrateVariant.SMALL, CrateVariant.MEDIUM),
            new Fixture("e", "medium + small", CrateVariant.MEDIUM, CrateVariant.SMALL),
            new Fixture("f", "small + medium + small + medium",
                    CrateVariant.SMALL, CrateVariant.MEDIUM,
                    CrateVariant.SMALL, CrateVariant.MEDIUM),
            new Fixture("tall", "eight small, at the four-cell cap",
                    CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL,
                    CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL));

    private CrateStackDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var command = Commands.literal("cratestack")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(CrateStackDebugCommand::describe)))
                .then(Commands.literal("all")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(CrateStackDebugCommand::buildEvery)));
        for (Fixture fixture : FIXTURES) {
            command = command.then(Commands.literal(fixture.name())
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(context -> build(
                                    context,
                                    BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                    fixture))));
        }
        dispatcher.register(command);
    }

    /** Lays every fixture out in a row, four blocks apart, so they can be compared side by side. */
    private static int buildEvery(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos origin = BlockPosArgument.getLoadedBlockPos(context, "pos");
        int built = 0;
        for (int index = 0; index < FIXTURES.size(); index++) {
            if (build(context, origin.offset(index * 4, 0, 0), FIXTURES.get(index)) > 0) {
                built++;
            }
        }
        int total = built;
        context.getSource().sendSuccess(
                () -> Component.literal("Built " + total + " crate column fixtures")
                        .withStyle(ChatFormatting.GREEN), false);
        return built;
    }

    private static int build(
            CommandContext<CommandSourceStack> context, BlockPos base, Fixture fixture) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // A clean footing and a clear column, so a rebuilt fixture never inherits the last one.
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        for (int cell = 0; cell < 6; cell++) {
            level.removeBlock(base.above(cell), false);
        }

        level.setBlock(
                base,
                BlockRegistry.SMALL_CRATE.get().defaultBlockState()
                        .setValue(com.seggellion.britannia_mod.block.CrateBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        CrateStackPromotion.Result promoted = CrateStackPromotion.promote(level, base);
        if (!promoted.succeeded()) {
            source.sendFailure(Component.literal(
                    "Could not start a column at " + base + ": " + promoted.refusal().orElseThrow()));
            return 0;
        }

        // The first crate is always small, so a fixture beginning with a medium replaces it.
        CrateStackBlockEntity stack = promoted.promoted().orElseThrow().stack();
        if (fixture.variants().get(0) != CrateVariant.SMALL) {
            stack.removeCrate(promoted.promoted().orElseThrow().crateId());
            stack.appendCrate(fixture.variants().get(0), Direction.NORTH);
        }
        for (int index = 1; index < fixture.variants().size(); index++) {
            CrateStackColumnSync.Growth growth = CrateStackColumnSync.appendCrate(
                    level, base, fixture.variants().get(index), Direction.NORTH);
            if (!growth.succeeded()) {
                source.sendFailure(Component.literal("Column " + fixture.name() + " stopped at crate "
                        + index + ": " + growth.refusal()));
                break;
            }
        }
        CrateStackColumnSync.notifyClients(level, base, CrateStackColumnSync.reconcile(level, base));

        source.sendSuccess(() -> Component.literal(
                "  " + fixture.name() + " (" + fixture.description() + ") at " + base)
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
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
                stack.originHundredths()
                        / (double) com.seggellion.britannia_mod.crate.CrateStackLayout
                                .HUNDREDTHS_PER_VOXEL,
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

    private record Fixture(String name, String description, List<CrateVariant> variants) {
        Fixture(String name, String description, CrateVariant... variants) {
            this(name, description, List.of(variants));
        }
    }
}

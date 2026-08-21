package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the deposit ledger knows, in a form an operator can act on.
 *
 * <p>Each branch is a public static method taking the command source, so a GameTest can drive the
 * behaviour without going through Brigadier parsing. Each returns the count it reported, which is
 * both the command's result value and the thing worth asserting.
 *
 * <h2>Why this and not more of {@code /manageddeposit}</h2>
 * {@code /manageddeposit} is about a <em>place</em>: put a bed here, look at what is under my
 * crosshair, take that one away. Everything below is about the <em>ledger</em>: how many deposits
 * exist, where a resource can be found, what state one is in, when its worked cells are owed back.
 * Those are different questions with different arguments — an instance id rather than a block
 * position — and folding them into a command whose every existing branch takes a position would
 * make both harder to use.
 *
 * <h2>Read-only, deliberately</h2>
 * Nothing here creates or deletes a deposit. The playbook is explicit that arbitrary create/delete
 * waits until the integrity constraints are proven, and the one write this command does offer —
 * {@code regenerate} — does not invent anything: it brings a deposit's <em>existing</em> restoration
 * debts forward to now, so the ordinary restoration scheduler puts back exactly the cells it was
 * already going to put back, and nothing else. No second scheduler, no bypass of the occupancy
 * rules, no new cells.
 */
public final class OreVeinDiagnosticsCommand {

    /** Longest listing a single command will print, so a large ledger cannot flood the console. */
    private static final int MAX_ROWS = 40;

    private OreVeinDiagnosticsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orevein")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.argument("resource", StringArgumentType.word())
                                .executes(context -> list(context.getSource(),
                                        StringArgumentType.getString(context, "resource")))))
                .then(Commands.literal("locate")
                        .then(Commands.argument("resource", StringArgumentType.word())
                                .executes(context -> locate(context.getSource(),
                                        StringArgumentType.getString(context, "resource")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> inspect(context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("regenerate")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> regenerate(context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("refusals")
                        .executes(context -> refusals(context.getSource()))));
    }

    /* ------------------------------------------------------------------ */
    /*  stats                                                              */
    /* ------------------------------------------------------------------ */

    /** How many deposits exist, broken down by the two things an operator cares about. */
    public static int stats(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        DepositLedger ledger = DepositLedger.get(level);
        BrokenBlockDataStorage debts = BrokenBlockDataStorage.get(level);

        Map<String, Integer> byResource = new LinkedHashMap<>();
        Map<DepositSource, Integer> bySource = new LinkedHashMap<>();
        int progressKnown = 0;
        int fullyMaterialized = 0;
        long plannedTotal = 0;
        long materializedTotal = 0;
        long blockedTotal = 0;

        for (DepositInstance instance : ledger.all()) {
            byResource.merge(instance.resourceId(), 1, Integer::sum);
            bySource.merge(instance.source(), 1, Integer::sum);
            plannedTotal += instance.plannedCells();
            if (instance.progressKnown()) {
                progressKnown++;
                materializedTotal += instance.materializedCells();
                blockedTotal += instance.blockedCells();
                if (instance.fullyMaterialized()) {
                    fullyMaterialized++;
                }
            }
        }

        send(source, "Deposit ledger for " + level.dimension().location() + ":");
        send(source, "  instances            " + ledger.size());
        send(source, "  planned cells        " + plannedTotal);
        send(source, "  progress known       " + progressKnown + " of " + ledger.size()
                + " (the rest have only been partly materialised so far)");
        send(source, "  materialised cells   " + materializedTotal);
        send(source, "  blocked cells        " + blockedTotal);
        send(source, "  fully materialised   " + fullyMaterialized);
        send(source, "  outstanding debts    " + debts.totalCount()
                + " worked cells awaiting restoration");
        send(source, "  duplicates refused   " + ledger.refusals().size()
                + " (see /orevein refusals)");

        if (!bySource.isEmpty()) {
            send(source, "  by source:");
            bySource.forEach((key, count) -> send(source, "    " + key.id() + "  " + count));
        }
        if (!byResource.isEmpty()) {
            send(source, "  by resource:");
            byResource.forEach((key, count) -> send(source, "    " + key + "  " + count));
        }
        return ledger.size();
    }

    /* ------------------------------------------------------------------ */
    /*  list                                                               */
    /* ------------------------------------------------------------------ */

    public static int list(CommandSourceStack source, String resourceFilter) {
        ServerLevel level = source.getLevel();
        String resourceId = resourceFilter == null ? null : resolveResourceId(resourceFilter);
        if (resourceFilter != null && resourceId == null) {
            source.sendFailure(Component.literal(unknownResource(resourceFilter)));
            return 0;
        }

        List<DepositInstance> matching = new ArrayList<>();
        for (DepositInstance instance : DepositLedger.get(level).all()) {
            if (resourceId == null || instance.resourceId().equals(resourceId)) {
                matching.add(instance);
            }
        }
        matching.sort((a, b) -> Long.compare(a.instanceId(), b.instanceId()));

        if (matching.isEmpty()) {
            send(source, "No deposits registered"
                    + (resourceId == null ? "" : " for " + resourceId) + '.');
            return 0;
        }
        send(source, matching.size() + " deposit(s)"
                + (resourceId == null ? "" : " of " + resourceId) + ':');
        int shown = 0;
        for (DepositInstance instance : matching) {
            if (shown++ >= MAX_ROWS) {
                send(source, "  ... " + (matching.size() - MAX_ROWS)
                        + " more; narrow with /orevein list <resource>");
                break;
            }
            send(source, "  " + summarise(instance));
        }
        return matching.size();
    }

    /* ------------------------------------------------------------------ */
    /*  locate                                                             */
    /* ------------------------------------------------------------------ */

    /** The nearest deposit of a resource to whoever asked, which is the useful sense of "where". */
    public static int locate(CommandSourceStack source, String resourceFilter) {
        ServerLevel level = source.getLevel();
        String resourceId = resolveResourceId(resourceFilter);
        if (resourceId == null) {
            source.sendFailure(Component.literal(unknownResource(resourceFilter)));
            return 0;
        }
        BlockPos from = BlockPos.containing(source.getPosition());

        DepositInstance nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        int candidates = 0;
        for (DepositInstance instance : DepositLedger.get(level).all()) {
            if (!instance.resourceId().equals(resourceId)) {
                continue;
            }
            candidates++;
            double distance = Math.sqrt(instance.origin().distSqr(from));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = instance;
            }
        }
        if (nearest == null) {
            send(source, "No " + resourceId + " deposit is registered in "
                    + level.dimension().location() + ". A deposit only enters the ledger when it is"
                    + " placed or generated, so an unexplored region has none yet.");
            return 0;
        }
        send(source, "Nearest of " + candidates + " " + resourceId + " deposit(s): "
                + Math.round(nearestDistance) + " blocks away");
        send(source, "  " + summarise(nearest));
        return 1;
    }

    /* ------------------------------------------------------------------ */
    /*  inspect                                                            */
    /* ------------------------------------------------------------------ */

    /** Everything the ledger and the restoration store know about one deposit. */
    public static int inspect(CommandSourceStack source, String rawId) {
        ServerLevel level = source.getLevel();
        Optional<Long> parsed = parseId(rawId);
        if (parsed.isEmpty()) {
            source.sendFailure(Component.literal(
                    "'" + rawId + "' is not a deposit id. Ids are hexadecimal, as printed by"
                            + " /orevein list."));
            return 0;
        }
        DepositInstance instance = DepositLedger.get(level).byId(parsed.get()).orElse(null);
        if (instance == null) {
            source.sendFailure(Component.literal(
                    "No deposit " + Long.toHexString(parsed.get()) + " in "
                            + level.dimension().location() + '.'));
            return 0;
        }

        int depleted = DepositRegistrar.depletedCells(level, instance);
        send(source, "Deposit " + Long.toHexString(instance.instanceId()));
        send(source, "  resource        " + instance.resourceId()
                + " (definition revision " + instance.definitionRevision() + ')');
        send(source, "  source          " + instance.source().id());
        send(source, "  source identity " + instance.sourceIdentity());
        send(source, "  origin          " + instance.origin().toShortString()
                + "  radius " + instance.radius() + "  rotation " + instance.rotation());
        send(source, "  bounds          " + instance.boundsMin().toShortString()
                + " .. " + instance.boundsMax().toShortString()
                + "  (" + instance.touchedChunks().size() + " chunk(s))");
        send(source, "  planned cells   " + instance.plannedCells());
        if (instance.progressKnown()) {
            send(source, "  materialised    " + instance.materializedCells());
            send(source, "  blocked         " + instance.blockedCells()
                    + " (cells the host policy refused)");
        } else {
            send(source, "  materialised    unknown -- no complete pass has covered this deposit yet");
        }
        send(source, "  depleted now    " + depleted + " worked cell(s) awaiting restoration");
        nextDue(level, instance).ifPresentOrElse(
                due -> send(source, "  next due        " + describeDelay(due - System.currentTimeMillis())),
                () -> send(source, "  next due        nothing outstanding"));
        return 1;
    }

    /* ------------------------------------------------------------------ */
    /*  regenerate                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Bring one deposit's outstanding restoration debts forward to now.
     *
     * <p>Deliberately the smallest possible operation. It does not place cells, does not consult
     * the planner, and does not touch a deposit that owes nothing — it rewrites the due time of
     * debts that already exist, and the ordinary {@code BlockRestoreHandler} then restores them on
     * its own terms, into cells it has checked are free. There is no second scheduler and no way
     * for this to conjure a cell the deposit did not have.
     */
    public static int regenerate(CommandSourceStack source, String rawId) {
        ServerLevel level = source.getLevel();
        Optional<Long> parsed = parseId(rawId);
        if (parsed.isEmpty()) {
            source.sendFailure(Component.literal("'" + rawId + "' is not a deposit id."));
            return 0;
        }
        DepositInstance instance = DepositLedger.get(level).byId(parsed.get()).orElse(null);
        if (instance == null) {
            source.sendFailure(Component.literal(
                    "No deposit " + Long.toHexString(parsed.get()) + " in "
                            + level.dimension().location() + '.'));
            return 0;
        }

        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        long now = System.currentTimeMillis();
        int brought = 0;
        for (BrokenBlockData debt : debtsOf(level, instance)) {
            if (debt.dueAt <= now) {
                continue;
            }
            storage.replace(new BrokenBlockData(
                    debt.pos, debt.originalState, now - 1_000L, debt.playerUUID,
                    now - 1_000L, debt.instanceId, debt.resourceId, 0L, 0));
            brought++;
        }
        if (brought == 0) {
            send(source, "Deposit " + Long.toHexString(instance.instanceId())
                    + " has nothing outstanding to bring forward.");
            return 0;
        }
        send(source, "Deposit " + Long.toHexString(instance.instanceId()) + ": " + brought
                + " worked cell(s) are now due; the restoration scheduler will return them on its"
                + " next pass, into cells it finds free.");
        return brought;
    }

    /* ------------------------------------------------------------------ */
    /*  refusals                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * What the ledger turned away.
     *
     * <p>The playbook asks operators to be able to answer "whether a duplicate instance was
     * rejected". A refusal leaves no trace in the ledger by design — refusing is precisely not
     * writing anything — so the ledger keeps a bounded record of them for exactly this question.
     */
    public static int refusals(CommandSourceStack source) {
        List<DepositLedger.Refusal> refusals = DepositLedger.get(source.getLevel()).refusals();
        if (refusals.isEmpty()) {
            send(source, "No deposit registration has been refused since this server started.");
            return 0;
        }
        send(source, refusals.size() + " refused registration(s), most recent last:");
        for (DepositLedger.Refusal refusal : refusals) {
            send(source, "  " + Long.toHexString(refusal.instanceId()) + "  "
                    + refusal.outcome() + "  " + refusal.message());
        }
        return refusals.size();
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private static String summarise(DepositInstance instance) {
        String progress = instance.progressKnown()
                ? instance.materializedCells() + "/" + instance.plannedCells() + " placed"
                : instance.plannedCells() + " planned, progress unknown";
        return Long.toHexString(instance.instanceId())
                + "  " + instance.resourceId()
                + "  " + instance.source().id()
                + "  at " + instance.origin().toShortString()
                + "  r=" + instance.radius()
                + "  " + progress;
    }

    /** Accepts a full id or a bare path, so an operator can type what they can see. */
    private static String resolveResourceId(String raw) {
        String wanted = raw.toLowerCase(Locale.ROOT);
        for (ResourceDefinition definition : ResourceCatalog.instance().all()) {
            if (definition.id().equals(wanted) || definition.path().equals(wanted)) {
                return definition.id();
            }
        }
        return null;
    }

    private static String unknownResource(String raw) {
        List<String> known = new ArrayList<>();
        ResourceCatalog.instance().all().forEach(definition -> known.add(definition.path()));
        return "Unknown resource '" + raw + "'. Known: " + String.join(", ", known);
    }

    private static Optional<Long> parseId(String raw) {
        try {
            return Optional.of(Long.parseUnsignedLong(raw.toLowerCase(Locale.ROOT)
                    .replaceFirst("^0x", ""), 16));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static List<BrokenBlockData> debtsOf(ServerLevel level, DepositInstance instance) {
        BrokenBlockDataStorage storage = BrokenBlockDataStorage.get(level);
        List<BrokenBlockData> found = new ArrayList<>();
        for (var chunk : instance.touchedChunks()) {
            for (BrokenBlockData debt : storage.debtsIn(chunk)) {
                if (debt.instanceId == instance.instanceId()) {
                    found.add(debt);
                }
            }
        }
        return found;
    }

    private static Optional<Long> nextDue(ServerLevel level, DepositInstance instance) {
        long soonest = Long.MAX_VALUE;
        for (BrokenBlockData debt : debtsOf(level, instance)) {
            soonest = Math.min(soonest, debt.dueAt);
        }
        return soonest == Long.MAX_VALUE ? Optional.empty() : Optional.of(soonest);
    }

    private static String describeDelay(long millis) {
        if (millis <= 0) {
            return "now (overdue)";
        }
        long minutes = millis / 60_000L;
        if (minutes < 60) {
            return "in " + minutes + " minute(s)";
        }
        return "in " + (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}

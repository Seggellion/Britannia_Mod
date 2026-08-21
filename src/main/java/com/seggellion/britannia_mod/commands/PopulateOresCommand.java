package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

import com.seggellion.britannia_mod.util.OreVeinFetcher;
import com.seggellion.britannia_mod.features.VeinPlacementValidation;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.deposit.DepositIdentity;
import com.seggellion.britannia_mod.resource.deposit.DepositInstance;
import com.seggellion.britannia_mod.resource.deposit.DepositLedger;
import com.seggellion.britannia_mod.resource.deposit.DepositRegistrar;
import com.seggellion.britannia_mod.resource.deposit.DepositSource;
import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlacementPlanner;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import com.seggellion.britannia_mod.config.ModConfig;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * The legacy operator route that writes Rails' curated vein rows into the world.
 *
 * <h2>Milestone 1 containment</h2>
 * This command is scheduled for replacement by the deterministic deposit platform (milestones 3
 * and 4). Until then it has been contained rather than rebuilt:
 *
 * <ul>
 *   <li><b>Rows are validated before a shape sees them.</b> {@link VeinPlacementValidation} holds
 *       the rules; a bad row is reported and skipped instead of throwing out of a shape after the
 *       command has already written blocks.</li>
 *   <li><b>Coal is withdrawn.</b> It placed {@code minecraft:coal_ore}, which the Mining catalogue
 *       does not govern, so it broke with vanilla drops, no requirement and no restoration. A
 *       managed generation route must not manufacture unmanaged economic material. This removes
 *       only the placement route; vanilla coal world generation is untouched, and suppressing that
 *       is milestone 5's job.</li>
 *   <li><b>{@code clear} and {@code undoores} are disabled.</b> See {@link #refuseLegacyOperation}.</li>
 *   <li><b>The static undo map is gone.</b> It was a {@code static Map<BlockPos, BlockState>} with
 *       no dimension key, never serialised, shared across every invocation and every dimension —
 *       so it confused two dimensions' positions with each other and was empty after a restart.
 *       The shapes still take a map, because changing their signatures is milestone 3, but it is
 *       now created per invocation and discarded.</li>
 * </ul>
 */
public class PopulateOresCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The ore types this command can place, read from the canonical resource catalogue.
     *
     * <p>Milestone 2 removed the {@code ORE_TYPES} map that used to live here. It was a third copy
     * of knowledge the catalogue now owns — which block an ore name places, and which ore names
     * exist at all — sitting alongside the shape {@code switch} below and
     * {@link VeinPlacementValidation}'s own table. A resource is placeable exactly when its
     * definition carries a {@code generation} block, so adding or retiring one is a data edit.
     *
     * <p>Coal's absence is therefore no longer a deletion here but a fact about the data: it has no
     * definition, because {@code minecraft:coal_ore} is not a Mining-catalogued resource and a
     * managed generation route must not manufacture unmanaged economic material. Vanilla coal world
     * generation remains untouched; suppressing that is milestone 5.
     */
    public static Set<String> placeableOreTypes() {
        return Set.copyOf(VeinPlacementValidation.placeableOreTypes());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("populateores")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear")
                    .executes(context -> refuseLegacyOperation(context.getSource(), "populateores clear"))
                    .then(Commands.argument("ore_type", StringArgumentType.string())
                        .executes(context -> refuseLegacyOperation(context.getSource(), "populateores clear"))
                    )
                )
                .then(Commands.argument("ore_type", StringArgumentType.string())
                    .executes(PopulateOresCommand::executePopulateOres)
                )
                .then(Commands.literal("region")
                    .then(Commands.argument("region_name", StringArgumentType.string())
                        .executes(PopulateOresCommand::executePopulateRegionOres)
                        .then(Commands.argument("ore_type", StringArgumentType.string())
                            .executes(PopulateOresCommand::executePopulateRegionOresWithType)
                        )
                    )
                )
        );

        dispatcher.register(
            Commands.literal("undoores")
                .requires(source -> source.hasPermission(2))
                .executes(context -> refuseLegacyOperation(context.getSource(), "undoores"))
        );
    }

    /**
     * The single refusal for both retired operations.
     *
     * <p>{@code clear} walked a 41x41 block of chunks around the operator through
     * {@code level.getChunk(x, z)}, which <em>generates</em> a chunk that does not exist yet, and
     * read every block from the bottom of the world to the top — roughly 165 million reads on the
     * server thread, generating up to 1681 chunks on the way. It then replaced every block whose
     * type appeared in its own table with stone, with no way to tell a placed deposit from
     * natural terrain, a village blacksmith's decoration or a player's wall. Its message called
     * that "Globally cleared" while the scan reached about 320 blocks. There is no repair for a
     * tool whose premise is that ore identity implies ore provenance; the bounded, previewable
     * retrofit in milestone 8 is its replacement.
     *
     * <p>{@code undoores} depended on the static, dimension-blind, non-persistent map described on
     * the class, so after any restart it restored nothing, and across dimensions it could restore
     * the wrong blocks. The deposit ledger in milestone 4 is its replacement.
     *
     * <p>The literals stay registered so an operator who types them gets this explanation instead
     * of a syntax error. Nothing is scanned, generated or written here.
     */
    private static int refuseLegacyOperation(CommandSourceStack source, String operation) {
        source.sendFailure(Component.literal(
                "/" + operation + " is disabled. It could not tell a managed deposit from natural "
                        + "terrain, a structure or a player's build, and clearing also generated "
                        + "chunks to scan them. Deposit identity (milestone 4) and the bounded, "
                        + "previewable retrofit (milestone 8) replace it. Read-only diagnostics "
                        + "remain: /brokenblocks list, /mining restorations, /manageddeposit inspect."));
        return 0;
    }

    private static int executePopulateOres(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String oreType = StringArgumentType.getString(context, "ore_type").toLowerCase(Locale.ROOT);

        ResourceDefinition resource = VeinPlacementValidation.resourceFor(oreType).orElse(null);
        if (resource == null) {
            source.sendFailure(Component.literal("Unknown or unplaceable ore type: " + oreType
                    + ". Placeable: " + String.join(", ", VeinPlacementValidation.placeableOreTypes())));
            return 0;
        }

        MinecraftServer server = level.getServer();
        String shardName = ModConfig.SHARD_NAME;
        List<OreVeinFetcher.OreVein> veins = OreVeinFetcher.fetchOreVeins(server, shardName);

        Tally tally = new Tally();
        LOGGER.info("Populating ore: {}", oreType);

        for (OreVeinFetcher.OreVein vein : veins) {
            if (!vein.oreType.equalsIgnoreCase(oreType)) {
                continue;
            }
            place(source, level, resource, vein, tally);
        }
        tally.report(source, "of " + oreType);
        return tally.placed;
    }

    private static int executePopulateRegionOres(CommandContext<CommandSourceStack> context) {
        String region = StringArgumentType.getString(context, "region_name").toLowerCase(Locale.ROOT);
        return populateRegion(context, region, null);
    }

    private static int executePopulateRegionOresWithType(CommandContext<CommandSourceStack> context) {
        String region = StringArgumentType.getString(context, "region_name").toLowerCase(Locale.ROOT);
        String oreType = StringArgumentType.getString(context, "ore_type").toLowerCase(Locale.ROOT);
        return populateRegion(context, region, oreType);
    }

    private static int populateRegion(
            CommandContext<CommandSourceStack> context, String region, String oreType) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        MinecraftServer server = level.getServer();
        String shardName = ModConfig.SHARD_NAME;
        List<OreVeinFetcher.OreVein> veins = OreVeinFetcher.fetchOreVeins(server, shardName);

        Tally tally = new Tally();
        LOGGER.info("Populating ores for region: {}", region);
        if (oreType != null) LOGGER.info("Filtered by ore type: {}", oreType);

        for (OreVeinFetcher.OreVein vein : veins) {
            if (!region.equalsIgnoreCase(vein.region)) continue;
            if (oreType != null && !vein.oreType.equalsIgnoreCase(oreType)) continue;

            ResourceDefinition resource =
                    VeinPlacementValidation.resourceFor(vein.oreType).orElse(null);
            if (resource == null) {
                tally.skipped++;
                reportSkip(source, vein, "unknown or unplaceable ore type '" + vein.oreType + "'");
                continue;
            }
            place(source, level, resource, vein, tally);
        }
        tally.report(source, "in region " + region + (oreType != null ? (" for ore " + oreType) : ""));
        return tally.placed;
    }

    /** One curated row against the build limits of the level it would be written into. */
    private static Optional<String> validate(ServerLevel level, OreVeinFetcher.OreVein vein) {
        BlockPos position = vein.getPosition();
        return VeinPlacementValidation.reject(
                vein.oreType,
                vein.radius,
                vein.rotation,
                position.getX(),
                position.getY(),
                position.getZ(),
                level.getMinBuildHeight(),
                level.getMaxBuildHeight());
    }

    /** A skipped row is said out loud: silence would look like a row that placed nothing. */
    private static void reportSkip(
            CommandSourceStack source, OreVeinFetcher.OreVein vein, String reason) {
        String message = "Skipped vein " + vein.oreType + " at "
                + vein.getPosition().toShortString() + ": " + reason;
        LOGGER.warn(message);
        source.sendSystemMessage(Component.literal(message));
    }

    /** Running totals for one command invocation. */
    private static final class Tally {
        int placed;
        int skipped;
        int rejected;
        int alreadyRegistered;
        boolean truncated;

        void report(CommandSourceStack source, String what) {
            String message = "Populated " + placed + " blocks " + what
                    + (skipped > 0 ? " (" + skipped + " row(s) skipped)" : "")
                    + (rejected > 0 ? " (" + rejected + " cell(s) refused by host policy)" : "")
                    + (alreadyRegistered > 0
                            ? " (" + alreadyRegistered + " deposit(s) already registered, resumed)" : "")
                    + (truncated ? " -- budget reached, run again to continue" : "");
            source.sendSuccess(() -> Component.literal(message), true);
        }
    }

    /**
     * One curated row, planned and materialised.
     *
     * <p>The whole of milestone 3's change to this command is visible here. It resolves the
     * resource, asks {@link PlacementPlanner} for a deterministic plan, and hands that plan to
     * {@link MaterializationService}. It does not choose an algorithm, does not touch randomness,
     * and does not write a block. Running it twice re-derives the same seed, re-plans the same
     * cells, finds them already correct and writes nothing -- which is what stopped the same
     * command producing a different vein every time it was run.
     */
    private static void place(
            CommandSourceStack source,
            ServerLevel level,
            ResourceDefinition resource,
            OreVeinFetcher.OreVein vein,
            Tally tally) {

        java.util.Optional<String> rejection = validate(level, vein);
        if (rejection.isPresent()) {
            tally.skipped++;
            reportSkip(source, vein, rejection.get());
            return;
        }
        ShapeRotation rotation = VeinPlacementValidation.normaliseRotation(vein.rotation).orElseThrow();

        String shard = ModConfig.SHARD_NAME;
        String dimension = level.dimension().location().toString();
        BlockPos origin = vein.getPosition();

        // One identity contract: the id the deposit is registered under and the seed its geometry
        // is drawn from come from the same canonical encoding of this row.
        long instanceId = DepositIdentity.rails(shard, dimension, resource.id(),
                origin.getX(), origin.getY(), origin.getZ(), vein.radius, rotation, vein.region);
        String identity = DepositIdentity.railsEncoding(shard, dimension, resource.id(),
                origin.getX(), origin.getY(), origin.getZ(), vein.radius, rotation, vein.region);

        PlannedDeposit deposit = PlacementPlanner.plan(resource, dimension, origin,
                vein.radius, rotation, DepositIdentity.plannerSeed(instanceId));

        DepositInstance candidate =
                DepositRegistrar.describe(deposit, instanceId, DepositSource.RAILS, identity);
        DepositLedger.Registration registration = DepositLedger.get(level).register(candidate);

        if (!registration.mayMaterialize()) {
            // A collision or a revision change. Refused loudly, and nothing is written -- silently
            // overwriting one deposit with another, or writing half a vein in each of two shapes,
            // is exactly what the ledger exists to prevent.
            tally.skipped++;
            reportSkip(source, vein, registration.message());
            return;
        }
        if (registration.outcome() == DepositLedger.Outcome.ALREADY_REGISTERED) {
            tally.alreadyRegistered++;
        }

        MaterializationService.Result result = MaterializationService.materialize(level, deposit);
        DepositRegistrar.recordCompletePass(level, instanceId, result, deposit.count());

        tally.placed += result.placed();
        tally.rejected += result.totalRejected();
        tally.truncated |= result.truncated();

        LOGGER.info("Deposit {} ({}) at {}: {} of {} cells written, registration {} ({})",
                Long.toHexString(instanceId), resource.path(), origin.toShortString(),
                result.placed(), deposit.count(), registration.outcome(), result.describeRejections());
    }
}

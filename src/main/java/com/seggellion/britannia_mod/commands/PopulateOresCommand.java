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
import com.seggellion.britannia_mod.features.ClusterVein;
import com.seggellion.britannia_mod.features.LayeredVein;
import com.seggellion.britannia_mod.features.VerticalVein;
import com.seggellion.britannia_mod.features.VerticalLayeredVein;
import com.seggellion.britannia_mod.features.GeodeVein;
import com.seggellion.britannia_mod.features.SnakeVein;
import com.seggellion.britannia_mod.features.VeinPlacementValidation;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.ResourceShape;
import com.seggellion.britannia_mod.resource.Resources;
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
        Block oreBlock = Resources.standingBlock(resource);

        MinecraftServer server = level.getServer();
        String shardName = ModConfig.SHARD_NAME;
        List<OreVeinFetcher.OreVein> veins = OreVeinFetcher.fetchOreVeins(server, shardName);

        // Per invocation, never static: see the class comment.
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        int placedCount = 0;
        int skipped = 0;

        LOGGER.info("Populating ore: {}", oreType);

        for (OreVeinFetcher.OreVein vein : veins) {
            if (!vein.oreType.equalsIgnoreCase(oreType)) {
                continue;
            }
            Optional<String> rejection = validate(level, vein);
            if (rejection.isPresent()) {
                skipped++;
                reportSkip(source, vein, rejection.get());
                continue;
            }
            String rotation = VeinPlacementValidation
                    .normaliseRotation(vein.rotation).orElse(VeinPlacementValidation.DEFAULT_ROTATION);
            LOGGER.info("Placing ore at: {} radius {}", vein.getPosition(), vein.radius);
            placedCount += generateOreVein(level, vein.getPosition(), vein.radius,
                    resource, oreBlock, rotation, originalBlocks);
        }

        final int finalPlacedCount = placedCount;
        final int finalSkipped = skipped;
        final String finalOreType = oreType;
        source.sendSuccess(() -> Component.literal("Populated " + finalPlacedCount + " blocks of "
                + finalOreType + (finalSkipped > 0 ? " (" + finalSkipped + " row(s) skipped)" : "")), true);

        return placedCount;
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

        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        int placedCount = 0;
        int skipped = 0;

        LOGGER.info("Populating ores for region: {}", region);
        if (oreType != null) LOGGER.info("Filtered by ore type: {}", oreType);

        for (OreVeinFetcher.OreVein vein : veins) {
            if (!region.equalsIgnoreCase(vein.region)) continue;
            if (oreType != null && !vein.oreType.equalsIgnoreCase(oreType)) continue;

            ResourceDefinition resource =
                    VeinPlacementValidation.resourceFor(vein.oreType).orElse(null);
            Optional<String> rejection = resource == null
                    ? Optional.of("unknown or unplaceable ore type '" + vein.oreType + "'")
                    : validate(level, vein);
            if (rejection.isPresent()) {
                skipped++;
                reportSkip(source, vein, rejection.get());
                continue;
            }

            String rotation = VeinPlacementValidation
                    .normaliseRotation(vein.rotation).orElse(VeinPlacementValidation.DEFAULT_ROTATION);
            LOGGER.info("Placing {} at {} with radius {}", vein.oreType, vein.getPosition(), vein.radius);
            placedCount += generateOreVein(level, vein.getPosition(), vein.radius,
                    resource, Resources.standingBlock(resource), rotation, originalBlocks);
        }

        final int finalPlacedCount = placedCount;
        final int finalSkipped = skipped;
        source.sendSuccess(() -> Component.literal("Populated " + finalPlacedCount + " blocks in region "
                + region + (oreType != null ? (" for ore " + oreType) : "")
                + (finalSkipped > 0 ? " (" + finalSkipped + " row(s) skipped)" : "")), true);

        return placedCount;
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

    /**
     * Dispatch to the legacy implementation of this resource's configured shape.
     *
     * <p>Milestone 2's central claim, in one method: this used to switch on the ore <em>name</em>,
     * so adding a resource meant editing Java. It now switches on the {@link ResourceShape} the
     * resource's definition names, so a new ore that reuses an existing shape is a row in
     * {@code resources.json} and nothing else. The remaining branch is a shape-to-implementation
     * lookup, which milestone 3 replaces with a planner registry when the six imperative classes
     * become deterministic pure planners.
     *
     * <p>Every caller validates first, so the ranges each algorithm needs are already guaranteed.
     */
    private static int generateOreVein(
            ServerLevel level,
            BlockPos center,
            int radius,
            ResourceDefinition resource,
            Block oreBlock,
            String rotation,
            Map<BlockPos, BlockState> originalBlocks) {

        ResourceShape shape = resource.generation().orElseThrow().shape();
        return switch (shape) {
            case CLUSTER ->
                    ClusterVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
            case VERTICAL ->
                    VerticalVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
            case SNAKE ->
                    SnakeVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
            case GEODE ->
                    GeodeVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
            case VERTICAL_LAYERED ->
                    VerticalLayeredVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
            case LAYERED ->
                    LayeredVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
        };
    }
}

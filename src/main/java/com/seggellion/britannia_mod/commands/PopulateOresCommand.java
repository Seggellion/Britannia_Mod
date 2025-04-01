package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;


import com.seggellion.britannia_mod.util.OreVeinFetcher;
import com.seggellion.britannia_mod.features.ClusterVein;
import com.seggellion.britannia_mod.features.LayeredVein;
import com.seggellion.britannia_mod.features.VerticalVein;
import com.seggellion.britannia_mod.features.VerticalLayeredVein;
import com.seggellion.britannia_mod.features.GeodeVein;
import com.seggellion.britannia_mod.features.SnakeVein;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.config.ModConfig;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
public class PopulateOresCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<String, Block> ORE_TYPES = new HashMap<>();
    private static final Map<BlockPos, BlockState> originalBlocks = new HashMap<>(); // Stores original block states

    static {
        ORE_TYPES.put("copper", BlockRegistry.COPPER_ORE.get());
        ORE_TYPES.put("tin", BlockRegistry.TIN_ORE.get());
        ORE_TYPES.put("silver", BlockRegistry.SILVER_ORE.get());
        ORE_TYPES.put("coal", Blocks.COAL_ORE);
        ORE_TYPES.put("gold", Blocks.GOLD_ORE);
        ORE_TYPES.put("iron", Blocks.IRON_ORE);
        ORE_TYPES.put("shadow_iron", BlockRegistry.SHADOW_IRON_ORE.get());
        ORE_TYPES.put("agapite", BlockRegistry.AGAPITE_ORE.get());
        ORE_TYPES.put("verite", BlockRegistry.VERITE_ORE.get());
        ORE_TYPES.put("valorite", BlockRegistry.VALORITE_ORE.get());
        ORE_TYPES.put("high_purity_silver", BlockRegistry.HIGH_PURITY_SILVER_ORE.get());
        ORE_TYPES.put("diamond", Blocks.DIAMOND_ORE);
        ORE_TYPES.put("deepslate_diamond", Blocks.DEEPSLATE_DIAMOND_ORE);
        ORE_TYPES.put("redstone", Blocks.REDSTONE_ORE);
        ORE_TYPES.put("deepslate_redstone", Blocks.DEEPSLATE_REDSTONE_ORE);
        ORE_TYPES.put("vanilla_copper", Blocks.COPPER_ORE);
        ORE_TYPES.put("emerald", Blocks.EMERALD_ORE);
        ORE_TYPES.put("deepslate_emerald", Blocks.DEEPSLATE_EMERALD_ORE);
        ORE_TYPES.put("deepslate_lapis", Blocks.DEEPSLATE_LAPIS_ORE);
        ORE_TYPES.put("lapis", Blocks.LAPIS_ORE);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("populateores")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear")
                    .executes(PopulateOresCommand::executeClearAllOres)
                    .then(Commands.argument("ore_type", StringArgumentType.string())
                        .executes(PopulateOresCommand::executeClearSpecificOre)
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
                .executes(PopulateOresCommand::executeUndoOres)
        );
    }

    private static int executePopulateOres(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String oreType = StringArgumentType.getString(context, "ore_type").toLowerCase();

        Block oreBlock = ORE_TYPES.get(oreType);
        if (oreBlock == null) {
            source.sendFailure(Component.literal("Unknown ore type: " + oreType));
            return 0;
        }


        MinecraftServer server = level.getServer();
        String shardName = ModConfig.SHARD_NAME;
        List<OreVeinFetcher.OreVein> veins = OreVeinFetcher.fetchOreVeins(server, shardName);

        int placedCount = 0;

        LOGGER.info("Populating ore: {}", oreType);

        for (OreVeinFetcher.OreVein vein : veins) {
            LOGGER.info("Checking vein: {} at {}", vein.oreType, vein.getPosition());

            if (vein.oreType.equals(oreType)) {
                String rotation = vein.rotation != null ? vein.rotation : "XZ";
                LOGGER.info("Placing ore at: {} radius {}", vein.getPosition(), vein.radius);
                placedCount += generateOreVein(level, vein.getPosition(), vein.radius, oreType, oreBlock, rotation);
            }
        }

        // ✅ Fix: Store final variables before passing to lambda
        final int finalPlacedCount = placedCount;
        final String finalOreType = oreType;
        source.sendSuccess(() -> Component.literal("Populated " + finalPlacedCount + " blocks of " + finalOreType), true);

        return placedCount;
    }


    private static int executeUndoOres(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        if (originalBlocks.isEmpty()) {
            source.sendFailure(Component.literal("No ores have been placed yet!"));
            return 0;
        }

        int restoredCount = 0;

        for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
            restoredCount++;
        }

        originalBlocks.clear();

        // ✅ Fix: Store final variable before lambda
        final int finalRestoredCount = restoredCount;
        source.sendSuccess(() -> Component.literal("Restored " + finalRestoredCount + " blocks to their original state."), true);

        return restoredCount;
    }


// In PopulateOresCommand.java

private static int executeClearAllOres(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ServerLevel level = source.getLevel();
    BlockPos center = BlockPos.containing(source.getPosition());

    int cleared = clearAllOres(level, center);

    final int finalCleared = cleared;
    source.sendSuccess(() -> Component.literal("Globally cleared " + finalCleared + " ore blocks from the world."), true);
    return cleared;
}

private static int executeClearSpecificOre(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ServerLevel level = source.getLevel();
    BlockPos center = BlockPos.containing(source.getPosition());
    String oreType = StringArgumentType.getString(context, "ore_type").toLowerCase();

    Block oreBlock = ORE_TYPES.get(oreType);
    if (oreBlock == null) {
        source.sendFailure(Component.literal("Unknown ore type: " + oreType));
        return 0;
    }

    int cleared = clearAllPlacedOres(level, center, oreBlock);

    final int finalCleared = cleared;
    source.sendSuccess(() -> Component.literal("Globally cleared " + finalCleared + " blocks of " + oreType + "."), true);
    return cleared;
}



private static int clearOreVein(ServerLevel level, BlockPos center, int radius, Block targetOreBlock) {
    int cleared = 0;
    int buffer = 6;
    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

    for (int x = -radius - buffer; x <= radius + buffer; x++) {
        for (int y = -10; y <= 10; y++) {
            for (int z = -radius - buffer; z <= radius + buffer; z++) {
                mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                if (level.getBlockState(mutable).is(targetOreBlock)) {
                    level.setBlock(mutable, Blocks.STONE.defaultBlockState(), 2);
                    cleared++;
                }
            }
        }
    }

    return cleared;
}

private static final int CHUNK_RADIUS = 20; // Radius of chunks from center to scan (can be increased)

private static int clearAllOres(ServerLevel level, BlockPos center) {
    int cleared = 0;
    int centerChunkX = center.getX() >> 4;
    int centerChunkZ = center.getZ() >> 4;

    for (int chunkX = centerChunkX - CHUNK_RADIUS; chunkX <= centerChunkX + CHUNK_RADIUS; chunkX++) {
        for (int chunkZ = centerChunkZ - CHUNK_RADIUS; chunkZ <= centerChunkZ + CHUNK_RADIUS; chunkZ++) {
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            cleared += clearChunkOres(level, chunk, null);
        }
    }

    return cleared;
}

private static int clearAllPlacedOres(ServerLevel level, BlockPos center, Block targetOreBlock) {
    int cleared = 0;
    int centerChunkX = center.getX() >> 4;
    int centerChunkZ = center.getZ() >> 4;

    for (int chunkX = centerChunkX - CHUNK_RADIUS; chunkX <= centerChunkX + CHUNK_RADIUS; chunkX++) {
        for (int chunkZ = centerChunkZ - CHUNK_RADIUS; chunkZ <= centerChunkZ + CHUNK_RADIUS; chunkZ++) {
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            cleared += clearChunkOres(level, chunk, targetOreBlock);
        }
    }

    return cleared;
}


private static int clearChunkOres(ServerLevel level, LevelChunk chunk, Block specificOre) {
    int cleared = 0;
    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

    for (int x = 0; x < 16; x++) {
        for (int z = 0; z < 16; z++) {
            for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y++) {
                mutable.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                Block block = level.getBlockState(mutable).getBlock();

                if ((specificOre == null && ORE_TYPES.containsValue(block)) ||
                    (specificOre != null && block.equals(specificOre))) {
                    level.setBlock(mutable, Blocks.STONE.defaultBlockState(), 2);
                    cleared++;
                }
            }
        }
    }

    return cleared;
}

    private static int executePopulateRegionOres(CommandContext<CommandSourceStack> context) {
        String region = StringArgumentType.getString(context, "region_name").toLowerCase();
        return populateRegion(context, region, null);
    }

    private static int executePopulateRegionOresWithType(CommandContext<CommandSourceStack> context) {
        String region = StringArgumentType.getString(context, "region_name").toLowerCase();
        String oreType = StringArgumentType.getString(context, "ore_type").toLowerCase();
        return populateRegion(context, region, oreType);
    }


    private static int populateRegion(CommandContext<CommandSourceStack> context, String region, String oreType) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        MinecraftServer server = level.getServer();
        String shardName = ModConfig.SHARD_NAME;
        List<OreVeinFetcher.OreVein> veins = OreVeinFetcher.fetchOreVeins(server, shardName);

        int placedCount = 0;

        LOGGER.info("Populating ores for region: {}", region);
        if (oreType != null) LOGGER.info("Filtered by ore type: {}", oreType);

        for (OreVeinFetcher.OreVein vein : veins) {
            if (!region.equalsIgnoreCase(vein.region)) continue;
            if (oreType != null && !vein.oreType.equalsIgnoreCase(oreType)) continue;

            Block oreBlock = ORE_TYPES.get(vein.oreType.toLowerCase());
            if (oreBlock == null) {
                LOGGER.warn("Unknown ore block for type: {}", vein.oreType);
                continue;
            }

            String rotation = vein.rotation != null ? vein.rotation : "XZ";
            LOGGER.info("Placing {} at {} with radius {}", vein.oreType, vein.getPosition(), vein.radius);
            placedCount += generateOreVein(level, vein.getPosition(), vein.radius, vein.oreType, oreBlock, rotation);
        }

        final int finalPlacedCount = placedCount;
        source.sendSuccess(() -> Component.literal("Populated " + finalPlacedCount + " blocks in region " + region + (oreType != null ? (" for ore " + oreType) : "")), true);

        return placedCount;
    }


    private static int generateOreVein(ServerLevel level, BlockPos center, int radius, String oreType, Block oreBlock, String rotation) {
        int placedBlocks = 0;

        switch (oreType) {
            case "copper", "verite":
                placedBlocks = ClusterVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
                break;
            case "iron", "valorite", "shadow_iron":
                placedBlocks = VerticalVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
                break;
            case "gold":
                placedBlocks = SnakeVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
                break;
            case "agapite":
                placedBlocks = GeodeVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
                break;
            case "silver":
                placedBlocks = VerticalLayeredVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
                break;
            case "coal", "tin":
                placedBlocks = LayeredVein.generate(level, center, radius, oreBlock, rotation, originalBlocks);
                break;
            default:
                return 0;
        }

        return placedBlocks;
    }
}


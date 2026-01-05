package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.seggellion.britannia_mod.block.CustomWallBlock;
import com.seggellion.britannia_mod.client.RegionCache;
import com.seggellion.britannia_mod.util.RegionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Locale;

public class RandomizeWallsCommand {

    private static final int DEFAULT_CHUNK_RADIUS = 8;
    private static final int VIEW_DISTANCE = 12; // used for global scan around each player

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("randomizewalls")
                .requires(source -> source.hasPermission(2))

                .executes(ctx -> executeLocal(ctx.getSource(), DEFAULT_CHUNK_RADIUS, null))

                .then(Commands.literal("radius")
                    .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> executeLocal(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "chunks"), null))
                        .then(Commands.argument("block_name", StringArgumentType.string())
                            .executes(ctx -> executeLocal(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "chunks"),
                                StringArgumentType.getString(ctx, "block_name"))))
                    )
                )

                .then(Commands.literal("region")
                    .then(Commands.argument("region_name", StringArgumentType.string())
                        .executes(ctx -> executeRegion(ctx.getSource(),
                            StringArgumentType.getString(ctx, "region_name"), null))
                        .then(Commands.argument("block_name", StringArgumentType.string())
                            .executes(ctx -> executeRegion(ctx.getSource(),
                                StringArgumentType.getString(ctx, "region_name"),
                                StringArgumentType.getString(ctx, "block_name"))))
                    )
                )

                .then(Commands.literal("global")
                    .executes(ctx -> executeGlobal(ctx.getSource(), null))
                    .then(Commands.argument("block_name", StringArgumentType.string())
                        .executes(ctx -> executeGlobal(ctx.getSource(),
                            StringArgumentType.getString(ctx, "block_name"))))
                )
        );
    }

    // ------------------------------------------------------------------------
    // Command Implementations
    // ------------------------------------------------------------------------

    private static int executeLocal(CommandSourceStack source, int chunkRadius, String targetBlockName) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());

        int randomized = randomizeInRadius(level, center, chunkRadius, targetBlockName);
        source.sendSuccess(() ->
            Component.literal("Randomized " + randomized + " CustomWallBlocks within "
                + chunkRadius + " chunks"
                + (targetBlockName != null ? " matching " + targetBlockName : "")), true);
        return randomized;
    }

    private static int executeGlobal(CommandSourceStack source, String targetBlockName) {
        ServerLevel level = source.getLevel();
        int randomized = randomizeVisibleChunks(level, targetBlockName);

        source.sendSuccess(() ->
            Component.literal("Globally randomized " + randomized + " CustomWallBlocks"
                + (targetBlockName != null ? " matching " + targetBlockName : "")), true);
        return randomized;
    }

    private static int executeRegion(CommandSourceStack source, String regionName, String targetBlockName) {
        ServerLevel level = source.getLevel();
        RegionData region = RegionCache.all().stream()
            .filter(r -> r.name.equalsIgnoreCase(regionName))
            .findFirst()
            .orElse(null);

        if (region == null) {
            source.sendFailure(Component.literal("Region '" + regionName + "' not found."));
            return 0;
        }

        int randomized = randomizeRegion(level, region, targetBlockName);
        source.sendSuccess(() ->
            Component.literal("Randomized " + randomized + " CustomWallBlocks in region '" + regionName + "'"
                + (targetBlockName != null ? " matching " + targetBlockName : "")), true);
        return randomized;
    }

    // ------------------------------------------------------------------------
    // Randomization Logic
    // ------------------------------------------------------------------------

    /**
     * Randomizes all loaded / visible chunks around all online players.
     */
    private static int randomizeVisibleChunks(ServerLevel level, String targetBlockName) {
        int randomized = 0;

        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = player.chunkPosition();

            for (int dx = -VIEW_DISTANCE; dx <= VIEW_DISTANCE; dx++) {
                for (int dz = -VIEW_DISTANCE; dz <= VIEW_DISTANCE; dz++) {
                    int cx = playerChunk.x + dx;
                    int cz = playerChunk.z + dz;

                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = level.getChunk(cx, cz);
                    randomized += randomizeChunkWalls(level, chunk, targetBlockName);
                }
            }
        }
        return randomized;
    }

    private static int randomizeInRadius(ServerLevel level, BlockPos center, int chunkRadius, String targetBlockName) {
        int randomized = 0;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                randomized += randomizeChunkWalls(level, chunk, targetBlockName);
            }
        }
        return randomized;
    }

    private static int randomizeRegion(ServerLevel level, RegionData region, String targetBlockName) {
        int randomized = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    Block block = state.getBlock();

                    if (block instanceof CustomWallBlock custom) {
                        if (targetBlockName != null &&
                            !block.toString().toLowerCase(Locale.ROOT)
                                .contains(targetBlockName.toLowerCase(Locale.ROOT))) {
                            continue;
                        }

                        int randomVariant = level.random.nextInt(custom.getTextureVariants().size());
                        boolean mirror = level.random.nextBoolean();
                        level.setBlock(mutable, state
                            .setValue(CustomWallBlock.VARIANT, randomVariant)
                            .setValue(CustomWallBlock.MIRRORED, mirror), 3);
                        randomized++;
                    }
                }
            }
        }
        return randomized;
    }

    private static int randomizeChunkWalls(ServerLevel level, LevelChunk chunk, String targetBlockName) {
        int randomized = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y++) {
                    mutable.set(chunk.getPos().getMinBlockX() + x, y,
                                chunk.getPos().getMinBlockZ() + z);
                    BlockState state = level.getBlockState(mutable);
                    Block block = state.getBlock();

                    if (block instanceof CustomWallBlock custom) {
                        if (targetBlockName != null &&
                            !block.toString().toLowerCase(Locale.ROOT)
                                .contains(targetBlockName.toLowerCase(Locale.ROOT))) {
                            continue;
                        }

                        int randomVariant = level.random.nextInt(custom.getTextureVariants().size());
                        boolean mirror = level.random.nextBoolean();

                        level.setBlock(mutable, state
                            .setValue(CustomWallBlock.VARIANT, randomVariant)
                            .setValue(CustomWallBlock.MIRRORED, mirror), 3);
                        randomized++;
                    }
                }
            }
        }
        return randomized;
    }
}

package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StructureCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structures")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("list_all")
                .executes(ctx -> listAllStructures(ctx.getSource())))
            .then(Commands.literal("list_chunk")
                .executes(ctx -> listStructuresInChunk(ctx.getSource())))
        );
    }

    private static int listAllStructures(CommandSourceStack source) {
        Map<StructureRegionManager.RegionKey, List<StructureRecord>> structureMap =
                StructureRegionManager.getChunkStructureMap();

        if (structureMap.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No structures registered."), false);
            return 0;
        }

        int[] count = {0};
        for (List<StructureRecord> records : structureMap.values()) {
            for (StructureRecord record : records) {
                sendStructureInfo(source, record);
                count[0]++;
            }
        }

        source.sendSuccess(() -> Component.literal("Total structures: " + count[0]), false);
        return count[0];
    }

    private static int listStructuresInChunk(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        List<StructureRecord> records = StructureRegionManager.getStructuresInChunk(
                player.level().dimension(), chunkPos.x, chunkPos.z);

        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No structures in current chunk."), false);
            return 0;
        }

        for (StructureRecord record : records) {
            sendStructureInfo(source, record);
        }

        source.sendSuccess(() -> Component.literal("Structures in current chunk: " + records.size()), false);
        return records.size();
    }

    private static void sendStructureInfo(CommandSourceStack source, StructureRecord record) {
        UUID uuid = record.getHouseUuid();
        String style = record.getStyleId();
        String posStr = String.format("(%d, %d, %d)",
            (int) record.getStructureBox().minX,
            (int) record.getStructureBox().minY,
            (int) record.getStructureBox().minZ
        );

        source.sendSuccess(() -> Component.literal(
            String.format("UUID: %s | Pos: %s | Style: %s", uuid, posStr, style)
        ), false);
    }
}

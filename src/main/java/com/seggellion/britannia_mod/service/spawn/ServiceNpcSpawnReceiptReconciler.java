package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;

/** Server-thread-only, location-directed reconciliation that never loads a chunk. */
public final class ServiceNpcSpawnReceiptReconciler {
    public static final int MAX_RECEIPTS_PER_CYCLE = 4;

    private ServiceNpcSpawnReceiptReconciler() {}

    public static void reconcileReceipts(MinecraftServer server) {
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(server.overworld());
        List<ServiceNpcSpawnAcknowledgementReceipt> receipts = data.snapshotAcknowledgements().values().stream()
            .sorted(Comparator
                .comparingLong(ServiceNpcSpawnAcknowledgementReceipt::acknowledgedAtEpochMillis)
                .thenComparing(ServiceNpcSpawnAcknowledgementReceipt::spawnPointId)
                .thenComparing(ServiceNpcSpawnAcknowledgementReceipt::operationId))
            .limit(MAX_RECEIPTS_PER_CYCLE)
            .toList();
        receipts.forEach(receipt -> reconcileLocation(server, receipt));
    }

    public static void reconcileLocation(
            MinecraftServer server, ServiceNpcSpawnAcknowledgementReceipt receipt
    ) {
        ServerLevel level = loadedLevel(server, receipt.location());
        if (level == null) return;
        BlockPos pos = receipt.location().pos();
        if (!level.getBlockState(pos).is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get())) return;
        if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity)) return;
        reconcileLoadedBlock(level, blockEntity);
    }

    public static void reconcilePendingLocation(
            MinecraftServer server, ServiceNpcSpawnPendingRecord pending
    ) {
        ServerLevel level = loadedLevel(server, pending.location());
        if (level == null) return;
        BlockPos pos = pending.location().pos();
        if (!level.getBlockState(pos).is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get())) return;
        if (level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity blockEntity
                && blockEntity.matchesPending(pending, level)) {
            blockEntity.applyPendingDeliveryState(pending);
        }
    }

    public static void reconcileLoadedBlock(
            ServerLevel level, ServiceNpcSpawnBlockEntity blockEntity
    ) {
        if (!level.getBlockState(blockEntity.getBlockPos())
                .is(BlockRegistry.SERVICE_NPC_SPAWN_BLOCK.get())) {
            return;
        }
        if (blockEntity.getSpawnPointId() == null) return;
        ServiceNpcSpawnPendingData data = ServiceNpcSpawnPendingData.get(level);
        ServiceNpcSpawnAcknowledgementReceipt receipt =
            data.findAcknowledgement(blockEntity.getSpawnPointId());
        if (receipt != null && blockEntity.matchesAcknowledgement(receipt, level)) {
            if (blockEntity.hasAcknowledgementMarker(receipt)) {
                data.consumeAcknowledgementIfMatches(
                    receipt.spawnPointId(), receipt.location(),
                    receipt.configurationRevision(), receipt.operationId()
                );
            } else {
                blockEntity.applyAcknowledgement(receipt);
            }
            return;
        }
        ServiceNpcSpawnPendingRecord pending = data.snapshot().get(blockEntity.getSpawnPointId());
        if (pending != null && blockEntity.matchesPending(pending, level)) {
            blockEntity.applyPendingDeliveryState(pending);
        }
    }

    static boolean isChunkLoaded(MinecraftServer server, ServiceNpcSpawnLocation location) {
        return loadedLevel(server, location) != null;
    }

    private static ServerLevel loadedLevel(MinecraftServer server, ServiceNpcSpawnLocation location) {
        if (!server.getWorldData().getLevelName().equals(location.worldName())) return null;
        ResourceKey<net.minecraft.world.level.Level> dimension =
            ResourceKey.create(Registries.DIMENSION, location.dimension());
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return null;
        BlockPos pos = location.pos();
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) ? level : null;
    }
}

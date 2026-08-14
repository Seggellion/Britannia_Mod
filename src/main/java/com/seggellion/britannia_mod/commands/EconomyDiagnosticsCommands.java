package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.service.spawn.SpawnPostDiagnostics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.time.Instant;

/**
 * Vendor/Trader Milestone 18: {@code /economy post} — safe, read-only operator
 * diagnostics for the nearest authoritative spawn post (permission level 2).
 * Formats {@link SpawnPostDiagnostics}; collection logic lives there so the
 * GameTests prove the numbers.
 */
public final class EconomyDiagnosticsCommands {
    private static final int SEARCH_RADIUS = 16;

    private EconomyDiagnosticsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("economy")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("post")
                                .executes(ctx -> describeNearestPost(ctx.getSource()))
                        )
        );
    }

    private static int describeNearestPost(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        ServiceNpcSpawnBlockEntity post = findNearestPost(level, origin);
        if (post == null) {
            source.sendFailure(Component.literal(
                    "No authoritative spawn post within " + SEARCH_RADIUS + " blocks."));
            return 0;
        }

        SpawnPostDiagnostics.Snapshot snapshot = SpawnPostDiagnostics.collect(level, post);
        send(source, "Spawn post " + snapshot.postId() + " at " + post.getBlockPos().toShortString());
        send(source, "  city: " + orDash(snapshot.cityPublicId())
                + "  type: " + orDash(snapshot.storedTypeKey())
                + (snapshot.economic() ? " (economic:" + snapshot.economicTypeKey() + ")" : ""));
        send(source, "  enabled: " + snapshot.enabled()
                + "  registration: " + snapshot.registrationState()
                + "  revision: " + snapshot.configurationRevision()
                + (snapshot.lastErrorCode() == null ? "" : "  lastError: " + snapshot.lastErrorCode()));
        send(source, "  assigned NPC: " + orDash(snapshot.assignedNpcDisplayName())
                + " " + orDash(snapshot.assignedNpcPublicId())
                + "  assignment revision: " + snapshot.assignmentRevision());
        send(source, "  last sync: " + epoch(snapshot.lastSuccessfulSyncEpochMillis())
                + "  last ack: " + epoch(snapshot.lastAcknowledgedAtEpochMillis()));
        send(source, "  pending operations (outbox): " + snapshot.pendingOperationCount()
                + "  economic registry revision: " + orDash(snapshot.economicRegistryRevision()));
        send(source, "  stamped projections for this assignment: " + snapshot.stampedEntityCount()
                + (snapshot.duplicateEntitiesDetected() ? "  DUPLICATES DETECTED" : ""));
        return 1;
    }

    private static ServiceNpcSpawnBlockEntity findNearestPost(ServerLevel level, BlockPos origin) {
        ServiceNpcSpawnBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (!(level.getBlockEntity(pos) instanceof ServiceNpcSpawnBlockEntity post)) continue;
            double distance = pos.distSqr(origin);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = post;
            }
        }
        return nearest;
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static String orDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String epoch(Long epochMillis) {
        return epochMillis == null ? "-" : Instant.ofEpochMilli(epochMillis).toString();
    }
}

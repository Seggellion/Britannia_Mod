package com.seggellion.britannia_mod.worldstate;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;
import com.seggellion.britannia_mod.service.ServiceNpcAssignmentsCache;
import com.seggellion.britannia_mod.sync.WorldBootstrapAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Milestone 13 NeoForge Slice 3: the fallback {@link WorldStateSyncPoller} triggers when a poll
 * response reports {@code full_bootstrap_required} -- Rails is telling this server its delta
 * history has a gap this client cannot safely apply incrementally (see {@code
 * WorldStateChanges::ChangesSince}'s own gap-detection docs on the Rails side), so the only
 * correct recovery is a fresh full fetch, exactly the same one a player login already performs
 * (Milestone 6).
 *
 * Reuses {@link WorldBootstrapAPI#fetch(ServerPlayer)} verbatim for the actual network fetch and
 * parse -- not a new HTTP client. Deliberately does NOT reuse {@code WorldBootstrapHandler}'s own
 * {@code apply()} step, though: that method also re-syncs shard_user stats, quests, and city
 * treasuries for the specific player it is called with -- real side effects (including pushing a
 * network payload to that player) that make sense at a real login moment but not from a
 * background world-state poll firing for an arbitrary currently-online player who has nothing to
 * do with why this fetch happened. This applies only the one section Milestone 13 actually owns,
 * {@code service_npc_assignments}, into {@link ServiceNpcAssignmentsCache}, together with the
 * version this recovers to, using the same durable single-write commit Slice 2 established for
 * the ordinary delta-apply path (see {@link ServiceNpcAssignmentsCache#replaceFromFullBootstrapFallback}).
 *
 * The fetch itself needs a real, currently-online {@link ServerPlayer} to authenticate as (the
 * bootstrap endpoint has no service-account identity) -- the resulting {@code
 * service_npc_assignments} section is filtered to this server by {@link WorldBootstrapAPI#fetch}
 * itself using this server's own credentials, not the chosen player's identity, so which online
 * player is picked has no bearing on the result. If none are online, there is nothing to
 * authenticate the fetch with; this reports {@link NoPlayerOnline} without advancing any version,
 * and the next scheduled poll simply tries again -- exactly like any other failed poll.
 */
public final class WorldStateFullBootstrapFallback {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldStateFullBootstrapFallback() {
    }

    public sealed interface Result permits Applied, NoPlayerOnline, Failed {
    }

    public record Applied(long version) implements Result {
    }

    public record NoPlayerOnline() implements Result {
    }

    public record Failed(String safeCode) implements Result {
    }

    /**
     * @param targetVersion the version this fallback recovers to -- the triggering poll
     *                      response's own {@code current_version}, not anything this method
     *                      derives itself (the bootstrap wire payload carries no version field).
     */
    public static CompletableFuture<Result> triggerAndApply(MinecraftServer server, long targetVersion) {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.isEmpty()) {
            LOGGER.warn("World state full bootstrap fallback skipped: no player online to authenticate the fetch");
            return CompletableFuture.completedFuture(new NoPlayerOnline());
        }
        ServerPlayer player = online.get(0);

        CompletableFuture<Result> outcome = new CompletableFuture<>();
        ServerHttpExecutor.submit(server, () -> WorldBootstrapAPI.fetch(player))
                .whenComplete((data, failure) -> server.execute(() -> {
                    if (failure != null || data == null || !data.successful()) {
                        String code = failure != null
                                ? "transport_error"
                                : (data != null && data.failureCode() != null ? data.failureCode() : "fetch_failed");
                        LOGGER.warn("World state full bootstrap fallback failed code={}", code);
                        outcome.complete(new Failed(code));
                        return;
                    }
                    ServiceNpcAssignmentsCache.get(server.overworld())
                            .replaceFromFullBootstrapFallback(data.serviceNpcAssignments(), targetVersion);
                    server.overworld().getDataStorage().save();
                    LOGGER.info("World state full bootstrap fallback applied version={}", targetVersion);
                    outcome.complete(new Applied(targetVersion));
                }));
        return outcome;
    }
}
